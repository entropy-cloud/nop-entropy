# 2283 深度审计 2026-09-30 安全 P2 修复（8 项）

> Plan Status: completed
> Last Reviewed: 2026-09-30
> Source: `ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/summary.md`（P2 安全主题清单 + 复核结论）
> Related: `ai-dev/plans/2282-deep-audit-p1-defect-fixes.md`

## Purpose

收口 2026-09-30 深度审计中经独立复核保留的安全/权限类 P2：dev 工具面鉴权、请求取消归属校验、custom_sql 沙箱黑名单对齐、AI 工具黑名单死条目、RAG BizModel 鉴权与命名、外部协议边界默认值（OkHttp trust-all 告警、SFTP hostkey、飞书回调身份验证）。每项修复行为 + 回归测试。

## Current Baseline

- 审计与复核证据见 `ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/` 对应报告（03/05/08/12）。
- [G5-13-01]（报告 05）：DevDocBizModel/DevToolBizModel 全部操作无 `@Auth`，`nop.debug` 部署下任意登录用户可导出 IoC 容器 XML、全部配置值、全量 schema；同包 `DevStatBizModel` 已按 `@Auth(roles="admin")` 整改（auth==null 即公开访问的语义已被该先例确立）。
- [G5-13-02]（报告 05）：`Sys__cancel` 为公开 `@BizQuery`；`CancelTokenManager.cancel(reqId)` 无用户归属校验；`register` 在 reqId 撞车时主动取消先到请求。
- [G8-13-01]（报告 08）：`MetaQualityRuleExecutor.java:100-125` custom_sql 沙箱黑名单缺 `INTO`（裸 token）与 `PG_TERMINATE_BACKEND`；兄弟校验器 `ExpressionMeasureValidator`（:86/:116）已收口；测试 `TestMetaQualityRuleExecutorCustomSqlSandbox` 的 25+ 拒绝向量无这两个形态。
- [G3-13-02]（报告 03）：`BashExecutor.java:44-49,209-217` `BASH_FUNC_` 黑名单条目用精确 `Set.contains`，前缀型变量名永不命中；复核修正：`DockerBashSandbox` 的 `ENV_KEY_PATTERN` 会拒绝含 `%` 的键名，残余暴露面集中在对 bash 导出函数注入无字符集检查的路径，但黑名单死条目本体成立。
- [G3-07-01]（报告 03）：`NopAiRagBizModel.java:24-67` 全仓唯一 `/` 前缀 bizObjName（生成非法 GraphQL 操作名），写操作 `ingestDocument` 无 `@Auth`，偏离平台 MR2 基线。
- [G12-13-01]（报告 12，复核降级 P1→P2）：`nop-http-client-okhttp/OkHttpClientProvider.java:150-168` 在 `useSsl=true` 且未注入显式 TrustManager/HostnameVerifier 时默认安装 `DisableValidationTrustManager`+`TrustAllHostnames` 且零告警，与 Jdk 客户端 useSsl 语义相反；同仓 `CompositeX509TrustManager` 的 F-N1-3 WARN 基线为参照。模块在仓内零接线（故 P2）。
- [G12-13-02]（报告 12）：SftpClient 硬编码 `StrictHostKeyChecking=no`，无配置无告警。
- [G12-13-03]（报告 12）：`FeishuBindProvider` 扫码回调直接信任客户端自声明 open_id，无飞书侧验证。

## Goals

- dev 工具面与 RAG 写操作获得与平台既有基线一致的 `@Auth` 防护。
- 请求取消只能由发起者触发；reqId 撞车不再干扰先到请求。
- custom_sql 沙箱黑名单与兄弟校验器逐项对齐，并有测试钉死。
- 外部协议边界（TLS/SSH/飞书回调）不再静默采用不安全默认：要么有响亮告警，要么改为服务端验证。
- 全部修复带回归测试，受影响模块测试全绿。

## Non-Goals

