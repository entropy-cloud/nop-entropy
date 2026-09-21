---
status: active
mission: nop-lint
work-item: "item-12"
group: "2026-09-21-1420"
verify: [test]
---

# L1 DeclTypeResolver：声明类型提取（roadmap item 12）

## Current Baseline

以下事实均已对照 live repo（2026-09-21）核实：

- `LintNode` 门面已就绪（plan 02，item 2 done）：`kind()/kindId()/text()/childByField(String)/range()` 等访问器可用；java 语法绑定（`JavaLanguage`，nop-lint-java）与 core 内可直接构造的 `TreeSitterLanguageAdapter`（见测试 `bench/BenchLanguage.java` 的构造方式）均可用。
- 匹配内核（M1）与规则加载（item 8）均已 done；item 12 只依赖 item 2（已 done），与本批次其他 item 无依赖关系。
- roadmap item 12 原文："L1 `DeclTypeResolver`（declaration-type extraction, design 06 §5.2）"。design 06 §5.2 的 L1 契约：**仅声明类型提取，纯 AST，无外部依赖**——对声明节点取 `child("type")` 的文本（`variable_declaration.type` → `"String"`、`method_declaration.return_type` → `"int"`、`formal_parameter.type` → `"List<String>"`），泛型写法按书写文本保留；明确"不自建 resolver、不引入 JavaParser"，类型解析/继承链属 L2（item 26）。
- design 11 §3 成本表：L1 DeclTypeResolver 初始化成本 0、查询成本 ~0，全部档位可用（"pattern/xscript 内联查询"）——即 `requires: L1` 的规则在 fast/standard 都可运行。
- design 06 §4.6 把 L1 框图画为 `DeclTypeResolver` + `AnnotationParser` 两半，但 roadmap item 12 的文本只点名 DeclTypeResolver；仓库 nop-lint 内目前**不存在**任何 `type` 包或类型提取类。
- tree-sitter-java 语法中类型挂在声明的 `type` 命名字段上（field_declaration/method_declaration/formal_parameter/local 变量声明），`variable_declarator` 自身无 type 字段（类型在父声明上）——提取入口必须按声明类节点进入。
- L1 的首个消费方是 Wave 3 的 xscript 引擎（item 14）与 Wave 2 末的 L1 类规则；本 plan 交付的组件 API 需可被这两类消费方直接调用。

## Goals

- nop-lint-core 新增 `DeclTypeResolver`（roadmap/design 06 §5.2 命名）：从 Java 声明类节点提取书写形式的声明类型文本，纯 `LintNode` 实现，零新依赖。
- 覆盖 design 06 §5.2 列举的声明形态：字段声明、局部变量声明、方法返回类型、形式参数类型；泛型（`List<String>`）、数组（`String[]`）、限定名（`java.util.Map`）按书写文本保真返回。
- 对无法提取的情形语义明确：无 type 字段/非声明节点返回 null（可判定、可测试），不猜测、不推断。
- 为 item 14（xscript 内联查询 L1）提供可注入的稳定 API。

## Non-Goals

- L2 及以上：symbol solver 复用（item 26）、tsc bridge（item 20）、继承链/方法签名解析（design 06 §5.2 明确 L2 范围）。
- `var`/初始化表达式推断：`var x = ...` 仅返回书写文本 `"var"`，不做 initializer 推导（推断属 L2/数据流，items 26/30）。
- `AnnotationParser`（design 06 §4.6 L1 框图另一半）：不在 item 12 文本内，且当前无任何规则消费注解类型；由首个需要注解语义的规则项（Wave 4–5 规则库批次）立项承接，本 plan 在收口时将该缺口以 follow-up 形式显式登记，不作静默假设。
- TS 侧类型提取（item 19/20 之后）。
- 与 `LintEngine`/`requires` 能力检查的集成（engine plan 已把 `L1` 计入两档能力集，无需本组件参与；执行顺序上建议在本 plan 之后，但无硬依赖）。

