param([Parameter(Mandatory=$true)][string]$ArtifactRoot)
$ErrorActionPreference = 'Stop'
$hh = Join-Path $env:WINDIR 'hh.exe'
$reports = @(Get-ChildItem -LiteralPath $ArtifactRoot -Filter verification.json -Recurse -File)
if ($reports.Count -ne 6) { throw "Expected 6 application scenarios, found $($reports.Count)" }
$results = @()
foreach ($report in $reports) {
  $manifest = Get-Content -LiteralPath $report.FullName -Raw -Encoding utf8 | ConvertFrom-Json
  $source = $report.Directory.FullName
  $chm = Join-Path $source 'database.chm'
  $destination = Join-Path $env:RUNNER_TEMP ([guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $destination | Out-Null
  $arguments = '-decompile "{0}" "{1}"' -f $destination,$chm
  $process = Start-Process -FilePath $hh -ArgumentList $arguments -WindowStyle Hidden -Wait -PassThru
  foreach ($file in $manifest.files) {
    $extracted = Join-Path $destination $file.file
    if (-not (Test-Path -LiteralPath $extracted -PathType Leaf)) { throw "Missing extracted $($file.file) from $chm" }
    if ((Get-FileHash -LiteralPath $extracted -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256) { throw "Content mismatch: $extracted" }
  }
  $results += [pscustomobject]@{source=$source; scenario=$manifest.scenario; tableCount=$manifest.tableCount; extractedVerified=$manifest.files.Count; hhExitCode=$process.ExitCode; viewerInteraction='pending'}
}
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ArtifactRoot 'windows-verification.json') -Encoding utf8
Write-Output 'Extraction and content hashes passed. Viewer TOC, index and search acceptance remains pending.'
