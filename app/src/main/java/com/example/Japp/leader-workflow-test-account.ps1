[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://47.94.95.110:8080',
    [Parameter(Mandatory)]
    [int]$RouteId,
    [string]$RunId = (Get-Date -Format 'MMddHHmm'),
    [string]$Password = '123456',
    [string]$LeaderPhone,
    [string]$PublisherPhone,
    [string]$RegionCode = '320100',
    [switch]$Apply,
    [string]$OutputPath,
    [scriptblock]$ApiInvoker,
    [switch]$LibraryOnly
)

function New-WorkflowIdentity {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$RunId,
        [string]$LeaderPhone,
        [string]$PublisherPhone
    )

    $digits = $RunId -replace '\D', ''
    if ($digits.Length -lt 8) {
        $digits = $digits.PadLeft(8, '0')
    } elseif ($digits.Length -gt 8) {
        $digits = $digits.Substring($digits.Length - 8)
    }

    if ([string]::IsNullOrWhiteSpace($LeaderPhone)) {
        $LeaderPhone = "188$digits"
    }
    if ([string]::IsNullOrWhiteSpace($PublisherPhone)) {
        $PublisherPhone = "189$digits"
    }

    foreach ($entry in @(
        [pscustomobject]@{ Name = 'LeaderPhone'; Value = $LeaderPhone },
        [pscustomobject]@{ Name = 'PublisherPhone'; Value = $PublisherPhone }
    )) {
        if ($entry.Value -notmatch '^1\d{10}$') {
            throw "$($entry.Name) 必须是 11 位手机号格式"
        }
    }
    if ($LeaderPhone -eq $PublisherPhone) {
        throw '领队手机号和发布者手机号不能相同'
    }

    [pscustomobject]@{
        Suffix = $digits
        LeaderPhone = $LeaderPhone
        PublisherPhone = $PublisherPhone
        LeaderName = "流程领队_$digits"
        PublisherName = "流程发布者_$digits"
    }
}

