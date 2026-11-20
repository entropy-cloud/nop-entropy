# nop-git 设计文档目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

nop-git 是 Nop 平台的纯 Java Git 工具模块：基于 JGit 提供 (1) 供平台内直接集成的 Java API 门面，(2) 一个与原生 `git` 命令行语义对齐的 CLI（可经 PATH 覆盖供 AI 编程工具当作系统 Git 使用），(3) 一个配置驱动的 AI Git 操作权限策略引擎，在 CLI 与 API 两条路径上共用。

## 文档结构与阅读顺序

| 文档 | 层级 | 职责 |
|---|---|---|
| [00-vision.md](00-vision.md) | **Vision**（必读） | 产品定位、成功标准、non-goals、自完备裁定 |
| [01-architecture-baseline.md](01-architecture-baseline.md) | **Architecture Baseline**（必读） | 模块拓扑、分层、核心对象职责契约、依赖方向 |
| [02-cli-compat-design.md](02-cli-compat-design.md) | 子系统专题 | git CLI 兼容契约：子命令优先级、输出格式对齐、AI 工具兼容性 |
| [03-policy-engine-design.md](03-policy-engine-design.md) | 子系统专题 | 权限策略引擎：三态裁决、五维规则、observe/enforce、审计 |
| [04-api-integration-design.md](04-api-integration-design.md) | 子系统专题 | Java API 集成契约 + 可选 HTTP Git 服务层 |

必读路径：`00-vision.md` → `01-architecture-baseline.md`；按需深入其余专题。

实现排期见 roadmap：`ai-dev/backlog/nop-git-roadmap.md`。
