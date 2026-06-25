# Execute este script no PowerShell como Administrador.

$ruleName = "REI Server 8765"

if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8765 | Out-Null
}

Get-NetFirewallRule -DisplayName $ruleName |
    Select-Object DisplayName, Enabled, Direction, Action

