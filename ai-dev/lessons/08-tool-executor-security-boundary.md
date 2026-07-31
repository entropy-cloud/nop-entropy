# 08: Tool Executor 层是安全缺陷集中区 — SSRF/路径逃逸同源 P1

> Date: 2026-07-31
> Severity: High — MA6.2 Agent 编排安全审计发现 4 个 P1 全部集中在 tool executor 层（HttpRequestExecutor、GraphqlQueryExecutor、LocalToolFileSystem、BashExecutor），且均为同一类"外部输入信任缺失"缺陷

## 场景

MA6.2（Agent 编排安全审计）对 nop-ai toolkit 工具执行器层的审计发现 4 个同源 P1：

| Finding | 执行器 | 缺陷 |
|---------|--------|------|
| P1-MA6.2-001 | `HttpRequestExecutor` | SSRF — 无 URL 白名单/内网 IP 黑名单，可请求内网元数据服务 |
| P1-MA6.2-002 | `GraphqlQueryExecutor` | SSRF — endpoint URL 原样接受、无校验 |
| P1-MA6.2-003 | `LocalToolFileSystem` | 路径逃逸 — `isPathAllowed()` 已实现但从未被任何文件操作调用（静默未接线） |
| P1-MA6.2-004 | `BashExecutor` | 命令注入 — 无输入校验，任何 shell 命令/危险环境变量可执行 |

## 根因

1. **工具执行器是"AI 与外部世界的边界"**：LLM 生成的 tool call 参数是外部输入（攻击者可注入指令让模型输出恶意参数），但执行器普遍把参数当可信输入直接使用。
2. **工具执行器数量多、实现分散**（20+ 执行器），没有统一的输入校验/安全基类；安全审查逐个执行器做才暴露问题。
3. **"校验函数已实现"≠"已接线"**：`isPathAllowed()` 存在于 `LocalToolFileSystem`，但文件操作路径从未调用它 — 实现存在性与运行时接线是两件事（与 Anti-Hollow 检查同一模式）。

## 正确做法

1. **审计工具层时按"输入来源"分类**：凡参数可能来自 LLM tool call（或用户可控输入）的执行器，一律视为安全边界，必须逐项检查 URL/路径/命令/环境变量的校验。
2. **SSRF 类校验模板**：URL 只允许 http/https + 域名白名单 + 内网/loopback/link-local/metadata 地址黑名单（`validateUrl`/`isPrivateIp` 模式，见 `HttpRequestExecutor` 修复）。
3. **路径类校验必须接线到每次文件操作**：`isPathAllowed()` 在 `resolveFile()` 等入口调用，不能只存在不接线；closure audit 需验证调用链（Anti-Hollow）。
4. **命令类校验分两层**：危险命令/危险环境变量拒绝（`DESTRUCTIVE_COMMAND` + `DANGEROUS_ENV_VARS`）+ 输入白名单/转义。
5. **测试固化**：每个修复附行为断言测试（`BashExecutorTest` 覆盖 destructive 命令拒绝路径；`GraphqlQueryExecutorTest` 覆盖外部端点校验）。

## 判定规则

> **Tool executor 参数一律按不可信输入处理。** 任何直接使用参数拼 URL/路径/shell 命令的执行器，未校验即使用 = P1 安全缺陷。
>
> **"校验函数存在"不能作为修复证据**，必须验证其在运行时调用链上被实际调用（接线验证，Anti-Hollow 规则 #23）。

## 适用范围

- 任何 agent/tool 框架的工具执行器审计
- LLM 输出 → 外部副作用（网络/文件/命令/DB）的转换点
- SSRF、路径遍历、命令注入类安全审计

## 参考

- `ai-dev/audits/2026-07-31-arm-MA6.2-nop-ai-agent-security.md`（4 个 P1 全部在 tool executor 层）
- `ai-dev/audits/arm-index.md`（P1-MA6.2-001~004，MR3 修复证据）
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java`、`BashExecutor.java`
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java`（接线修复）
