# 后端可能需要添加、删除与修改的内容

更新时间：2026-10-09

本文根据当前 Android 客户端实现、`README(2).md` 接口说明以及线上后端只读验证结果整理。

## 一、后端变更表

| 优先级 | 类型 | 模块 | 当前情况 | 建议变更 | 验收标准 |
|---|---|---|---|---|---|
| P0 | 修改 | `POST /projects/{id}/abandon` | README 只要求“出发前”；线上请求未体现 `reason` 必填校验 | 改为距离开始时间至少 6 小时才能放弃，恰好 6 小时允许 | 提前 5 小时 59 分拒绝；提前 6 小时整允许；使用服务器时间判断 |
| P0 | 修改 | 放弃原因 | 客户端会发送 `reason`，但后端没有必填和长度校验的证据 | `reason` 必填，去除首尾空格后限制为 5–200 字 | 缺失、空白、少于 5 字、超过 200 字均返回 400 |
| P0 | 添加 | 放弃记录 | 当前成功后会恢复订单，但文档没有说明放弃原因被保存 | 新增放弃记录表，保存项目、领队、原因、原状态和操作时间 | 放弃成功后能查询到完整审计记录 |
| P0 | 修改 | 放弃事务 | 当前会清除领队、恢复 `OPEN` 并移除群聊领队身份 | 将时间校验、原因记录、状态恢复和群聊移除放在同一事务中 | 任一步失败时全部回滚，不出现半完成状态 |
| P0 | 修改 | 异常时间 | 当前规则未定义时间缺失或格式异常时的行为 | `departureDate/departureTime` 缺失或非法时拒绝操作，返回“订单时间异常，请联系客服处理” | 不得使用默认时间继续执行 |
| P0 | 添加 | `/projects/mine` 阶段筛选 | 线上只支持单个原始 `status`；`stage/statuses` 会被静默忽略 | 增加 `stage=ACCEPTED/COMPLETED` | 非法阶段返回 400，不得静默忽略 |
| P0 | 修改 | 分页查询 | 客户端只能在分页结果返回后再次过滤，可能漏单 | 数据库查询先按 `stage` 转换为状态条件，再执行 `count` 和分页 | `total/pages/items` 全部基于筛选后的结果 |
| P1 | 添加 | 数据库索引 | 阶段查询需要按领队和多个状态筛选 | 为项目表增加适合 `leaderAccountId + status + 时间/id` 的复合索引 | 查询计划不再全表扫描 |
| P1 | 修改 | README/OpenAPI | 文档没有 `stage`、放弃原因和提前 6 小时规则 | 更新参数、错误响应、示例及状态映射 | 文档与真实接口测试结果一致 |
| P1 | 添加 | 后端自动测试 | 当前接口行为主要依靠客户端保护 | 增加时间边界、原因校验、并发接单、放弃事务和分页筛选测试 | 所有业务边界都有回归测试 |

## 二、推荐接口设计

### 2.1 我的项目阶段筛选

```http
GET /projects/mine?relation=LEADING&stage=ACCEPTED&pageNum=1&pageSize=15
Authorization: Bearer <token>
```

阶段映射：

| 阶段 | 后端状态 |
|---|---|
| `ACCEPTED` | `MATCHING / CONFIRMED / IN_PROGRESS` |
| `COMPLETED` | `DONE` |

约束：

- `stage` 与原有 `status` 不能同时传入；同时传入时返回 400。
- 原有 `status` 参数继续保留，避免破坏其他客户端。
- `CANCELLED` 不进入 `ACCEPTED` 或 `COMPLETED`。
- 阶段筛选必须发生在统计总数和分页之前。
- 排序应增加 `id` 作为稳定的第二排序键，避免翻页时重复或遗漏。

### 2.2 放弃接单

建议使用 JSON 请求体：

```http
POST /projects/{id}/abandon
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "个人行程冲突，无法按时带队"
}
```

后端执行顺序：

1. 使用数据库行锁读取项目。
2. 校验当前账号确实是该项目的领队。
3. 校验项目状态允许放弃。
4. 使用服务器时间校验距离开始时间不少于 6 小时。
5. 校验并保存放弃原因。
6. 清空 `leaderAccountId` 并将项目恢复为 `OPEN`。
7. 移除项目群聊中的领队身份。
8. 提交事务。

建议允许尚未开始的 `MATCHING/CONFIRMED` 项目放弃；`IN_PROGRESS/DONE/CANCELLED` 应拒绝。若历史数据可能出现异常状态，应记录日志并交由客服处理。

建议新增审计表，例如 `project_leader_abandonment`：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `project_id` | 项目 ID |
| `leader_account_id` | 放弃时的领队账号 ID |
| `previous_status` | 放弃前项目状态 |
| `reason` | 放弃原因，5–200 字 |
| `abandoned_at` | 放弃时间 |

## 三、不需要添加或删除的内容

| 内容 | 结论 |
|---|---|
| `OPEN/MATCHING/CONFIRMED/IN_PROGRESS/DONE/CANCELLED` | 不删除。三个阶段只是客户端展示和查询层聚合，不改变数据库状态体系 |
| `CANCELLED` 项目数据 | 不删除，仅在领队端隐藏 |
| 原有 `status` 查询参数 | 不删除，继续用于兼容旧客户端 |
| 评分接口 | 已有 `averageRating` 和 `ratingCount`，后端无需修改 |
| 性别、出生年月 | 已从客户端移除，不需要增加后端字段 |
| 分享应用 | 暂缓，不需要后端接口 |
| 资质认证 | 当前未启用，暂不增加数据库表或接口 |
| 历史订单分页 | 已有 `/projects/mine?relation=LEADING&status=DONE`，主要是客户端接入问题 |

## 四、客户端兼容提醒

README 示例中的 `departureTime` 为 `08:30:00`，当前 Android 时间校验器只接受 `HH:mm`。后端不应为此改变标准时间格式；客户端应同时兼容：

- `HH:mm`
- `HH:mm:ss`

## 五、推荐实施顺序

1. 后端实现放弃时间、原因校验和审计事务。
2. 后端实现 `/projects/mine` 的 `stage` 分页前筛选。
3. 补数据库索引、自动测试和接口文档。
4. 对线上接口完成回归验证。
5. 最后切换 Android 客户端到新接口，避免旧后端静默忽略 `stage`。
