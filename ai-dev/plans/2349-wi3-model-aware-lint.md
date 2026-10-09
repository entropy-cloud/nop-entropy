# 2349 WI3 XDSL 最佳实践检查第二层（模型感知 lint 子命令）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI3 行，M1 检查门）
> Related: plan 2347（WI1 表 B 同源判定面）、plan 2348（WI2 L1 规则集）

## Purpose

落地 XDSL 最佳实践检查的 L2 层：`StreamMaintenanceMain lint` 子命令——模型感知（复用 DslModelParser → StreamModel 解析链与构建期校验器），一次性报告全部声明面违规（L1/builder 只报首个），复用 `nop.err.stream.*` 错误码族（零新增码），沿用 conf-validate 的 D7 退出码契约与 FAIL/SKIP/WARNING 三级报告结构。

## Current Baseline（2026-10-09 live 核对）

- `StreamMaintenanceMain`（runtime/maintain）现有子命令 reset-state/reshard/conf-validate/dry-run/sample；`StreamConfValidateCommand.run(args, resolver)` 形态：`file=<path> [--connect]`，退出码 0/1/2（D7），bean 源三形态（编程注入/容器包装/全局容器回退）。
- `StreamConfValidator`（flow/validate，431 行）三层校验：LAYER_MODEL(1) 解析 / LAYER_CONSTRUCTION(2) 构图 / LAYER_CONNECTIVITY(3) 连通探测；`StreamConfValidationReport` + `ValidationIssue`（Severity: FAIL/SKIP/WARNING，`ofSkip`/fail 工厂，render() 文本渲染）已支持 lint 需要的全部报告语义。
- 解析链：`DslModelParser.parseFromNode` → `StreamModel`（flow/model）；`StreamModelDslBuilder.build()` 为构建期 fail-fast 真值源。
- 测试先例：`TestStreamConfValidateCommand`（runtime）——命令级测试模式照抄对象。
- L1 侧（plan 2348）：6 条 nop-xdsl-* 规则 + WI1 表 B；R13 的 L1 半边已裁定移交 WI21 仓扫工具。

## Goals

- `StreamModelLinter`（flow/validate）：9 个规则面的模型感知实现——R1+R12（无消费方面同源比对，一次性全报）、R2（声明式优先退化提示）、R3（custom 状态归属提示）、R4（类型声明）、R5（水位供给）、R6（幂等组合预检，显式 skip 形态）、R11（串联 conf-validate 构建期两层）、R14（平台复用，显式 skip 形态）、R13 模型感知半边（显式 skip，WI21 激活点登记）。
- `StreamLintCommand`（runtime/maintain）+ `StreamMaintenanceMain` 增 `lint` 子命令 + usage 文本；退出码 0=无 FAIL（WARNING/SKIP 允许）、1=有 FAIL、2=用法错误。
- 运维文档（user-guide conf-validate/dry-run 同族小节）。

## Non-Goals

- 不升 error 阻塞（两阶段，消号归 WI4）；不改 conf-validate/dry-run 行为；不新增 `nop.err.*` 码；不实现 R13 注册表比对（WI21 激活）。
- R6/R14 的完整机械面依赖 WI15 端点属性放行，本 WI 以显式 SKIP 注记表达（与 D7「显式跳过允许」契约一致，非静默）。

## Scope

### In Scope

- `nop-stream-flow`：`validate/StreamModelLinter`（新）。
- `nop-stream-runtime`：`maintain/StreamLintCommand`（新）+ `StreamMaintenanceMain` lint 分支 + usage。
- 测试：`TestStreamModelLinter`（flow）、`TestStreamLintCommand`（runtime）。
- 文档：`docs-for-ai/03-modules/nop-stream-user-guide.md` lint 小节 + WI1 矩阵 L2 注记。

### Out Of Scope

- nop-lint 集成、CI 接线（WI4）；error 升级；场景注册表（WI21）。

## Execution Plan

### Phase 1 - StreamModelLinter（9 规则面）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/validate/`

- Item Types: `Feature`

