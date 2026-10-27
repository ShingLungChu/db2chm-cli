param(
  [string]$OutDir = 'target/native/win32-x64',
  [string]$BootstrapArchive
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$repo = Split-Path $PSScriptRoot -Parent
$out = [IO.Path]::GetFullPath($OutDir)
if (Test-Path -LiteralPath $out) { throw 'Use a fresh native output directory' }
New-Item -ItemType Directory -Path $out | Out-Null
$work = Join-Path ([IO.Path]::GetTempPath()) ('db2chm-fpc-'+[guid]::NewGuid().ToString('N'))
$bootstrap = Join-Path $work 'bootstrap'
$source = Join-Path $work 'source'
New-Item -ItemType Directory -Path $bootstrap,$source | Out-Null
$url = 'https://downloads.freepascal.org/fpc/snapshot/trunk/x86_64-win64/fpc-3.3.1.x86_64-win64.built.on.i386-win32.zip'
$expected = '2206d8d866360844e02a2920c4827bd139ec4a8713b14be2afacc04ee3e8d66e'
if (-not $BootstrapArchive) {
  $BootstrapArchive = Join-Path $work 'fpc-win64.zip'
  Invoke-WebRequest -Uri $url -OutFile $BootstrapArchive
}
if ((Get-FileHash -LiteralPath $BootstrapArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Official bootstrap archive hash changed; review it before changing the pin' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($BootstrapArchive)
try {
  foreach ($item in $zip.Entries) {
    $target = [IO.Path]::GetFullPath((Join-Path $bootstrap $item.FullName))
    if (-not $target.StartsWith($bootstrap+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Bootstrap archive path escapes its directory' }
  }
} finally { $zip.Dispose() }
[IO.Compression.ZipFile]::ExtractToDirectory($BootstrapArchive,$bootstrap)
$compiler = Join-Path $bootstrap 'bin/i386-win32/ppcrossx64.exe'
if ((Get-FileHash -LiteralPath $compiler -Algorithm SHA256).Hash.ToLowerInvariant() -ne '3c230fa579aaa595382b7e7e9daddd00adfd196e9743112bd551141de260b0bc') { throw 'Unexpected bootstrap compiler' }
$version = (& $compiler -iV).Trim()
if ($version -ne '3.3.1') { throw 'Unexpected compiler version' }
$commit = 'de69d52048cf52c72ebfeef61617c04c9bc2005c'
& git -C $source init --quiet
& git -C $source config core.autocrlf false
& git -C $source config core.eol lf
& git -C $source remote add origin https://github.com/fpc/FPCSource.git
$fetched = $false
for ($attempt = 1; $attempt -le 3; $attempt++) {
  try {
    & git -c http.sslBackend=schannel -c http.version=HTTP/1.1 -C $source fetch --depth=1 origin $commit
    if ($LASTEXITCODE -eq 0) { $fetched = $true; break }
  } catch { if ($attempt -eq 3) { throw } }
}
if (-not $fetched) { throw 'Could not fetch pinned FPC source' }
& git -C $source checkout --detach FETCH_HEAD
if ((& git -C $source rev-parse HEAD).Trim() -ne $commit) { throw 'Unexpected source revision' }
foreach($name in @('chmcmd-fpc322.patch','chmcmd-encoding.patch')) {
  $patch = Join-Path $repo ('release/patches/'+$name)
  & git -C $source apply $patch
  if($LASTEXITCODE -ne 0){throw "Source patch failed: $name"}
  Copy-Item -LiteralPath $patch -Destination (Join-Path $out $name)
}
$units = Join-Path $out 'units'
New-Item -ItemType Directory -Path $units | Out-Null
$chmSource = Join-Path $source 'packages/chm/src'
& $compiler '-Twin64' '-Px86_64' '-B' '-O2' ('-Fu'+(Join-Path $bootstrap 'units/x86_64-win64/*')) ('-Fu'+$chmSource) ('-FU'+$units) ('-FE'+$out) (Join-Path $chmSource 'chmcmd.lpr') 2>&1 | Tee-Object -FilePath (Join-Path $out 'build.log')
if($LASTEXITCODE -ne 0){throw 'Native compiler build failed'}
if (-not (Test-Path -LiteralPath (Join-Path $out 'chmcmd.exe') -PathType Leaf)) { throw 'Compiler did not produce a fresh executable' }
# Inspection helper is a CI tool, kept outside the npm native payload.
$verify = Join-Path $repo 'target/navigation-tools'
New-Item -ItemType Directory -Path $verify -Force | Out-Null
& $compiler '-Twin64' '-Px86_64' '-B' '-O2' ('-Fu'+(Join-Path $bootstrap 'units/x86_64-win64/*')) ('-Fu'+$chmSource) ('-FU'+$units) ('-FE'+$verify) (Join-Path $repo 'scripts/chm-nav-dump.pas') > (Join-Path $verify 'build.log')
if($LASTEXITCODE -ne 0){throw 'Navigation inspector build failed'}
$imports = & node (Join-Path $repo 'scripts/inspect-pe.cjs') (Join-Path $out 'chmcmd.exe')
[IO.File]::WriteAllLines((Join-Path $out 'binary-dependencies.json'),[string[]]$imports,[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $source 'LICENSE') -Destination (Join-Path $out 'FPC-LICENSE.txt')
Copy-Item -LiteralPath (Join-Path $source 'rtl/COPYING.FPC') -Destination (Join-Path $out 'RTL-COPYING.FPC')
Copy-Item -LiteralPath (Join-Path $source 'rtl/COPYING.txt') -Destination (Join-Path $out 'RTL-COPYING.txt')
& tar -czf (Join-Path $out 'chm-source.tar.gz') -C $source packages/chm LICENSE rtl/COPYING.FPC rtl/COPYING.txt
$notice = @"
CHM utility source: https://github.com/fpc/FPCSource.git
Commit: $commit
Bootstrap FPC: $version
Bootstrap URL: $url
Bootstrap archive SHA256: $expected
Local modifications: conditional rtlString alias; explicit db2chm UTF-8/CP936 navigation, keyword and search encoding profile. Included patches record all changes.
Windows delivery: external HHC first, bundled chmcmd fallback when HHC is not found.
Formal distribution and oldest-system validation remain pending.
"@
[IO.File]::WriteAllText((Join-Path $out 'SOURCE.txt'),$notice,[Text.UTF8Encoding]::new($false))
& node (Join-Path $repo 'scripts/write-native-manifest.cjs') $out win32-x64 $commit $version $compiler $url $expected
