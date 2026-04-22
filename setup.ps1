# One-time setup: downloads Gradle + Android SDK and prepares the project to build.
# Run from the project root:  .\setup.ps1
#
# Requires: PowerShell 5+, internet access, ~3 GB free disk.
# Uses Eclipse Adoptium JDK 17 (already installed on this machine).

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

# --- Java 17 ---
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
if (-not $Jdk17) { throw "JDK 17 not found. Install Temurin 17 from https://adoptium.net/ then re-run." }
$env:JAVA_HOME = $Jdk17
$env:Path = "$Jdk17\bin;" + $env:Path
Write-Host "Using JAVA_HOME=$Jdk17" -ForegroundColor Cyan

# --- Gradle distribution ---
$GradleVersion = "8.10.2"
$GradleDir = Join-Path $ProjectRoot ".gradle-dist"
$GradleHome = Join-Path $GradleDir "gradle-$GradleVersion"
if (-not (Test-Path "$GradleHome\bin\gradle.bat")) {
    Write-Host "Downloading Gradle $GradleVersion..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $GradleDir | Out-Null
    $GradleZip = Join-Path $GradleDir "gradle.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" `
        -OutFile $GradleZip
    Write-Host "Extracting Gradle..." -ForegroundColor Cyan
    Expand-Archive -Path $GradleZip -DestinationPath $GradleDir -Force
    Remove-Item $GradleZip
}
$env:Path = "$GradleHome\bin;" + $env:Path

# --- Generate gradle wrapper (first time only) ---
if (-not (Test-Path (Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.jar"))) {
    Write-Host "Generating Gradle wrapper..." -ForegroundColor Cyan
    & "$GradleHome\bin\gradle.bat" --no-daemon wrapper --gradle-version $GradleVersion --distribution-type bin
}

# --- Android SDK ---
$SdkRoot = Join-Path $ProjectRoot "android-sdk"
$CmdLineTools = Join-Path $SdkRoot "cmdline-tools\latest"
if (-not (Test-Path (Join-Path $CmdLineTools "bin\sdkmanager.bat"))) {
    Write-Host "Downloading Android command-line tools..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $CmdLineZip = Join-Path $SdkRoot "cmdline-tools.zip"
    # Recent known-good revision (11076708). Replace URL if a newer version is preferred.
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" `
        -OutFile $CmdLineZip
    $Extract = Join-Path $SdkRoot "_cmdline_extract"
    if (Test-Path $Extract) { Remove-Item $Extract -Recurse -Force }
    Expand-Archive -Path $CmdLineZip -DestinationPath $Extract -Force
    New-Item -ItemType Directory -Force -Path $CmdLineTools | Out-Null
    Copy-Item -Path (Join-Path $Extract "cmdline-tools\*") -Destination $CmdLineTools -Recurse -Force
    Remove-Item $Extract -Recurse -Force
    Remove-Item $CmdLineZip
}
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$CmdLineTools\bin;$SdkRoot\platform-tools;" + $env:Path

# --- Accept licenses + install SDK components ---
Write-Host "Accepting Android SDK licenses..." -ForegroundColor Cyan
$yes = ("y`n" * 40)
$yes | & "$CmdLineTools\bin\sdkmanager.bat" --licenses | Out-Null

Write-Host "Installing Android SDK components (platform-tools, platform-35, build-tools)..." -ForegroundColor Cyan
& "$CmdLineTools\bin\sdkmanager.bat" --install `
    "platform-tools" `
    "platforms;android-35" `
    "build-tools;35.0.0"

# --- local.properties ---
$LocalProps = Join-Path $ProjectRoot "local.properties"
$sdkForProps = $SdkRoot.Replace("\", "\\").Replace(":", "\:")
"sdk.dir=$sdkForProps" | Set-Content -Path $LocalProps -Encoding ASCII
Write-Host "Wrote local.properties -> $LocalProps" -ForegroundColor Cyan

Write-Host "`nSetup complete." -ForegroundColor Green
Write-Host "Next: .\build.ps1      (produces TLog.apk in the project root)" -ForegroundColor Green
Write-Host "Then: transfer TLog.apk to your phone and tap it to install." -ForegroundColor Green