- 不改 OkHttp useSsl 的默认行为语义（只加告警与文档；行为变更涉及下游兼容，留待该模块实际接线时处理）。
- 不做 SkillExecutor 的纵深加固（复核裁定为 P3 加固建议，见报告 03 附带线索）。
- 不处理 P3 安全项（G5-13-03/04/05 等）。
- 不引入新的公共 API 面（如新的配置项仅在必要处新增，遵循既有 `@cfg:` 模式）。

## Scope

### In Scope

- 上列 8 条 P2 对应源码修改与回归测试。
- 涉及 `@Auth` 注解与 feishu 集成内部实现；不触碰 nop-auth 模块本体（protected area 未涉及）。

### Out Of Scope

- nop-auth 登录/MFA/限流链路（本轮审计已确认收敛）。
- nop-ai-gateway channel 会话存储等非审计发现项。

## Execution Plan

> 三个 Workstream 相互独立、可并行。执行顺序：WS1+WS2 一批，WS3 一批。

### Workstream 1 - 服务框架 dev 工具面鉴权 + cancel 归属校验 [G5-13-01][G5-13-02]

Status: completed
Targets: DevDocBizModel、DevToolBizModel、Sys__cancel 所在 BizModel、CancelTokenManager

- Item Types: `Fix`、`Proof`

- [x] DevDocBizModel/DevToolBizModel 全部公开操作加 `@Auth(roles="admin")`，对齐同包 DevStatBizModel 先例（Fix）
- [x] Sys__cancel 增加归属校验。**语义裁定（对抗审查 F-2283-4）**：(a) register 时绑定 `IServiceContext.getUserId()`；cancel 时仅允许取消本人请求，非本人返回显式失败；(b) 未登录（userId==null）注册的请求不开放公开 API 取消（cancel 加登录要求）；(c) 同一用户携同 reqId 重试在途请求视为幂等重绑定（返回既有 token，不取消先到请求），不同用户同 reqId 响亮拒绝——保留合法重试语义，仅切断跨用户干扰（Fix）
- [x] register 的 reqId 撞车语义修正：不再无条件 `oldToken.cancel("replace")`，按上述 (c) 裁定实现（Fix）
- [x] 回归测试：非 admin 调 dev 导出被拒；用户 A 无法取消用户 B 的请求；同用户同 reqId 重试不取消先到请求；不同用户同 reqId 被拒绝（Proof）

Exit Criteria:

- [x] 三类新行为各有测试断言（未登录/非 admin、跨用户取消、撞车），修复前至少鉴权项失败
- [x] 既有 dev 工具 admin 路径测试（如有）不回归
- [x] 无静默跳过：拒绝路径显式失败（错误码/异常），不返回空成功
- [x] No owner-doc update required（对齐既有 DevStatBizModel 基线，无新契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 2 - nop-metadata 沙箱对齐 + nop-ai 工具与 RAG 鉴权 [G8-13-01][G3-13-02][G3-07-01]

Status: completed
Targets: `MetaQualityRuleExecutor`、`TestMetaQualityRuleExecutorCustomSqlSandbox`、`BashExecutor`、`NopAiRagBizModel`

- Item Types: `Fix`、`Proof`

- [x] MetaQualityRuleExecutor custom_sql 黑名单补 `INTO`（裸 token）与 `PG_TERMINATE_BACKEND`，逐项对齐 ExpressionMeasureValidator（Fix）
- [x] 沙箱测试补拒绝向量：`SELECT ... INTO new_table`、`pg_terminate_backend(...)`（Proof）
- [x] BashExecutor `BASH_FUNC_` 黑名单改为前缀匹配（Fix）
- [x] BashExecutor 回归测试：`BASH_FUNC_foo=...` 与 `BASH_FUNC_foo%%=...` 形态均被拒绝（Proof）
- [x] NopAiRagBizModel：写操作 `ingestDocument` 加 `@Auth(permissions="NopAiRag:write")`（对齐 FileToolBizModel MR2 基线；bizObjName 去前缀已由计划 2282-WS4 连带完成，本 WS 加防回归断言）（Fix）
- [x] NopAiRagBizModel 暴露路径最小测试：**断言层级裁定（对抗审查 F-2283-6）**——nop-ai-rag 测试基建无 IoC 容器/GraphQL 管道，测试在注解/工具类层级断言（bizObjName 为合法 GraphQL 标识符、写 action 具备 @Auth 元数据），不搭建 GraphQL 管道（Proof）

