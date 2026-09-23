# Nop Lint — 迁移路径与竞争优势

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> **职责边界**：本文档是阶段划分与**分析器 × Phase 依赖矩阵**的唯一权威；**执行状态跟踪的唯一权威是 [ai-dev/backlog/nop-lint-roadmap.md](../../backlog/nop-lint-roadmap.md)**（work items / 里程碑 / 验收闭环都在 roadmap，本文不携带执行状态）。其他文档中的 Phase 标记（🔧 Phase N）必须与本文档一致；类型推导的层级（L1–L4）见 `06-pmd-errorprone-alignment.md` §4.6，其与 Phase 的映射以本文档为准。

## 0. 关键决策

| 决策 | 结论 | 理由 |
|------|------|------|
| **Pattern 引擎** | 新建 `SourcePatternCompiler`：用 TSParser 把 pattern 代码片段解析成目标语言 CST，再做 meta-var 树匹配。**不是**基于 TSQuery（TSQuery 是 S-expression 查询语言，不支持 `$`/`$$$`） | TSQueryParser 明确拒绝量词，无 meta-var 概念 |
| **类型推导** | Phase 1: 仅声明类型提取（轻量）；Phase 2: 复用 `nop-utils/nop-java-parser`（JavaParseTool + symbol-solver）和 `nop-ai/nop-ai-skills/nop-ai-code-analyzer`（JavaParserBuilder 的 CombinedTypeSolver + MavenModule jar 加载、MavenProject） | 这些模块已存在，勿重复造轮子 |
| **XML 规则** | 不引入 tree-sitter XML grammar（当前只有 json/java/js/python/ts/tsx）。XML 规则走 Nop 自有 XNode 解析器，Pattern DSL 对 XNode 树做结构匹配（语义契约见 01 §3.5） | 发挥 Nop 平台优势，避免 grammar 维护成本 |
| **语言范围** | 源码语言只做 Java + TypeScript/TSX；XML 模型规则走 XNode 引擎（language 枚举含 XML，但解析层不是 tree-sitter）。Python 移出计划（backlog） | 需求明确只有这些 |
| **TS 优先级** | TypeScript 提前到 Phase 2（与 Java 类型推导同期），tsc bridge 需处理 program 缓存与降级 | TS 是核心需求，不能压到末尾 |
| **复合规则** | 单层 `any`（OR）提前到 Phase 1（旗舰规则 nop-no-raw-exception 需要）；`all`/`not`/`matches`（递归）在 Phase 2 | OR 是 Phase 1 十条核心规则的最小需求 |
| **xscript 超时** | 全局 executor 包装路线（零平台改动；07 §4 三路线决策） | nop-xlang 是 Protected Area，避免 plan-first 平台改造 |
| **最小 CLI 提前** | `nop-lint check` + console 输出从 Phase 4 提前到 Wave 3（roadmap） | Phase 1–2 交付的规则需要真实执行面 dogfood，避免"双份维护、单侧生效"窗口 |

## 1. 阶段划分（执行状态见 roadmap）

> 原"4 Phase / 14 周"的日历口径**废止**：对照 nop-treesitter 的 item 节奏（每 item 一个专项 plan + closure audit），本项目的合理估算为 **24–36 周（单 agent，不顺）**。阶段（Phase）保留为**能力分层**概念，执行顺序与状态跟踪按 roadmap 的 Wave/item 组织。

| Phase | 能力主题 | roadmap Wave | 核心内容（详单见 roadmap items） | 现实工期区间 |
|-------|---------|-------------|--------------------------------|------------|
| Phase 1 | 核心引擎 + 基础规则 | Wave 1–3 | SourcePatternCompiler（编译管线 + meta-var + lockstep + 省略号回溯 + 严格度）、lint-rule.xdef + register-model + RuleDslParser、LintEngine + RuleTester、fast/standard 档位 v1、LintNode、xscript v1（含 deadline 执行器）、EditCalculator、抑制 v1、Java 适配、L1、10 条核心规则（含吸收 3 条现有 ast-grep 规则）、**最小 CLI**、benchmark 基线 | 7–11 周 |
| Phase 2 | TypeScript + 类型推导 + 约束系统 | Wave 4 | TS/TSX 适配、tsc bridge、XNode Pattern 引擎、约束求值器、关系规则、复合规则、autofix、L2 solver + ASTMapping（边界消解见 06 §6.3）、baseline v2、check-\*.mjs 逐脚本迁移（manifest 驱动）、PMD/EP manifest 首版、质量/安全/XNode 规则 9 条 | 8–14 周 |
| Phase 3 | 数据流 + 完整规则库 | Wave 5 | 数据流 + 常量传播、deep 档、MetricsEvaluator、Scope 分析、L3/L4、规则库 48+（含反模式 8 条枚举清单）、ESLint 数据流规则移植、PMD/EP P0/P1 批量 | 6–10 周 |
| Phase 4 | 生态集成 | Wave 6 | maven-plugin、GraphQL（含安全边界，03 §2.3）、CLI 完整化、SARIF 等输出格式、checkstyle/pmd 迁移 + 并行期、编辑器集成、规则目录生成器、规则版本策略收口、CI 缓存工件化 | 3–4 周 |

