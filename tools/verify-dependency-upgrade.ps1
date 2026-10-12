param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9_-]+$')]
    [string]$Stage,
    [string]$DeviceId = 'emulator-5554',
    [string[]]$GradleArguments = @()
)

$ErrorActionPreference = 'Stop'
$repositoryPath = Split-Path -Parent $PSScriptRoot
$reportPath = Join-Path $repositoryPath ".gradle/dependency-upgrades/$Stage"
$adbCommand = (Get-Command adb -ErrorAction Stop).Source

function Invoke-Adb {
    param([string[]]$Arguments)
    $output = & $adbCommand -s $DeviceId @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return ($output -join "`n")
}

Push-Location $repositoryPath
try {
    New-Item -ItemType Directory -Force -Path $reportPath | Out-Null
    if ((Invoke-Adb -Arguments @('get-state')).Trim() -ne 'device') {
        throw "Android device $DeviceId is not ready."
    }

    $buildLog = Join-Path $reportPath 'build.log'
    & .\gradlew.bat :app:assembleDebug :app:minifyReleaseWithR8 @GradleArguments --continue --console=plain *> $buildLog
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $buildLog -Tail 100
        throw "Build validation failed for $Stage. See $buildLog"
    }

    # Stop before updating to avoid Android's foreground package-update activity.
    Invoke-Adb -Arguments @('shell', 'am', 'force-stop', 'com.example.chat') | Out-Null
    Invoke-Adb -Arguments @('install', '-r', '-t', 'app/build/outputs/apk/debug/app-debug.apk') | Set-Content (Join-Path $reportPath 'install-app.log')
    Invoke-Adb -Arguments @('shell', 'am', 'force-stop', 'com.example.chat') | Out-Null
    Invoke-Adb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb -Arguments @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    $launch = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-S', '-n', 'com.example.chat/.MainActivity')
    $launch | Set-Content (Join-Path $reportPath 'launch.log')
    if ($launch -notmatch 'Status: ok' -or $launch -notmatch 'Activity: com\.example\.chat/' -or $launch -notmatch 'LaunchState: COLD') {
        throw "Application launch failed for $Stage.`n$launch"
    }
    Start-Sleep -Seconds 3
    $applicationProcessId = (Invoke-Adb -Arguments @('shell', 'pidof', 'com.example.chat')).Trim()
    if (-not $applicationProcessId) {
        throw "Application exited after launching for $Stage."
    }
    $activityState = Invoke-Adb -Arguments @('shell', 'dumpsys', 'activity', 'activities')
    $activityState | Set-Content (Join-Path $reportPath 'activity.log')
    if ($activityState -notmatch '(?:topResumedActivity|mResumedActivity).*com\.example\.chat/\.MainActivity') {
        throw "MainActivity is not in the foreground for $Stage."
    }
    $crashes = Invoke-Adb -Arguments @('logcat', '-b', 'crash', '-d', "--pid=$applicationProcessId")
    $crashes | Set-Content (Join-Path $reportPath 'crash.log')
    if ($crashes -match 'FATAL EXCEPTION|Fatal signal') {
        throw "Application crash detected for $Stage.`n$crashes"
    }

    $mainUiVisible = $false
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        Invoke-Adb -Arguments @('shell', 'uiautomator', 'dump', '/data/local/tmp/petchat-upgrade.xml') | Out-Null
        Invoke-Adb -Arguments @('pull', '/data/local/tmp/petchat-upgrade.xml', (Join-Path $reportPath 'launch.xml')) | Out-Null
        $viewHierarchy = Get-Content -LiteralPath (Join-Path $reportPath 'launch.xml') -Raw -Encoding utf8
        if ($viewHierarchy -match 'package="com\.example\.chat"' -and $viewHierarchy -match 'text="聊天"') {
            $mainUiVisible = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $mainUiVisible) {
        throw "Application process is running but the main UI is not visible for $Stage."
    }

    Invoke-Adb -Arguments @('shell', 'screencap', '-p', '/data/local/tmp/petchat-upgrade.png') | Out-Null
    Invoke-Adb -Arguments @('pull', '/data/local/tmp/petchat-upgrade.png', (Join-Path $reportPath 'launch.png')) | Out-Null

    $result = [ordered]@{
        stage = $Stage
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        device = $DeviceId
        androidApi = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
        debugBuild = $true
        releaseR8 = $true
        coldStart = $true
        mainUiVisible = $true
        apkSha256 = (Get-FileHash 'app/build/outputs/apk/debug/app-debug.apk' -Algorithm SHA256).Hash
        mainActivityRunning = $true
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $reportPath 'result.json')
    Copy-Item 'gradle/libs.versions.toml' (Join-Path $reportPath 'libs.versions.toml')
    Copy-Item 'gradle/wrapper/gradle-wrapper.properties' (Join-Path $reportPath 'gradle-wrapper.properties')
    $result | ConvertTo-Json -Depth 5
    Get-Content -LiteralPath $buildLog -Tail 12
}
finally {
    Pop-Location
}
