# This script creates all project files in a GitHub repo via the GitHub Contents API
# Usage: Provide a GitHub Personal Access Token as the first argument
# Token can be created at: https://github.com/settings/tokens/new (check "repo" scope)

param([string]$Token = "")

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Host "ERROR: Please provide a GitHub Personal Access Token"
    Write-Host "Create one at: https://github.com/settings/tokens/new"
    Write-Host "Check 'repo' scope, then run: .\api-upload.ps1 YOUR_TOKEN_HERE"
    exit 1
}

$repo = "lzl477/HardwareInfoPro"
$baseUrl = "https://api.github.com/repos/$repo/contents"
$headers = @{
    "Authorization" = "token $Token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "HardwareInfoPro-Builder"
}
$projDir = "C:\Users\20248751\Documents\Qoder\2026-07-30\chat-1\HardwareInfoPro"

# Get all tracked files
$gitExe = "C:\Users\20248751\Documents\Qoder\2026-07-30\chat-1\.tools\git\cmd\git.exe"
$files = & $gitExe -C $projDir ls-files 2>&1
$totalFiles = $files.Count
Write-Host "Uploading $totalFiles files to GitHub..."

$successCount = 0
$failCount = 0

foreach ($file in $files) {
    $filePath = Join-Path $projDir $file
    $content = [Convert]::ToBase64String([IO.File]::ReadAllBytes($filePath))
    
    $body = @{
        message = "Add $file"
        content = $content
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/$file" -Method Put -Headers $headers -Body $body -ContentType "application/json"
        $successCount++
        Write-Host "[$successCount/$totalFiles] Uploaded: $file"
    } catch {
        $failCount++
        Write-Host "[$($successCount+$failCount)/$totalFiles] FAILED: $file - $($_.Exception.Message)"
    }
    
    # Rate limiting - be gentle with the API
    Start-Sleep -Milliseconds 500
}

Write-Host "`nDone! Success: $successCount, Failed: $failCount"

if ($successCount -gt 0) {
    Write-Host "`nBuild should start automatically at:"
    Write-Host "https://github.com/$repo/actions"
    Write-Host "`nAPK will be available as an artifact after build completes."
}
