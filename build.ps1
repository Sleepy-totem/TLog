param(
    [ValidateSet("debug","release")]
    [string]$Variant = "debug",
    [switch]$NoBump,
    [switch]$NoDrive,
    [switch]$NoPublish
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

# ---------------------------------------------------------------------------
# Versioning - read/bump version.properties before gradle reads it.
# ---------------------------------------------------------------------------
$VersionFile = Join-Path $ProjectRoot "version.properties"
if (-not (Test-Path $VersionFile)) {
    @"
versionCode=1
versionName=1.0.0
updateOwner=REPLACE_ME_GITHUB_USER
updateRepo=TLog
"@ | Set-Content -Path $VersionFile -Encoding ASCII
}

function Read-VersionProps {
    $props = @{}
    foreach ($line in Get-Content $VersionFile) {
        if ($line -match '^\s*#') { continue }
        if ($line -match '^\s*([^=]+?)\s*=\s*(.*)\s*$') {
            $props[$matches[1]] = $matches[2]
        }
    }
    return $props
}
function Write-VersionProps($props) {
    $lines = @(
        "versionCode=$($props.versionCode)",
        "versionName=$($props.versionName)",
        "updateOwner=$($props.updateOwner)",
        "updateRepo=$($props.updateRepo)"
    )
    $lines | Set-Content -Path $VersionFile -Encoding ASCII
}

$Props = Read-VersionProps
if (-not $NoBump) {
    $Props.versionCode = ([int]$Props.versionCode + 1).ToString()
    Write-VersionProps $Props
}
$VersionCode = [int]$Props.versionCode
$VersionName = $Props.versionName
$UpdateOwner = $Props.updateOwner
$UpdateRepo  = $Props.updateRepo
Write-Host "Building TLog $VersionName (code $VersionCode) [$Variant]" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# Java 17
# ---------------------------------------------------------------------------
$Jdk17 = $null
$JdkCandidates = @(
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.14.7-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"
)
$Jdk17 = $JdkCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Jdk17) {
    Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-17*hotspot" } |
        ForEach-Object { $Jdk17 = $_.FullName }
}
if (-not $Jdk17) { throw "JDK 17 not found. Run .\setup.ps1 first." }
$env:JAVA_HOME = $Jdk17
$env:Path = "$Jdk17\bin;" + $env:Path

# Android SDK
$SdkRoot = Join-Path $ProjectRoot "android-sdk"
if (-not (Test-Path $SdkRoot)) { throw "android-sdk/ not found. Run .\setup.ps1 first." }
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

