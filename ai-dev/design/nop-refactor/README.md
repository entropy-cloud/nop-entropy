# Nop Refactor — 代码修改工具链设计（AI-First）

> 状态：Vision 层 active（2026-09-25 用户裁定）；Architecture Baseline 为目标基线（实现未启动）
> 范围：nop 平台"改代码"能力（refactor / codemod / 语义级重构）的定位、接口形态与架构方向

## 文档结构

| 文档 | 层级 | 内容 |
|------|------|------|
| [00-vision.md](./00-vision.md) | **Vision** | AI 一等用户原则、GraphQL-first 非 LSP、机器可核验反馈、自完备能力源、复杂度预算 + 性价比门、按 AI 使用价值收敛的能力范围（P0 codemod / P1 rename，结构变换类 out）、显式 non-goals、成功标准 |
| [01-architecture-baseline.md](./01-architecture-baseline.md) | **Architecture Baseline** | 读/查/改三件套定位、模块拓扑、GraphQL 契约面、结果反馈载荷契约、复杂度预算与性价比门、复用与依赖边界 |

## 阅读顺序

必读：`00-vision.md` → `01-architecture-baseline.md`。

## 职责边界

- 本目录裁定：代码修改能力的**接口形态**（面向 AI 的 GraphQL 操作）、**反馈契约**（self-verification 载荷）、**模块边界**（core + 语言适配 + graphql 面）。
- 本目录不裁定：具体重构操作的算法与清单（逐操作另行立项 design/plan）、nop-lint 既有设计（见 [../nop-lint/](../nop-lint/README.md)）。

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。
