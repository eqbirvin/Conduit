$lines = Get-Content "build.log\caveman.log"
$versions = @()

foreach ($line in $lines) {
    if ($line -match '\[(v[0-9]+(?:\.[0-9]+)*[^\]]*)\]') {
        $versions += $matches[1]
    }
}

$versions | Set-Content "versions_list.txt"
