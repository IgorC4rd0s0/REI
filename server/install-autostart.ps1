$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$startup = [Environment]::GetFolderPath('Startup')
$launcher = Join-Path $startup 'REI-Central-Server.cmd'
$command = "@echo off`r`nstart `"`" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$serverRoot\start-server.ps1`"`r`n"
[System.IO.File]::WriteAllText($launcher, $command, [System.Text.Encoding]::ASCII)
Write-Host "Inicialização automática instalada em: $launcher"
