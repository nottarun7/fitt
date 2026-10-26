param(
    [string]$JavaHome,
    [string]$AndroidHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $JavaHome) {
    $javaCandidates = @(
        $env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
        "$env:ProgramFiles\Java\jdk-17"
    )
    $JavaHome = $javaCandidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe'))
    } | Select-Object -First 1
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'JDK 17 was not found. Install it, then rerun with -JavaHome <JDK folder>.'
}
$compilerVersion = & (Join-Path $JavaHome 'bin\javac.exe') -version 2>&1
if ($LASTEXITCODE -ne 0 -or "$compilerVersion" -notmatch '^javac 17(?:\.|$)') {
    throw "JDK 17 is required; selected compiler: $compilerVersion"
}

if (-not $AndroidHome) {
    $sdkCandidates = @(
        $env:ANDROID_HOME,
        [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User'),
        "$env:LOCALAPPDATA\Android\Sdk"
    )
    $AndroidHome = $sdkCandidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe'))
    } | Select-Object -First 1
}
if (-not $AndroidHome -or -not (Test-Path -LiteralPath (Join-Path $AndroidHome 'platform-tools\adb.exe'))) {
    throw 'Android SDK was not found. Install it in Android Studio, then rerun with -AndroidHome <SDK folder>.'
}

$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$AndroidHome = (Resolve-Path -LiteralPath $AndroidHome).Path
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
[Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $AndroidHome, 'User')

$toolPaths = @((Join-Path $JavaHome 'bin'), (Join-Path $AndroidHome 'platform-tools'))
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$userEntries = @($userPath -split ';' | Where-Object { $_ })
$missingEntries = @($toolPaths | Where-Object { $_ -notin $userEntries })
if ($missingEntries.Count -gt 0) {
    [Environment]::SetEnvironmentVariable('Path', (($missingEntries + $userEntries) -join ';'), 'User')
}
$env:Path = ($toolPaths -join ';') + ';' + $env:Path

$sdkProperty = $AndroidHome.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.Encoding]::ASCII)

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'))) {
    & python (Join-Path $PSScriptRoot 'bootstrap-wrapper.py')
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper bootstrap failed.' }
}

Write-Host "JDK: $JavaHome ($compilerVersion)"
Write-Host "Android SDK: $AndroidHome"
foreach ($package in @('platforms\android-36', 'build-tools\35.0.0')) {
    if (Test-Path -LiteralPath (Join-Path $AndroidHome $package)) {
        Write-Host "Installed: $package"
    } else {
        Write-Host "Missing: $package. Install in Android Studio, or let the first Gradle build download it with accepted SDK licenses."
    }
}
Write-Host 'User environment and ignored local.properties configured. Reopen existing terminals to pick up saved variables.'