## Phase 1 — DeclTypeResolver 组件与焦点测试

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/type`

- Item Types: `Fix | Proof`

- [x] 新增 `DeclTypeResolver`（包 `io.nop.lint.core.type`）：入口接受声明类 `LintNode`，按 `childByField("type")` 提取文本；覆盖 design 06 §5.2 的四类声明形态（字段/局部变量/方法返回/形式参数）。
- [x] 形态保真：泛型参数、数组后缀、限定名、通配符（`<?>`）按书写文本返回，不做任何规范化改写（保真本身是可测试契约）。
- [x] 明确失败语义：非声明节点或无 type 字段 → 返回 null；`var` 声明 → 返回字面 `"var"`（推断明确排除，见 Non-Goals）。以上每条路径均有断言。
- [x] 单元测试（core 模块内，按 `BenchLanguage` 模式构造 java 适配器解析源码片段）：至少覆盖——局部变量（简单类型/泛型/数组/var）、字段声明、方法返回类型（void/int/泛型）、形式参数（限定名/泛型/通配符）、非声明节点返回 null、无 type 字段的声明形态。
- [x] 消费方视角测试：对同一段源码，resolver 结果可经节点 `range()` 定位回原文本（证明提取结果与源码切片一致，防 off-by-one/文本错位）。

Exit Criteria:

- [x] `DeclTypeResolver` 落地，上述全部声明形态与失败路径有焦点测试断言（Minimum Rules #25：新功能显式列出测试覆盖）。
- [x] **接线验证**（Minimum Rules #23）：提取结果经 `range()` 与源码字节切片逐字符一致（证明组件在真实解析树上工作，而非对自造节点自证）。
- [x] **无静默跳过**：null 返回路径即"无法提取"的显式契约并有测试，无吞错/占位值（Minimum Rules #24）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：实现与 design 06 §5.2 示意在类名/方法形态（`DeclTypeResolver.resolveDeclType(LintNode)` → 文本或 null）上一致；唯一偏差是示意中的 `child("type")` 访问器（LintNode 实际 API 为 `childByField("type")`），已在 §5.2 修订中更正（见 Phase 2）并在日志记录。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 消费契约收口与 roadmap 状态回写

Status: planned
Targets: `ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Decision | Follow-up`

- [x] **Decision**：与 item 14（xscript）的消费契约显式化——resolver 以单方法 API（输入声明节点、输出类型文本或 null）供 xscript/规则层调用；契约写入 design 06 §5.2 的 L1 小节（最终形态一句话，不写实现细节）。
- [x] **Follow-up 登记**：`AnnotationParser` 缺口（design 06 §4.6 L1 框图另一半，roadmap 无属主 item）以 follow-up 形式显式记录于本 plan Closure，并确认其触发条件挂在首个注解语义规则项上；该登记同步进日志。
- [x] 收口项：roadmap item 12 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 M2 剩余项（9/10/11）状态未受扰动。

Exit Criteria:

- [x] design 06 §5.2 的 L1 消费契约已更新为最终形态（或明确记录 `No owner-doc update required` 的理由）。
- [x] AnnotationParser 缺口已在本 plan Closure 与 `ai-dev/logs/` 显式登记（有触发条件，非模糊待办）。
- [x] roadmap item 12 状态与 plan 状态一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-21-1420-2-decl-type-resolver-l1-1-2e228ab8 to ses_f3c334849ffeM9P4ArCaWTtK9X
- 2026-09-21：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-21-1420-2-decl-type-resolver-l1-1-2e228ab8

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [ ] 所有 in-scope confirmed live defects 已修复（起草时未知悉此类缺陷）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（起草时未知悉此类漂移）
- [ ] 行为/契约结果已达成：`DeclTypeResolver` 对 design 06 §5.2 四类声明形态按书写文本保真提取，失败路径显式返回 null，API 可被 item 14（xscript）按 Phase 2 契约直接调用
- [ ] 必要 focused verification 已完成：两个 Phase 全部测试通过，含形态保真/失败路径/range() 源码切片接线断言
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（AnnotationParser 缺口为显式登记的 Follow-up，触发条件挂在首个注解语义规则项，非静默假设）
- [ ] 受影响的 owner docs 已同步到 live baseline，或各 Phase Exit Criteria 已明确写明 No owner-doc update required（design 06 §5.2 消费契约）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入下方 Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）提取结果来自真实解析树（经 range() 与源码字节切片逐字符比对，非对自造节点自证），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Verification

- `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C`（每个 Phase 执行；Phase 2 为回归）
- `node ai-dev/tools/check-doc-links.mjs --strict`（Phase 2 执行，design/roadmap 变更后）

## Closure

Follow-up:

- **AnnotationParser 缺口登记**（design 06 §4.6 L1 框图另一半，roadmap 无属主 item，non-blocking follow-up）：L1 注解类型/注解语义提取（`@Nullable`、`@Test` 等）无组件承接，当前也无任何规则消费注解类型信息。触发条件：首个需要注解语义的规则项立项时（Wave 4–5 规则库批次，如 JUnit 测试检测/注解约束类规则，roadmap items 29/35）由该规则项的 plan 承接建设，不假设并入其他 item。本 plan 不作静默假设，仅显式登记缺口与触发条件。
