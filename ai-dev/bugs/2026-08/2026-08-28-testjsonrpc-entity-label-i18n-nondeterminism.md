# 2026-08-28 testJsonRpc 实体显示名 i18n 查找非确定性（根因未闭环）

## Problem

- 全量 reactor（`./mvnw test`）下 `nop-auth-service` 的 `TestNopAuthUserBizModel#testJsonRpc` 偶发 `check-output-fail`：录制期望错误消息 `类型为[用户]，id为[123]的记录不存在`，实际为 `类型为[io.nop.auth.dao.entity.NopAuthUser]，id为[123]的记录不存在`（errorCode 恒为 `nop.err.dao.unknown-entity`）。
- 出现频率：reactor 上下文 2/4 轮；模块隔离运行 0/9。完全相同代码、相同录制，不同轮次结果不同。

## Diagnostic Method

- 首次出现时实际 diff 未持久化（模块立即被复跑覆盖），一度按低频 flake 记观察项；第二次出现时从 log 中提取到完整不匹配详情（教训：flake 的现场要第一时间保存）。
- 从 diff 定位渲染链：`UnknownEntityException` 的 `entityName` 参数在 `ErrorMessageManager` 渲染时经 `I18nHelper.getEntityDisplayName` 查 `entity.label.*` 字典（nop-auth-meta 的 zh-CN/en i18n yaml 含 `NopAuthUser: 用户`），未命中回退裸实体名。
- 排除"locale 为英文导致中文模板不符"：模板本体来自 `define()` 缺省中文，与 locale 无关；只有 label 查找按 locale 走 registeredMessages（`SysI18nMessageLoader` 的 DB 注册）+ VFS yaml 双通道。
- 未完成：label 查找在 reactor 负载下偶发未命中的确切触发（locale 线程残留 vs i18n 缓存加载时序）未定位——模块隔离无法复现，reactor 复现不可控。

## Root Cause

- 已确认层：`ErrorMessageManager` 对 `entityName` 参数做 i18n label 翻译，查找未命中时静默回退实体全名——错误消息的渲染依赖一个**非确定可用**的翻译层。
- 未闭环层：reactor 负载下 label 查找偶发未命中的机制（nop-core `I18nMessageManager` 的 registeredMessages/VFS 双通道在时序下的行为）。

## Fix

- 测试侧（已落地，commit 3b9a3d846b）：录制 `response.json5` 的 message 字段改 `@or:{patterns:[...]}`（nop-match DSL）同时接受两种等价渲染——同 errorCode、仅实体名渲染不同。
- 产品侧（未动，观察项）：nop-core 属 plan-first 保护区，且无稳定复现路径；需专项归因（建议入手点：label 未命中时记录 locale 与字典快照的 WARN，收集现场后再定）。

## Tests

- `nop-auth/nop-auth-service/_cases/.../testJsonRpc/output/response.json5` - @or 模式接受 displayName/实体全名两种形态，测试 9/9 绿。

## Affected Files

- `nop-auth/nop-auth-service/_cases/io/nop/auth/service/TestNopAuthUserBizModel/testJsonRpc/output/response.json5`

## Notes For Future Refactors

- 若在 reactor/负载环境再见到"错误消息里实体名偶发变成全限定类名"，即本条重现——保存现场（locale、i18n 缓存状态）后按 Root Cause 未闭环层继续归因。
- `ErrorMessageManager` 的参数翻译是静默回退语义：翻译层不可用时消费方无感知。若未来要求错误消息确定性，需在渲染层显式区分"翻译失败"与"无翻译"。