# Release signing key (one-time generation, survives reinstalls)
if ($Variant -eq "release") {
    $KeystorePath = Join-Path $ProjectRoot "tlog-release.keystore"
    if (-not (Test-Path $KeystorePath)) {
        Write-Host "Generating personal release keystore (one-time)..." -ForegroundColor Cyan
        & "$Jdk17\bin\keytool.exe" -genkey -v `
            -keystore $KeystorePath `
            -alias tlog `
            -keyalg RSA -keysize 2048 -validity 36500 `
            -storepass tlogkey -keypass tlogkey `
            -dname "CN=Jordan Belsito, OU=TLog, O=TLog, L=SC, ST=SC, C=US"
    }
    $env:TLOG_KEYSTORE = $KeystorePath
}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
$Task = if ($Variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
Write-Host "Running gradlew $Task ..." -ForegroundColor Cyan
& ".\gradlew.bat" $Task --no-daemon

$SourceApk = if ($Variant -eq "release") {
    Join-Path $ProjectRoot "app\build\outputs\apk\release\app-release.apk"
} else {
    Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
}
if (-not (Test-Path $SourceApk)) {
    throw "Build finished but expected APK not found at $SourceApk"
}

$FriendlyApk = Join-Path $ProjectRoot "TLog.apk"
Copy-Item -Path $SourceApk -Destination $FriendlyApk -Force

$VersionedName = "TLog-$VersionName-$VersionCode-$Variant.apk"
$VersionedApk = Join-Path $ProjectRoot $VersionedName
Copy-Item -Path $SourceApk -Destination $VersionedApk -Force

$Size = "{0:N2} MB" -f ((Get-Item $FriendlyApk).Length / 1MB)
Write-Host ""
Write-Host "APK ready: $FriendlyApk  ($Size)" -ForegroundColor Green
Write-Host "Versioned: $VersionedApk" -ForegroundColor Green

# ---------------------------------------------------------------------------
# Google Drive backup - copy into <Drive>\TLog-apks\
#
# Google Drive for desktop mounts your drive as a virtual letter (usually G:)
# or as a folder under %USERPROFILE%\My Drive. We check both.
# Install Drive for desktop once from https://www.google.com/drive/download/
# and sign in; the folder appears automatically.
# ---------------------------------------------------------------------------
if (-not $NoDrive) {
    $DriveRoots = @()
    foreach ($code in 71..90) {
        $letter = [char]$code
        $p = "$letter" + ":\My Drive"
        if (Test-Path $p) { $DriveRoots += $p }
    }
    $userDrive = Join-Path $env:USERPROFILE "My Drive"
    if (Test-Path $userDrive) { $DriveRoots += $userDrive }

    if ($DriveRoots.Count -eq 0) {
        Write-Host "Google Drive for desktop not detected - skipping Drive backup." -ForegroundColor Yellow
        Write-Host "  Install from https://www.google.com/drive/download/ and sign in." -ForegroundColor Yellow
    } else {
        $Root = $DriveRoots[0]
        $TargetDir = Join-Path $Root "TLog-apks"
        if (-not (Test-Path $TargetDir)) { New-Item -ItemType Directory -Path $TargetDir | Out-Null }
        $DriveTarget = Join-Path $TargetDir $VersionedName
        Copy-Item -Path $VersionedApk -Destination $DriveTarget -Force
        Copy-Item -Path $VersionedApk -Destination (Join-Path $TargetDir "TLog-latest.apk") -Force
        Write-Host "Google Drive backup: $DriveTarget" -ForegroundColor Green
    }
}

# ---------------------------------------------------------------------------
# GitHub Releases publish - powers the in-app auto-updater.
#
# One-time setup on this PC:
#   1. winget install --id GitHub.cli     (or download from https://cli.github.com/)
#   2. gh auth login                      (pick GitHub.com / HTTPS / browser)
#   3. Create a repo:  gh repo create TLog --public --source . --remote origin --push
#   4. Edit version.properties:  updateOwner=<your github username>
#
# Each build then runs `gh release create vX.Y.Z+CODE TLog.apk` here.
# ---------------------------------------------------------------------------
if (-not $NoPublish -and $Variant -eq "release") {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        Write-Host "gh CLI not installed - skipping GitHub release." -ForegroundColor Yellow
        Write-Host "  Install: winget install --id GitHub.cli" -ForegroundColor Yellow
    } elseif ($UpdateOwner -eq "REPLACE_ME_GITHUB_USER" -or [string]::IsNullOrWhiteSpace($UpdateOwner)) {
        Write-Host "version.properties updateOwner not set - skipping GitHub release." -ForegroundColor Yellow
    } else {
        $Tag = "v$VersionName+$VersionCode"
        $Title = "TLog $VersionName (build $VersionCode)"
        Write-Host "Publishing GitHub release $Tag ..." -ForegroundColor Cyan
        & gh release create $Tag $VersionedApk --repo "$UpdateOwner/$UpdateRepo" --title $Title --notes "Automated build $VersionCode"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "gh release create failed (exit $LASTEXITCODE)." -ForegroundColor Yellow
        } else {
            Write-Host "Release published. Phone will see it on next launch." -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Build complete." -ForegroundColor Cyan
