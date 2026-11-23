param(
  [string]$AdminEmail = 'admincf@gmail.com'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

$env:VITE_USE_EMULATORS = $null
$env:VITE_LOCAL_PRODUCTION = 'true'
$env:CRAYFARM_LOCAL_ADMIN_EMAIL = $AdminEmail.Trim().ToLowerInvariant()

Write-Host 'Building the trusted local production admin...'
& pnpm run backend:build
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
& pnpm run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

Write-Host 'Starting on http://127.0.0.1:5173/ (live production data).'
& node .\scripts\local-production-server.mjs
if ($LASTEXITCODE -ne 0) { throw 'Local production server stopped unexpectedly.' }