function New-WorkflowPreview {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [int]$RouteId,
        [Parameter(Mandatory)] [string]$RunId,
        [Parameter(Mandatory)] [string]$Password,
        [string]$LeaderPhone,
        [string]$PublisherPhone,
        [scriptblock]$ApiInvoker
    )

    if ($RouteId -le 0) {
        throw 'RouteId 必须大于 0'
    }
    if ([string]::IsNullOrWhiteSpace($Password) -or $Password.Length -lt 6) {
        throw '测试账号密码至少需要 6 位'
    }
    $identity = New-WorkflowIdentity -RunId $RunId `
        -LeaderPhone $LeaderPhone -PublisherPhone $PublisherPhone

    [pscustomobject]@{
        Mode = 'Preview'
        WillWrite = $false
        BaseUrl = $BaseUrl.TrimEnd('/')
        RouteId = $RouteId
        Leader = [pscustomobject]@{
            Username = $identity.LeaderName
            Phone = $identity.LeaderPhone
            Role = 'BOTH'
        }
        Publisher = [pscustomobject]@{
            Username = $identity.PublisherName
            Phone = $identity.PublisherPhone
            Role = 'USER'
        }
        PlannedFixtures = @(
            'AVAILABLE：保持 OPEN，验证待接单与接单入口',
            'ACCEPTED：由领队接单，验证已接单、详情与群聊',
            'ABANDON_CANDIDATE：由领队接单，供手工验证放弃规则',
            'COMPLETED：尝试推进到 DONE，验证历史、评分与评价'
        )
        Note = '这是预览模式，未发送任何写请求。传入 -Apply 后才会创建测试数据。'
    }
}

function Invoke-WorkflowApi {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [ValidateSet('GET', 'POST', 'PUT', 'DELETE')] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        $Body,
        [string]$Token,
        [scriptblock]$ApiInvoker
    )

    $request = [pscustomobject]@{
        Method = $Method
        Path = $Path
        Body = $Body
        Token = $Token
    }
    if ($null -ne $ApiInvoker) {
        return & $ApiInvoker $request
    }

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    $invokeArgs = @{
        Method = $Method
        Uri = $BaseUrl.TrimEnd('/') + $Path
        Headers = $headers
        TimeoutSec = 30
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $invokeArgs.ContentType = 'application/json'
        $invokeArgs.Body = $Body | ConvertTo-Json -Depth 10
    }

    try {
        Invoke-RestMethod @invokeArgs
    } catch {
        $status = $null
        if ($null -ne $_.Exception.Response) {
            try { $status = [int]$_.Exception.Response.StatusCode } catch {}
        }
        $serverMessage = $null
        if (-not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            try {
                $serverMessage = ($_.ErrorDetails.Message | ConvertFrom-Json).msg
            } catch {
                $serverMessage = $_.ErrorDetails.Message
            }
        }
        if ([string]::IsNullOrWhiteSpace($serverMessage)) {
            $serverMessage = $_.Exception.Message
        }
        throw "API 请求失败：$Method $Path；HTTP=$status；消息=$serverMessage"
    }
}

function Assert-WorkflowSuccess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Response,
        [Parameter(Mandatory)] [string]$Operation
    )
    if ($null -eq $Response -or [int]$Response.code -ne 1) {
        $message = if ($null -ne $Response -and -not [string]::IsNullOrWhiteSpace($Response.msg)) {
            [string]$Response.msg
        } else {
            '后端未返回成功结果'
        }
        throw "$Operation 失败：$message"
    }
    $Response.data
}

function Register-OrLoginWorkflowAccount {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [string]$Username,
        [Parameter(Mandatory)] [string]$Phone,
        [Parameter(Mandatory)] [string]$Password,
        [Parameter(Mandatory)] [ValidateSet('User', 'Both')] [string]$Role,
        [Parameter(Mandatory)] [string]$RegionCode,
        [scriptblock]$ApiInvoker
    )

    $registerError = $null
    try {
        $registration = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method POST -Path '/register' `
            -Body @{
                username = $Username
                phone = $Phone
                password = $Password
                confirmPassword = $Password
                role = $Role
                regionCode = $RegionCode
            } -ApiInvoker $ApiInvoker
        if ($null -ne $registration -and [int]$registration.code -ne 1) {
            $registerError = [string]$registration.msg
        }
    } catch {
        $registerError = $_.Exception.Message
    }

    $login = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method POST -Path '/login' `
        -Body @{ phone = $Phone; password = $Password } -ApiInvoker $ApiInvoker
    $loginData = Assert-WorkflowSuccess -Response $login -Operation "登录测试账号 $Phone"
    if ($null -eq $loginData.account -or [string]::IsNullOrWhiteSpace($loginData.token)) {
        throw "登录测试账号 $Phone 失败：响应缺少账号或 Token"
    }

    $expectedRole = if ($Role -eq 'Both') { 'BOTH' } else { 'USER' }
    if ([string]$loginData.account.role -ne $expectedRole) {
        throw "账号 $Phone 的角色为 $($loginData.account.role)，期望 $expectedRole"
    }

    [pscustomobject]@{
        AccountId = [int]$loginData.account.id
        Phone = $Phone
        Username = $Username
        Role = $expectedRole
        Token = [string]$loginData.token
        RegistrationMessage = $registerError
    }
}

function New-WorkflowProject {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [string]$PublisherToken,
        [Parameter(Mandatory)] [int]$RouteId,
        [Parameter(Mandatory)] [string]$Title,
        [Parameter(Mandatory)] [datetime]$StartAt,
        [scriptblock]$ApiInvoker
    )

    $response = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method POST -Path '/projects' `
        -Token $PublisherToken -ApiInvoker $ApiInvoker -Body @{
            routeId = $RouteId
            title = $Title
            departureDate = $StartAt.ToString('yyyy-MM-dd')
            departureTime = $StartAt.ToString('HH:mm:ss')
            startPointType = 'MANUAL'
            startPoint = '全流程测试起点'
            leaderRequirements = '全流程测试领队账号接单'
            participantRequirements = '自动化测试数据，请勿用于真实行程'
            representedCount = 1
            maxMembers = 20
            status = 'OPEN'
        }
    [int](Assert-WorkflowSuccess -Response $response -Operation "创建项目 $Title")
}

