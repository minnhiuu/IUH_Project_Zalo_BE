param(
    [switch]$SkipInfra,
    [switch]$SkipBuild,
    [switch]$OnlyCore
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

function Write-Step {
    param([string]$Message)
    Write-Host "[BondHub] $Message" -ForegroundColor Cyan
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 180,
        [int]$IntervalSeconds = 3
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds $IntervalSeconds
            continue
        }

        Start-Sleep -Seconds $IntervalSeconds
    }

    return $false
}

function Start-ServiceWindow {
    param(
        [string]$Module,
        [string]$Title
    )

    $command = "Set-Location '$RepoRoot'; `$Host.UI.RawUI.WindowTitle = '$Title'; .\\mvnw -pl $Module spring-boot:run"
    Start-Process -FilePath "powershell" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -WorkingDirectory $RepoRoot | Out-Null
}

$services = @(
    @{ Module = "discovery-server"; Name = "Discovery Server"; Health = "http://localhost:8761/" },
    @{ Module = "config-server"; Name = "Config Server"; Health = "http://localhost:8888/actuator/health" },
    @{ Module = "auth-service"; Name = "Auth Service"; Health = "http://localhost:8084/actuator/health" },
    @{ Module = "social-feed-service"; Name = "Social Feed Service"; Health = "http://localhost:8088/actuator/health" },
    @{ Module = "api-gateway"; Name = "API Gateway"; Health = "http://localhost:8080/actuator/health" }
)

if (-not $OnlyCore) {
    $services = @(
        @{ Module = "discovery-server"; Name = "Discovery Server"; Health = "http://localhost:8761/" },
        @{ Module = "config-server"; Name = "Config Server"; Health = "http://localhost:8888/actuator/health" },
        @{ Module = "auth-service"; Name = "Auth Service"; Health = "http://localhost:8084/actuator/health" },
        @{ Module = "user-service"; Name = "User Service"; Health = "http://localhost:8081/actuator/health" },
        @{ Module = "friend-service"; Name = "Friend Service"; Health = "http://localhost:8086/actuator/health" },
        @{ Module = "social-feed-service"; Name = "Social Feed Service"; Health = "http://localhost:8088/actuator/health" },
        @{ Module = "api-gateway"; Name = "API Gateway"; Health = "http://localhost:8080/actuator/health" }
    )
}

if (-not $SkipInfra) {
    Write-Step "Starting infrastructure containers with docker compose..."
    docker compose up -d
}

if (-not $SkipBuild) {
    $moduleList = ($services | ForEach-Object { $_.Module }) -join ","
    Write-Step "Building required modules and dependencies..."
    .\mvnw -pl $moduleList -am -DskipTests compile
}

foreach ($svc in $services) {
    Write-Step "Starting $($svc.Name)..."
    Start-ServiceWindow -Module $svc.Module -Title $svc.Name

    Write-Step "Waiting for $($svc.Name) to be ready at $($svc.Health)..."
    $ready = Wait-HttpReady -Url $svc.Health -TimeoutSeconds 240 -IntervalSeconds 4

    if (-not $ready) {
        Write-Host "[BondHub] Warning: $($svc.Name) is not ready yet. Continuing startup." -ForegroundColor Yellow
    } else {
        Write-Host "[BondHub] $($svc.Name) is ready." -ForegroundColor Green
    }
}

Write-Host "[BondHub] Startup sequence finished." -ForegroundColor Green
Write-Host "[BondHub] API Gateway: http://localhost:8080" -ForegroundColor Green
Write-Host "[BondHub] Eureka: http://localhost:8761" -ForegroundColor Green
Write-Host "[BondHub] Config Server: http://localhost:8888" -ForegroundColor Green
