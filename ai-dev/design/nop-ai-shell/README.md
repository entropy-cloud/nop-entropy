# nop-ai-shell Design

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 阅读顺序

1. **必读路径**：`00-vision.md` → `01-architecture-baseline.md` → `02-io-and-pipeline.md`
2. **按需深入**：`03-executor-and-async.md` → `04-bash-syntax.md`

## 文档结构

| 文件 | 层级 | 职责 |
|------|------|------|
| `00-vision.md` | Vision | 产品定位、应用场景、设计约束 |
| `01-architecture-baseline.md` | Architecture Baseline | 系统分层、核心对象职责、模块边界 |
| `02-io-and-pipeline.md` | IO 与管道 | 流模型设计、管道接线、流式执行 |
| `03-executor-and-async.md` | 执行器与异步 | 执行引擎、Background、错误传播、外部 shell 回退 |
| `04-bash-syntax.md` | 语法模型 | AST 模型、解析器集成、表达式树设计 |

## 职责边界

- 本目录只记录架构决策和使用契约
- 具体命令实现（`CdCommand`、`EchoCommand` 等）是代码细节，不在此处记录
