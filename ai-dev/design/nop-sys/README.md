# nop-sys 设计文档

> Status: active
> Created: 2026-07-07

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，用于承载 `nop-sys` 子系统的架构决策。当前覆盖 `sys-event` 重构设计与紧凑扩展字段设计，后续可继续纳入 sequence、code-rule、lock 等专题。

## 文档结构与阅读顺序

### 必读路径

1. `sys-event-architecture.md`
   - `sys-event` 的架构基线：广播事件与普通事件拆分存储、可靠顺序广播、普通事件按 `partitionIndex` 并行消费、与 `nop-batch` 的复用边界

2. `compact-ext-field-design.md`
   - `nop-sys` 紧凑扩展字段的配置表、ORM 集成契约与使用边界

### 按需深入

- `docs-for-ai/03-modules/nop-sys.md`
  - 使用者视角的 `nop-sys` 能力说明
- `docs-for-ai/03-modules/nop-batch.md`
  - `nop-batch` 的 chunk / partition / 并发执行模型
- `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java`
  - 当前 `sys-event` 发送与消费实现锚点

## 职责边界

- `sys-event-architecture.md` 回答“`sys-event` 应该如何分层、如何持久化、如何保证广播不遗漏、普通事件如何并发消费”。
- `compact-ext-field-design.md` 回答“紧凑扩展字段如何建模、如何与 ORM 集成、业务如何使用”。
- 本目录不记录实现过程、迁移日志、测试结果；这些进入 `ai-dev/logs/`、`ai-dev/plans/` 或 `ai-dev/analysis/`。
