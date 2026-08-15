# nop-ai-gateway 设计目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 阅读顺序

| 顺序 | 文档 | 职责 |
|------|------|------|
| 1 | `01-architecture.md` | 架构决策、模块边界、数据流、拒绝了什么方案 |
| 2 | `02-account-failover-requirement.md` | 透明账号切换（failover）需求规格：触发条件、切换语义、两种部署形态、并发限流、模型分类路由组 |

## 文档职责

- `01-architecture.md` — 记录 `nop-ai-gateway` 模块的架构决策原则、模块边界、核心数据流转换规则。不包含实现代码。
- `02-account-failover-requirement.md` — 账号级透明 failover + 账号并发限流 + 模型类路由组 + 动态选择策略的需求规格（状态：审查通过，R1-R8 共识达成；§3.7 双向协议转换补全已落地，plan `2026-08-15-0849-1`）。其 §3.7 曾要求双向协议转换补全——这是对 `01-architecture.md`"前端恒 OpenAI"假设的显式偏离，W4 收口后该假设已放开（前端可为任意 dialect）；§4.1 要求 LLM 可靠性子集下沉 nop-ai-core（网关能力级不依赖 nop-ai-agent）。
