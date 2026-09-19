$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Security
$project = Split-Path $PSScriptRoot -Parent
$minecraft = Join-Path $env:APPDATA '.minecraft'
$needles = New-Object 'System.Collections.Generic.List[string]'
# Keys are read only into memory for comparison, never printed or written into the build.
$keyFile = Join-Path $minecraft 'config\universalaccountmanager_localts_api_key.dat'
if (Test-Path -LiteralPath $keyFile) {
    $encryptedText = [IO.File]::ReadAllText($keyFile).Trim()
    $needles.Add($encryptedText)
    $encrypted = [Convert]::FromBase64String($encryptedText)
    if ($encrypted.Length -ge 4 -and $encrypted[0] -eq 1 -and $encrypted[1] -eq 0 -and $encrypted[2] -eq 0 -and $encrypted[3] -eq 0) {
        $clear = [Security.Cryptography.ProtectedData]::Unprotect($encrypted, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        try {
            $needles.Add([Text.Encoding]::UTF8.GetString($clear))
            $needles.Add([Convert]::ToBase64String($clear))
        } finally { [Array]::Clear($clear, 0, $clear.Length) }
    } else {
        # Older/manual files may contain the key itself instead of a DPAPI blob.
        $needles.Add([Text.Encoding]::UTF8.GetString($encrypted))
        $needles.Add([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($encryptedText)))
    }
}
$oldKeyFile = Join-Path $env:APPDATA 'nicealts_manager\localtsapi.txt'
if (Test-Path -LiteralPath $oldKeyFile) {
    $value = [IO.File]::ReadAllText($oldKeyFile).Trim()
    if ($value.Length) { $needles.Add($value); $needles.Add([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value))) }
}
$artifacts = @(
    (Join-Path $project 'build\libs\UniversalAccountManager-2.16.jar'),
    (Join-Path $project 'build\libs\UniversalAccountManager-2.16-modern.jar'),
    (Join-Path $project 'build\libs\UniversalAccountManager-2.16-legacy.jar'),
    (Join-Path (Split-Path $project -Parent) 'BlankClient\build\libs\BlankUtils-1.0.jar')
)
foreach ($file in $artifacts) {
    $archive = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '^(config|run|logs)/|\.dat$|\.log$|universalaccountmanager_accounts\.json$|(localts|nicealts)api\.txt$') {
                throw ('Runtime data found in release: ' + (Split-Path $file -Leaf))
            }
            if ($entry.Length -eq 0) { continue }
            $stream = $entry.Open(); $memory = New-Object IO.MemoryStream
            try { $stream.CopyTo($memory); $text = [Text.Encoding]::UTF8.GetString($memory.ToArray()) }
            finally { $stream.Dispose(); $memory.Dispose() }
            foreach ($needle in $needles) {
                if ($needle.Length -gt 0 -and $text.IndexOf($needle, [StringComparison]::Ordinal) -ge 0) {
                    throw ('Localts credential found in release: ' + (Split-Path $file -Leaf))
                }
            }
        }
        Write-Output ('Release audit passed: ' + (Split-Path $file -Leaf))
    } finally { $archive.Dispose() }
}
Write-Output ('Local credential comparison performed: ' + ($needles.Count -gt 0))
$needles.Clear(); $text = $null; $encryptedText = $null; $value = $null
