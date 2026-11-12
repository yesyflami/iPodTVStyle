# Build the release APK. PowerShell twin of tools/build.sh - same checks, Windows paths.
#
#   powershell -ExecutionPolicy Bypass -File tools\build.ps1
#   powershell -ExecutionPolicy Bypass -File tools\build.ps1 -LauncherApplicationId com.coocaa.cleanhome
param(
    [string]$LauncherApplicationId = '',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs = @()
)
$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot '..\launcher' | Resolve-Path
Push-Location $launcher
try {
    function Fail([string]$message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }

    # --- JDK -- Gradle 8.9 stops on JDK 23+, and a JRE has no jlink, which the Android plugin needs.
    $javaHome = $null
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jlink.exe'))) { $javaHome = $env:JAVA_HOME }
    if (-not $javaHome) {
        foreach ($candidate in @(
                (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
                (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'),
                (Join-Path $env:ProgramFiles 'Eclipse Adoptium\jdk-17'),
                (Join-Path $env:ProgramFiles 'Java\jdk-17'))) {
            if ($candidate -and (Test-Path (Join-Path $candidate 'bin\jlink.exe'))) { $javaHome = $candidate; break }
        }
    }
    if (-not $javaHome) {
        $java = Get-Command java -ErrorAction SilentlyContinue
        if ($java) {
            $probe = & $java.Source -XshowSettings:properties -version 2>&1 | Select-String '^\s*java.home = (.+)$'
            if ($probe) {
                $candidate = $probe.Matches[0].Groups[1].Value.Trim()
                if (Test-Path (Join-Path $candidate 'bin\jlink.exe')) { $javaHome = $candidate }
            }
        }
    }
    if (-not $javaHome) {
        Fail "no JDK 17-22 found. Install Temurin 17, or set JAVA_HOME to a JDK. A JRE does not work: the Android Gradle plugin runs jlink."
    }
    $env:JAVA_HOME = $javaHome
    $versionLine = (& (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | Select-String 'version "' | Select-Object -First 1).ToString()
    if ($versionLine -notmatch 'version "(\d+)') { Fail "could not read the JDK version from: $versionLine" }
    $major = [int]$Matches[1]
    if ($major -eq 1) { $major = 8 }  # 1.8 style version strings
    if ($major -notin 17, 21, 22) {
        Fail "JDK $major at ${javaHome} - Gradle 8.9 needs 17, 21 or 22. Point JAVA_HOME at a JDK 17."
    }

    # --- Android SDK
    $sdk = $null
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), 'C:\Android\Sdk')) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platforms'))) { $sdk = $candidate; break }
    }
    if (-not $sdk) {
        Fail "no Android SDK found. Install Android Studio, or the command-line tools plus: sdkmanager ""platforms;android-34"" ""build-tools;34.0.0"", then set ANDROID_HOME."
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-34'))) {
        Write-Host "WARNING: $sdk\platforms\android-34 is missing; this build targets SDK 34." -ForegroundColor Yellow
        Write-Host '  sdkmanager "platforms;android-34" "build-tools;34.0.0"'
    }
    # local.properties is a Java properties file where a backslash is an escape: write forward slashes.
    $localProperties = Join-Path (Get-Location) 'local.properties'
    if (-not (Test-Path $localProperties)) {
        "sdk.dir=$($sdk -replace '\\', '/')" | Set-Content -Path $localProperties -Encoding ASCII
    }

    Write-Host "JAVA_HOME     = $javaHome"
    Write-Host "ANDROID_HOME  = $sdk"

    $gradlew = Join-Path $launcher 'gradlew.bat'
    $all = @('assembleRelease', '--console=plain') + $GradleArgs
    if ($LauncherApplicationId) { $all += "-PlauncherApplicationId=$LauncherApplicationId" }
    & $gradlew @all
    if ($LASTEXITCODE -ne 0) { Fail "gradlew assembleRelease failed (see above)." }

    $apk = Join-Path $launcher 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path $apk)) { Fail "the build finished but $apk is missing." }
    $size = [math]::Round((Get-Item $apk).Length / 1KB)
    Write-Host ''
    Write-Host "APK    : $apk ($size KB)"
    Write-Host 'Next   : powershell -ExecutionPolicy Bypass -File tools\install.ps1 -TvHost <ip>'
}
finally { Pop-Location }
