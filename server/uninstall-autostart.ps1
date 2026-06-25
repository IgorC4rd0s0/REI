$ErrorActionPreference = 'Stop'
$startup = [Environment]::GetFolderPath('Startup')
$launcher = Join-Path $startup 'REI-Central-Server.cmd'
if (Test-Path -LiteralPath $launcher) {
    Remove-Item -LiteralPath $launcher
    Write-Host 'Inicialização automática removida.'
} else {
    Write-Host 'A inicialização automática não estava instalada.'
}
