param(
    [Parameter(Mandatory = $true, HelpMessage = "App API key (sk-sangui-...)")]
    [string]$ApiKey,

    [string]$BackendBaseUrl = "http://localhost:8080",

    [string]$FrontendBaseUrl = "http://localhost:3000",

    [string]$Message = "What integration style does Sangui RAG Gateway provide?",

    [int]$AppId = 0,

    [long]$AdminUserId = 0,

    [string]$RevokedApiKey = "",

    [switch]$VerifyRevokedKey
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

function Write-FailBoundary {
    param([string]$Boundary, [string]$Text)
    Write-Host "  FAIL [$Boundary]: $Text" -ForegroundColor Red
    $script:exitCode = 1
}

function Stop-AfterFailure {
    Write-Host "`nOne or more checks FAILED." -ForegroundColor Red
    exit $script:exitCode
}

function Classify-GatewayError {
    param([int]$StatusCode, [string]$ErrorCode)
    switch ($ErrorCode) {
        'invalid_api_key'     { return 'auth' }
        'upstream_error'      { return 'upstream' }
        'upstream_timeout'    { return 'upstream' }
        'embedding_failed'    { return 'embedding' }
        'knowledge_base_not_ready' { return 'retrieval' }
        'model_config_not_ready'   { return 'retrieval' }
        default {
            if ($StatusCode -eq 401) { return 'auth' }
            if ($StatusCode -ge 502 -and $StatusCode -le 504) { return 'upstream' }
            if ($StatusCode -eq 409) { return 'retrieval' }
            return 'unknown'
        }
    }
}

function Test-ForbiddenFields {
    param([PSObject]$JsonObj)
    $forbidden = @(
        'key_hash', 'api_key', 'api_key_encrypted', 'provider_response_body',
        'stack_trace', 'embedding', 'prompt', 'messages', 'full_messages',
        'augmented_prompt', 'authorization', 'upstream_api_key', 'storage_path',
        'content', 'chunk_content'
    )
    $found = @()
    if ($null -eq $JsonObj) {
        return $found
    }
    if ($JsonObj -is [System.Array]) {
        foreach ($item in $JsonObj) {
            $found += Test-ForbiddenFields -JsonObj $item
        }
        return ($found | Select-Object -Unique)
    }
    if ($JsonObj -is [string] -or $JsonObj.GetType().IsPrimitive) {
        return $found
    }
    foreach ($prop in $JsonObj.PSObject.Properties) {
        if ($forbidden -contains $prop.Name.ToLowerInvariant()) {
            $found += $prop.Name
        }
        if ($null -ne $prop.Value -and -not ($prop.Value -is [string])) {
            $found += Test-ForbiddenFields -JsonObj $prop.Value
        }
    }
    return ($found | Select-Object -Unique)
}

function Get-ReadinessBoundary {
    param([PSObject[]]$Checks)
    $failedKeys = @()
    foreach ($check in $Checks) {
        if ($check.status -and $check.status -ne 'READY') {
            $failedKeys += $check.key
        }
    }

    if ($failedKeys -contains 'embedding_config') { return 'embedding' }
    if ($failedKeys -contains 'active_api_key') { return 'auth' }
    if ($failedKeys -contains 'default_model_config') { return 'retrieval' }
    if ($failedKeys -contains 'default_knowledge_base') { return 'retrieval' }
    if ($failedKeys -contains 'knowledge_base_status') { return 'retrieval' }
    return 'readiness'
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
    $bodyPath = $null
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
        if (-not [string]::IsNullOrEmpty($Body)) {
            $bodyPath = [System.IO.Path]::GetTempFileName()
            $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::WriteAllText($bodyPath, $Body, $utf8NoBom)
            $curlArgs += @("--data-binary", "@$bodyPath")
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
        if ($bodyPath -and (Test-Path $bodyPath)) {
            Remove-Item -LiteralPath $bodyPath -Force
        }
    }
}

Write-Host "RAG Demo Smoke Test" -ForegroundColor Yellow
Write-Host "Backend : $BackendBaseUrl"
Write-Host "Frontend: $FrontendBaseUrl"
Write-Host "Message : configured (length=$($Message.Length))"
if ($AppId -gt 0 -and $AdminUserId -gt 0) {
    Write-Host "AppId   : $AppId"
    Write-Host "Admin   : $AdminUserId"
}
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
        Write-FailBoundary "health" "curl exit code $($response.CurlExit); is backend running?"
    }
    elseif ($response.StatusCode -ne 200) {
        Write-FailBoundary "health" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
    }
    else {
        $json = $result | ConvertFrom-Json
        if ($json.code -eq 'OK' -and $json.data.status -eq 'UP' -and $json.data.service -eq 'sangui-rag-gateway') {
            Write-Pass "code=OK, status=UP, service=sangui-rag-gateway"
        }
        else {
            Write-FailBoundary "health" "Unexpected response: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-FailBoundary "health" "Exception: $_"
}

Write-Step "2. Frontend proxy health"
try {
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/api/health"
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-FailBoundary "proxy" "curl exit code $($response.CurlExit); is frontend running?"
    }
    elseif ($response.StatusCode -ne 200) {
        Write-FailBoundary "proxy" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
    }
    elseif ($result.TrimStart().StartsWith('<')) {
        Write-FailBoundary "proxy" "Response is HTML (SPA fallback), not JSON; proxy may be misrouted"
    }
    else {
        $json = $result | ConvertFrom-Json
        if ($json.code -eq 'OK') {
            Write-Pass "code=OK (JSON, not HTML)"
        }
        else {
            Write-FailBoundary "proxy" "Unexpected response: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
    catch {
    Write-FailBoundary "proxy" "Exception (likely not valid JSON): $_"
}

Write-Step "3. App readiness"
if ($AppId -le 0 -and $AdminUserId -le 0) {
    Write-Host "  SKIP: -AppId and -AdminUserId not supplied; readiness check skipped." -ForegroundColor Yellow
}
elseif ($AppId -le 0 -or $AdminUserId -le 0) {
    Write-FailBoundary "readiness" "Both -AppId and -AdminUserId are required for readiness check."
}
else {
    try {
        $readinessUrl = "$FrontendBaseUrl/api/admin/apps/$AppId/readiness"
        $readinessHeaders = @("X-Admin-User-Id: $AdminUserId")
        $readinessResp = Invoke-CurlCapture -Url $readinessUrl -Headers $readinessHeaders -MaxTimeSeconds 30

        if ($readinessResp.CurlExit -ne 0) {
            Write-FailBoundary "proxy" "curl exit code $($readinessResp.CurlExit) on readiness endpoint"
        }
        elseif ($readinessResp.StatusCode -ne 200) {
            $safePreview = $readinessResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            $readinessBoundary = "proxy"
            try {
                $readinessError = $readinessResp.Body | ConvertFrom-Json -ErrorAction Stop
                if ($readinessError.code -or $readinessError.message) {
                    $readinessBoundary = "readiness"
                }
            }
            catch {
                # Non-JSON responses through the frontend are treated as proxy failures.
            }
            Write-FailBoundary $readinessBoundary "HTTP $($readinessResp.StatusCode) on readiness. Body: $safePreview"
        }
        else {
            $readinessJson = $null
            try {
                $readinessJson = $readinessResp.Body | ConvertFrom-Json -ErrorAction Stop
            }
            catch {
                $safePreview = $readinessResp.Body
                if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
                Write-FailBoundary "proxy" "Readiness response is not valid JSON: $safePreview"
            }

            if ($readinessJson) {
                if ($readinessJson.code -ne 'OK') {
                    Write-FailBoundary "readiness" "Readiness envelope code=$($readinessJson.code), message=$($readinessJson.message)"
                }
                else {
                    $readinessForbidden = Test-ForbiddenFields -JsonObj $readinessJson.data
                    if ($readinessForbidden.Count -gt 0) {
                        Write-FailBoundary "readiness" "Forbidden field(s) found in readiness: $($readinessForbidden -join ', ')"
                    }
                    else {
                        $overallStatus = $readinessJson.data.overall_status
                        $checkCount = if ($readinessJson.data.checks) { $readinessJson.data.checks.Count } else { 0 }

                        Write-Host "  overall_status: $overallStatus" -ForegroundColor Gray
                        $requiredChecks = @('app', 'default_model_config', 'default_knowledge_base', 'knowledge_base_status', 'active_api_key', 'embedding_config')
                        $missingChecks = @()
                        foreach ($ck in $requiredChecks) {
                            $found = $false
                            foreach ($check in $readinessJson.data.checks) {
                                if ($check.key -eq $ck) { $found = $true; break }
                            }
                            if (-not $found) { $missingChecks += $ck }
                        }

                        if ($missingChecks.Count -gt 0) {
                            Write-FailBoundary "readiness" "Missing readiness check(s): $($missingChecks -join ', ')"
                        }
                        elseif ($overallStatus -ne 'READY') {
                            $readinessBoundary = Get-ReadinessBoundary -Checks $readinessJson.data.checks
                            Write-FailBoundary $readinessBoundary "overall_status=$overallStatus, expected READY"
                        }
                        else {
                            Write-Pass "overall_status=READY, $checkCount checks present (no forbidden fields)"
                        }
                    }
                }
            }
        }
    }
    catch {
        Write-FailBoundary "readiness" "Exception: $_"
    }
}

Write-Step "4. Non-streaming chat"
try {
    $body = Get-JsonBody -Content $Message
    $headers = @("Content-Type: application/json", "Authorization: Bearer $ApiKey")
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/v1/chat/completions" -Method "POST" -Headers $headers -Body $body -MaxTimeSeconds 60
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-FailBoundary "proxy" "curl exit code $($response.CurlExit)"
    }
    elseif ($response.StatusCode -ne 200) {
        try {
            $json = $result | ConvertFrom-Json
            if ($json.error) {
                $boundary = Classify-GatewayError -StatusCode $response.StatusCode -ErrorCode $json.error.code
                Write-FailBoundary $boundary "HTTP $($response.StatusCode), gateway error code=$($json.error.code), message=$($json.error.message)"
            }
            else {
                Write-FailBoundary "proxy" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
            }
        }
        catch {
            Write-FailBoundary "proxy" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
    else {
        try {
            $json = $result | ConvertFrom-Json
            if ($json.choices -and $json.choices[0].message.content) {
                $contentLength = $json.choices[0].message.content.Length
                Write-Pass "HTTP 200, choices[0].message.content present (length=$contentLength)"
            }
            else {
                Write-FailBoundary "proxy" "No content in choices[0].message.content"
            }
        }
        catch {
            Write-FailBoundary "proxy" "HTTP 200 but response is not valid JSON: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-FailBoundary "proxy" "Exception: $_"
}

Write-Step "5. Streaming chat"
try {
    $body = Get-JsonBody -Content $Message -Stream $true
    $headers = @("Content-Type: application/json", "Authorization: Bearer $ApiKey")
    $response = Invoke-CurlCapture -Url "$FrontendBaseUrl/v1/chat/completions" -Method "POST" -Headers $headers -Body $body -MaxTimeSeconds 60 -NoBuffer
    $result = $response.Body
    if ($response.CurlExit -ne 0) {
        Write-FailBoundary "proxy" "curl exit code $($response.CurlExit)"
    }
    elseif ($response.StatusCode -ne 200) {
        try {
            $err = $result | ConvertFrom-Json
            if ($err.error) {
                $boundary = Classify-GatewayError -StatusCode $response.StatusCode -ErrorCode $err.error.code
                Write-FailBoundary $boundary "HTTP $($response.StatusCode), stream returned gateway error code=$($err.error.code), message=$($err.error.message)"
            }
            else {
                Write-FailBoundary "proxy" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
            }
        }
        catch {
            Write-FailBoundary "proxy" "HTTP $($response.StatusCode), expected 200. Body: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
    else {
        try {
            $err = $result | ConvertFrom-Json -ErrorAction Stop
            if ($err.error) {
                $boundary = Classify-GatewayError -StatusCode $response.StatusCode -ErrorCode $err.error.code
                Write-FailBoundary $boundary "Stream returned JSON error instead of SSE: code=$($err.error.code), message=$($err.error.message)"
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
            Write-FailBoundary "upstream" "SSE chunks received but [DONE] missing; stream may have been truncated"
        }
        else {
            Write-FailBoundary "proxy" "No SSE data: chunks or [DONE] marker found. First 200 chars: $($result.Substring(0, [Math]::Min(200, $result.Length)))"
        }
    }
}
catch {
    Write-FailBoundary "proxy" "Exception: $_"
}

Write-Step "6. Request-log validation"
if ($AppId -le 0 -and $AdminUserId -le 0) {
    Write-Host "  SKIP: -AppId and -AdminUserId not supplied; request-log automation skipped." -ForegroundColor Yellow
}
elseif ($AppId -le 0 -or $AdminUserId -le 0) {
    Write-FailBoundary "request-log" "Both -AppId and -AdminUserId are required for request-log automation."
}
else {
    try {
        # 5a. Query request-log list
        $listUrl = "$FrontendBaseUrl/api/admin/apps/$AppId/request-logs?page=1&page_size=5&status=success"
        $listHeaders = @("X-Admin-User-Id: $AdminUserId")
        $listResp = Invoke-CurlCapture -Url $listUrl -Headers $listHeaders -MaxTimeSeconds 30

        if ($listResp.CurlExit -ne 0) {
            Write-FailBoundary "request-log" "curl exit code $($listResp.CurlExit) on request-log list"
            Stop-AfterFailure
        }
        if ($listResp.StatusCode -ne 200) {
            $safePreview = $listResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "HTTP $($listResp.StatusCode) on request-log list. Body: $safePreview"
            Stop-AfterFailure
        }

        $listJson = $null
        try {
            $listJson = $listResp.Body | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $safePreview = $listResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "Request-log list response is not valid JSON: $safePreview"
            Stop-AfterFailure
        }

        if ($listJson.code -ne 'OK') {
            Write-FailBoundary "request-log" "Request-log list envelope code=$($listJson.code)"
            Stop-AfterFailure
        }

        if (-not $listJson.data -or -not $listJson.data.items -or $listJson.data.items.Count -eq 0) {
            Write-FailBoundary "request-log" "No success request log found for app $AppId"
            Stop-AfterFailure
        }

        # 5b. Find the log matching this smoke run's Message
        $matchPrefix = if ($Message.Length -gt 30) { $Message.Substring(0, 30) } else { $Message }
        $matchedLog = $null
        foreach ($item in $listJson.data.items) {
            if ($item.question_summary -and $item.question_summary.StartsWith($matchPrefix)) {
                $matchedLog = $item
                break
            }
        }

        if (-not $matchedLog) {
            Write-FailBoundary "request-log" "No recent success log found with question_summary matching '$matchPrefix'"
            Stop-AfterFailure
        }

        $logId = $matchedLog.request_id
        if (-not $logId) {
            Write-FailBoundary "request-log" "Matched log has null/empty request_id"
            Stop-AfterFailure
        }

        # 5b2. Forbidden-field scan on list item
        $listForbidden = Test-ForbiddenFields -JsonObj $matchedLog
        if ($listForbidden.Count -gt 0) {
            Write-FailBoundary "request-log" "Forbidden field(s) found in list item: $($listForbidden -join ', ')"
            Stop-AfterFailure
        }

        # 5c. Validate required fields
        $fieldErrors = @()

        if ($matchedLog.status -ne 'success') {
            $fieldErrors += "status=$($matchedLog.status), expected success"
        }
        if (-not $matchedLog.model -or $matchedLog.model.ToString().Trim() -eq '') {
            $fieldErrors += "model is blank or null"
        }
        if (-not $matchedLog.provider_name -or $matchedLog.provider_name.ToString().Trim() -eq '') {
            $fieldErrors += "provider_name is blank or null"
        }
        if ($matchedLog.latency_ms -eq $null -or $matchedLog.latency_ms -isnot [long] -and $matchedLog.latency_ms -isnot [int]) {
            $fieldErrors += "latency_ms is null or non-numeric"
        }
        elseif ([long]$matchedLog.latency_ms -lt 0) {
            $fieldErrors += "latency_ms=$($matchedLog.latency_ms) is negative"
        }
        if (-not $matchedLog.question_summary -or $matchedLog.question_summary.ToString().Trim() -eq '') {
            $fieldErrors += "question_summary is blank or null"
        }
        if (-not $matchedLog.hit_chunk_ids -or $matchedLog.hit_chunk_ids.Count -eq 0) {
            $fieldErrors += "hit_chunk_ids is empty or null"
        }
        else {
            foreach ($hitChunkId in $matchedLog.hit_chunk_ids) {
                if ($hitChunkId -eq $null -or ($hitChunkId -isnot [long] -and $hitChunkId -isnot [int])) {
                    $fieldErrors += "hit_chunk_ids contains non-numeric value"
                    break
                }
            }
        }

        if ($fieldErrors.Count -gt 0) {
            foreach ($err in $fieldErrors) {
                Write-FailBoundary "request-log" $err
            }
            Stop-AfterFailure
        }

        # 5d. Print safe evidence
        Write-Pass "request_id=$logId"
        Write-Host "         model: $($matchedLog.model)" -ForegroundColor Gray
        Write-Host "         provider_name: $($matchedLog.provider_name)" -ForegroundColor Gray
        Write-Host "         latency_ms: $($matchedLog.latency_ms)" -ForegroundColor Gray
        Write-Host "         hit_chunk_ids: [$($matchedLog.hit_chunk_ids -join ', ')] (count=$($matchedLog.hit_chunk_ids.Count))" -ForegroundColor Gray

        # 5d2. Validate request-log detail
        $detailUrl = "$FrontendBaseUrl/api/admin/apps/$AppId/request-logs/$logId"
        $detailResp = Invoke-CurlCapture -Url $detailUrl -Headers $listHeaders -MaxTimeSeconds 30

        if ($detailResp.CurlExit -ne 0) {
            Write-FailBoundary "request-log" "curl exit code $($detailResp.CurlExit) on request-log detail"
            Stop-AfterFailure
        }
        if ($detailResp.StatusCode -ne 200) {
            $safePreview = $detailResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "HTTP $($detailResp.StatusCode) on request-log detail. Body: $safePreview"
            Stop-AfterFailure
        }

        $detailJson = $null
        try {
            $detailJson = $detailResp.Body | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $safePreview = $detailResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "Request-log detail response is not valid JSON: $safePreview"
            Stop-AfterFailure
        }

        if ($detailJson.code -ne 'OK') {
            Write-FailBoundary "request-log" "Request-log detail envelope code=$($detailJson.code)"
            Stop-AfterFailure
        }

        $detail = $detailJson.data
        if (-not $detail) {
            Write-FailBoundary "request-log" "Request-log detail data is null"
            Stop-AfterFailure
        }

        # 5d3. Forbidden-field scan on detail
        $detailForbidden = Test-ForbiddenFields -JsonObj $detail
        if ($detailForbidden.Count -gt 0) {
            Write-FailBoundary "request-log" "Forbidden field(s) found in detail: $($detailForbidden -join ', ')"
            Stop-AfterFailure
        }

        $detailErrors = @()
        if ($detail.request_id -ne $logId) {
            $detailErrors += "detail request_id=$($detail.request_id) does not match list row $logId"
        }
        $detailSafeFields = @('user_id', 'app_id', 'api_key_id', 'model', 'provider_name', 'status', 'latency_ms', 'messages_count', 'question_summary', 'hit_chunk_ids', 'created_at', 'updated_at')
        foreach ($fieldName in $detailSafeFields) {
            if (-not ($detail.PSObject.Properties.Name -contains $fieldName)) {
                $detailErrors += "detail is missing safe field: $fieldName"
            }
        }

        if ($detailErrors.Count -gt 0) {
            foreach ($err in $detailErrors) {
                Write-FailBoundary "request-log" $err
            }
            Stop-AfterFailure
        }

        Write-Pass "request-log detail: request_id=$logId, user_id=$($detail.user_id), messages_count=$($detail.messages_count)"

        # 5e. Query hit-chunk summaries (safe evidence only)
        $chunkUrl = "$FrontendBaseUrl/api/admin/apps/$AppId/request-logs/$logId/hit-chunks"
        $chunkResp = Invoke-CurlCapture -Url $chunkUrl -Headers $listHeaders -MaxTimeSeconds 30

        if ($chunkResp.CurlExit -ne 0) {
            Write-FailBoundary "request-log" "curl exit code $($chunkResp.CurlExit) on hit-chunks endpoint"
            Stop-AfterFailure
        }
        if ($chunkResp.StatusCode -ne 200) {
            $safePreview = $chunkResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "HTTP $($chunkResp.StatusCode) on hit-chunks endpoint. Body: $safePreview"
            Stop-AfterFailure
        }

        $chunkJson = $null
        try {
            $chunkJson = $chunkResp.Body | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $safePreview = $chunkResp.Body
            if ($safePreview.Length -gt 200) { $safePreview = $safePreview.Substring(0, 200) }
            Write-FailBoundary "request-log" "Hit-chunks response is not valid JSON: $safePreview"
            Stop-AfterFailure
        }

        if ($chunkJson.code -ne 'OK') {
            Write-FailBoundary "request-log" "Hit-chunks envelope code=$($chunkJson.code)"
            Stop-AfterFailure
        }

        if (-not $chunkJson.data -or $chunkJson.data.Count -eq 0) {
            Write-FailBoundary "request-log" "Hit-chunks returned empty list but hit_chunk_ids was non-empty"
            Stop-AfterFailure
        }

        $chunkSummaryCount = $chunkJson.data.Count
        $chunkErrors = @()
        foreach ($chunk in $chunkJson.data) {
            if ($chunk.chunk_id -eq $null -or ($chunk.chunk_id -isnot [long] -and $chunk.chunk_id -isnot [int])) {
                $chunkErrors += "hit-chunk summary has null/non-numeric chunk_id"
            }
            if ($chunk.document_id -eq $null -or ($chunk.document_id -isnot [long] -and $chunk.document_id -isnot [int])) {
                $chunkErrors += "hit-chunk summary has null/non-numeric document_id"
            }
            if ($chunk.knowledge_base_id -eq $null -or ($chunk.knowledge_base_id -isnot [long] -and $chunk.knowledge_base_id -isnot [int])) {
                $chunkErrors += "hit-chunk summary has null/non-numeric knowledge_base_id"
            }
            if ($chunk.chunk_index -eq $null -or ($chunk.chunk_index -isnot [long] -and $chunk.chunk_index -isnot [int])) {
                $chunkErrors += "hit-chunk summary has null/non-numeric chunk_index"
            }
            if (-not ($chunk.PSObject.Properties.Name -contains 'source_filename')) {
                $chunkErrors += "hit-chunk summary is missing source_filename"
            }
        }

        if ($chunkErrors.Count -gt 0) {
            foreach ($err in ($chunkErrors | Select-Object -Unique)) {
                Write-FailBoundary "request-log" $err
            }
            Stop-AfterFailure
        }

        # 5e2. Forbidden-field scan on hit-chunk items
        $chunkForbiddenErrors = @()
        foreach ($chunk in $chunkJson.data) {
            $chunkForbidden = Test-ForbiddenFields -JsonObj $chunk
            if ($chunkForbidden.Count -gt 0) {
                $chunkForbiddenErrors += "hit-chunk chunk_id=$($chunk.chunk_id) has forbidden field(s): $($chunkForbidden -join ', ')"
            }
        }
        if ($chunkForbiddenErrors.Count -gt 0) {
            foreach ($err in $chunkForbiddenErrors) {
                Write-FailBoundary "request-log" $err
            }
            Stop-AfterFailure
        }

        Write-Pass "hit-chunk summaries count=$chunkSummaryCount"
        foreach ($chunk in $chunkJson.data) {
            Write-Host "         chunk_id=$($chunk.chunk_id) document_id=$($chunk.document_id) kb_id=$($chunk.knowledge_base_id) file=$($chunk.source_filename) chunk_idx=$($chunk.chunk_index)" -ForegroundColor Gray
        }
    }
    catch {
        Write-FailBoundary "request-log" "Exception: $_"
    }
}

Write-Step "7. Revoked-key 401 verification"
if (-not $VerifyRevokedKey) {
    Write-Host "  SKIP: -VerifyRevokedKey not supplied; revoked-key automation skipped." -ForegroundColor Yellow
}
elseif ([string]::IsNullOrWhiteSpace($RevokedApiKey)) {
    Write-FailBoundary "auth" "-VerifyRevokedKey supplied but -RevokedApiKey is blank."
}
else {
    try {
        $revokedBody = Get-JsonBody -Content "test"
        $revokedHeaders = @("Content-Type: application/json", "Authorization: Bearer $RevokedApiKey")
        $revokedResp = Invoke-CurlCapture -Url "$FrontendBaseUrl/v1/chat/completions" -Method "POST" -Headers $revokedHeaders -Body $revokedBody -MaxTimeSeconds 30

        if ($revokedResp.CurlExit -ne 0) {
            Write-FailBoundary "auth" "curl exit code $($revokedResp.CurlExit) on revoked-key call"
        }
        elseif ($revokedResp.StatusCode -ne 401) {
            Write-FailBoundary "auth" "HTTP $($revokedResp.StatusCode), expected 401 for revoked key"
        }
        else {
            try {
                $revokedJson = $revokedResp.Body | ConvertFrom-Json -ErrorAction Stop
                if ($revokedJson.error -and $revokedJson.error.code -eq 'invalid_api_key') {
                    Write-Pass "HTTP 401, error.code=invalid_api_key"
                }
                else {
                    Write-FailBoundary "auth" "HTTP 401 but error.code is not invalid_api_key"
                }
            }
            catch {
                Write-FailBoundary "auth" "HTTP 401 but response is not valid JSON"
            }
        }
    }
    catch {
        Write-FailBoundary "auth" "Exception: $_"
    }
}

if ($script:exitCode -eq 0) {
    Write-Host "`nAll checks passed." -ForegroundColor Green
    exit 0
}
else {
    Write-Host "`nOne or more checks FAILED." -ForegroundColor Red
    exit $script:exitCode
}
