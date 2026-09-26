param(
    [string]$Java = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $repo 'app\build\direct-junit'
$testClasspath = (Get-Content -LiteralPath (Join-Path $runtime 'classpath.txt') -Raw).Trim()
$testClasses = @(Get-Content -LiteralPath (Join-Path $runtime 'classes.txt') | Where-Object { $_.Trim() })
if ($testClasses.Count -eq 0) { throw 'No compiled test classes; exportDebugTestRuntime must run first.' }
Push-Location $repo
try {
    & $Java '-cp' $testClasspath 'org.junit.runner.JUnitCore' @testClasses
    if ($LASTEXITCODE -ne 0) { throw "Direct JUnit failed: $LASTEXITCODE" }
} finally { Pop-Location }
