# nop-lint 设计文档

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。
> 执行状态不在此跟踪——唯一动态状态块是 [ai-dev/backlog/nop-lint-roadmap.md](../../backlog/nop-lint-roadmap.md)。

## 层级声明

必备两层（`00-design-writing-guide.md`）在本目录的承载：

| 层级 | 承载文档 | 说明 |
|------|---------|------|
| **Vision**（愿景/原则层） | [00-overview.md](./00-overview.md) §1–§2 | 设计目标（可验收口径）、设计原则（DSL 独立性/xscript/YAML 表示）、显式 non-goals（Python/taint/CPD 等排除见各对标文档） |
| **Architecture Baseline**（架构基线层） | [00-overview.md](./00-overview.md) §3 + [01-pattern-dsl.md](./01-pattern-dsl.md) §1/§4 | 分层架构、模块划分与依赖方向、Pattern 引擎决策（SourcePatternCompiler vs TSQuery） |

其余为专题层（Pattern DSL、规则库、执行引擎、三份能力对标、xscript、迁移、抑制、xdef 元模型、性能档位）。

## 阅读顺序

1. **必读路径**：`00-overview.md` → `01-pattern-dsl.md` → `08-migration.md`（阶段/依赖矩阵）→ [backlog roadmap](../../backlog/nop-lint-roadmap.md)（执行状态）
2. **按需深入**：写规则 → 02/09/10；做引擎 → 03/04/07/11；对标迁移 → 05/06
3. **索引与单一事实点**：[00-nop-lint-design.md](./00-nop-lint-design.md)（快速导航 + 权威来源表）

## 各文档职责边界

| 文档 | 职责（唯一权威点） |
|------|------------------|
| 00-overview | 目标/原则/架构概览（Vision + Baseline） |
| 01-pattern-dsl | 模块划分、规则 DSL 示例、Pattern 语法、编译管线、LintNode 契约、XNode 匹配语义 |
| 02-rule-library | 首批规则、规则继承、**需求追溯表**（现有检查机制 → 迁移去向） |
| 03-execution-engine | 执行流程、增量、Maven/IoC/GraphQL/CI 集成、autofix 安全、RuleTester |
| 04-ast-grep-alignment | ast-grep 算法对标（meta-var/lockstep/省略号/严格度/关系/组合/Fix） |
| 05-eslint-alignment | ESLint 能力对标（Visitor/Scope/CodePath/修复/抑制） |
| 06-pmd-errorprone-alignment | PMD/EP 对标、类型推导 L1–L4、JavaParser 复用、覆盖 manifest、checkstyle/pmd 迁移 |
| 07-xscript-engine | xscript API 契约、安全模型、**deadline 执行器技术路线决策** |
| 08-migration | 关键决策、阶段划分、**分析器 × Phase 依赖矩阵**、竞争优势口径 |
| 09-suppression | 四层抑制机制、baseline 生命周期 |
| 10-xdef-metamodel | 规则 DSL 的 **xdef 元模型**（字段/枚举权威）与加载管线 |
| 11-performance-profiles | **性能唯一权威**：档位/成本模型/缓存/降级阶梯 |

## 修订约定

- 设计文档不携带执行历史与评审过程叙事（历史追溯用 git）；跨文档一致性契约见 10 §5 与 11 §7
- 修改任何文档后运行 `node ai-dev/tools/check-doc-links.mjs --strict`