- [x] `StreamModelLinter.lint(IResource, BeanFunctionResolver)` → `StreamConfValidationReport`：
  - **R1+R12 无消费方面**（LAYER_MODEL，复用 `ERR_STREAM_NOT_IMPLEMENTED`/`ERR_STREAM_WINDOW_ATTR_UNSUPPORTED` 码语义，Severity=WARNING——build 仍是最终权威）：声明面清单驱动（**对齐 2026-10-09 live 表 B**——allowedLateness/ACCUMULATING/maxDelay/emit 已放行不报，不得按 roadmap 旧快照报违例）——triggerId（strategy 属性/节点子元素）、ACCUMULATING_AND_RETRACTING、六注册表+三回调、边流控四属性、端点 8 属性、custom params/source 体、window parallelism；判据与 builder 相同（maxParallelism `>0`、consistencyCapability 非默认枚举），属性名编码进 issue target（WARNING 工厂无 paramName 参数，不做基建改动）；全部违规一次报齐
  - **R4 类型声明**（WARNING，`nop.err.stream.invalid-arg` 语义）：模型含 window/aggregate/join 且 `<schemas>` 为空
  - **R5 水位供给**（WARNING）：模型含 window/cep 且无 timestampsAndWatermarks 转换
  - **R2 声明式优先**（WARNING 提示）：process bean 存在且模型无任何 window/aggregate/join/cep——提示核对可否声明化（启发式，消息注明）
  - **R3 状态归属**（WARNING 提示）：`<custom>` 算子存在——提示核对状态登记与 checkpoint 参与（声明面无状态 API 为结构性满足，此为 custom 逃生门的提示面）
  - **R6 幂等组合**（SKIP 注记）：`processingGuarantee=STRICT_EXACTLY_ONCE` 时声明「端点 capability 预检待 WI15 端点属性面放行后激活；引擎侧 validator 保留为最终关卡」
  - **R14 平台复用**（SKIP 注记）：维表接线形态检测待 WI15 params 面落地（当前无可检声明信号）
  - **R13 模型感知半边**（SKIP 注记）：场景注册表比对待 WI21 注册表落地
  - **R11 串联**（LAYER_CONSTRUCTION 折叠）：内部调用 `StreamConfValidator.validateStream(resource, resolver, connect=false)`，把其 FAIL/WARNING/SKIP 条目并入 lint 报告（layer 标记保留），构建失败即 lint FAIL
- [x] **编排契约**：lint 自行解析模型 → 无消费面等全量扫描（一次报齐）→ **无条件**调用 `StreamConfValidator.validateStream(resource, resolver, connect)` 合并结果 → xdef 解析失败时以单条 LAYER_MODEL FAIL 短路（跳过方面扫描，不重复报告）。同源 co-occurrence 契约：R1/R12 违规模型的报告 = N 条同源 WARNING + 1 条 R11 FAIL（builder fail-fast），exit=1——R1 fixture 须断言此共存形态。
- [x] findings 一律复用既有错误码语义（消息引用 builder 将抛的码），零新码。

Exit Criteria:

- [x] 9 个规则面各有一条单测证明（已知坏模型 → 预期 finding；干净模型 → 零 finding）
- [x] 无静默跳过：R13/R14 的 SKIP 注记无条件出现在报告中；R6 的 SKIP 为条件出现（exactly-once fixture 断言之）——两类断言齐备
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - lint 子命令接线

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/maintain/`

- Item Types: `Feature`

- [x] `StreamLintCommand.run(args, resolver)`（镜像 StreamConfValidateCommand：`file=<path> [--connect]`——--connect 透传 validator 打通 R11 完整面（含 dry-run 连通层，对齐 roadmap R11「串联 conf-validate 与 dry-run」）、VFS/本地双路径解析、D2 bean 源三形态）
- [x] `StreamMaintenanceMain` 增 `case "lint"` + usage 文本补 lint 行
- [x] 渲染策略：lint 自拼输出头（`lint target=... exit=N`），报告体复用 `StreamConfValidationReport.render()` 条目行（不打印其 `conf-validate:` 头，避免误导标签）
- [x] 退出码契约：0=无 FAIL、1=有 FAIL（**含 xdef 解析失败**——照抄 `parseModel` 先例，FAIL 非 usage error）、2=用法错误（缺参/文件不存在）

Exit Criteria:

- [x] **接线验证**：`TestStreamLintCommand` 证明命令入口链 StreamLintCommand.run → StreamModelLinter → StreamConfValidator 真实连通（clean 模型 exit 0 + R1 违规模型 exit 1 且报告含同源 WARNING+FAIL 共存 + xdef 解析失败 exit 1 + 缺 file 参数 exit 2；`main` 一律 System.exit 不可在 JUnit 调——镜像 TestStreamConfValidateCommand 先例只测 Command.run + `usage()` 文本断言 lint 行）
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 文档与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Proof`

