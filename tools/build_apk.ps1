param([string]$JavaHome = $env:JAVA_HOME, [switch]$StandardPenOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$socketDirectory = Join-Path $projectRoot 'tmp/java-sockets'
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
try {
    $env:JAVA_HOME = $JavaHome
    # Store-app filesystem virtualization can break AF_UNIX sockets in %TEMP%.
    # JAVA_TOOL_OPTIONS also reaches Gradle's daemon and worker JVMs.
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions `"-Djdk.net.unixdomain.tmpdir=$socketDirectory`"".Trim()
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
}
