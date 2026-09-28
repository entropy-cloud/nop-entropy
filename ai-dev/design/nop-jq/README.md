# nop-jq Design

## Context

nop-jq 是 Nop 平台的自研 JSON 查询引擎，支持 jq 风格语法和 JsonPath 标准。jq 引擎位于顶层模块组 `nop-jq/`（parent=nop-entropy，2026-09-28 自 nop-kernel 迁出）；JSONPath 求值与 JPath SPI 桥接拆分为 nop-kernel 内的 `nop-kernel/nop-jpath/`。

本模块族替代 nop-core 中对 Jayway JsonPath 的依赖，提供统一的 JSON 查询/转换能力。

## Structure

| Document | Layer | Purpose |
|----------|-------|---------|
| `00-vision.md` | Vision | 产品定位、成功标准、non-goals |
| `01-architecture-baseline.md` | Architecture Baseline | 系统分层、核心对象职责、模块边界 |
| `02-jq-complete-design.md` | Design | jq 1.7.1 引擎最终设计（语义、执行模型） |
| `03-jpath-bridge-contract.md` | Contract | nop-core JPath 门面的 SPI 求值桥接契约 |

## Reading Order

1. `00-vision.md` — 为什么做、做什么、不做什么
2. `01-architecture-baseline.md` — 怎么分层、核心对象是谁、模块间依赖方向
3. `02-jq-complete-design.md` / `03-jpath-bridge-contract.md` — 按任务需要深入

## Convention

本目录按 AGE (Attractor-Guided Engineering) owner-doc 模式组织。
