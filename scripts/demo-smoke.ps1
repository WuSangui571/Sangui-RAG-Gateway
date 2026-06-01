param(
    [Parameter(Mandatory = $true, HelpMessage = "App API key (sk-sangui-...)")]
    [string]$ApiKey,

    [string]$BackendBaseUrl = "http://localhost:8080",

    [string]$FrontendBaseUrl = "http://localhost:3000",

    [string]$Message = "What integration style does Sangui RAG Gateway provide?"
)

$ErrorActionPreference = 'Stop'
$script:exitCode = 0

function Write-Step {
    param([string]$Text)
    Write-Host "--- $Text ---" -ForegroundColor Cyan
}

function Write-Pass {
    param([string]$Text)
    Write-Host "  PASS: $Text" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Text)
    Write-Host "  FAIL: $Text" -ForegroundColor Red
    $script:exitCode = 1
}

function Get-JsonBody {
    param([string]$Content, [bool]$Stream = $false)
    $obj = @{
        model    = "ignored"
        messages = @(
            @{ role = "user"; content = $Content }
        )
    }
    if ($Stream) {
        $obj["stream"] = $true
    }
    return ($obj | ConvertTo-Json -Compress -Depth 5)
}

function Invoke-CurlCapture {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [string[]]$Headers = @(),
        [string]$Body = $null,
        [int]$MaxTimeSeconds = 30,
        [switch]$NoBuffer
    )

    if ([string]::IsNullOrWhiteSpace($Url)) {
        throw "Url must not be blank"
    }

    $outputPath = [System.IO.Path]::GetTempFileName()
    try {
        $curlArgs = @("-s", "-o", $outputPath, "-w", "%{http_code}", "--connect-timeout", "10", "--max-time", "$MaxTimeSeconds")
        if ($NoBuffer) {
            $curlArgs += "-N"
        }
        if ($Method -ne "GET") {
            $curlArgs += @("-X", $Method)
        }
        foreach ($header in $Headers) {
            $curlArgs += @("-H", $header)
        }
        if ($null -ne $Body) {
            $curlArgs += @("-d", $Body)
        }
        $curlArgs += @("--", $Url)

        $statusText = & curl.exe @curlArgs
        $curlExitCode = $LASTEXITCODE
        $responseBody = [System.IO.File]::ReadAllText($outputPath)
        $statusCode = 0
        if ($statusText -match '^\d{3}$') {
            $statusCode = [int]$statusText
        }

        return [PSCustomObject]@{
            StatusCode = $statusCode
            Body       = $responseBody
            CurlExit   = $curlExitCode
        }
    }
    finally {
        if (Test-Path $outputPath) {
            Remove-Item -LiteralPath $outputPath -Force
        }
    }
}

Write-Host "RAG Demo Smoke Test" -ForegroundColor Yellow
Write-Host "Backend : $BackendBaseUrl"
Write-Host "Frontend: $FrontendBaseUrl"
Write-Host "Message : $Message"
Write-Host ""

if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: curl.exe not found on PATH. This script requires the Windows system curl." -ForegroundColor Red
    exit 1
}

Write-Step "1. Backend health"
try {
    $response = Invoke-CurlCapture -Url "$BackendBaseUrl/api/health"
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-Fail "curl exit code $($response.CurlExit); is backend running?"
    }
    elseif ($response.StatusCode -ne 200) {
        Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
    }
    else {
        $json = $result | ConvertFrom-Json
        if ($json.code -eq 'OK' -and $json.data.status -eq 'UP') {
            Write-Pass "code=OK, status=UP"
        }
        else {
            Write-Fail "Unexpected response: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-Fail "Exception: $_"
}

Write-Step "2. Frontend proxy health"
try {
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/api/health"
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-Fail "curl exit code $($response.CurlExit); is frontend running?"
    }
    elseif ($response.StatusCode -ne 200) {
        Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
    }
    elseif ($result.TrimStart().StartsWith('<')) {
        Write-Fail "Response is HTML (SPA fallback), not JSON; proxy may be misrouted"
    }
    else {
        $json = $result | ConvertFrom-Json
        if ($json.code -eq 'OK') {
            Write-Pass "code=OK (JSON, not HTML)"
        }
        else {
            Write-Fail "Unexpected response: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-Fail "Exception (likely not valid JSON): $_"
}

Write-Step "3. Non-streaming chat"
try {
    $body = Get-JsonBody -Content $Message
    $headers = @("Content-Type: application/json", "Authorization: Bearer $ApiKey")
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/v1/chat/completions" -Method "POST" -Headers $headers -Body $body -MaxTimeSeconds 60
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-Fail "curl exit code $($response.CurlExit)"
    }
    elseif ($response.StatusCode -ne 200) {
        try {
            $json = $result | ConvertFrom-Json
            if ($json.error) {
                Write-Fail "HTTP $($response.StatusCode), gateway error code=$($json.error.code), message=$($json.error.message)"
            }
            else {
                Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
            }
        }
        catch {
            Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
    else {
        try {
            $json = $result | ConvertFrom-Json
            if ($json.choices -and $json.choices[0].message.content) {
                $preview = $json.choices[0].message.content
                if ($preview.Length -gt 100) { $preview = $preview.Substring(0, 100) + "..." }
                Write-Pass "HTTP 200, content: $preview"
            }
            else {
                Write-Fail "No content in choices[0].message.content"
            }
        }
        catch {
            Write-Fail "HTTP 200 but response is not valid JSON: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-Fail "Exception: $_"
}

Write-Step "4. Streaming chat"
try {
    $body = Get-JsonBody -Content $Message -Stream $true
    $headers = @("Content-Type: application/json", "Authorization: Bearer $ApiKey")
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/v1/chat/completions" -Method "POST" -Headers $headers -Body $body -MaxTimeSeconds 60 -NoBuffer
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-Fail "curl exit code $($response.CurlExit)"
    }
    elseif ($response.StatusCode -ne 200) {
        try {
            $err = $result | ConvertFrom-Json
            if ($err.error) {
                Write-Fail "HTTP $($response.StatusCode), stream returned gateway error code=$($err.error.code), message=$($err.error.message)"
            }
            else {
                Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
            }
        }
        catch {
            Write-Fail "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
    else {
        try {
            $err = $result | ConvertFrom-Json -ErrorAction Stop
            if ($err.error) {
                Write-Fail "Stream returned JSON error instead of SSE: code=$($err.error.code), message=$($err.error.message)"
                return
            }
        }
        catch {
            # Not JSON, check for SSE
        }
        if ($result -match '\[DONE\]') {
            $chunkCount = ([regex]::Matches($result, '(?m)^data:')).Count
            Write-Pass "SSE stream received, $chunkCount data chunk(s), [DONE] present"
        }
        elseif ($result -match 'data:') {
            Write-Fail "SSE chunks received but [DONE] missing; stream may have been truncated"
        }
        else {
            Write-Fail "No SSE data: chunks or [DONE] marker found. First 200 chars: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-Fail "Exception: $_"
}

if ($script:exitCode -eq 0) {
    Write-Host "`nAll checks passed." -ForegroundColor Green
    exit 0
}
else {
    Write-Host "`nOne or more checks FAILED." -ForegroundColor Red
    exit $script:exitCode
}
