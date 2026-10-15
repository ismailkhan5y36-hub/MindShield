$runId = "38776498798"
$adb = "C:\Users\saad\platform-tools\platform-tools\adb.exe"
$device = "13018374CV000113"
$targetApk = "F:\1 New Project\MindShield\app-v6.6.5.apk"

Write-Host "Monitoring GitHub Actions Run $runId..."
while ($true) {
    try {
        $resp = Invoke-RestMethod -Uri "https://api.github.com/repos/ismailkhan5y36-hub/MindShield/actions/runs/$runId"
        Write-Host "Current Status: $($resp.status), Conclusion: $($resp.conclusion)"
        if ($resp.status -eq "completed") {
            if ($resp.conclusion -ne "success") {
                Write-Host "Build failed with conclusion: $($resp.conclusion)"
                exit 1
            }
            break
        }
    } catch {
        Write-Host "Waiting for network / API response..."
    }
    Start-Sleep -Seconds 10
}

Write-Host "Build completed successfully! Downloading release APK..."
$downloadUrl = "https://github.com/ismailkhan5y36-hub/MindShield/releases/download/v6.6.5/app-debug.apk"
$downloadSucceeded = $false
for ($i = 1; $i -le 15; $i++) {
    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $targetApk
        if ((Test-Path $targetApk) -and ((Get-Item $targetApk).Length -gt 1000000)) {
            $downloadSucceeded = $true
            Write-Host "Downloaded APK successfully ($((Get-Item $targetApk).Length) bytes)."
            break
        }
    } catch {
        Write-Host "Release asset not ready yet, retrying in 5s... ($i/15)"
        Start-Sleep -Seconds 5
    }
}

if (-not $downloadSucceeded) {
    Write-Host "Failed to download APK from release."
    exit 1
}

Write-Host "Installing APK on device $device..."
& $adb -s $device install -r -d $targetApk

Write-Host "Granting WRITE_SECURE_SETTINGS..."
& $adb -s $device shell pm grant com.mindshield.app android.permission.WRITE_SECURE_SETTINGS

Write-Host "Enabling Accessibility Service..."
& $adb -s $device shell settings put secure enabled_accessibility_services "com.transsion.tranfacmode/com.transsion.tranfacmode.base.UiListeningService:com.mindshield.app/.ShieldAccessibilityService"
& $adb -s $device shell settings put secure accessibility_enabled 1

Write-Host "Launching MainActivity..."
& $adb -s $device shell am start -n com.mindshield.app/.MainActivity

Write-Host "Verifying installed version..."
& $adb -s $device shell dumpsys package com.mindshield.app | findstr versionName
