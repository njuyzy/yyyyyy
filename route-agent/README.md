# 研学路线 Agent Python 原型（非当前线上后端）

2026-10-10：正式 Agent 已集成到原业务后端，Android 共用原 `ApiClient` 和登录 token。
当前前端接入以 [更新后的接口说明](./docs/API.md) 为准。
以下 Python 能力、环境变量、工具集合和启动步骤仅描述保留的早期原型；
不代表正式后端支持天气/周边/排序工具，也不要求线上部署该 Python 服务。

真正的工具调用循环：模型提出工具调用 → 参数校验 → 执行真实地图/天气/交通查询 →
将结果回传模型 → 调整方案 → 行程硬约束校验 → 返回建议或澄清问题。
Android 不持有模型 Key，不执行模型指定的任意 URL，也不会自动发布或预约。

## 能力与边界

| 工具 | 实际行为 |
| --- | --- |
| `set_constraints` | 城市、日期、天数、每日时间、最晚结束时间、每人预算、交通、步行上限、必去/避开、群体、无障碍、节奏 |
| `search_places` / `get_place_details` | 高德真实 POI 搜索，使用 ID 重新核验坐标/地址/详情 |
| `search_nearby` | 周边餐饮、休息点、厕所等配套检索 |
| `use_current_location` | 只使用客户端经定位权限取得的坐标，并反查城市 |
| `get_weather` | 实际短期天气预报，超出预报日期标注未知 |
| `calculate_leg` | 步行、公共交通、驾车的道路时间、步行量及折线 |
| `optimize_order` | 固定首尾的地理启发式优化，随后必须道路核验，不宣称全局最优 |
| `build_itinerary` | 多日行程；含出发/返程、交通、每段 5 分钟缓冲、停留、休息及开放等待；校验必去、避开、交通偏好、每日时长、结束时间和步行上限 |
| `finish_plan` / `ask_user` | 交付已校验建议，或澄清决定性缺失/冲突；不可直接修改当前路线 |

显式需求优先于历史；常见中文数值时长/预算/步行约束还会用规则提取并锁定。
当前路线与最近 24 条有效对话随请求发送，需求可连续修改。方案在 Android 草稿中保存，
应用前核对路线快照，防止覆盖规划期间的手动调整。删点列表在确认窗口明确显示。

预算只能核验估算中的已统计费用，未知费用不会当作免费；高德人均消费不是门票报价。
开放时段只对查询当天的明确时间窗口做自动校验，周规则、假期、预约、无障碍设施和场馆内部
步行均提示向运营方确认。未指定出发/住宿地点时从第一站计时并提示未计入往返/转场交通。
日期未知或超出天气预报范围时不会编造预测。

## 启动（Python 3.11+）

从 `route-agent` 目录运行：

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.lock.txt
# 将 .env.example 复制为 .env，并在本机编辑真实配置；.env 被 Git 忽略。
.venv/Scripts/python.exe -m uvicorn route_agent.app:app --host 127.0.0.1 --port 8787 --env-file .env
```

Linux 对应使用 `.venv/bin/python`。环境文件由 uvicorn 的 `--env-file` 读取。
模型、地图和认证配置缺失时启动会明确失败，不会使用演示地点冒充真实结果。

必需的环境变量：

- `AGENT_LLM_API_KEY`、`AGENT_LLM_MODEL`：服务端模型密钥及明确的模型 ID。
- `AGENT_LLM_BASE_URL`：模型 API 基地址，默认 `https://api.openai.com/v1`。
- `AGENT_LLM_PROTOCOL`：默认 `responses`；兼容服务使用 `chat_completions`。
  选择支持该协议的工具模型；当前 GPT-6 系列工具调用使用 Responses。
- `AMAP_WEB_API_KEY`：高德 **Web 服务** Key，与 Android SDK 的 `AMAP_API_KEY` 分开。
- `AGENT_AUTH_URL`：现有后端的可信身份验证 GET 接口。Agent 转发用户的 Authorization，
  要求返回 `{"accountId":123}` 或 `{"code":1,"data":{"accountId":123}}`。
  该接口必须根据 token 返回本人身份；不可使用按 URL 中账号查询的公开资料接口。
  原 Android 仓库没有该后端源码，需要在原后端接入此接口或网关提供同等身份验证。

本地无后端认证的测试可设置随机 `AGENT_DEV_TOKEN`，身份固定为账号 1，只允许直接回环连接；
仅用于本机测试，**不得在反向代理后的部署服务启用**。客户端仍使用已有登录 token；
Android 真机联调应使用正式身份验证，不把调试 token 或供应商密钥写入 APK。

## 当前 Android 接入

