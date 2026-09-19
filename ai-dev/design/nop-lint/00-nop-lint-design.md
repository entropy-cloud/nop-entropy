# Nop Lint — 设计文档索引

> 日期: 2026-09-19（修订 2026-09-20）
> 状态: 设计基线草案（执行尚未开始，状态跟踪见 [backlog roadmap](../../backlog/nop-lint-roadmap.md)）
> 目录说明与层级声明: [README.md](./README.md)

## 文档结构

| 文档 | 内容 |
|------|------|
| [README.md](./README.md) | 目录结构、阅读顺序、职责边界、AGE 声明 |
| [00-overview.md](./00-overview.md) | 设计目标、设计原则、架构概览（Vision + Architecture Baseline） |
| [01-pattern-dsl.md](./01-pattern-dsl.md) | 核心模块、Pattern DSL、SourcePatternCompiler、LintNode 门面、XNode 匹配语义 |
| [02-rule-library.md](./02-rule-library.md) | 规则库（首批 20 条分 Phase 交付）、x:extends 规则继承、需求追溯表 |
| [03-execution-engine.md](./03-execution-engine.md) | 执行引擎、增量解析、CI 输出、autofix 安全、RuleTester |
| [04-ast-grep-alignment.md](./04-ast-grep-alignment.md) | ast-grep 能力对标：Pattern 编译、Meta-变量、严格度、关系/组合规则、Fix |
| [05-eslint-alignment.md](./05-eslint-alignment.md) | ESLint 能力对标：Visitor、Scope、Code Path、修复、抑制、TS/React |
| [06-pmd-errorprone-alignment.md](./06-pmd-errorprone-alignment.md) | PMD/ErrorProne 对标、类型推导 L1–L4、复用 nop-java-parser、覆盖 manifest、checkstyle 迁移 |
| [07-xscript-engine.md](./07-xscript-engine.md) | xscript 引擎：API 契约、安全模型、deadline 执行器技术路线 |
| [08-migration.md](./08-migration.md) | 关键决策、阶段划分（Phase→Wave 映射）、依赖矩阵、竞争优势口径 |
| [09-suppression.md](./09-suppression.md) | 抑制机制：内联注释、@SuppressWarnings、exemption、baseline |
| [10-xdef-metamodel.md](./10-xdef-metamodel.md) | 规则 DSL 的 xdef 元模型：真实 xdef 语法（check-mutex/parser-class）、x:extends 继承 |
| [11-performance-profiles.md](./11-performance-profiles.md) | 性能权威：fast/standard/deep 档位、成本模型、缓存、降级阶梯 |

## 快速导航

- **设计目标/架构** → [00-overview.md](./00-overview.md)
- **Pattern 语法与编译** → [01-pattern-dsl.md](./01-pattern-dsl.md)
- **ast-grep / ESLint / PMD+ErrorProne 对标** → 04 / 05 / 06
- **类型推导（L1–L4）与 JavaParser 复用** → [06-pmd-errorprone-alignment.md](./06-pmd-errorprone-alignment.md) §4.6–§6
- **xscript** → [07-xscript-engine.md](./07-xscript-engine.md)
- **阶段划分与依赖矩阵** → [08-migration.md](./08-migration.md)；**执行状态** → [backlog roadmap](../../backlog/nop-lint-roadmap.md)
- **抑制/baseline** → [09-suppression.md](./09-suppression.md)
- **规则测试（RuleTester）** → [03-execution-engine.md](./03-execution-engine.md) §4
- **规则 DSL 元模型（xdef）** → [10-xdef-metamodel.md](./10-xdef-metamodel.md)
- **性能档位/缓存/降级（唯一权威）** → [11-performance-profiles.md](./11-performance-profiles.md)
- **现有检查机制迁移底账** → [02-rule-library.md](./02-rule-library.md) §3

## 权威来源（单一事实点）

| 关注点 | 权威文档 |
|--------|---------|
| 执行状态 / work items / 里程碑 | [ai-dev/backlog/nop-lint-roadmap.md](../../backlog/nop-lint-roadmap.md) |
| 阶段划分 / 分析器 × Phase 依赖矩阵 | 08-migration.md |
| 性能/档位/缓存/降级 | 11-performance-profiles.md |
| 规则 DSL 结构（字段/枚举） | 10-xdef-metamodel.md（01 示例须与之一致） |
