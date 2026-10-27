param([Parameter(Mandatory=$true)][string]$BootstrapRoot,[string]$OutDir=(Join-Path $PSScriptRoot 'build'))
$ErrorActionPreference='Stop'
$compiler=Join-Path $BootstrapRoot 'bin/i386-win32/ppcrossx64.exe'
if((Get-FileHash -LiteralPath $compiler -Algorithm SHA256).Hash.ToLowerInvariant() -ne '3c230fa579aaa595382b7e7e9daddd00adfd196e9743112bd551141de260b0bc'){throw 'Use the documented pinned FPC bootstrap'}
if((& $compiler -iW).Trim() -ne '3.3.1-21298-g814ece0205'){throw 'Unexpected FPC revision'}
$out=[IO.Path]::GetFullPath($OutDir)
if(Test-Path -LiteralPath $out){throw 'Choose a fresh output directory'}
New-Item -ItemType Directory -Path $out,(Join-Path $out 'units') | Out-Null
# Sources already contain both patches. This also permits rebuilding modified source.
& $compiler '-Twin64' '-Px86_64' '-B' '-O2' '-viu' ('-Fu'+(Join-Path $BootstrapRoot 'units/x86_64-win64/*')) ('-Fu'+(Join-Path $PSScriptRoot 'packages/chm/src')) ('-FU'+(Join-Path $out 'units')) ('-FE'+$out) (Join-Path $PSScriptRoot 'packages/chm/src/chmcmd.lpr') 2>&1 | Tee-Object -FilePath (Join-Path $out 'build.log')
if($LASTEXITCODE -ne 0){throw 'CHM utility rebuild failed'}
