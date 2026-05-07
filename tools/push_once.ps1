# Singolo ingest consumi: login ADMIN + POST del file consumi_template.json
# Uso (da PowerShell, con Spring Boot gia' avviato):
#   cd ...\Middleware-main\tools
#   .\push_once.ps1
# Opzionale: .\push_once.ps1 -BaseUrl "http://192.168.1.10:8080"

param(
    [string] $BaseUrl = "http://localhost:8081",
    [string] $Username = "admin",
    [string] $Password = "AdminPass123!",
    [string] $JsonFile = ""
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($JsonFile)) {
    $JsonFile = Join-Path $PSScriptRoot "consumi_template.json"
}
if (-not (Test-Path $JsonFile)) {
    throw "File non trovato: $JsonFile"
}

$pair = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))"
Write-Host "Login su $BaseUrl/auth/login ..."
$login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -Body $pair -ContentType "application/x-www-form-urlencoded"
if (-not $login.token) {
    throw "Login fallito: $($login | ConvertTo-Json -Compress)"
}

$headers = @{ Authorization = "Bearer $($login.token)" }
$body = Get-Content -LiteralPath $JsonFile -Raw -Encoding UTF8
Write-Host "POST $BaseUrl/api/consumi ..."
$result = Invoke-RestMethod -Uri "$BaseUrl/api/consumi" -Method POST -Body $body -ContentType "application/json; charset=utf-8" -Headers $headers
Write-Host "OK:" ($result | ConvertTo-Json -Depth 5 -Compress)
