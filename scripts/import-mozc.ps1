param([Parameter(Mandatory=$true)][string]$Source)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$revision = (Get-Content -LiteralPath (Join-Path $sourcePath 'MOZC_REVISION') -Raw).Trim()
if ($revision -ne 'c4b2aef7c65e61be394bacadaec200eb5b97f91c') { throw 'Unexpected Mozc revision' }
$hashes = Get-Content -LiteralPath (Join-Path $sourcePath 'SHA256SUMS')
foreach ($relative in @('jniLibs/arm64-v8a/libmozc.so', 'assets/mozc.data')) {
    $entry = @($hashes | Where-Object { $_ -match ('^[0-9a-f]{64}  ' + [regex]::Escape($relative) + '$') })
    if ($entry.Count -ne 1) { throw "Missing or ambiguous checksum: $relative" }
    $file = Join-Path $sourcePath $relative
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry[0].Substring(0,64)) {
        throw "Checksum mismatch: $relative"
    }
}
foreach ($relative in @('jniLibs/arm64-v8a/libmozc.so', 'assets/mozc.data')) {
    $target = Join-Path $root "app/src/main/$relative"
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourcePath $relative) -Destination $target -Force
}
Write-Output "Imported verified Mozc $revision (arm64-v8a)"
