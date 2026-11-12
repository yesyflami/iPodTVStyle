# Install the launcher on the TV and hand it the Home key - or put the stock launcher back.
# PowerShell twin of tools/install.sh.
#
#   python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
#   powershell -ExecutionPolicy Bypass -File tools\install.ps1 -TvHost 192.168.1.50
#
#   -SkipBuild                 install the APK that is already built
#   -LauncherApplicationId <p> package name to build as, for a firmware with a HOME allowlist
#   -AppId <package>           when the APK cannot name itself
#   -StockLauncher <package>   the launcher to disable (default: ask the TV, only a known one counts)
#   -StockLauncher none        keep the stock launcher and install ours as an ordinary app
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$SkipBuild,
    [string]$LauncherApplicationId = '',
    [string]$AppId = '',
    [string]$StockLauncher = '',
    [string]$MainActivity = 'com.gathin.tvhome.MainActivity'
)
$ErrorActionPreference = 'Stop'
$root = (Join-Path $PSScriptRoot '..' | Resolve-Path).Path
$tools = Join-Path $root 'tools'
$python = Join-Path $tools 'venv\Scripts\python.exe'
$tvpy = Join-Path $tools 'tv.py'
$apk = Join-Path $root 'launcher\app\build\outputs\apk\release\app-release.apk'
$meta = Join-Path $root 'launcher\app\build\outputs\apk\release\output-metadata.json'
$knownStockLaunchers = @(
    'com.google.android.tvlauncher',
    'com.google.android.apps.tv.launcherx',
    'com.google.android.leanbacklauncher',
    'com.mitv.tvhome'
)
$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

function Fail([string]$message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }
function Note([string]$message) { Write-Host "==> $message" }
function Tv([string]$command) {
    $output = & $python -I $tvpy $command 2>&1 | Out-String
    return $output
}

if (-not $TvHost) { Fail 'pass -TvHost <ip>, or set TV_HOST. Example: tools\install.ps1 -TvHost 192.168.1.50' }
if (-not (Test-Path $python)) {
    Fail "no Python environment yet. Run:`n  python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt"
}
$env:TV_HOST = $TvHost

# --- Connect ---------------------------------------------------------------------------------
$probe = Tv '--probe'
Write-Host $probe
if ($LASTEXITCODE -ne 0) {
    Fail "could not reach the TV. Check -TvHost, the 'Allow USB debugging' prompt on the TV, and that network debugging is still on (docs/ADAPT.md §2)."
}

# --- Pre-flight: the TV's SDK against the launcher's minSdk -----------------------------------
$buildFile = Get-Content (Join-Path $root 'launcher\app\build.gradle') -Raw
$minSdk = if ($buildFile -match '(?m)^\s*minSdk\s+(\d+)\s*$') { [int]$Matches[1] } else { 0 }
$tvSdk = (Tv 'getprop ro.build.version.sdk') -replace '[^0-9]', ''
if ($minSdk -gt 0 -and $tvSdk -and [int]$tvSdk -lt $minSdk) {
    Fail "this TV runs SDK $tvSdk and the launcher needs SDK $minSdk or newer. Nothing was built, installed or disabled."
}

# --- Build -----------------------------------------------------------------------------------
if (-not $SkipBuild) {
    $buildArgs = @{}
    if ($LauncherApplicationId) { $buildArgs['LauncherApplicationId'] = $LauncherApplicationId }
    & (Join-Path $tools 'build.ps1') @buildArgs
    if ($LASTEXITCODE -ne 0) { Fail 'the build failed (see above).' }
}
if (-not (Test-Path $apk)) { Fail "no APK at $apk. Run tools\build.ps1 first, or pass -SkipBuild only when it exists." }

# --- Which package did we just build? --------------------------------------------------------
if (-not $AppId -and (Test-Path $meta)) {
    try {
        $metadata = Get-Content $meta -Raw | ConvertFrom-Json
        if ($metadata.applicationId) { $AppId = $metadata.applicationId }
        elseif ($metadata.elements -and $metadata.elements[0].applicationId) { $AppId = $metadata.elements[0].applicationId }
    }
    catch { }
}
if (-not $AppId) {
    $properties = Join-Path $root 'launcher\gradle.properties'
    if (Test-Path $properties) {
        $line = Get-Content $properties | Where-Object { $_ -match '^\s*launcherApplicationId\s*=' } | Select-Object -First 1
        if ($line) { $AppId = ($line -split '=', 2)[1].Trim() }
    }
}
if (-not $AppId) { Fail "cannot tell which package this APK uses. Pass -AppId <package> (the APK records it in output-metadata.json)." }
Note "package: $AppId   activity: $MainActivity"

# --- Who owns Home right now? ----------------------------------------------------------------
if (-not $StockLauncher) {
    $resolved = (Tv $homeQuery) -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1
    $homePkg = ($resolved.Trim() -split '/')[0]
    if (-not $homePkg) { Fail 'could not ask the TV which launcher owns Home. Pass -StockLauncher <package>, or -StockLauncher none.' }
    if ($knownStockLaunchers -contains $homePkg) {
        $StockLauncher = $homePkg
    }
    elseif ($homePkg -eq $AppId) {
        $StockLauncher = 'none'
        Note 'Home already belongs to this launcher; nothing will be disabled.'
    }
    elseif ($homePkg -eq 'android') {
        Fail "Home is Android's launcher chooser (two launchers tie). Pick a default on the TV first, then run this again - or pass -StockLauncher none."
    }
    else {
        Fail "Home resolves to '$homePkg', which is not a launcher this script knows. Re-run with -StockLauncher <package> or -StockLauncher none."
    }
}
Note "stock launcher: $StockLauncher"
$disableStock = if ($StockLauncher -eq 'none') { 'echo stock launcher left alone' } else { "pm disable-user --user 0 $StockLauncher" }