## 2. 分析器可用性 × Phase 依赖矩阵

| 分析器 | Phase 1 | Phase 2 | Phase 3 | 依赖它的规则 |
|--------|---------|---------|---------|-------------|
| SourcePatternCompiler（meta-var） | ✅ | ✅ | ✅ | 全部 pattern 规则 |
| XNode Pattern 引擎（XML 规则） | — | ✅ | ✅ | ORM/xbiz 模型规则 |
| 单层 any | ✅ | ✅ | ✅ | nop-no-raw-exception 等 |
| all/not/matches 复合 | — | ✅ | ✅ | is-safe-close 等 |
| 关系规则 inside/has | — | ✅ | ✅ | bizmodel-dao-access 等 |
| 约束求值器（跨节点） | — | ✅（controlFlow 除外） | ✅（+controlFlow） | sameText/regex 规则 |
| autofix 模板引擎（TemplateFix/Fixer/FixApplier） | — | ✅ | ✅ | 带 `fix` 字段的规则（`--fix`/`--fix-dry-run`） |
| L1 声明类型 | ✅ | ✅ | ✅ | LooseCoupling 等轻量类型规则 |
| L2 symbol solver（Java）/ tsc（TS） | — | ✅ | ✅ | CollectionIncompatibleType 等 |
| L3 数据流/常量传播 | — | — | ✅ | UnusedLocalVariable/DeadException |
| MetricsEvaluator | — | — | ✅ | 复杂度规则 |
| Scope 分析 | — | — | ✅ | exhaustive-deps 等 |
| xscript 引擎 | ✅（v1） | ✅ | ✅（+scopeAnalyzer） | BizModel 注解检查等 |

> xscript 在 Phase 1 即可用，但 `scopeAnalyzer` 绑定 Phase 3 才可用；规则作者需按矩阵声明依赖（`requires` 字段，10 §2），加载时校验。

## 3. 验收闭环

- 每 Phase 交付物有 RuleTester fixtures 覆盖（Phase 1–3 经 JUnit `RuleTestRunner`（`./mvnw test`）验证；CLI 可用后经 `nop-lint test`）
- `./mvnw test -pl nop-lint-core -am` 通过
- 06 §7 覆盖 manifest 中该 Phase tier 的规则全部有验收 fixture
- **独立 closure audit**：每个 roadmap item 完成须经独立子 agent 对照 exit criteria 与实际代码审计（不允许自审），审计记录落 `ai-dev/audits/nop-lint/`；文档状态列（✅/🔧）与实际代码的一致性由该审计一并核验

## 4. 竞争优势（可验收口径）

> 性能声称的发布门槛是 roadmap Wave 2 的 benchmark item（JMH + 与 ast-grep CLI 同规则对比）；在其落地前，下表为**设计目标**而非已验证事实。

| vs | 优势（限定条件） |
|----|------|
| **ESLint** | 多语言（Java+TS）、tree-sitter CST、声明式 YAML、Nop 业务语义；单线程劣势是 ESLint 的（多文件并行是我们的默认能力） |
| **ast-grep** | 跨节点约束、类型感知、数据流分析（Phase 3 子集）、Nop 规则库；**性能不宣称超越**（Rust vs 纯 Java），目标是同规则集下 CI 可接受（分钟级，11 §6） |
| **Semgrep** | 纯 pattern 场景性能目标 10x+（Semgrep 简单搜索 7.5s/500 文件 → 目标 <750ms，待 benchmark 验证）；Java 原生、Nop 深度集成；taint 分析不在范围（不宣称） |
| **Checkstyle** | YAML 配置、自动修复、meta-变量、多语言、现有 checkstyle.xml 迁移路径（06 §8） |
| **PMD** | 声明式规则、无需 Java 编码、更快、CPD 明确排除（见 06 §7 排除声明） |
| **ErrorProne** | 独立运行、YAML 配置、无需编译器集成、编译期类型推导用 symbol solver 近似替代（精度差异在 manifest 标注） |