当前客户端直接调用原后端 `/routes/agent/*`，不需要转发到本原型。
已移除 `ROUTE_AGENT_BASE_URL` 独立配置，生产环境建议为原后端使用 HTTPS。
原来的 `/routes/ai/{memoryId}` 单次生成接口不再用于路线助手；未部署新服务会显示接入错误，
不会静默调用旧接口冒充 Agent。用户手动选点、保存和发布继续使用原接口。
确认应用前等待所有 POI 同步成功，失败保留原路线；发布仍由用户触发，
先复核 POI 同步，并将 visitTime 转为 ISO 后通过原 `/routes/manual`、发布接口保存。

客户端显示服务端真实工具阶段；发送键在规划期间变成取消。独立绘制各交通段，
步行为蓝色、公交为紫色、驾车为绿色，不把跨日地点直线相连。
未应用的方案在对话里显示“待应用”，不能直接发布。

## HTTP 契约

字段、状态、错误码与联调示例见 [完整接口文档](./docs/API.md)，
机器可读契约见 [OpenAPI JSON](./docs/openapi.json)。

三类请求都带现有用户 `Authorization: Bearer ...`，返回普通 JSON：

```text
POST   /routes/agent/runs          -> 202 {runId,status,stage,events,result,error}
GET    /routes/agent/runs/{runId}  -> 当前状态/工具事件/终态方案
DELETE /routes/agent/runs/{runId}  -> 请求取消，可能仍返回 running；继续查询至终态
```

请求示例（认证账号须与 accountId 一致）：

```json
{
  "requestId": "unique-per-turn",
  "memoryId": "route-conversation",
  "accountId": 1,
  "message": "南京，明天亲子研学，控制在4小时，预算每人200元，尽量少走路，必须去南京博物院",
  "history": [],
  "currentRoute": [],
  "constraints": {},
  "origin": {"longitude": 118.79, "latitude": 32.06}
}
```

终态 `result.kind=plan` 携带 `nodes`（兼容现有 RouteNode）、`days`、实际 `legs`、
`constraints`、警示、假设和地点增删列表；`kind=clarification` 携带最多三个问题，节点为空。
`requestId` 按账号去重；同号不同内容拒绝。一个账号的同一会话只允许一轮执行，
进度和取消接口按验证身份隔离，不相信请求中的 accountId。

## 原型运行保障与部署范围

查询超时、有限重试、读缓存、模型轮次/工具数/地图请求上限、每账号限流和并发上限均已实现。
工具名称白名单与 Pydantic 参数校验阻止任意工具执行。错误和日志不返回上游 URL/密钥。
默认上限为 240 秒、14 模型轮次、48 工具调用、90 地图查询、8 个并行规划。
HTTP 请求有短超时；Android 按 1.8 秒读进度，最多 3 次连续读失败后退出。

当前任务状态为**单进程内存存储**，任务从创建时刻算 30 分钟后可过期（活跃任务除外），上限 500 条。请用单个 uvicorn worker，
不要以多 worker 的随机负载分发运行。重启服务会中断活跃任务，客户端草稿仍保留。
对话上下文由客户端带入，不承诺跨设备共享记忆。大规模部署需把任务/会话锁/限流迁移到
共享持久队列并接入监控；本次未部署到原服务器，也未验证真实模型和高德账号权限。

反向代理建议 TLS、请求体上限 512 KB、禁用记录 Authorization/请求体、约 30 秒 HTTP 读超时，
只放行三种 Agent 路径。模型调用发生在任务中，创建请求即返回，不需要保持 4 分钟长连接。
服务器会收到用户当前路线和对话；按产品的隐私策略告知并限定日志保留。

## 原型验证

```powershell
# mock 网络与模型，不消耗真实服务额度
.venv/Scripts/python.exe -m unittest discover -s tests -v
# 项目根目录
.\gradlew.bat testDebugUnitTest assembleDebug
```

覆盖真实工具反馈循环、错误/未知工具恢复、约束冲突和修复、开放等待、预算未知、
步行上限、首尾/返程、跨日折线、模型协议、配额、认证隔离、幂等、取消和会话并发。
上线前应以实际供应商进行场景评测：同名异地景点、雨天亲子、严格预算、闭馆日、
轮椅设施、删除/替换地点、临时网络断开、重复提交、取消后重试。

接口依据：[OpenAI 工具调用](https://developers.openai.com/api/docs/guides/function-calling)、
[高德 POI 2.0](https://lbs.amap.com/api/webservice/guide/api-advanced/newpoisearch)、
[高德路径规划](https://lbs.amap.com/api/webservice/guide/api/direction)、
[高德天气](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)。
