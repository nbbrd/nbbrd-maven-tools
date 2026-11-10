$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$runtimes = @(
    @{
        Platform = 'linux-x64'
        Asset = 'OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz'
        Sha256 = 'a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd'
    },
    @{
        Platform = 'macos-x64'
        Asset = 'OpenJDK21U-jre_x64_mac_hotspot_21.0.13_9.tar.gz'
        Sha256 = 'c909674b1c10c261b9a3fb7e9c0efebd303e33f88a4fe84be7135b5e2d711f89'
    },
    @{
        Platform = 'macos-aarch64'
        Asset = 'OpenJDK21U-jre_aarch64_mac_hotspot_21.0.13_9.tar.gz'
        Sha256 = '225bab0a09491ef06835488cd942f5d2befa9a0410a2484f0c8bbcf7506ec09a'
    }
)
$baseUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9'
$downloads = Join-Path $PSScriptRoot 'target/runtimes'
$bundles = Join-Path $PSScriptRoot 'target/bundles'
New-Item -ItemType Directory -Force $downloads, $bundles | Out-Null

foreach ($runtime in $runtimes) {
    $archive = Join-Path $downloads "$($runtime.Platform).tar.gz"
    if (-not (Test-Path -LiteralPath $archive)) {
        Write-Host "Downloading $($runtime.Asset)"
        Invoke-WebRequest "$baseUrl/$($runtime.Asset)" -OutFile $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $runtime.Sha256) {
        throw "Checksum mismatch for $archive"
    }

    # Read source metadata with .NET, independently of the plugin's Commons Compress reader.
    $entries = [System.Collections.Generic.List[object]]::new()
    $inputStream = [System.IO.File]::OpenRead($archive)
    try {
        $gzip = [System.IO.Compression.GZipStream]::new($inputStream, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            $tar = [System.Formats.Tar.TarReader]::new($gzip)
            try {
                while ($null -ne ($entry = $tar.GetNextEntry())) {
                    $name = ($entry.Name -replace '^\./', '').TrimEnd('/')
                    if ($name -eq '' -or $name -eq '.') { continue }
                    $type = $entry.EntryType.ToString()
                    if ($type -notin @('RegularFile', 'V7RegularFile', 'Directory', 'SymbolicLink', 'HardLink')) {
                        throw "Unsupported source entry type $type for $name"
                    }
                    $entries.Add(@{
                        name = $name
                        type = $type
                        mode = [int]$entry.Mode -band 511
                        link = $entry.LinkName
                    })
                }
            } finally { $tar.Dispose() }
        } finally { $gzip.Dispose() }
    } finally { $inputStream.Dispose() }

    $homes = @($entries | Where-Object { $_.name -cmatch '(^|/)bin/java$' } | ForEach-Object {
        $runtimeHome = if ($_.name -ceq 'bin/java') { '' } else { $_.name.Substring(0, $_.name.Length - '/bin/java'.Length) }
        $releaseFile = if ($runtimeHome -eq '') { 'release' } else { "$runtimeHome/release" }
        if ($entries.name -ccontains $releaseFile) { $runtimeHome }
    })
    if ($homes.Count -ne 1) { throw "Expected one Java installation in $archive, found $($homes.Count)" }
    $links = @($entries | Where-Object type -eq 'SymbolicLink')
    if ($runtime.Platform -eq 'linux-x64' -and $links.Count -eq 0) {
        throw "Expected real runtime symlinks in the Linux fixture: $archive"
    }
    @{
        platform = $runtime.Platform
        javaHome = $homes[0]
        entries = $entries.ToArray()
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $bundles "$($runtime.Platform).json") -Encoding utf8NoBOM
    Write-Host "Recorded $($entries.Count) entries and $($links.Count) symlinks for $($runtime.Platform)"
}
