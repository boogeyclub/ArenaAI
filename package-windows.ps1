<#
.SYNOPSIS
    Builds a native Windows package for the Arena desktop app.

.DESCRIPTION
    1. Compiles + packages the app with Maven (.\mvnw.cmd).
    2. Builds a trimmed runtime image via the javafx-maven-plugin (javafx:jlink).
    3. Runs jpackage to produce either a portable folder (app-image, no extra
       tools needed) or a real installer (msi / exe, requires WiX Toolset v3).

.EXAMPLE
    .\package-windows.ps1
    # -> portable app at target\dist\Arena\Arena.exe

.EXAMPLE
    .\package-windows.ps1 -Type msi -AppVersion 1.0.0
    # -> installer at target\dist\Arena-1.0.0.msi (needs WiX on PATH)
#>
[CmdletBinding()]
param(
    [ValidateSet("app-image", "msi", "exe")]
    [string]$Type = "app-image",
    [string]$AppVersion = "1.0.0"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

function Require-Command([string]$Name, [string]$InstallHint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found. $InstallHint"
    }
}

Require-Command "java" "Install JDK 21+ and ensure 'java' is on PATH."
Require-Command "jpackage" "Install JDK 21+ (jpackage ships with the JDK) and ensure it is on PATH."

Write-Host "==> Building Arena desktop app ($Type) ..." -ForegroundColor Cyan

# 1) Compile + package the modular jar.
& .\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

# 2) Build a trimmed runtime image with the app bundled in.
#    (Output dir comes from <jlinkImageName> in pom.xml -> target\app)
& .\mvnw.cmd javafx:jlink
if ($LASTEXITCODE -ne 0) { throw "javafx:jlink failed." }

$RuntimeImage = Join-Path $RepoRoot "target\app"
if (-not (Test-Path $RuntimeImage)) { throw "Runtime image not found at $RuntimeImage" }

# 3) Optional installer icon. Windows needs .ico format — see README-DESKTOP.md.
$IconArg = @()
$IconPath = Join-Path $RepoRoot "src\main\resources\arena-icon.ico"
if (Test-Path $IconPath) {
    $IconArg = @("--icon", $IconPath)
} else {
    Write-Warning "No arena-icon.ico found — continuing with the default Java icon. See README-DESKTOP.md to add one."
}

if ($Type -ne "app-image") {
    Write-Host "Note: '$Type' installers require WiX Toolset v3 on PATH (https://wixtoolset.org)." -ForegroundColor Yellow
}

$Dest = Join-Path $RepoRoot "target\dist"
New-Item -ItemType Directory -Force -Path $Dest | Out-Null

$JPackageArgs = @(
    "--type", $Type,
    "--name", "Arena",
    "--app-version", $AppVersion,
    "--vendor", "O'Digital",
    "--description", "Arena desktop app - arena.ai on your Windows desktop.",
    "--copyright", "O'Digital",
    "--runtime-image", $RuntimeImage,
    "--module", "cm.odigital.arenaai/cm.odigital.arenaai.Launcher",
    "--dest", $Dest,
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser"
) + $IconArg

Write-Host "==> Running: jpackage $($JPackageArgs -join ' ')" -ForegroundColor Cyan
& jpackage @JPackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

Write-Host ""
Write-Host "Done! Output is in target\dist" -ForegroundColor Green
if ($Type -eq "app-image") {
    Write-Host "Portable app: target\dist\Arena\Arena.exe"
}
