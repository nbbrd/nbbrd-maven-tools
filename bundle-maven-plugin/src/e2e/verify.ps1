param(
    [Parameter(Mandatory)]
    [ValidateSet('linux-x64', 'macos-x64', 'macos-aarch64')]
    [string] $Platform,
    [Parameter(Mandatory)]
    [ValidateSet('zip', 'tar.gz')]
    [string] $Format,
    [Parameter(Mandatory)]
    [string] $BundleDirectory
)

$ErrorActionPreference = 'Stop'
if ($IsWindows) { throw 'Verify Linux/macOS bundles on a native runner, not on Windows' }
$expectedArch = if ($Platform -eq 'macos-aarch64') { 'arm64' } else { 'x86_64' }
$arch = & uname -m
if ($LASTEXITCODE -ne 0 -or $arch -ne $expectedArch) { throw "Expected $expectedArch runner, found $arch" }
if (($Platform.StartsWith('linux-') -and -not $IsLinux) -or ($Platform.StartsWith('macos-') -and -not $IsMacOS)) {
    throw "Wrong runner OS for $Platform"
}

$manifest = Get-Content -LiteralPath (Join-Path $BundleDirectory "$Platform.json") -Raw | ConvertFrom-Json
if ($manifest.platform -ne $Platform) { throw "Wrong source metadata for $Platform" }
$bundle = Join-Path $BundleDirectory "$Platform.$Format"
# Spaces exercise launcher quoting. Never chmod or otherwise repair the extracted bundle.
$destination = Join-Path $env:RUNNER_TEMP "bundle smoke $Platform $Format"
New-Item -ItemType Directory $destination | Out-Null
Write-Host "Extracting $bundle into $destination"
if ($Format -eq 'zip') {
    & unzip -v | Select-Object -First 2
    & unzip -q $bundle -d $destination
} else {
    & tar --version | Select-Object -First 1
    & tar -xzf $bundle -C $destination
}
if ($LASTEXITCODE -ne 0) { throw "Native extraction failed for $bundle" }

$app = Join-Path $destination 'app'
$links = 0
foreach ($entry in $manifest.entries) {
    $path = Join-Path $app $entry.name
    if ($entry.type -eq 'SymbolicLink') {
        $target = [System.IO.FileInfo]::new($path).LinkTarget
        if ($target -cne $entry.link) { throw "Symlink changed: $path -> $target; expected $($entry.link)" }
        if (-not (Test-Path -LiteralPath $path)) { throw "Broken runtime symlink: $path" }
        $links++
    } else {
        $kind = if ($entry.type -eq 'Directory') { 'Container' } else { 'Leaf' }
        if (-not (Test-Path -LiteralPath $path -PathType $kind)) { throw "Missing runtime entry: $path" }
        $mode = [int][System.IO.File]::GetUnixFileMode($path) -band 511
        if ($mode -ne $entry.mode) { throw "Permissions changed for ${path}: $mode; expected $($entry.mode)" }
        if ($entry.type -eq 'HardLink') {
            $target = Join-Path $app $entry.link
            if ((Get-FileHash -LiteralPath $path).Hash -ne (Get-FileHash -LiteralPath $target).Hash) {
                throw "Hard-link contents changed: $path"
            }
        }
    }
}
if ($Platform -eq 'linux-x64' -and $links -eq 0) { throw 'No Linux runtime symlinks were checked' }
Write-Host "Verified $($manifest.entries.Count) runtime entries, including $links symlinks"

$javaHome = Join-Path $app $manifest.javaHome
$java = Join-Path $javaHome 'bin/java'
& $java -version
if ($LASTEXITCODE -ne 0) { throw 'Bundled Java failed to start' }

$launcher = Join-Path $app 'bin/smoke'
foreach ($hostileEnvironment in @($false, $true)) {
    if ($hostileEnvironment) {
        $env:JAVA_HOME = Join-Path $destination 'wrong Java home'
        $env:JAVACMD = Join-Path $destination 'wrong Java command'
    }
    $output = @(& $launcher $javaHome 2>&1)
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0 -or -not ($output -cmatch '^BUNDLE_SMOKE_OK ')) {
        throw "Application launcher failed (hostile environment: $hostileEnvironment)"
    }
}
