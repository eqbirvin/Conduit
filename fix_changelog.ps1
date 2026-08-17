$readmeFile = "README.md"
$logFile = "build.log\caveman.log"

# Truncate README.md to remove the broken changelog
$readmeLines = Get-Content $readmeFile -Encoding UTF8 | Select-Object -First 54
Set-Content -Path $readmeFile -Value $readmeLines -Encoding UTF8

$lines = Get-Content $logFile -Encoding UTF8
$changelogEntries = @()
$seen = @{}

$regex = '^\[(.*?)\]\s+\[(.*?)\]\s+(.*)$'

foreach ($line in $lines) {
    $line = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($line)) { continue }

    if ($line -match $regex) {
        $timestamp = $matches[1]
        $version = $matches[2]
        $description = $matches[3]
        
        # Deduplicate to handle caveman.log repetitions
        $key = "$version-$description"
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true

        # Capitalize first letter
        $firstChar = $description.Substring(0, 1).ToUpper()
        if ($description.Length -gt 1) {
            $formattedDesc = $firstChar + $description.Substring(1)
        } else {
            $formattedDesc = $firstChar
        }
        
        $changelogEntries += "- **$version** ($timestamp): $formattedDesc"
    } else {
        if (-Not $line.StartsWith('[')) {
            $changelogEntries += "  - $line"
        }
    }
}

$changelogContent = "`n## Extensive Changelog (from caveman.log)`n`n" + ($changelogEntries -join "`n") + "`n"
Add-Content -Path $readmeFile -Value $changelogContent -Encoding UTF8

Write-Output "Successfully fixed README.md formatting"
