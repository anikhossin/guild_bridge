$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$versions = @(
    @{ Minecraft = "26.1"; FabricApi = "0.145.1+26.1" },
    @{ Minecraft = "26.1.1"; FabricApi = "0.145.4+26.1.1" },
    @{ Minecraft = "26.1.2"; FabricApi = "0.155.3+26.1.2" },
    @{ Minecraft = "26.2"; FabricApi = "0.161.0+26.2" }
)

foreach ($version in $versions) {
    Write-Host "Building Minecraft $($version.Minecraft)"
    ./gradlew build --stacktrace "-Pminecraft_version=$($version.Minecraft)" "-Pfabric_api_version=$($version.FabricApi)"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
