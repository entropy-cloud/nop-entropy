# nop-plugin 设计文档索引

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

记录 `nop-plugin` 模块（`nop-core-framework/nop-plugin`：api / manager / support）的架构决策与使用契约。

本目录聚焦**增强设计**：在现有 plugin 系统（load/unload + plugin=子容器模式）基础上，吸收 Cordis 时空可组合性思想（加载/激活两态、revertible effects、reactive coeffects），补齐条件激活、effect 系统化、HMR。

本目录只记录架构决策层面（选了什么、为什么、拒绝了什么），不记录代码实现细节——源码是代码层面的唯一事实。

## 文档结构

| 文档 | 层级 | 职责 |
|------|------|------|
| `00-vision.md` | Vision | 增强目标、不可违反约束、non-goals、成功标准 |
| `01-architecture-baseline.md` | Architecture Baseline | 状态模型、effect 系统化、coeffect 条件激活、HMR、核心接口契约、模块边界 |
| `02-dsh-usage-coverage.md` | 评估 | 对照 dsh plugin 用法，评估 nop-plugin 覆盖度（支持/差距/Nop 优势） |

## 阅读顺序

1. 必读：`00-vision.md` —— 明确增强的范围与边界。
2. 必读：`01-architecture-baseline.md` —— 核心架构决策与接口契约。
3. 按需：源码锚点见各文档末尾，可直接跳转到 `nop-core-framework/nop-plugin/` 对应实现。

## 关联设计

- `../nop-ioc/bean-dependency-semantics.md` —— plugin 的子容器复用 IoC 的 bean 依赖语义。
- `../xlang-scope-access-design.md` —— scope 访问机制。
