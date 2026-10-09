$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'leader-workflow-test-account.ps1'
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "主脚本不存在：$scriptPath"
}

. $scriptPath -RouteId 42 -RunId '10091234' -LibraryOnly

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message。期望：$Expected；实际：$Actual"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-False {
    param([bool]$Condition, [string]$Message)
    if ($Condition) {
        throw $Message
    }
}

$identity = New-WorkflowIdentity -RunId '10091234'
Assert-Equal '18810091234' $identity.LeaderPhone '领队手机号应由 RunId 稳定生成'
Assert-Equal '18910091234' $identity.PublisherPhone '发布者手机号应与领队手机号不同'

$script:PreviewApiCalls = 0
$previewInvoker = {
    param($Request)
    $script:PreviewApiCalls++
    throw '预览模式不应访问网络'
}
$preview = New-WorkflowPreview -BaseUrl 'http://example.test' -RouteId 42 `
    -RunId '10091234' -Password '123456' -ApiInvoker $previewInvoker
Assert-Equal 'Preview' $preview.Mode '默认模式应为预览'
Assert-False $preview.WillWrite '预览模式不得写入后端'
Assert-Equal 0 $script:PreviewApiCalls '预览模式不得调用 API'
Assert-Equal 4 $preview.PlannedFixtures.Count '应规划四类项目夹具'

$script:Requests = [System.Collections.Generic.List[object]]::new()
$script:NextProjectId = 100
$script:FailCompletedTransition = $false
$fakeInvoker = {
    param($Request)
    $script:Requests.Add($Request)

    if ($Request.Method -eq 'POST' -and $Request.Path -eq '/register') {
        return [pscustomobject]@{ code = 1; msg = 'success'; data = $null }
    }
    if ($Request.Method -eq 'POST' -and $Request.Path -eq '/login') {
        $isLeader = [string]$Request.Body.phone -like '188*'
        return [pscustomobject]@{
            code = 1
            msg = 'success'
            data = [pscustomobject]@{
                account = [pscustomobject]@{
                    id = $(if ($isLeader) { 501 } else { 502 })
                    role = $(if ($isLeader) { 'BOTH' } else { 'USER' })
                }
                token = $(if ($isLeader) { 'leader-secret-token' } else { 'publisher-secret-token' })
                refreshToken = 'refresh-secret-token'
            }
        }
    }
    if ($Request.Method -eq 'GET' -and $Request.Path -eq '/routes/42') {
        return [pscustomobject]@{ code = 1; msg = 'success'; data = [pscustomobject]@{ id = 42 } }
    }
    if ($Request.Method -eq 'POST' -and $Request.Path -eq '/projects') {
        $script:NextProjectId++
        return [pscustomobject]@{ code = 1; msg = 'success'; data = $script:NextProjectId }
    }
    if ($Request.Method -eq 'POST' -and $Request.Path -match '^/projects/\d+/accept$') {
        return [pscustomobject]@{ code = 1; msg = 'success'; data = $null }
    }
    if ($Request.Method -eq 'POST' -and $Request.Path -match '^/projects/\d+/status\?status=') {
        if ($script:FailCompletedTransition) {
            return [pscustomobject]@{ code = 0; msg = '当前时间不允许推进状态'; data = $null }
        }
        return [pscustomobject]@{ code = 1; msg = 'success'; data = $null }
    }
    if ($Request.Method -eq 'POST' -and $Request.Path -eq '/reviews') {
        return [pscustomobject]@{ code = 1; msg = 'success'; data = 9001 }
    }
    if ($Request.Method -eq 'GET' -and $Request.Path -eq '/chat/sessions') {
        return [pscustomobject]@{
            code = 1
            msg = 'success'
            data = @(
                [pscustomobject]@{ projectId = 102 },
                [pscustomobject]@{ projectId = 103 },
                [pscustomobject]@{ projectId = 104 }
            )
        }
    }
    throw "未处理的假请求：$($Request.Method) $($Request.Path)"
}

$result = Invoke-LeaderWorkflowSeed -BaseUrl 'http://example.test' -RouteId 42 `
    -RunId '10091234' -Password '123456' -ApiInvoker $fakeInvoker

Assert-Equal 'Applied' $result.Mode '执行结果应标记为 Applied'
Assert-Equal 501 $result.Leader.AccountId '应返回领队账号 ID'
Assert-Equal 502 $result.Publisher.AccountId '应返回发布者账号 ID'
Assert-Equal 101 $result.Projects.Available.Id '应创建待接单项目'
Assert-Equal 102 $result.Projects.Accepted.Id '应创建稳定已接单项目'
Assert-Equal 103 $result.Projects.AbandonCandidate.Id '应创建放弃候选项目'
Assert-Equal 104 $result.Projects.Completed.Id '应创建已完成项目'
Assert-True $result.CompletedFixtureReady '状态推进成功时应生成完成夹具'
Assert-True $result.ReviewCreated '完成夹具成功时应创建评价'

$projectCreates = @($script:Requests | Where-Object { $_.Method -eq 'POST' -and $_.Path -eq '/projects' })
$accepts = @($script:Requests | Where-Object { $_.Path -match '/accept$' })
$statusUpdates = @($script:Requests | Where-Object { $_.Path -match '/status\?status=' })
Assert-Equal 4 $projectCreates.Count '应创建四个项目'
Assert-Equal 3 $accepts.Count '应接取三个项目'
Assert-Equal 2 $statusUpdates.Count '完成夹具应按两步推进状态'

$json = $result | ConvertTo-Json -Depth 10
Assert-False ($json -match '(?i)token') '结果对象不得包含 Token 字段或值'

$script:Requests.Clear()
$script:NextProjectId = 200
$script:FailCompletedTransition = $true
$fallback = Invoke-LeaderWorkflowSeed -BaseUrl 'http://example.test' -RouteId 42 `
    -RunId '10091235' -Password '123456' -ApiInvoker $fakeInvoker
Assert-False $fallback.CompletedFixtureReady '后端拒绝状态推进时不得伪造完成夹具'
Assert-False $fallback.ReviewCreated '没有完成夹具时不得创建评价'
Assert-True (-not [string]::IsNullOrWhiteSpace($fallback.CompletedFixtureMessage)) `
    '完成夹具失败时应保留后端原因'

$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    $scriptPath,
    [ref]$null,
    [ref]$parseErrors
) | Out-Null
Assert-Equal 0 $parseErrors.Count '主脚本必须通过 PowerShell AST 语法检查'

Write-Output 'PASS: leader workflow test account script tests'
