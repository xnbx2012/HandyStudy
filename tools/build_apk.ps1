param([string]$JavaHome = $env:JAVA_HOME, [switch]$StandardPenOnly, [switch]$DebugBuild)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$requiredSigningVariables = @('ANDROID_KEYSTORE_FILE', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
$previousSigning = @{}
foreach ($name in $requiredSigningVariables) { $previousSigning[$name] = [Environment]::GetEnvironmentVariable($name) }
$localSigningFile = Join-Path $env:APPDATA 'HandyStudy\signing\signing.clixml'
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
    if (-not $DebugBuild) {
        if (Test-Path -LiteralPath $localSigningFile) {
            $config = Import-Clixml -LiteralPath $localSigningFile
            if (-not $env:ANDROID_KEYSTORE_FILE) { $env:ANDROID_KEYSTORE_FILE = $config.KeyFile }
            if (-not $env:ANDROID_KEY_ALIAS) { $env:ANDROID_KEY_ALIAS = $config.Alias }
            if (-not $env:ANDROID_KEYSTORE_PASSWORD) { $env:ANDROID_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', $config.StorePassword).Password }
            if (-not $env:ANDROID_KEY_PASSWORD) { $env:ANDROID_KEY_PASSWORD = [System.Net.NetworkCredential]::new('', $config.KeyPassword).Password }
        }
        $missing = @($requiredSigningVariables | Where-Object { -not [Environment]::GetEnvironmentVariable($_) })
        if ($missing.Count -gt 0) { throw "Signed release requires the GitHub Actions keystore and credentials. Missing: $($missing -join ', ')." }
        if (-not (Test-Path -LiteralPath $env:ANDROID_KEYSTORE_FILE -PathType Leaf)) { throw 'ANDROID_KEYSTORE_FILE does not point to an existing keystore.' }
    }
    $env:JAVA_HOME = $JavaHome
    # A short absolute temp path avoids Windows Java local-socket failures.
    $env:TEMP = $socketDirectory
    $env:TMP = $socketDirectory
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djava.io.tmpdir=$socketDirectory".Trim()
    Push-Location $projectRoot
    try {
        # Recreate the APK: incremental ZIP updates can retain deleted bank data.
        $buildArguments = @('--no-daemon', 'clean', $(if ($DebugBuild) { ':app:assembleDebug' } else { ':app:assembleRelease' }), 'lintDebug')
        if ($StandardPenOnly) { $buildArguments += '-PstandardPenOnly' }
        & .\gradlew.bat @buildArguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        if (-not $DebugBuild) {
            $apk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
            $apksigner = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0/apksigner.bat'
            if (-not (Test-Path -LiteralPath $apksigner)) { throw 'Android build-tools 36.0.0 apksigner is required to verify the release APK.' }
            & $apksigner verify --verbose $apk
            if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
            $certificateDetails = & $apksigner verify --print-certs $apk
            $expectedCertificate = '49a36334aea762d12ade60486429c601e19adbf7556a48e90491a8d922d45a33'
            if ($LASTEXITCODE -ne 0 -or -not ($certificateDetails -match "SHA-256 digest: $expectedCertificate")) {
                throw 'Release APK signer does not match the certificate of the published GitHub APK.'
            }
            Write-Host "Signed release APK: $apk"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    foreach ($name in $requiredSigningVariables) { [Environment]::SetEnvironmentVariable($name, $previousSigning[$name]) }
}
