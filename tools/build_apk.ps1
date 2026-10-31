param([string]$JavaHome = $env:JAVA_HOME, [switch]$StandardPenOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$socketDirectory = Join-Path $env:SystemDrive 'Temp'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null

if (-not $JavaHome) {
    $candidates = @(
        'C:/Program Files/Java/jdk-21.0.12',
        'C:/Program Files/Android/Android Studio/jbr'
    )
    $JavaHome = $candidates | Where-Object { Test-Path (Join-Path $_ 'bin/java.exe') } | Select-Object -First 1
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME or pass -JavaHome with an installed JDK 17 or 21 path.'
}

$previousJavaHome = $env:JAVA_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
try {
    $env:JAVA_HOME = $JavaHome
    # A short absolute temp path avoids Windows Java local-socket failures.
    $env:TEMP = $socketDirectory
    $env:TMP = $socketDirectory
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djava.io.tmpdir=$socketDirectory".Trim()
    Push-Location $projectRoot
    try {
        # Recreate the APK: incremental ZIP updates can retain deleted bank data.
        $buildArguments = @('--no-daemon', 'clean', 'assembleDebug', 'lintDebug')
        if ($StandardPenOnly) { $buildArguments += '-PstandardPenOnly' }
        & .\gradlew.bat @buildArguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
}
