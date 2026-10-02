# 2026-10-02 WI5 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2297（WI5 nop-wf 引擎面补强）执行期发现。各项均以 trip-wire 测试 + 模型资源固化（记录现状而非错误行为），修复独立立项。

## 1. nop-core GraphBreadthFirstIterator 未把 root 加入 visited（P1 嫌疑）

- Problem：环包含根节点（如起始步骤回退环）的工作流在模型校验期抛 `ArrayIndexOutOfBoundsException`（`DagAnalyzer.checkStartReachable`），友好的 `ERR_WF_GRAPH_CONTAINS_LOOP` 错误不可达（环不含根时校验正常）。缺陷在 nop-core 图迭代器，wf 校验路径只是触发面。
- trip-wire：`TestWfModelParserValidation.testRootCycleCrashesBeforeFriendlyLoopError` + `errRootLoop/v1.xwf`。

## 2. WfModelAnalyzer.checkEnd 的 eventuallyToAssigned 传播缺前置条件（P2 嫌疑）

- Problem：`checkEnd` 无条件标记 `eventuallyToAssigned=true`（缺少 `isNextToAssigned` 前置条件），任何步骤都被视为"最终会到 assigned"→ `ERR_WF_STEP_NOT_ENDABLE` 校验为死代码。
- trip-wire：`testStepNotEndableValidationIsDeadCode` + `errNotEndable/v1.xwf`。
- 影响：不可能结束的流程定义可通过模型校验。

## Notes For Future Refactors

- 两项修复后对应 trip-wire 测试应改为正向断言（友好错误码 / 校验生效），当前固化的是缺陷现状。
