$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$browser = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw 'Chrome or Edge is required for these browser checks.' }
$profile = Join-Path $root 'out\browser-tests-profile'
foreach ($test in @('csv_tests.html', 'catalog_tests.html')) {
    $page = ([System.Uri](Join-Path $PSScriptRoot $test)).AbsoluteUri
    $dom = & $browser --headless=new --disable-gpu --no-first-run --no-default-browser-check `
        --allow-file-access-from-files "--user-data-dir=$profile" --virtual-time-budget=10000 `
        --dump-dom $page 2>(Join-Path $root 'out\browser-tests-stderr.log') | Out-String
    $exitCode = $LASTEXITCODE
    $match = [regex]::Match($dom, '<pre id="result">([^<]+)</pre>')
    if ($exitCode -ne 0 -or -not $match.Success -or -not $match.Groups[1].Value.StartsWith('PASS ')) {
        throw "$test failed: $($match.Groups[1].Value). See out\browser-tests-stderr.log and rerun with --dump-dom."
    }
    Write-Output "$test`: $($match.Groups[1].Value)"
}
