param([switch]$Verify, [switch]$Offline)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) {
    $taskJdk = Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
}
if (-not (Test-Path 'local.properties')) {
    $taskSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath $taskSdk)) { throw '请安装 Android SDK 并设置 ANDROID_HOME。' }
    $taskEscapedSdk = $taskSdk.Replace('\','/').Replace(':','\:')
    Set-Content 'local.properties' "sdk.dir=$taskEscapedSdk"
}
# Windows AF_UNIX has path restrictions. Use a short writable temporary directory.
$taskSocketTemp = if ($env:HANDYMATH_SOCKET_DIR) { $env:HANDYMATH_SOCKET_DIR } else { Join-Path $env:SystemRoot 'Temp' }
New-Item -ItemType Directory -Force $taskSocketTemp | Out-Null
$taskPreviousJavaOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = "$taskPreviousJavaOptions -Djdk.net.unixdomain.tmpdir=$($taskSocketTemp.Replace('\','/'))"
try {
    $taskArgs = @('assembleDebug','--no-daemon')
    if ($Verify) { $taskArgs += @('assembleDebugAndroidTest','lintDebug') }
    if ($Offline) { $taskArgs += '--offline' }
    & "$PSScriptRoot\gradlew.bat" @taskArgs
    if ($LASTEXITCODE -ne 0) { throw "构建失败，退出码 $LASTEXITCODE" }
} finally { $env:JAVA_TOOL_OPTIONS = $taskPreviousJavaOptions }