function Invoke-WorkflowProjectAction {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$Token,
        [Parameter(Mandatory)] [string]$Operation,
        [scriptblock]$ApiInvoker
    )
    $response = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method POST -Path $Path `
        -Token $Token -ApiInvoker $ApiInvoker
    $null = Assert-WorkflowSuccess -Response $response -Operation $Operation
}

function Invoke-LeaderWorkflowSeed {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$BaseUrl,
        [Parameter(Mandatory)] [int]$RouteId,
        [Parameter(Mandatory)] [string]$RunId,
        [Parameter(Mandatory)] [string]$Password,
        [string]$LeaderPhone,
        [string]$PublisherPhone,
        [string]$RegionCode = '320100',
        [scriptblock]$ApiInvoker
    )

    $null = New-WorkflowPreview -BaseUrl $BaseUrl -RouteId $RouteId -RunId $RunId `
        -Password $Password -LeaderPhone $LeaderPhone -PublisherPhone $PublisherPhone
    $identity = New-WorkflowIdentity -RunId $RunId `
        -LeaderPhone $LeaderPhone -PublisherPhone $PublisherPhone

    $leaderInternal = Register-OrLoginWorkflowAccount -BaseUrl $BaseUrl `
        -Username $identity.LeaderName -Phone $identity.LeaderPhone -Password $Password `
        -Role Both -RegionCode $RegionCode -ApiInvoker $ApiInvoker
    $publisherInternal = Register-OrLoginWorkflowAccount -BaseUrl $BaseUrl `
        -Username $identity.PublisherName -Phone $identity.PublisherPhone -Password $Password `
        -Role User -RegionCode $RegionCode -ApiInvoker $ApiInvoker

    $route = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method GET -Path "/routes/$RouteId" `
        -Token $publisherInternal.Token -ApiInvoker $ApiInvoker
    $null = Assert-WorkflowSuccess -Response $route -Operation "验证路线 $RouteId"

    $baseStart = [DateTime]::Now.Date.AddDays(7).AddHours(9)
    $availableId = New-WorkflowProject -BaseUrl $BaseUrl `
        -PublisherToken $publisherInternal.Token -RouteId $RouteId `
        -Title "[测试]待接单_$($identity.Suffix)" -StartAt $baseStart -ApiInvoker $ApiInvoker
    $acceptedId = New-WorkflowProject -BaseUrl $BaseUrl `
        -PublisherToken $publisherInternal.Token -RouteId $RouteId `
        -Title "[测试]已接单_$($identity.Suffix)" -StartAt $baseStart.AddHours(1) -ApiInvoker $ApiInvoker
    $abandonId = New-WorkflowProject -BaseUrl $BaseUrl `
        -PublisherToken $publisherInternal.Token -RouteId $RouteId `
        -Title "[测试]放弃候选_$($identity.Suffix)" -StartAt $baseStart.AddHours(2) -ApiInvoker $ApiInvoker
    $completedId = New-WorkflowProject -BaseUrl $BaseUrl `
        -PublisherToken $publisherInternal.Token -RouteId $RouteId `
        -Title "[测试]已完成_$($identity.Suffix)" -StartAt $baseStart.AddHours(3) -ApiInvoker $ApiInvoker

    foreach ($projectId in @($acceptedId, $abandonId, $completedId)) {
        Invoke-WorkflowProjectAction -BaseUrl $BaseUrl -Path "/projects/$projectId/accept" `
            -Token $leaderInternal.Token -Operation "领队接单 $projectId" -ApiInvoker $ApiInvoker
    }

    $completedReady = $false
    $completedMessage = $null
    try {
        Invoke-WorkflowProjectAction -BaseUrl $BaseUrl `
            -Path "/projects/$completedId/status?status=IN_PROGRESS" `
            -Token $leaderInternal.Token -Operation "项目 $completedId 进入进行中" -ApiInvoker $ApiInvoker
        Invoke-WorkflowProjectAction -BaseUrl $BaseUrl `
            -Path "/projects/$completedId/status?status=DONE" `
            -Token $leaderInternal.Token -Operation "项目 $completedId 完成" -ApiInvoker $ApiInvoker
        $completedReady = $true
    } catch {
        $completedMessage = $_.Exception.Message
    }

    $reviewCreated = $false
    $reviewId = $null
    if ($completedReady) {
        try {
            $review = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method POST -Path '/reviews' `
                -Token $publisherInternal.Token -ApiInvoker $ApiInvoker -Body @{
                    projectId = $completedId
                    routeId = $RouteId
                    fromAccountId = $publisherInternal.AccountId
                    toAccountId = $leaderInternal.AccountId
                    reviewType = 'USER_TO_LEADER'
                    overallScore = 5
                    content = '全流程测试评价：讲解清晰，组织有序。'
                }
            $reviewId = Assert-WorkflowSuccess -Response $review -Operation '创建领队测试评价'
            $reviewCreated = $true
        } catch {
            $completedMessage = if ([string]::IsNullOrWhiteSpace($completedMessage)) {
                "完成项目已生成，但评价创建失败：$($_.Exception.Message)"
            } else {
                "$completedMessage；评价创建失败：$($_.Exception.Message)"
            }
        }
    }

    $chatProjectIds = @()
    try {
        $chats = Invoke-WorkflowApi -BaseUrl $BaseUrl -Method GET -Path '/chat/sessions' `
            -Token $leaderInternal.Token -ApiInvoker $ApiInvoker
        $chatData = Assert-WorkflowSuccess -Response $chats -Operation '查询领队群聊'
        $chatProjectIds = @($chatData | ForEach-Object { [int]$_.projectId })
    } catch {
        if ([string]::IsNullOrWhiteSpace($completedMessage)) {
            $completedMessage = "群聊确认失败：$($_.Exception.Message)"
        }
    }

    [pscustomobject]@{
        Mode = 'Applied'
        BaseUrl = $BaseUrl.TrimEnd('/')
        RouteId = $RouteId
        Leader = [pscustomobject]@{
            AccountId = $leaderInternal.AccountId
            Username = $leaderInternal.Username
            Phone = $leaderInternal.Phone
            Password = $Password
            Role = $leaderInternal.Role
        }
        Publisher = [pscustomobject]@{
            AccountId = $publisherInternal.AccountId
            Username = $publisherInternal.Username
            Phone = $publisherInternal.Phone
            Password = $Password
            Role = $publisherInternal.Role
        }
        Projects = [pscustomobject]@{
            Available = [pscustomobject]@{ Id = $availableId; ExpectedStage = '待接单'; ExpectedStatus = 'OPEN' }
            Accepted = [pscustomobject]@{ Id = $acceptedId; ExpectedStage = '已接单'; ExpectedStatus = 'CONFIRMED' }
            AbandonCandidate = [pscustomobject]@{ Id = $abandonId; ExpectedStage = '已接单'; ExpectedStatus = 'CONFIRMED' }
            Completed = [pscustomobject]@{
                Id = $completedId
                ExpectedStage = $(if ($completedReady) { '已完成' } else { '已接单' })
                ExpectedStatus = $(if ($completedReady) { 'DONE' } else { 'CONFIRMED' })
            }
        }
        CompletedFixtureReady = $completedReady
        CompletedFixtureMessage = $completedMessage
        ReviewCreated = $reviewCreated
        ReviewId = $reviewId
        ChatProjectIds = $chatProjectIds
        ManualSteps = @(
            "使用领队账号登录并检查待接单项目 $availableId",
            "检查稳定已接单项目 $acceptedId 的详情与群聊",
            "在放弃候选项目 $abandonId 输入 5-200 字原因，验证放弃后恢复为待接单",
            $(if ($completedReady) {
                "检查已完成项目 $completedId、历史路线和 5 分评价"
            } else {
                "后端拒绝自动完成项目 $completedId；需由后端测试数据工具将其合法推进到 DONE"
            })
        )
    }
}

if (-not $LibraryOnly) {
    if (-not $Apply) {
        New-WorkflowPreview -BaseUrl $BaseUrl -RouteId $RouteId -RunId $RunId `
            -Password $Password -LeaderPhone $LeaderPhone -PublisherPhone $PublisherPhone `
            -ApiInvoker $ApiInvoker | ConvertTo-Json -Depth 10
    } else {
        $result = Invoke-LeaderWorkflowSeed -BaseUrl $BaseUrl -RouteId $RouteId `
            -RunId $RunId -Password $Password -LeaderPhone $LeaderPhone `
            -PublisherPhone $PublisherPhone -RegionCode $RegionCode -ApiInvoker $ApiInvoker
        if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
            $parent = Split-Path -Parent $OutputPath
            if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path -LiteralPath $parent)) {
                throw "输出目录不存在：$parent"
            }
            $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding utf8
        }
        $result | ConvertTo-Json -Depth 10
    }
}
