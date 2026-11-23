param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('crayfarm-eb2ea')]
  [string]$ConfirmProject,
  [Parameter(Mandatory = $true)]
  [switch]$ConfirmBlaze
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

if ($env:VITE_USE_EMULATORS -eq 'true') {
  throw 'Refusing deployment while VITE_USE_EMULATORS=true.'
}
if ($env:VITE_LOCAL_PRODUCTION -eq 'true') {
  throw 'Refusing deployment while VITE_LOCAL_PRODUCTION=true.'
}

$firebase = Join-Path $projectRoot 'node_modules\.bin\firebase.cmd'
if (-not (Test-Path -LiteralPath $firebase)) {
  throw 'Firebase CLI is missing. Run pnpm install first.'
}

Write-Host 'Running production build, backend build, tests, and release preflight...'
& pnpm run release:check
if ($LASTEXITCODE -ne 0) { throw 'Release checks failed.' }

Write-Host 'Verifying project, Web app, Hosting site, CLI access, and Cloud Functions API...'
& pnpm run deployment:check
if ($LASTEXITCODE -ne 0) { throw 'Firebase deployment prerequisites are not ready.' }

Write-Host 'Deploying the reviewed Functions codebase first...'
& $firebase deploy --config firebase.production.json --project $ConfirmProject --only 'functions:crayfarm-admin' --non-interactive
if ($LASTEXITCODE -ne 0) { throw 'Functions deployment failed; Hosting was not changed.' }

Write-Host 'Deploying Hosting only after Functions succeeded...'
& $firebase deploy --config firebase.production.json --project $ConfirmProject --only 'hosting' --non-interactive
if ($LASTEXITCODE -ne 0) { throw 'Hosting deployment failed. Functions are deployed; retry this guarded command.' }

Write-Host 'Deployment completed. Database rules were not included.'
