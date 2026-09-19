param([ValidateSet('modern','legacy')][string]$Edition = 'modern', [switch]$WithUtils, [switch]$UseSelectedWidgets, [switch]$BlankUiCheck, [switch]$WithOptifine)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = Split-Path $PSScriptRoot -Parent
$minecraft = Join-Path $env:APPDATA '.minecraft'
$version = '1.8.9-forge1.8.9-11.15.1.2318-1.8.9'
$forge = Get-Content -Raw -LiteralPath (Join-Path $minecraft "versions\$version\$version.json") | ConvertFrom-Json
$vanilla = Get-Content -Raw -LiteralPath (Join-Path $minecraft 'versions\1.8.9\1.8.9.json') | ConvertFrom-Json
$run = Join-Path $project ('build\smoke-' + $Edition + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run, (Join-Path $run 'mods'), (Join-Path $run 'natives') | Out-Null
if ($UseSelectedWidgets) {
    # Copy only GUI artwork into the isolated run; never change the player's selected packs.
    $selection = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $minecraft 'options.txt') | Where-Object { $_.StartsWith('resourcePacks:') } | Select-Object -First 1
    $packs = $selection.Substring('resourcePacks:'.Length) | ConvertFrom-Json
    $testPack = Join-Path $run 'resourcepacks\uam-selected-widgets'
    New-Item -ItemType Directory -Force -Path (Join-Path $testPack 'assets\minecraft\textures\gui') | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures\pack.mcmeta') -Destination $testPack
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures\options.txt') -Destination $run
    $foundWidgets = $false
    foreach ($pack in $packs) {
        $packPath = Join-Path (Join-Path $minecraft 'resourcepacks') $pack
        foreach ($asset in @('assets/minecraft/textures/gui/widgets.png', 'assets/minecraft/textures/gui/options_background.png')) {
            $destination = Join-Path $testPack $asset
            if (Test-Path -LiteralPath $packPath -PathType Container) {
                $source = Join-Path $packPath $asset
                if (Test-Path -LiteralPath $source) {
                    Copy-Item -LiteralPath $source -Destination $destination -Force
                    if ($asset.EndsWith('/widgets.png')) { $foundWidgets = $true; Write-Output "Testing widgets from $pack" }
                }
            } elseif (Test-Path -LiteralPath $packPath -PathType Leaf) {
                $archive = [IO.Compression.ZipFile]::OpenRead($packPath)
                try {
                    $entry = $archive.GetEntry($asset)
                    if ($entry) {
                        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
                        if ($asset.EndsWith('/widgets.png')) { $foundWidgets = $true; Write-Output "Testing widgets from $pack" }
                    }
                } finally { $archive.Dispose() }
            }
        }
    }
    if (!$foundWidgets) { throw 'No widgets.png found in the selected resource packs' }
}
Copy-Item -LiteralPath (Join-Path $project "build\libs\UniversalAccountManager-2.16-$Edition.jar") -Destination (Join-Path $run 'mods')
Copy-Item -LiteralPath (Join-Path $project 'build\libs\uam-ui-smoke.jar') -Destination (Join-Path $run 'mods')
if ($WithUtils) { Copy-Item -LiteralPath (Join-Path (Split-Path $project -Parent) 'BlankClient\build\libs\BlankUtils-1.0.jar') -Destination (Join-Path $run 'mods') }
if ($BlankUiCheck -and !$WithUtils) { throw '-BlankUiCheck requires -WithUtils' }
if ($WithOptifine) { Copy-Item -LiteralPath (Join-Path $minecraft 'mods\OptiFine_1.8.9_HD_U_M5.jar') -Destination (Join-Path $run 'mods') }
$classpath = New-Object 'System.Collections.Generic.List[string]'
$seen = @{}
foreach ($lib in @($forge.libraries) + @($vanilla.libraries)) {
    $allowed = $true
    if ($lib.rules) {
        $allowed = $false
        foreach ($rule in $lib.rules) {
            if (!$rule.os -or $rule.os.name -eq 'windows') { $allowed = $rule.action -eq 'allow' }
        }
    }
    if (!$allowed) { continue }
    $parts = $lib.name.Split(':')
    $key = $parts[0] + ':' + $parts[1]
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true
    $base = $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2]
    if ($lib.natives) {
        if (!$lib.natives.windows) { continue }
        $classifier = $lib.natives.windows.Replace('${arch}', '64')
        $native = Join-Path $minecraft ('libraries/' + $base + '-' + $classifier + '.jar')
        $zip = [IO.Compression.ZipFile]::OpenRead($native)
        try {
            foreach ($entry in $zip.Entries) {
                if ($entry.Name.EndsWith('.dll')) {
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $run ('natives\' + $entry.Name)), $true)
                }
            }
        } finally { $zip.Dispose() }
    } else {
        $path = Join-Path $minecraft ('libraries/' + $base + '.jar')
        if (!(Test-Path -LiteralPath $path)) { throw "Missing library: $path" }
        $classpath.Add($path)
    }
}
$classpath.Add((Join-Path $minecraft 'versions\1.8.9\1.8.9.jar'))
$arguments = @('-Xmx2G', ('-Djava.library.path="' + (Join-Path $run 'natives') + '"'), '-cp', ('"' + ($classpath -join ';') + '"'),
    'net.minecraft.launchwrapper.Launch', '--username', 'UiPreview', '--version', $version,
    '--gameDir', ('"' + $run + '"'), '--assetsDir', ('"' + (Join-Path $minecraft 'assets') + '"'),
    '--assetIndex', '1.8', '--uuid', '00000000000000000000000000000000', '--accessToken', '0',
    '--userProperties', '{}', '--userType', 'legacy', '--width', '1100', '--height', '700',
    '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker')
if ($BlankUiCheck) { $arguments = @('-Dblank.ui.smoke=true') + $arguments }
$originalAppData = $env:APPDATA
try {
    # Blank intentionally shares preferences across game directories. Isolate its global config too.
    $env:APPDATA = Join-Path $run 'isolated-appdata'
    New-Item -ItemType Directory -Force -Path $env:APPDATA | Out-Null
    $process = Start-Process -FilePath 'C:\Program Files\Zulu\zulu-8\bin\java.exe' -ArgumentList $arguments -WorkingDirectory $run -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'stdout.log') -RedirectStandardError (Join-Path $run 'stderr.log')
} finally { $env:APPDATA = $originalAppData }
Write-Output "Smoke process: $($process.Id)"
Write-Output "Smoke directory: $run"
