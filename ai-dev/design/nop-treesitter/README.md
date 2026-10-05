# nop-treesitter Design

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 阅读顺序

1. **必读路径**：`01-bash-shell-analysis.md`

## 文档结构

| 文件 | 层级 | 职责 |
|------|------|------|
| `01-bash-shell-analysis.md` | Subsystem | bash grammar 装配契约 + shell 分析语义层的六项能力、证明边界与类型级隔离 |
| `02-error-recovery-termination.md` | Subsystem | GLRParser 错误恢复必须终止：stack summary 的生命周期是恢复的关键状态、backstop 只是症状处理器、三个仍未修复的复现输入 |

## 职责边界

- 本目录只记录架构决策和使用契约：模块归属、"可证明 / 不可证明"的表达形态、
  白名单封闭性、能力 1/2 的类型级隔离、heredoc 提取的实现路径、动态构造点的
  枚举范围、文件规模裁定
- 具体类名、方法签名、字段定义与伪代码是代码层面的唯一事实，见源码与
  `ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`（实测指纹、节点/
  field 契约清单）
- 运行时实现进度、corpus 分歧裁定、执行历史见
  `ai-dev/plans/nop-treesitter/` 与 `ai-dev/logs/`

## 与其他 owner-doc 的边界

| 关注点 | 归属 |
|---|---|
| tree-sitter 运行时的**使用**方式（加 blob、注册 provider、GraphQL 调用、语义层能力） | 本目录 |
| bash **语言**的语法模型与命令实现（`io.nop.ai.shell` 的 Tier 1/2/3 矩阵） | `ai-dev/design/nop-ai-shell/` |
| agent 侧"这条命令危不危险"的**判定** | 不属于本目录。语义层只产出结构化事实与证明状态，判定是 agent guardrail 的职责 |
