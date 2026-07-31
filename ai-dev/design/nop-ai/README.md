# nop-ai 子系统设计文档

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，记录 nop-ai 模块组（nop-ai-core / nop-ai-toolkit / nop-ai-agent 等）的架构决策与使用契约。

## 目录结构

| 文件 | 层级 | 职责 |
|------|------|------|
| `01-file-operator-abstraction-contract.md` | 架构基线 | `IFileOperator` vs `IToolFileSystem` 双抽象边界契约（P2-MA1-012 裁定：保持 + forRemoval=true + 迁移前置条件） |
| `02-code-analyzer-module-boundary.md` | 架构基线 | nop-ai-code-analyzer 模块职责边界（P3-MA1-014 裁定：不拆模块 + maven 包内部子域 + git 包公共面 + nop-shell 保留） |

## 阅读顺序

1. **必读**：`01-file-operator-abstraction-contract.md` — 触碰任何 nop-ai 文件操作抽象（`IFileOperator` / `IToolFileSystem` / `FileToolBizModel` / `DslToolImpl`）前必须先读。
2. **必读**：`02-code-analyzer-module-boundary.md` — 触碰 `nop-ai-code-analyzer` 模块边界（maven/git/stats 包归属、nop-shell 依赖）前必须先读。
3. **按需深入**：nop-ai-agent 子系统的引擎/DSL/会话设计见 `../nop-ai-agent/`（独立子系统目录）。
4. **扩展方向**：新的 nop-ai 架构决策按 `00-design-writing-guide.md` 模板追加到本目录（两位数字编号递增）。

## 职责边界

- 本目录只记录 nop-ai **模块组共用**的跨模块架构决策与契约。
- 单一模块内部或单一子系统的设计归对应子系统目录（如 `nop-ai-agent/`、`nop-ai-shell/`、`nop-ai-gateway/`）。
- 使用层面规范（API、约定）归 `docs-for-ai/`；本目录与 `docs-for-ai/` 冲突时，`docs-for-ai/` 为使用面 source of truth，本目录为决策面 source of truth。
