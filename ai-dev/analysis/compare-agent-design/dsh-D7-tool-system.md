# dsh-D7 工具系统对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D7 子机制 D7-1..D7-5）、02-terminology-map.md（T13）; 机制事实引用 03（链 2/P12-P16）、04（工具能力 ③3.1.4/3.2）、06（deny×block 叠加）
> Owner: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`

## ① 结论摘要

- 总裁定：**等价（强项互补）**。nop 强在修复链与安全纵深（ChainRepairer 4 阶段为两方独有；7-checkpoint 纵深含路径/写冲突/post-denial）；dsh 强在结果投影分离与并发调度模型（output.render 与 isConcurrencySafe 分组屏障为 dsh 独有）。
- 关键差异 1（D7-1）：dsh `defineTool` 的 output{schema, render} 实现 canonical value 与模型可见投影分离——工具返回结构化值、模型看到渲染文本；nop 工具结果即文本消息。
- 关键差异 2（D7-2）：nop 批内全 fan-out 并行（无并发安全标记，300s 超时兜底）；dsh isConcurrencySafe(args) 参数化判定 + exclusive 屏障 + 有界池（默认 10）。
- 关键差异 3（D7-5）：双方都有 fail-closed 沙箱与 spill 趋同演化（nop ISpillStore vs dsh SpillStore+spill-policy）；nop 多出路径访问检查/写冲突注册/post-denial 指纹三面。
- 可吸收增量建议一句话：nop 可吸收 dsh 的参数化并发安全标记与结果投影分离（见 ⑥）。

## ② nop 侧机制与锚点

按 02 T13、03 §2.1 链 2、04 ③3.1，对比增量：

- **声明与 schema**（D7-1）：`tool.xdef` DSL 声明（模型驱动，Owner doc 04-tool-invocation.md 契约）；工具经 `IToolManager` 注册，`AgentToolPlanResolver` 按 _tools 白名单→denyTools→activeTags/denyTags 过滤（`engine/AgentToolPlanResolver.java:57-132`）。结果即 ChatToolResponseMessage 文本，无 value/投影分离。
- **执行调度**（D7-2）：批内全 fan-out——`executeAllowedCalls` 逐工具 PRE_TOOL_ATTEMPT→提交 future+orTimeout(300s 默认)→allOf join→按序提交结果（`engine/AgentToolDispatcher.java:203-282`）；无并发安全标记（全部并行）；中途同步异常取消全部已启动 future（:246-253）。
- **结果回填**（D7-3）：spill 趋同——成功结果超阈值入 `ISpillStore`（inline 留 preview + `[SPILL_REF id=...]`，:311、:481-511，read-spill 工具取回）；回填经 BEFORE_TOOL_RESULT_PROCESSED hook（可 Reenter 替换）→ ctx.addMessage → TOOL_EXECUTION checkpoint。
- **调用修复**（D7-4）：`ChainRepairer` 4 阶段（ToolNameNormalization→ArgumentStructure→ValueCoercion→ArgumentCleanup，`repair/ChainRepairer.java:42-80`）——安全检查前 transform，修不动原样交 access-check 拒绝；默认 NoOp opt-in。
- **权限与沙箱**（D7-5）：7-checkpoint 纵深（postDenialGuard→toolAccess→permission→pathAccess→layer2(securityLevel+matrix)→layer3 approvalGate→conflict(writeIntent)，`engine/AgentSecurityConsultation.java:276-284`）+ `ISandboxBackend`（Docker/NoOp，fail-closed，`security/ISandboxBackend.java`）+ `IPathAccessChecker` + `IWriteIntentRegistry`（跨 session 写冲突）+ IDenialLedger 阈值治理。

## ③ 对方侧机制与锚点

按 02 T13、03 §2.2（P15-P16）、04 ③3.2，对比增量：

- **声明与 schema**（D7-1）：`defineTool` 完整字段（`packages/core/tools/src/schema.ts:483-545`）：name/description/parameters（隐式根对象 schema）/**output{schema, render(args,value)}**（canonical lossless-JSON 值与模型可见纯投影分离，:490-498）/timeoutMs（协作超时预算，永不下发模型）/execute/finalizeContent/presentCall/presentResult（UI render intent，回放可重放）。
- **执行调度**（D7-2）：`ToolExecutionMode` parallel|exclusive——仅 `isConcurrencySafe(args)`===true 入 parallel（参数化判定，省略/异常一律 exclusive，`tools/src/index.ts:261,1266-1275`）；有界滚动池 maxParallelToolCalls（默认 10，settings 热改下一组生效）；**exclusive 调用是屏障**；结果按模型顺序提交（`tool-calls.ts:198-213`）。
- **结果回填**（D7-3）：tools/post-execute accept 可替换 content 或 canonical value（重校验重渲染）+ additionalContexts 注入 next-step inbox；spill 趋同——spill-policy 挂 post-execute（prepend 钉位，有界头尾预览+取回指引，`packages/spill/spill-policy/src/index.ts:185-211`）；deny 不跳过 post-execute（06 ④.1）。
- **调用修复**（D7-4）：无修复链；`INVALID_PREPARED_CALL` 码（参数错误即失败）。
- **权限与沙箱**（D7-5）：tools/pre-execute 审批瀑布（allow/deny/ask；ask 经 approval seam 无服务降级 deny，`tools/src/index.ts:1465-1506,1669-1719`）；`SandboxProvider.confine(argv, policy)` 返回禁闭 argv + enforcement 完整度 + **该后端 denial 方言签名**（消费方按方言识别拒绝，`packages/sandbox/sandbox/src/index.ts:90-176`）；SANDBOX_UNAVAILABLE fail-closed；code-mode（run_code 子派发，tools/code-dispatch-log 只改日志副本）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D7-1 声明与 schema | tool.xdef DSL 声明式（模型驱动、XDef 投影）；结果即文本 | defineTool 代码式 + output{schema,render} canonical 值/投影分离 + presentCall/presentResult UI 意图 | 对方领先 | nop Owner doc `04-tool-invocation.md`；dsh `schema.ts:483-545`——声明方式等价（DSL/代码取舍），但值/投影分离与可重放 UI 意图是 dsh 独有的质量杠杆 |
| D7-2 执行调度 | 批内全 fan-out（无并发标记）；300s 超时；中途异常取消全部 | isConcurrencySafe(args) 参数化分组 + exclusive 屏障 + 有界池 10 + 模型序提交 | 对方领先 | nop `AgentToolDispatcher.java:203-282`；dsh `tools/src/index.ts:261`、`tool-calls.ts:198-213`——参数化并发安全避免"只读工具与写工具并行"的竞态，nop 全并行依赖工具自身无副作用假设 |
| D7-3 结果回填 | spill（ISpillStore+SPILL_REF）+ hook 可替换（Reenter）+ checkpoint 落账 | post-execute accept/block + additionalContexts + spill-policy（prepend 钉位） | 等价 | nop `AgentToolDispatcher.java:311,481-511`；dsh `spill-policy/src/index.ts:185-211`——**spill 趋同演化**（Claim-Check 模式双方独立实现）；dsh 的 additionalContexts（结果触发上下文注入）nop 无直接对位（Reenter marker 最接近） |
| D7-4 工具调用修复 | ChainRepairer 4 阶段修复链（名称/结构/强转/清理），修不动交安全链拒绝 | 无修复链（INVALID_PREPARED_CALL 即失败） | nop 领先 | nop `ChainRepairer.java:42-80`；dsh 无对位——针对弱 schema 遵从模型（参数强转/名称规范化）的容错 nop 独有 |
| D7-5 权限与沙箱 | 7-checkpoint 纵深（含 pathAccess/writeIntent/postDenialGuard 指纹）+ Docker 沙箱 fail-closed + denial 阈值治理 | pre-execute 审批瀑布（allow/deny/ask）+ confine 沙箱（denial 方言签名）+ SANDBOX_UNAVAILABLE fail-closed | nop 领先 | nop `AgentSecurityConsultation.java:276-284`、`security/`；dsh `tools/src/index.ts:1465-1506`、`sandbox/src/index.ts:90-176`——nop 纵深多三面（路径/写冲突/post-denial）；dsh 的方言签名设计更细但单点审批面窄 |

## ⑤ 语义差异与取舍

- **并发安全的责任方**：nop 假设"工具实现自己保证并发安全"（全并行+超时兜底）；dsh 把并发安全做成工具声明的**参数化谓词**（isConcurrencySafe(args)——同一工具某些参数只读可并行、某些不可）。取舍：dsh 模型表达力强但要求声明纪律；nop 模型零声明成本但竞态风险由工具作者隐性承担。
- **修复的时点取舍**（06 交叉）：nop repairer 在 7-checkpoint **之前**改写调用——审批所见是修复后的参数（一致性正向）；但也意味着修复器有注入面（修复器自身须可信）。dsh 无修复，规避了该信任问题但失去容错。
- **审批的对象粒度**：nop approvalGate 是 7 层中的一层（可与其他层组合短路）；dsh ask 是 pre-execute 的三值之一。语义等价，编排位置不同。
- **spill 触发点差异**：nop 在执行器内联（spillIfOversized 于结果提交路径）；dsh 在 post-execute 监听者（prepend 钉位保证最后收尾）——dsh 的 spill 可被下游 listener 先 settle 的结果生效，nop 的 spill 是固定管线段。
- **code-mode**：dsh 的 run_code 子派发（tools/code-dispatch-log 只改日志副本）是"代码编排工具调用"的形态，nop 无对位（记录为 dsh 特有概念，02 已登记）。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——五个子机制 nop 两个领先（修复/安全纵深）、dsh 两个领先（投影分离/并发调度）、一个等价（回填/spill 趋同）；双方各持有对方缺失的独有机制，综合能力相当。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop 全并行】为 tool.xdef 增加 concurrencySafe 参数化标记（工具级或参数级），AgentToolDispatcher 按标记分组调度（只读并行/写屏障），消除写竞态隐患。
2. 【来源 dsh；针对 nop 文本结果】评估工具结果 value/投影分离（execute 返回结构化值 + render 生成模型可见文本），支撑下游精确审计与 UI 复用（当前 ChatToolResponseMessage 文本即一切）。
3. 【来源 dsh；针对 nop 修复信任面】ChainRepairer 修复前后差异落审计（对位 dsh"参数已落账"不变式），使审批与执行参数一致性可追溯。
4. 【来源 nop；反向记录供 dsh 参考】ChainRepairer 4 阶段修复链可作为 dsh 生态插件（tools/execute wrapper 内参数预处理），补弱 schema 模型场景。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D7 定义）、`02-terminology-map.md`（T13）、`03-flow-agent-loop.md`、`04-extension-capability-matrix.md`、`06-extension-composition.md`
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../engine/AgentToolDispatcher.java`、`engine/AgentToolPlanResolver.java`、`repair/ChainRepairer.java`、`security/`
- dsh：`packages/core/tools/src/{index,schema}.ts`、`packages/core/agent-loop/src/tool-calls.ts`、`packages/spill/spill-policy/src/index.ts`、`packages/sandbox/sandbox/src/index.ts`（`~/ai/deepseek-harness` @ c291e7961a）
