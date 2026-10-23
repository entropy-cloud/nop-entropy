# concurrency 设计文档（平台并发设施）

> Status: active
> Created: 2026-10-24

本目录承载 nop 平台**并发基础设施**（`io.nop.commons.concurrent` 及其 executor 家族）的架构决策与使用契约。本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 层级

### Vision 层

- 无独立 vision 文档：平台并发设施的定位与不可违反约束由源码包结构 + 各分项设计的「设计结论」节承载；当本目录文档数超过 3 篇时再拆分独立 Vision 层。

### Architecture Baseline 层

- `periodic-task-insulation.md` — 周期任务异常绝缘层（重臂 vs kill+alarm 选型、返回 future 终态 × isDone 守卫 × 重臂三相容、失败治理四件套[计数/退避/日志抑制/升级]、拒绝路径、API source-compat 形态与 C3-016 边界、MA-06 看门狗前置裁定、30 点表载体覆盖矩阵）

## 阅读顺序

1. `periodic-task-insulation.md`（当前唯一分项设计；新增分项设计按编号或主题追加于此表）
2. 实现锚：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（`IScheduledExecutor` javadoc 为周期调度成文契约的代码侧锚点）

## 职责边界

- 本目录：并发设施的**决策与契约**（为什么这样设计、语义是什么、拒绝了什么）
- `docs-for-ai/`：并发设施的**使用知识**（怎么用）；两者冲突时按 `ai-dev/design/README.md` 的 Precedence Model 处理
- 过程性审计证据（30 点表原始判定、单元审计详情）留在 `ai-dev/audits/`，本目录只保留最终裁定
