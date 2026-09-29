# nop-bytecode 设计目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载字节码分析通道（源码通道 nop-lint 之外的第二条编码期防线通道）的架构决策与契约。

## 结构与层级

| 文档 | 层级 | 状态 |
|---|---|---|
| [00-overview.md](./00-overview.md) | **Vision + Architecture Baseline 双职**（定位 / 原则 / 架构分层 / 拒绝项） | active（底座 ADR 落档，2026-09-29） |
| [gap-ledger.md](../../../nop-bytecode/docs/gap-ledger.md) | 缺口账本（行级缺口归属与执行状态的唯一动态载体） | active（已迁至 nop-bytecode/docs/，2026-09-29 plan 02） |
| [substrate-adjudication.md](./substrate-adjudication.md) | 底座终裁 ADR（Wave 0 item 2：A 采纳 / B 拒绝 / C 限 Wave 5） | active（2026-09-29，POC 数据背书） |

## 阅读顺序

- **必读路径**：00-overview → [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md)（执行状态唯一动态块）→ [gap-ledger](../../../nop-bytecode/docs/gap-ledger.md)（行级缺口状态，模块 docs）
- **按需深入**：[substrate-adjudication](./substrate-adjudication.md)（底座终裁与 Wave 1 内核裁定输入）；平台级约束 [self-contained-design.md](../self-contained-design.md)（§2.5 外部能力使用形态的依据）；姊妹通道 [nop-lint design](../nop-lint/README.md)（两通道分工与互不修改原则）

## 待落档（按 roadmap 波次）

- 内核设计（CFG / 抽象解释 / 分析器契约）— Wave 1 后按需拆分

## 职责边界

本目录不承载：执行历史（→ logs/plans）、方案对比过程（→ analysis）、进度状态（→ roadmap 动态块）。两通道（源码 nop-lint / 字节码本通道）的规则资产相互独立，本文档族不修改 nop-lint 任何既有内容。
