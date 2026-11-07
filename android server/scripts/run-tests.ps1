# Runs the JVM protocol tests in an ASCII-only copy of the project.
# Android Gradle Plugin's Windows test worker cannot load test classes from a
# path containing CJK characters, so the project itself stays where the user
# requested while the test copy is placed under %TEMP%.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$temp = Join-Path $env:TEMP 'miband-camera-protocol-tests'

if (Test-Path -LiteralPath $temp) {
    Remove-Item -LiteralPath $temp -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$robocopy = Start-Process -FilePath robocopy.exe -ArgumentList @(
    "`"$root`"", "`"$temp`"", '/E', '/XD', 'build', '.gradle', '.kotlin', '.idea',
    '/NFL', '/NDL', '/NJH', '/NJS', '/NP'
) -Wait -PassThru -NoNewWindow
if ($robocopy.ExitCode -gt 7) {
    throw "robocopy failed with exit code $($robocopy.ExitCode)"
}

if (-not $env:JAVA_HOME -and (Test-Path 'D:\dev\jdk17\jdk-17.0.2')) {
    $env:JAVA_HOME = 'D:\dev\jdk17\jdk-17.0.2'
}
if (-not $env:ANDROID_HOME -and (Test-Path 'D:\dev\android-sdk')) {
    $env:ANDROID_HOME = 'D:\dev\android-sdk'
    $env:ANDROID_SDK_ROOT = 'D:\dev\android-sdk'
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 installation.' }

Push-Location $temp
try {
    & .\gradlew.bat --console=plain :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Tests failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
Write-Host 'Protocol tests passed.' -ForegroundColor Green
