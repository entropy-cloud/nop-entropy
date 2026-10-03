# 2026-10-02 WI5 测试暴露的产品缺陷嫌疑清单（已修复，plan 2306 收口）

> 来源：plan 2297（WI5 nop-wf 引擎面补强）执行期发现。各项均以 trip-wire 测试 + 模型资源固化（记录现状而非错误行为），修复独立立项。

## 1. nop-core GraphBreadthFirstIterator 未把 root 加入 visited（P1 嫌疑）

- Problem：环包含根节点（如起始步骤回退环）的工作流在模型校验期抛 `ArrayIndexOutOfBoundsException`（`DagAnalyzer.checkStartReachable`），友好的 `ERR_WF_GRAPH_CONTAINS_LOOP` 错误不可达（环不含根时校验正常）。缺陷在 nop-core 图迭代器，wf 校验路径只是触发面。
- trip-wire：`TestWfModelParserValidation.testRootCycleCrashesBeforeFriendlyLoopError` + `errRootLoop/v1.xwf`。

## 2. WfModelAnalyzer.checkEnd 的 eventuallyToAssigned 传播缺前置条件（P2 嫌疑）

- Problem：`checkEnd` 无条件标记 `eventuallyToAssigned=true`（缺少 `isNextToAssigned` 前置条件），任何步骤都被视为"最终会到 assigned"→ `ERR_WF_STEP_NOT_ENDABLE` 校验为死代码。
- trip-wire：`testStepNotEndableValidationIsDeadCode` + `errNotEndable/v1.xwf`。
- 影响：不可能结束的流程定义可通过模型校验。


## Fix（2026-10-03 plan 2306 回填）

- **1 GraphBreadthFirstIterator root 未入 visited：`fixed`**。构造器补 `this.set.add(root)`。环含根时 DagAnalyzer.checkStartReachable 不再因 root 重复入队导致 data[index] 越界，友好错误 ERR_WF_GRAPH_CONTAINS_LOOP（带 loopEdges 定位参数）可达。回归：TestWfModelParserValidation.testRootCycleCrashesBeforeFriendlyLoopError 翻转为 testRootCycleRejectedWithFriendlyLoopError（正向错误码+参数断言）。
- **2 checkEnd 死校验：`fixed`**。eventuallyToAssigned 传播补 `isNextToAssigned()` 前置条件（nextToAssigned 在 DAG 构建期 addTransitionNext 中设置，修复自洽）。ERR_WF_STEP_NOT_ENDABLE 不再是死代码。回归：testStepNotEndableValidationIsDeadCode 翻转为 testStepNotEndableRejectedWithFriendlyError（errNotEndable/v1.xwf 解析必须失败且定位 dead 步骤）。▲ 下游：nop-wf-service 138 测试当期跑全绿（含 testPublish 流 _cases）。
  - **全仓验证暴露的合法形态边界（已补）**：首版前置条件误伤了两个合法流程形态——① independent="true" 的独立传阅步骤（loop/v1.xwf 的 kcy 自环）被判不可结束；② 把控制权移交给独立步骤的前驱（cyStart→kcy）。处置：① independent 步骤豁免可结束判定（其语义即"流程结束时允许继续运行"）；② 独立步骤同为传播种子（前驱视同流程责任终止，eventuallyToAssigned 实际承载"最终到达终态（assigned/独立移交）"语义，注释已写明）。errNotEndable 的 dead 步骤仍被正确拒绝，修复语义未弱化。

## Notes For Future Refactors

- （已执行：两个 trip-wire 均已翻转为正向断言。）

## Affected Files

- nop-kernel/nop-core/src/main/java/io/nop/core/model/graph/GraphBreadthFirstIterator.java
- nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/model/analyze/WfModelAnalyzer.java
- 对应 src/test 下 TestWfModelParserValidation
