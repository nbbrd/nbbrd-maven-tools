Set-StrictMode -Version 2.0

# 1. Check arguments
$sourceUri = [System.Uri]::new([Helper]::DecodeArg($args[0]))
echo "[.] Source URI: $sourceUri"

$targetUri = [System.Uri]::new([Helper]::DecodeArg($args[1]))
echo "[.] Target URI: $targetUri"

$targetProperty = [Helper]::DecodeArg($args[2])
echo "[.] Target property: $targetProperty"

$workingDir = [Helper]::DecodeArg($args[3])
echo "[.] Working directory: $workingDir"

$reportFile = [Helper]::DecodeArg($args[4])
echo "[.] Report file: $reportFile"

# 2. Deal with source
if ($sourceUri.Scheme -eq "file") {
    echo "[.] Local source"

    $sourcePath = $sourceUri.LocalPath
    echo "[.] Source path: $sourcePath"

    echo "[>] Getting source version"
    $sourceVersion = (mvn -q -f $sourcePath help:evaluate -Dexpression="project.version" -DforceStdout)
    echo "[.] Source version: $sourceVersion"

    echo "[>] Installing source to local repository"
    mvn -q -f $sourcePath install -DskipTests
} else {
    echo "[.] Remote source"
    echo "WIP"
}

# 3. Deal with target
if ($targetUri.Scheme -eq "file") {
    echo "[.] Local target"

    $targetPath = $targetUri.LocalPath
    echo "[.] Target path: $sourcePath"

    echo "WIP"
} else {
    echo "[.] Remote target"

    $targetPath = "$workingDir\target"
    echo "[.] Target path: $targetPath"

    echo "[>] Cloning target"
    git clone -q $targetUri $targetPath

    echo "[>] Getting tags"
    $tags = (git -C $targetPath tag --sort=-creatordate) -split "`n"
    echo "[.] Tags: $($tags -join ', ')"

    foreach ($tag in $tags) {
        echo "[>] Checking out tag $tag"
        git -C $targetPath checkout -q $tag

        echo "[>] Getting target version"
        $targetVersion = (mvn -q -f $targetPath help:evaluate -Dexpression="$targetProperty" -DforceStdout)
        echo "[.] Target version: $targetVersion"

        echo "[>] Getting default version"
        $defaultVersion = (mvn -q -f $targetPath help:evaluate -Dexpression="$targetProperty" -DforceStdout)
        echo "[.] Default version: $defaultVersion"

        echo "[>] Updating target version"
        mvn -q -f $targetPath versions:set-property -Dproperty="$targetProperty" -DnewVersion="$sourceVersion"

        echo "[>] Verifying target"
        mvn -q -f $targetPath clean verify -U -DskipTests -D"enforcer.skip"

        echo "[>] Writing result"
        echo "$LASTEXITCODE,$targetUri,$sourceVersion,$targetVersion,$defaultVersion" | out-file -encoding ASCII -append $reportFile

        echo "[>] Cleaning target"
        mvn -q -f $targetPath clean
        git -C $targetPath restore .
    }
}


# --- generic code ---

class Helper {

    static [string] DecodeArg( [string] $argument ) {
        if ($argument -like "base64_*") {
            return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($argument.Substring(7)))
        } else {
            return $argument
        }
    }

    static [array] DecodeArgs( [array] $arguments ) {
        if ($arguments.Length -eq 0) { return @() }
        return $arguments | ForEach-Object { [Helper]::DecodeArg($_) }
    }
}
