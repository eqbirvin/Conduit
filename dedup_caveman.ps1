$logFile = "build.log\caveman.log"
$lines = Get-Content $logFile -Encoding UTF8

$seen = @{}
$uniqueLines = @()

foreach ($line in $lines) {
    # Trim to avoid whitespace only duplicates
    $trimmed = $line.Trim()
    
    if ([string]::IsNullOrWhiteSpace($trimmed)) {
        # Keep empty lines but avoid too many consecutive ones
        $uniqueLines += ""
        continue
    }
    
    if (-not $seen.ContainsKey($trimmed)) {
        $seen[$trimmed] = $true
        $uniqueLines += $line
    }
}

$uniqueLines | Set-Content $logFile -Encoding UTF8
Write-Output "Deduplicated caveman.log successfully."
