# nop-task saveState 契约设计

**日期**：2026-09-26
**范围**：`nop-task/nop-task-core`（TaskStepExecution、TaskStepEnhancer）
**状态**：active
**来源**：plan 364 Phase 2 [维度03-05]（ai-dev/audits/2026-09/2026-09-25-1410-deep-audit-nop-task-quality/03-performance.md）

---

## 一、设计结论

1. **per-step `saveState` 契约按 xdef 承诺实现消费（方案 A）**：`TaskStepExecution` 在构建时读取
   `stepModel.getSaveState()`（`Boolean`），显式 `false` 的步骤跳过自身状态行的全部生命周期落盘
   ——ACTIVE 创建（`executeWithParentRt` 步骤启动保存）、挂起点保存、终态保存
   （`saveTerminalStateIfDone`）三个写点全部门控。
2. **语义边界**：未配置（null，缺省）保持既有行为——任务级 `defaultSaveState=true` 时所有步骤照旧落盘；
   显式 `false` 仅表示"该步骤自身状态不落盘"，任务级持久化开关、父步骤（如 loop/seq）自身的进度回写不受影响。
   挂起恢复对显式 `false` 的步骤退化为重新执行（无挂起点快照可续），这是 xdef 承诺的自然推论。
3. **不实现 load 侧门控**：显式 `false` 的步骤在此变更后不再产生自己的状态行；resume 加载
   （`loadStepState` 按 taskInstanceId+stepPath 定位）自然找不到行而走新建路径。配置变更前遗留的
   历史行可能仍被加载，属部署间配置漂移场景，不引入额外防御。

## 二、背景与动机

`task.xdef` 头注释与 `saveState` 属性文档把 per-step 持久化写成公开能力，但全仓唯一运行时开关是
任务级 `defaultSaveState`（`TaskFlowManagerImpl.newTaskRuntime` 的 store 选择）；`stepModel.getSaveState()`
此前零消费者（`ReflectionTaskStepBuilder` 只写不读）。契约漂移导致：写放大不可按步收敛、
用户配置 per-step saveState 无任何效果（audit 维度03-05，P2/契约漂移）。

## 三、核心设计

- 消费点在 `TaskStepExecution`（每步骤生命周期保存的唯一管理者），经构造器新参
  `persistState`（`Boolean`）注入，`persistStepState()` 谓词门控；未配置不改变任何行为（向后兼容）。
- 旧构造器签名保留委托新签名（缺省 null），既有调用方与测试替身无需改动。

## 四、拒绝了什么

- **方案 B（从 task.xdef 删除 per-step 承诺）**：task.xdef 属框架核心保护区（AGENTS.md plan-first），
  且删除承诺后 `saveState` 属性成为"接受配置但声明无效"的新形态静默 no-op（违反 No-Silent-No-Op 精神）；
  若连带删除属性会破坏存量带 `saveState` 的任务模型加载。拒绝。
- **在 `ITaskStepRuntime` 接口上加 setPersistState / 修改 `newStepRuntime` 签名**：接口是核心 SPI，
  签名变更波及全部测试替身；而消费点唯一（TaskStepExecution），构造器注入即可，无需接口面变更。拒绝。
- **门控结构步骤（loop/seq）自身的进度回写**：那些 `stepRt.saveState()` 保存的是父步骤自己的状态行，
  由父步骤的 saveState 语义管辖；本设计只消费"本步骤模型"的 saveState，跨层代管会造成语义混淆。拒绝。

## 五、与已有设计的关系

- 持久化边界与 resume 语义：`docs-for-ai/03-modules/nop-task.md`（owner 文档）。
- 持久化性能（往返次数/O(K²) 序列化）优化属 plan 364 Deferred 性能组，本设计不处理量化优化。
