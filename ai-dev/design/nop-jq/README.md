# nop-jq Design

## Context

nop-jq 是 Nop 平台的自研 JSON 查询引擎，支持 jq 风格语法和 JsonPath 标准。位于 `nop-kernel/nop-jq/`，属于基础内核层。

本模块替代 nop-core 中对 Jayway JsonPath 的依赖，提供统一的 JSON 查询/转换能力。

## Structure

| Document | Layer | Purpose |
|----------|-------|---------|
| `00-vision.md` | Vision | 产品定位、成功标准、non-goals |
| `01-architecture-baseline.md` | Architecture Baseline | 系统分层、核心对象职责、模块边界 |

## Reading Order

1. `00-vision.md` — 为什么做、做什么、不做什么
2. `01-architecture-baseline.md` — 怎么分层、核心对象是谁、模块间依赖方向

## Convention

本目录按 AGE (Attractor-Guided Engineering) owner-doc 模式组织。
