# 路线助手异步接口接入

契约依据：后端 `Viyangle/study_tour` 提交 `e22f9e5930da8756d0eff5b55e43abefde7807fd` 中的
`docs/openapi.json`、`docs/API.md`，以及 2026-10-10 的 `agent-api-frontend-notice.md`。
部署和鉴权以联调通知为准，继续使用现有 BASE_URL 和 access token；memoryId 按后端要求限制为 128 字符。

- 创建：`POST /routes/agent/runs`，直接解析 JSON，202 只表示接受。
- 查询：`GET /routes/agent/runs/{runId}`，约 2 秒一次，终态停止；事件快照替换显示。
- 取消：`DELETE /routes/agent/runs/{runId}`，仍按实际响应状态处理，可能返回 running 或 completed。
- 对话只保存真实用户需求和助手方案/追问，下一轮携带 history、constraints 和实际当前路线。
- 新一轮使用新 requestId；创建响应丢失时保留原 requestId 和原请求体重试。
- 任务按账号保存到本地，离开页面停止轮询，回来后恢复原任务。
- 候选方案经用户确认才应用；应用前后核对路线快照，逐个同步 POI 后才替换本地节点。
- 相对行程时间由用户选择首日日期，再转换 ISO；发布沿用 `/routes/manual` 与原发布接口。
- 费用估算、未核价项、假设与警示一并展示。地图交通段分开绘制，不跨天拼接。

## 线上联调记录（2026-10-20）

使用模拟器当前登录账号创建一轮测试任务，得到 HTTP 202；查询得到 HTTP 200、status=failed。
服务端错误为调用 `http://localhost:11434/v1/embeddings` 时 Connection refused。

这说明 Agent 路由和认证已部署，但候选地点检索依赖的向量嵌入服务连接失败。
需后端检查嵌入服务进程、端口、容器网络及 embedding 基地址配置；若使用容器，localhost 指向当前容器。
客户端对该故障显示“路线检索服务暂不可用，请联系管理员检查向量嵌入服务连接”。

## 本地验证

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

本机 Java Unix-domain socket 临时目录异常时，可仅在构建进程设置
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<项目目录>/.gradle/tmp`，无需修改项目构建配置。
