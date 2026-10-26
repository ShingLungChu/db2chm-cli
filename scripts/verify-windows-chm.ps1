param([Parameter(Mandatory=$true)][string]$ArtifactRoot)
$ErrorActionPreference = 'Stop'
$hh = Join-Path $env:WINDIR 'hh.exe'
$reports = @(Get-ChildItem -LiteralPath $ArtifactRoot -Filter verification.json -Recurse -File)
if ($reports.Count -ne 12) { throw "Expected 12 application scenarios, found $($reports.Count)" }
$results = @()
foreach ($report in $reports) {
  $manifest = Get-Content -LiteralPath $report.FullName -Raw -Encoding utf8 | ConvertFrom-Json
  $source = $report.Directory.FullName
  $chm = Join-Path $source 'database.chm'
  $work = Join-Path $env:RUNNER_TEMP ([guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $work | Out-Null
  $inputChm = Join-Path $work 'database.chm'
  Copy-Item -LiteralPath $chm -Destination $inputChm
  $destination = Join-Path $work 'extracted'
  New-Item -ItemType Directory -Path $destination | Out-Null
  # hh.exe's legacy decompile parser treats quote characters literally.
  if (($destination + $inputChm) -match '[^\x21-\x7e]') { throw 'hh.exe validation workspace must use ASCII paths without spaces' }
  $arguments = @('-decompile',$destination,$inputChm)
  $process = Start-Process -FilePath $hh -ArgumentList $arguments -WindowStyle Hidden -Wait -PassThru
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  do {
    $missing = @($manifest.files | Where-Object { -not (Test-Path -LiteralPath (Join-Path $destination $_.file) -PathType Leaf) })
    if ($missing.Count -eq 0) { break }
    Start-Sleep -Milliseconds 200
  } while ([DateTime]::UtcNow -lt $deadline)
  foreach ($file in $manifest.files) {
    $extracted = Join-Path $destination $file.file
    if (-not (Test-Path -LiteralPath $extracted -PathType Leaf)) { throw "Missing extracted $($file.file) from $chm" }
    if ((Get-FileHash -LiteralPath $extracted -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256) { throw "Content mismatch: $extracted" }
  }
  $results += [pscustomobject]@{source=$source; scenario=$manifest.scenario; tableCount=$manifest.tableCount; extractedVerified=$manifest.files.Count; hhExitCode=$process.ExitCode; viewerInteraction='pending'}
}
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ArtifactRoot 'windows-verification.json') -Encoding utf8
Write-Output 'Extraction and content hashes passed. Viewer TOC, index and search acceptance remains pending.'
