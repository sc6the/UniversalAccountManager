param([ValidateSet('modern','legacy')][string]$Edition = 'legacy', [switch]$WaitForUnlock)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$minecraft = [IO.Path]::GetFullPath((Join-Path $env:APPDATA '.minecraft'))
$mods = Join-Path $minecraft 'mods'
$variants = Join-Path $minecraft 'mod-variants\UniversalAccountManager'
$utils = Join-Path (Split-Path $project -Parent) 'BlankClient\build\libs\BlankUtils-1.0.jar'
$chosen = Join-Path $project "build\libs\UniversalAccountManager-2.15-$Edition.jar"
$modern = Join-Path $project 'build\libs\UniversalAccountManager-2.15-modern.jar'
$legacy = Join-Path $project 'build\libs\UniversalAccountManager-2.15-legacy.jar'
foreach ($file in @($chosen, $modern, $legacy, $utils)) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Build artifact missing: $file" }
}
New-Item -ItemType Directory -Force -Path $mods, $variants | Out-Null
$backup = Join-Path $minecraft ('mod-backups\uam-ui-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$old = @(Get-ChildItem -LiteralPath $mods -File | Where-Object { $_.Name -match '^(UniversalAccountManager[^\\/]*|BlankUtils[^\\/]*)\.jar$' })
$versionMods = Join-Path $mods '1.8.9'
if (Test-Path -LiteralPath $versionMods) {
    $old += @(Get-ChildItem -LiteralPath $versionMods -File | Where-Object { $_.Name -match '^(UniversalAccountManager[^\\/]*|BlankUtils[^\\/]*)\.jar$' })
}
# Check all targets before moving any jar, so a running game cannot leave a partial upgrade.
do {
    $locked = @()
    foreach ($file in $old) {
        if (!(Test-Path -LiteralPath $file.FullName)) { continue }
        try {
            $probe = [IO.File]::Open($file.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
            $probe.Dispose()
        } catch [IO.IOException] { $locked += $file.FullName }
    }
    if ($locked.Count) {
        if (!$WaitForUnlock) { throw 'Minecraft has the installed jars open. Close it or use -WaitForUnlock.' }
        Start-Sleep -Seconds 2
    }
} while ($locked.Count)
foreach ($file in $old) {
    $resolved = [IO.Path]::GetFullPath($file.FullName)
    if (!$resolved.StartsWith($mods + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Unexpected replacement target: $resolved" }
    $relative = $resolved.Substring($mods.Length + 1)
    $destination = Join-Path $backup $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Move-Item -LiteralPath $resolved -Destination $destination
}
try {
    Copy-Item -LiteralPath $chosen, $utils -Destination $mods
    Copy-Item -LiteralPath $modern, $legacy -Destination $variants -Force
    foreach ($file in @($chosen, $utils)) {
        $installed = Join-Path $mods (Split-Path $file -Leaf)
        if ((Get-FileHash -LiteralPath $file).Hash -ne (Get-FileHash -LiteralPath $installed).Hash) { throw "Installed checksum mismatch: $installed" }
    }
} catch {
    Write-Error "Installation failed. Previous jars are recoverable at $backup. $($_.Exception.Message)"
    throw
}
Write-Output "Installed $Edition UAM and BlankUtils to $mods"
Write-Output "Both edition jars available at $variants"
if ($old.Count) { Write-Output "Replaced jars backed up at $backup" }