- [x] user-guide conf-validate/dry-run 同族位置增 lint 小节（用法/规则面清单/退出码/与 conf-validate 的关系，指回 owner doc）
- [x] `docs-for-ai/03-modules/nop-stream.md`「提交前校验」节增 lint 条目（命令族 owner doc，逐命令用法/exit 契约同形）
- [x] WI1 矩阵尾部 L1 映射段补 L2 一句注记
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 绿（受并发会话影响时用 -o 离线 + 逐模块）
- [x] 独立 closure audit

Exit Criteria:

- [x] 文档落点真实存在且与实现一致
- [x] 模块测试全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] 9 规则面实现且各有正控测试（含 R6/R13/R14 显式 SKIP 断言）
- [x] lint 子命令接线验证通过（调用链连通 + 三档退出码）
- [x] 零新增错误码（findings 全部复用 `nop.err.stream.*`）
- [x] 受影响 owner docs 已同步（user-guide lint 小节 + 矩阵 L2 注记）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] 模块测试全绿（flow + runtime）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2349-wi3-model-aware-lint.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI3 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

### R13 场景注册表双向比对（模型感知半边）

- Classification: `watch-only residual`（激活点 WI21）
- Why Not Blocking Closure: 注册表在 WI21 才建立，本 WI 以显式 SKIP 注记占位并登记激活点，D7 契约允许显式跳过
- Successor Required: yes
- Successor Path: WI21（E1-E2 套件 + 场景注册表）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 9 规则面 + lint 子命令接线 + 三档退出码 + 文档同步全部落地；findings 复用 nop.err.stream.* 码（实测渲染为真实码字符串）。独立 closure audit 首轮 REJECT（Major-1：ErrorCode.toString() 渲染对象哈希）——按修复建议落地（3 处 getErrorCode() + 前缀防回归断言）后同 auditor 定点复审 APPROVE。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_ad03929b-8a17-4717-8c92-041cc09f42fc（fresh session，未参与实现）
- Audit Session: agent_ad03929b-8a17-4717-8c92-041cc09f42fc（首轮 REJECT + 修复后定点复审 APPROVE，同 agent 延续）
- Evidence:
  - Phase 1：9 规则面审查 PASS（表 B live 对齐、R6/R13/R14 显式 SKIP 非 空 方法、R11 无条件折叠）；TestStreamModelLinter 11/11 绿（audit 自行运行）
  - Phase 2：接线验证 PASS——clean exit 0 / co-existence exit 1 / 解析失败 exit 1（非 2）/ 缺参 exit 2 / usage 断言；TestStreamLintCommand 6/6 绿
  - Phase 3：文档与实现一致（nop-stream.md 提交前校验节 lint 条目 + user-guide 两处）；audit 实测 stdout：finding errorCode 渲染为 `nop.err.stream.*` 真实字符串（修复前 `ErrorCode@hash`），解析失败 FAIL 携带 xlang 既有码
  - Major-1 修复证据：3 处常量改 getErrorCode()、catch fallback 直用 NopException.getErrorCode()（返回 String）、防回归断言（WARNING 码前缀 startsWith("nop.err.stream.")）；全文件 toString() 零残留
  - 反应堆回归 1344/1344 绿（实施侧记录；两个 2PC E2E 首轮失败经隔离复跑证实为负载时序抖动）
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/check-plan-checklist.mjs` exit 0
  - Deferred 项分类检查：R4/R5 运行面裁剪与 R13 半边为显式裁定（Deferred But Adjudicated 已登记），非静默降级

Follow-up:

- 命令层 WARNING+FAIL 共存内容断言可在 WI4 接线时顺手补强（flow 层已兜住，non-blocking）
- R13 场景注册表比对待 WI21 激活（Deferred 已登记 successor）
