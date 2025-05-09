# download-model.ps1

$ErrorActionPreference = 'Stop'

$ModelDir    = 'model\vosk-model-small-en-us-0.15'
$ZipName     = 'vosk-model-small-en-us-0.15.zip'
$DownloadUrl = "https://alphacephei.com/vosk/models/$ZipName"

if (-not (Test-Path 'model')) {
    New-Item -ItemType Directory -Path 'model' | Out-Null
}

if (Test-Path $ModelDir) {
    Write-Host "Model already exists at $ModelDir"
    exit 0
}

Write-Host "Downloading Vosk model from $DownloadUrl..."
Invoke-WebRequest -Uri $DownloadUrl -OutFile "model\$ZipName"

Write-Host "Extracting to model\..."
Expand-Archive -Path "model\$ZipName" -DestinationPath model

Write-Host "Removing ZIP file"
Remove-Item "model\$ZipName"

Write-Host "Model is ready at $ModelDir"