# --- Install (alone, so a failed install cannot be followed by disabling the stock launcher) ---
$apkKb = [math]::Round((Get-Item $apk).Length / 1KB)
Note "uploading $apkKb KB"
& $python -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/ipodtvstyle.apk
if ($LASTEXITCODE -ne 0) { Fail 'the upload failed (see above). Nothing was installed or disabled.' }
$installOut = Tv 'pm install -r /data/local/tmp/ipodtvstyle.apk; rm -f /data/local/tmp/ipodtvstyle.apk'
Write-Host $installOut
if ($installOut -notmatch 'Success') {
    if ($installOut -match 'INSTALL_FAILED_OLDER_SDK') {
        Fail 'the APK is too new for this TV (INSTALL_FAILED_OLDER_SDK). The stock launcher was left untouched.'
    }
    if ($installOut -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Fail "a package called $AppId is already installed with a different signature - usually a build made on another computer. Uninstall that one first (set Home back to the stock launcher BEFORE uninstalling), then run this again."
    }
    if ($installOut -match 'INSTALL_FAILED_USER_RESTRICTED|INSTALL_FAILED_VERIFICATION_FAILURE') {
        Fail "the TV refused the install. On Coocaa/Skyworth sets an install confirmation appears on the TV screen and letting it time out produces exactly this answer. Accept it on the TV and run this again."
    }
    Fail 'pm install failed (see above). The stock launcher was left untouched.'
}

if (-not ((Tv "pm list packages $AppId") -match "package:$([regex]::Escape($AppId))")) {
    Fail "the APK installed, but not as $AppId. Re-run with -AppId <the package it really uses>; nothing was disabled and Home is unchanged."
}

# --- Start the new launcher and check it really came up --------------------------------------
$startCmd = if ($StockLauncher -eq 'none') {
    "am start -W -n $AppId/$MainActivity"
} else {
    "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $AppId/$MainActivity"
}
$startOut = Tv $startCmd
Write-Host $startOut
if (($startOut -notmatch 'Status: ok') -or ($startOut -match '(?m)^Error')) {
    Fail "the new launcher did not start (see above). $StockLauncher was left enabled and Home is unchanged."
}

# --- Hand over Home --------------------------------------------------------------------------
Note "handing the Home key to $AppId"
$batch = @(
    "cmd package compile -m speed -f $AppId",
    $disableStock,
    "cmd package set-home-activity --user 0 $AppId/$MainActivity"
)
$batchFile = [System.IO.Path]::GetTempFileName()
try {
    Set-Content -Path $batchFile -Value $batch -Encoding ASCII
    Get-Content $batchFile | & $python -I $tvpy --batch
    if ($LASTEXITCODE -ne 0) { Fail 'the Home handover commands failed (see above).' }
}
finally { Remove-Item $batchFile -ErrorAction SilentlyContinue }

function QueryHome {
    $lines = Tv $homeQuery 2>$null
    ($lines -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1).Trim()
}
function RollbackHome {
    if ($StockLauncher -eq 'none') { Write-Host 'No stock launcher was disabled, so there is nothing to roll back.'; return $false }
    Tv "pm enable --user 0 $StockLauncher" | Out-Null
    Tv "cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)" | Out-Null
    $back = QueryHome
    if ($back -like "$StockLauncher/*") { Write-Host "$StockLauncher owns Home again."; return $true }
    $recovery = Join-Path $env:USERPROFILE ("ipodtvstyle-recovery-" + ($TvHost -replace '[^A-Za-z0-9._-]', '_') + '.txt')
    @(
        "# iPodTVStyle: the Home handover on $TvHost did not finish. From the repo root:",
        "`$env:TV_HOST='$TvHost'; tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable --user 0 $StockLauncher'",
        "`$env:TV_HOST='$TvHost'; tools\venv\Scripts\python.exe -I tools\tv.py 'cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)'"
    ) | Set-Content -Path $recovery -Encoding UTF8
    Write-Host "ERROR: Home still resolves to '$back'." -ForegroundColor Red
    Write-Host "The commands that finish the recovery are in $recovery - the TV still needs ADB for them."
    return $false
}

$homeNow = QueryHome
if ($homeNow -like "$AppId/*") {
    Note "Home now resolves to $homeNow"
}
else {
    Write-Host "ERROR: Home resolves to '$homeNow', not to $AppId." -ForegroundColor Red
    RollbackHome | Out-Null
    Write-Host 'On some Coocaa/Skyworth firmware the physical Home key and the boot path use vendor properties,'
    Write-Host 'not Android''s HOME preference: docs/ADAPT.md §5 covers what to do there.'
    exit 1
}

Write-Host ''
Write-Host 'Installed. Press Home on the remote.'
Write-Host "  package   : $AppId"
Write-Host "  Home key  : $AppId/$MainActivity"
Write-Host "  stock     : $StockLauncher"
Write-Host ''
Write-Host 'To go back:'
Write-Host "  tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable --user 0 $StockLauncher'"
if ($StockLauncher -eq 'none') { Write-Host '  (nothing was disabled, so nothing has to be re-enabled)' }
Write-Host 'When you are done with ADB, turn debugging off and check the port is closed:'
Write-Host "  python tools\adb-closed.py $TvHost"
