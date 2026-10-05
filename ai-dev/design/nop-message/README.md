# nop-message 设计文档目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 文档结构

| 文档 | 层级 | 职责 |
| --- | --- | --- |
| `message-contract-design.md` | Architecture Baseline | SPI 消息契约：能力声明机制、信封元数据契约、选项字段演进规则（D1/D2/D4/D8） |
| `backend-semantics-design.md` | Architecture Baseline | 后端行为语义：seek 起点、返回协议、DLQ 分层、身份模型、错误码、模块处置、事务语义（D3/D5/D6/D7/D9/D10/D11） |

## 阅读顺序

1. 必读：`message-contract-design.md`（先理解能力声明与信封契约，任何后端实现/新增工作都受其约束）
2. 必读：`backend-semantics-design.md`（再理解各行为域的显式拒绝与分层归属）

## 层级说明

当前本目录仅有 Architecture Baseline 层两篇。**Vision 层暂缺，延至 nop-message owner doc 阶段（roadmap WI34）补齐**——届时产品定位、成功标准与 non-goals 随 owner doc 落地（docs-for-ai 03-modules 下的 nop-message 文档，WI34 建立后此处更新为链接）。

## 职责边界

- 本目录只记录消息抽象层（`io.nop.api.core.message` SPI + `nop-message` 各后端）的架构决策与使用契约。
- 实现细节见源码；平台使用规范见 `docs-for-ai/`（WI34 起建立消息层 owner doc）。
