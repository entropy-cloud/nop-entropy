# pi-D7 工具系统对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D7 子机制 D7-1..D7-5）、02-terminology-map.md（T13）; 机制事实引用 03 §2.3（P8 链）、04（③3.3 pi 能力）、06（tool_call 双通道裁决）
> Owner: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`

## ① 结论摘要

- 总裁定：**nop 领先**。nop 的工具调用修复链（ChainRepairer 4 阶段）与安全纵深（7-checkpoint+沙箱）为 pi 全无；pi 的独特资产是 deferred tools（结果触发工具集演进）与文件变更串行队列，但单点优势不足以翻转安全与修复两面的缺失。
- 关键差异 1（D7-5）：pi 核心无任何权限/审批/沙箱机制（权限留白给扩展 tool_call block 是显式设计）；nop 7-checkpoint 纵深+Docker 沙箱 fail-closed。
- 关键差异 2（D7-4）：pi 仅 prepareArguments 单点垫片（校验前兼容转换，`types.ts:394-398`）；nop 4 阶段修复链（名称/结构/强转/清理）。
- 关键差异 3（D7-2）：pi 双级调度控制（config toolExecution+单工具 executionMode 覆盖，默认并行）；nop 全 fan-out 无标记。
- 可吸收增量建议一句话：pi 的 deferred tools 与文件变更队列是值得 nop 吸收的两个独有概念（见 ⑥）；反向记录 pi 无沙箱的服务端部署风险。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D7 报告 ② 节共享：tool.xdef DSL 声明；`AgentToolDispatcher` 标签过滤+全 fan-out（300s 超时）；spill+Reenter 回填；`ChainRepairer` 4 阶段修复（`repair/ChainRepairer.java:42-80`）；7-checkpoint 安全链+`ISandboxBackend` fail-closed+路径检查/写冲突/post-denial（`engine/AgentSecurityConsultation.java:276-284`）。

## ③ pi 侧机制与锚点

按 02 T13、03 §2.3（P8）、04 ③3.3，对比增量：

- **声明与 schema**（D7-1）：`AgentTool<TParameters extends TSchema, TDetails>`——TypeBox schema（继承 pi-ai Tool 的 name/description/parameters）+label+`prepareArguments?`（校验前兼容垫片，:394-398）+`execute(toolCallId, params, signal, onUpdate)`+`executionMode?`（`packages/agent/src/types.ts:386-409`）；`AgentToolResult{content[], details, usage?, addedToolNames?, terminate?}`——**约定 throw 表失败**，不在 content 里编码错误（:361-375）；details 是工具自定义结构化详情（部分投影语义）。
- **执行调度**（D7-2）：config `toolExecution` 默认 parallel、单工具 `executionMode:"sequential"` 覆盖（`agent-loop.ts:411-426`）；并行=按源序 start+prepareToolCall 串行 preflight→thunk 推入→Promise.all 并发，end 事件按完成序、toolResult 消息按源序（:489-554）；`withFileMutationQueue` 按 (env,canonical path) Promise 链串行化同文件变更（write/edit/bash 共享，`harness/tools/file-mutation-queue.ts:29-56`）。
- **结果回填**（D7-3）：结果带 `addedToolNames`→`splitDeferredTools` 把"transcript 中被引入但未被调用过"的工具拆入 deferred（Anthropic `defer_loading:true`），实现结果触发工具集演进（`packages/ai/src/utils/deferred-tools.ts:8-39`、`agent-loop.ts:787`）；terminate 全批一致才早停（:582-584）。
- **调用修复**（D7-4）：仅 prepareArguments 垫片；校验失败即 error 结果（`validateToolArguments`，:618）。
- **权限与沙箱**（D7-5）：agent 核心（types/agent-loop/harness tools）无任何 approval/allowlist 机制——权限留白给扩展 tool_call block（官方示例 confirm-destructive.ts/bash-spawn-hook.ts，`examples/extensions/`）；仅 `FileErrorCode.permission_denied` 文件系统错误码（`harness/types.ts:131-139`）；beforeToolCall block 是唯一否决通道（06 ④.1 双通道裁决）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D7-1 声明与 schema | tool.xdef DSL 声明式；结果=文本消息 | AgentTool TypeBox schema 代码式+throw 表失败约定+details 结构化详情+onUpdate 流式进度 | 等价 | nop Owner doc `04-tool-invocation.md`；pi `types.ts:361-409`——声明方式取舍（DSL 契约化 vs 代码灵活）；pi 的 throw 约定与 onUpdate 进度回调是好实践但单点 |
| D7-2 执行调度 | 批内全 fan-out（无标记）；300s 超时 | 双级调度控制（config+单工具覆盖）+文件变更串行队列+完成序/源序事件分离 | 对方领先 | nop `AgentToolDispatcher.java:203-282`；pi `agent-loop.ts:411-554`、`file-mutation-queue.ts:29-56`——pi 的 sequential 覆盖与同文件串行化解决真实竞态；nop 全并行无对位 |
| D7-3 结果回填 | spill+Reenter 替换/marker 注入+checkpoint | deferred tools（addedToolNames 结果触发工具引入，三方独有）+terminate+details | 等价 | nop `AgentToolDispatcher.java:311,430-432`；pi `deferred-tools.ts:8-39`——双方各持独有机制（nop spill/Reenter vs pi deferred/terminate）；spill 是成本治理、deferred 是能力演进，维度不同 |
| D7-4 工具调用修复 | ChainRepairer 4 阶段（名称/结构/强转/清理），修不动交安全链 | 仅 prepareArguments 垫片；校验失败即失败 | nop 领先 | nop `ChainRepairer.java:42-80`；pi `types.ts:394-398`——弱 schema 模型容错 nop 完胜（与 dsh-D7 D7-4 同结论） |
| D7-5 权限与沙箱 | 7-checkpoint 纵深+Docker 沙箱 fail-closed+路径/写冲突/post-denial | **核心无权限层**（留白扩展 block）+无沙箱；file-mutation-queue 是数据一致性非权限 | nop 领先 | pi `harness/types.ts:131-139`（仅文件系统错误码）、`examples/extensions/confirm-destructive.ts`；nop `AgentSecurityConsultation.java:276-284`——服务端部署 pi 需自建全套安全面 |

## ⑤ 语义差异与取舍

- **失败的表达**（02 T13）：pi 约定 throw 表失败（content 只放成功输出）——类型上不可混淆；nop/dsh 允许 error result 内容化。pi 的约定更函数式，但依赖工具作者守约（约定无强制）。
- **deferred tools 的定位**：pi 独有概念（02 已登记）——工具定义随 transcript 演进引入，本质是"上下文相关的工具面裁剪"（省 token+防误用）；nop 的 activeTags 标签过滤是静态裁剪，deferred 是动态裁剪。两者可组合。
- **权限的架构位置**：nop 权限在引擎（7-checkpoint 每工具评估）；pi 权限在扩展（tool_call 事件 veto）；dsh 权限在工具管线（pre-execute waterfall）。三方位=引擎/生态/管线——D2 的组织哲学差异在安全面的投射。
- **修复垫片的语义差**：pi prepareArguments 在 validate 之前（垫片让非法参数变合法再校验）；nop ChainRepairer 在安全检查之前（修复后参数走审批）。两者都改参数，但 pi 垫片不改已验证参数、nop 修复的是未审批参数——审计顺序 pi 更保守。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：nop 领先**——D7-4/D7-5 nop 领先（修复链+安全纵深 pi 全缺），D7-2 对方领先（调度控制），D7-1/D7-3 等价；安全与修复是工具系统的刚性面（企业部署门槛），pi 的 deferred tools 等柔性创新不改变刚性缺口。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 工具面静态裁剪】在 activeTags 标签过滤之上增加 deferred 引入机制：工具结果可声明 addedTools（对位 addedToolNames），下一轮按需激活——降低工具面 token 常驻成本。
2. 【来源 pi；针对 nop 并发竞态】文件类工具（nop 的 path 检查器面）引入按路径串行队列（对位 withFileMutationQueue），与 D7 建议 1 的并发安全标记互补（标记防类竞态、队列防路径竞态）。
3. 【来源 pi；针对 nop 工具契约】throw 表失败约定写入 tool.xdef 工具开发规范（当前 nop 工具错误既可抛也可返回——两种路径并存无约定）。
4. 【来源 nop；反向记录供 pi 参考】pi 服务端部署需自建审批/沙箱/审计面（nop 7-checkpoint 可作设计参考）；nop 的 ChainRepairer 可移植为 pi 的 prepareArguments 增强实现。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D7 定义）、`02-terminology-map.md`（T13）、`03-flow-agent-loop.md`（§2.3）、`04/06` 专项、`dsh-D7-tool-system.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../engine/AgentToolDispatcher.java`、`repair/ChainRepairer.java`、`security/`
- pi：`packages/agent/src/{types,agent-loop}.ts`、`packages/ai/src/utils/deferred-tools.ts`、`packages/agent/src/harness/tools/file-mutation-queue.ts`（`~/ai/pi` @ c49906ec7）
