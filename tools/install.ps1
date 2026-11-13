# Install iPodTVStyle on a TV. Needs nothing but Python 3 on the computer and a TV on the same
# network -- no JDK, no Android SDK, no adb, no Gradle. The APK is prebuilt in prebuilt\.
#
#   powershell -ExecutionPolicy Bypass -File tools\install.ps1 192.168.1.50
#
#   ... tools\install.ps1 192.168.1.50 -Uninstall
#
# It works through the APK variants in prebuilt\ until the TV accepts one as Home: firmware that
# only hands Home to a package name on its own allow-list refuses the neutral name and accepts
# the vendor one. Nothing is disabled; the stock launcher keeps working.
param(
    [Parameter(Position = 0)][string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$ToolsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoDir = Split-Path -Parent $ToolsDir
$Prebuilt = Join-Path $RepoDir 'prebuilt'
$MainActivity = 'com.gathin.tvhome.MainActivity'

function Die([string]$Message) { Write-Host "ERROR: $Message" -ForegroundColor Red; exit 1 }
function Note([string]$Message) { Write-Host "==> $Message" }

function Get-PackageOf([string]$Apk) {
    $sidecar = [IO.Path]::ChangeExtension($Apk, '.package')
    if (Test-Path $sidecar) { return (Get-Content $sidecar -Raw).Trim() }
    return ''
}

if (-not $TvHost) {
    Die @"
tell me the TV address:
  powershell -ExecutionPolicy Bypass -File tools\install.ps1 192.168.1.50

Find it on the TV under Settings > Network > Wi-Fi > network details.
"@
}
$TvHost = ($TvHost -split ':')[0]
$env:TV_HOST = $TvHost

# --- Python + the ADB transport ----------------------------------------------------------------
$Py = Join-Path $ToolsDir 'venv\Scripts\python.exe'
if (-not (Test-Path $Py)) {
    $HostPy = $null
    foreach ($candidate in @('python', 'python3', 'py')) {
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) { $HostPy = $cmd.Source; break }
    }
    if (-not $HostPy) {
        Die @"
Python 3 is not installed. Get it from https://www.python.org/downloads/
During setup tick "Add python.exe to PATH", then run this again.
"@
    }
    Note 'first run: setting up a small Python environment (one minute, once)'
    & $HostPy -m venv (Join-Path $ToolsDir 'venv')
    if (-not (Test-Path $Py)) { Die 'could not create the Python environment.' }
    & $Py -m pip install --quiet --disable-pip-version-check -r (Join-Path $ToolsDir 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { Die 'could not download the two Python packages the installer needs.' }
}

function Invoke-Tv {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    $out = & $Py -I (Join-Path $ToolsDir 'tv.py') @Args 2>&1
    return ($out | Out-String)
}

# --- uninstall ---------------------------------------------------------------------------------
if ($Uninstall) {
    Note "TV: $TvHost"
    Invoke-Tv --probe | Out-Null
    Get-ChildItem $Prebuilt -Filter *.apk | ForEach-Object {
        $pkg = Get-PackageOf $_.FullName
        if ($pkg) {
            Invoke-Tv "pm uninstall $pkg" | Out-Null
            Note "removed $pkg"
        }
    }
    Note 'done. Press Home on the remote; if the TV asks which launcher, pick your old one.'
    exit 0
}

# --- reach the TV ------------------------------------------------------------------------------
Note "TV: $TvHost"
Invoke-Tv --probe | Out-Null
if ($LASTEXITCODE -ne 0) {
    Die @"
could not reach $TvHost.
  - is the TV on, and on the same network as this computer?
  - is Settings > Developer options > Network debugging switched on?
  - the first connection shows an 'Allow debugging' prompt on the TV -- accept it.
"@
}
$sdk = (Invoke-Tv 'getprop ro.build.version.sdk') -replace '\D', ''
if (-not $sdk) { Die "connected, but could not read the TV's Android version." }
if ([int]$sdk -lt 26) { Die "this TV runs Android SDK $sdk; iPodTVStyle needs 26 (Android 8.0) or newer." }

# --- try each variant --------------------------------------------------------------------------
$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
function Get-Home { return ((Invoke-Tv $homeQuery) -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1).Trim() }

$order = if ($env:VARIANTS) { $env:VARIANTS -split '\s+' } else {
    @('ipodtvstyle-neutral', 'ipodtvstyle-skyworth', 'ipodtvstyle-xiaomi', 'ipodtvstyle-tcl')
}

Note 'installing'
$winner = $null
foreach ($name in $order) {
    $apk = Join-Path $Prebuilt "$name.apk"
    if (-not (Test-Path $apk)) { continue }
    $pkg = Get-PackageOf $apk
    if (-not $pkg) { continue }

    & $Py -I (Join-Path $ToolsDir 'b64push.py') $apk /data/local/tmp/ipodtvstyle.apk 2>&1 | Out-Null
    $installOut = Invoke-Tv 'pm install -r /data/local/tmp/ipodtvstyle.apk; rm -f /data/local/tmp/ipodtvstyle.apk'
    if ($installOut -notmatch 'Success') { Note "  $pkg: install refused, trying the next variant"; continue }

    Invoke-Tv "cmd package compile -m speed -f $pkg" | Out-Null
    Invoke-Tv "cmd package set-home-activity --user 0 $pkg/$MainActivity" | Out-Null
    Invoke-Tv "am start -n $pkg/$MainActivity" | Out-Null
    Start-Sleep -Seconds 2

    # resolve-activity only echoes what set-home-activity just wrote. Coocaa/Skyworth firmware
    # routes the physical Home key through vendor properties and ignores that value, so press the
    # key and see who is actually in front -- that is the only answer that matters.
    Invoke-Tv 'input keyevent KEYCODE_HOME' | Out-Null
    Start-Sleep -Seconds 2
    $focus = (Invoke-Tv 'dumpsys window | grep mCurrentFocus') -split "`n" | Select-Object -First 1
    if ($focus -like "*$pkg*") { $winner = $pkg; Note "  $pkg: Home key lands here"; break }

    Note "  $pkg: installed, but the Home key still goes to the stock launcher"
    Invoke-Tv "pm uninstall $pkg" | Out-Null
}

if (-not $winner) {
    Die @"
every variant installed cleanly, but none was allowed to become Home.

This TV only hands Home to a package name on its own allow-list, and none of the
names shipped in prebuilt\ is on it. Nothing was left behind and your stock
launcher is untouched.

Options:
  - Contribute your model: docs\devices\README.md shows what to record.
  - Build a variant with your TV's own Home package name:
      powershell -File tools\build.ps1 -LauncherApplicationId <the name>
    Find that name with, on the TV:  pm list packages | findstr /i launch
"@
}

Note 'done'
Write-Host @"

  package   : $winner
  Home key  : $winner/$MainActivity

Press Home on the remote. If the TV asks which launcher to use, pick iPodTVStyle
and choose "Always".

To undo everything:
  powershell -ExecutionPolicy Bypass -File tools\install.ps1 $TvHost -Uninstall

When you are done with debugging, turn it off on the TV:
Settings > Developer options > Network debugging.
"@