Exit Criteria:

- [x] 两个沙箱拒绝向量进测试且通过；ExpressionMeasureValidator 既有测试不回归
- [x] BASH_FUNC_ 前缀形态测试通过
- [x] RAG BizModel 的 bizObjName 为合法标识符（无 `/` 前缀）、写操作鉴权元数据存在，均有测试断言
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service,nop-ai/nop-ai-toolkit,nop-ai/nop-ai-rag -am` 全绿
- [x] Owner doc 已同步：nop-metadata.md 列有 token 清单，已补 G8-13-01 blocklist 条目
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 3 - 外部协议边界默认值 [G12-13-01][G12-13-02][G12-13-03]

Status: completed
Targets: `nop-http-client-okhttp/OkHttpClientProvider`、SftpClient（nop-network）、`FeishuBindProvider`（nop-integration-feishu）

- Item Types: `Fix`、`Proof`

- [x] OkHttpClientProvider：trust-all 组件被默认安装时输出 WARN（对齐 CompositeX509TrustManager F-N1-3 告警基线），消息说明风险与显式注入替代方案；默认行为不变（Fix）
- [x] SftpClient（模块 `nop-integration/nop-integration-sftp`）：`StrictHostKeyChecking` 改为可配置（遵循仓内既有配置注入模式），显式关闭时输出 WARN；默认值保持现网兼容并在 javadoc 声明风险（Fix）
- [x] FeishuBindProvider（模块 `nop-integration/nop-integration-feishu`）：open_id 改为服务端验证。**契约与实现裁定（对抗审查 F-2283-1，前提修正：当前链路不存在服务端响应，`IFeishuHttpApi` 亦无 OAuth/用户信息通道可复用）**：在 `IFeishuHttpApi`/`JdkFeishuHttpApi` 上新增 code→user_access_token→用户信息(open_id) 两个外部 API 方法；`loginByScan` 公开入参（`ChannelScanCallback`）变更为携带 OAuth `code`，客户端自声明 open_id 字段不再被信任（忽略或移除，实现时按最小破坏选择并在代码注释声明）（Fix）
- [x] 回归测试：okhttp WARN 路径可触发断言（logback captor 或等价手段）；sftp 配置项两态行为；feishu 用桩 HTTP 响应验证"code 换取的 open_id 与客户端声明不一致时以服务端为准"（Proof）

Exit Criteria:

- [x] 三个修复各有测试证明新行为（告警触发/配置生效/服务端值优先）
- [x] **下游模块测试同步**（对抗审查 F-2283-2）：`nop-auth/nop-auth-service`（TestChannelScanBindLoginE2E/TestScanLoginMfa 直接喂 open_id）与 `nop-ai/nop-ai-gateway`（TestChannelLoginApi）的既有测试已适配新契约且全绿
- [x] 既有 okhttp/sftp/feishu 模块测试不回归
- [x] 无静默跳过：不安全配置路径必有告警，验证路径失败必响亮
- [x] `./mvnw test -pl nop-network/nop-http/nop-http-client-okhttp,nop-integration/nop-integration-sftp,nop-integration/nop-integration-feishu,nop-auth/nop-auth-service,nop-ai/nop-ai-gateway -am` 全绿
- [x] sftp 新增配置项已在模块 README 补配置表与风险声明（closure audit 条件项已闭环）；OkHttp/feishu 默认行为不变，无契约变化
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 8 条 in-scope 安全 P2 已修复且各有回归测试
- [x] 无 in-scope defect 被降级到 deferred / follow-up
- [x] 每个 Workstream 的 Exit Criteria 全部勾选
- [x] 受影响 owner docs 已核对（各 WS 已显式裁定）
- [x] 独立子 agent closure audit 完成并写入下方 Closure 段
- [x] Anti-Hollow Check：closure audit 已验证防护路径实际连通（如：无权限调用真实被拒、沙箱向量真实被拦截、不一致 open_id 真实被拒），无新增空分支
- [x] `./mvnw test -pl <受影响模块清单> -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high` 退出码 0

## Deferred But Adjudicated

### OkHttp useSsl 默认语义统一（trust-all → 显式 opt-in）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 模块在仓内零接线（复核证据），本计划已消除"静默"（WARN），风险面已可见；默认行为变更属独立兼容性裁定，不阻塞当前防护收敛。
- Successor Required: `yes`
- Successor Path: 触发条件为 nop-http-client-okhttp 被实际接线使用时——届时必须完成 trust-all 默认改显式 opt-in 的裁定方可上线（已记入代码注释与本文档）

## Non-Blocking Follow-ups

- SkillExecutor skillName 白名单纵深加固（P3，报告 03 附带线索）。
- G5-13-03/04/05（P3 安全项）留待治理批次。

## Closure

Status Note: 8 条安全 P2 全部行为级落地，独立 closure audit PASS（条件项：sftp 配置文档行已补 README、复选框已补勾，均完成）。
Completed: 2026-09-30

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（fresh session，agent_e45e3869-850d-4ce7-ab7b-1b49b698b187）
- Evidence:
  - WS1: PASS——DevDoc 7/7、DevTool 2/2、DevDocGrpc 1/1 @Auth(admin)；SysBizModel:44-46 登录要求 + CancelTokenManager 归属比对/幂等重绑定/REQ_ID_CONFLICT 三错误码实义；鉴权链 ReflectionBizModelBuilder:360-362→GraphQLActionAuthChecker 连通；红 _tmp/2283-ws1-red-evidence（4 例修复前失败）/绿 nop-biz 90 + graphql-core 134
  - WS2: PASS——黑名单 :126 两新 token 形态照抄 ExpressionMeasureValidator:86/:116；BashExecutor:52,217 前缀匹配且原 13 条目保留；NopAiRag ingestDocument @Auth + bizObjName 防回归断言；红 ws2-2283/red-*.log / 绿精确 -pl 三模块 EXIT=0
  - WS3: PASS——OkHttpClientProvider:160-165,176-181 两 WARN 且默认安装不变（=Deferred 裁定）；SftpClient:142-156 配置化+WARN+默认兼容；JdkFeishuHttpApi:84-110 真实 OAuth 端点（桩 HTTP 实测）；FeishuBindProvider:156-196 仅读 code、客户端 open_id 声明不读、ticket 消费前换取失败可重试（exchangeCalls==1 计数器接线验证）；下游 nop-auth-service 仅测试改动（git diff 实证）、gateway 零改动全绿；五模块 BUILD SUCCESS
  - Anti-Hollow：防护路径全连通（取消/拦截/验证均有红→绿行为翻转或日志捕获断言）；新增失败路径全部类型化异常 fail-loud；9 受影响模块 scan-hollow --severity high exit 0
  - check-plan-checklist --strict exit 0（closure 后复核）
  - Deferred 诚实性：OkHttp useSsl 默认语义统一 = watch-only residual + successor required yes，三方一致（Non-Goals 预声明/代码注释/复核降级证据）
  - Observation（不阻塞）：飞书 authen v1 端点属旧代 API，successor 接线 E2E 时复核（测试 javadoc 已声明）

Follow-up:

-（待 closure 时确认）
