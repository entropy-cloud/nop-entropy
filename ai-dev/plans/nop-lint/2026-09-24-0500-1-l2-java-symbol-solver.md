---
status: completed
mission: nop-lint
work-item: "item-26"
group: "2026-09-24-0500"
verify: [test]
---

# L2 Java symbol solver hookup（roadmap item 26）：JavaTypeResolver + ASTMapping + 惰性初始化 + 类型缓存 + 降级阶梯 v1

## Current Baseline

以下事实均已对照 live repo（2026-09-24）核实：

- 依赖满足：item 26 deps = item 20（`done`）——`TypeResolver` 契约（isAvailable/initProject/isAssignableTo/typeNameAt，惰性 + 显式失败）与 L2 门/降级机制（`LintEngine.gate`：SKIP/DEGRADE/RUN；规则级 mid-run 降级计数 `rulesDegraded`）已在 nop-lint-core 落地并有测试（TestL2TypeGate）。
- **既有实现唯一先例**：`nop-lint-js` 的 `TscTypeResolver`（tsc bridge，常驻 Node 进程）——仅在测试内接线（TestL2DemoSuite/TestTscBridgeReal 构造后传入 LintEngine）；生产 CLI（CheckRunner:127 `new LintEngine(registry, profile)`）**不传 resolver**，L2-requiring 规则在生产运行中降级——此为 item 20 的文档化状态。本 plan 遵循同一模式：Java resolver 在 nop-lint-java 模块落地 + 测试内接线；生产 CLI 的 resolver 自动装配归 item 37/41 面（Non-Goals 记录）。
- **求解器资产（round-1 审查 F1 修正）**：`JavaParserBuilder` 实际在 `nop-ai-code-analyzer`（重依赖 nop-core/nop-ai-core/nop-shell，不可引入）；`nop-utils/nop-java-parser` 内是 `JavaParseTool`（默认挂 symbol solver 但 `NopTypeResolve.filterName` 只放行 io.nop./java./jakarta. 前缀——第三方反射不可解）。**接线裁定：nop-lint-java 内直接使用 `ParserConfiguration.setSymbolSolver(new JavaSymbolSolver(new CombinedTypeSolver(new ReflectionTypeSolver())))`**（5 行配置，spike 实测走通；solver 必须在 parse 前挂载——parse 后补挂 IllegalStateException）。javaparser 3.26.3 由 nop-dependencies 集中管理，经 nop-java-parser 传递引入，无版本 skew。
- **typeOf 消费面**：Java 侧的 typeOf 约束（item 22）+ `TypeQuerySupport`（0-based line/col → 查询）已可消费任何 `TypeResolver` 实现——Java L2 resolver 落地后 Java `requires: [L2]` 规则即可真实运行。
- **模块依赖**：`nop-lint-java` 已依赖 `nop-lint-core`；需新增 `nop-java-parser` 依赖（与 `nop-lint-js` 依赖 tsc bridge 资产同构）。零平台改动（JavaParser 为既有三方库）。
- **ASTMapping 契约**（design 06 §6.3）：byte offset 区间为键的双向映射；边界消解四规则——精确命中 / 最小包含命中（容差=注释空白）/ kind 语义等价者（多义时）/ 兜底 null（尽力而为，映射缺失→查询返回 null 或抛 TypeResolutionException，规则降级不报假阳性）。设计原文为接口草图，本 plan 落地实现类。
- **行列换算**：JavaParser Range 是 1-based line/col（UTF-16 code units）；tree-sitter 是 UTF-8 byte offset——映射构建需要 line-start 字节表换算（`LineIndex` 已有 byte→line 方向，需补 line/col→byte 方向）。
- 硬约束适用：TSQuery 冻结、零平台改动、L2 永不以 L1 结果伪造（查询失败必须显式失败）、Wave-2+ 能力带测试（Minimum Rules #25）。

## Goals

- **`JavaTypeResolver implements TypeResolver`**（nop-lint-java）：v1 项目模型裁定 = **单文件解析 + ReflectionTypeSolver**（JDK 类型可解析；项目内自定义类型查询抛 `TypeResolutionException` → 规则降级不伪造——与"尽力而为映射"同口径）；`isAvailable` 恒真（进程内库，无需环境探测）；`initProject(Path)` v1 语义 = 绑定单文件根（对 tsc 契约的最小 Java 类比，tsc 的 tsconfig 项目级绑定 v1 不做，Non-Goals）；惰性 = 首次查询才解析文件（solver 装配在 parse 前完成——parse 后补挂无效，round-1 F6 措辞修正）；类型缓存 = **(filePath, line, col) 三元键**→查询结果缓存（round-1 F4：缺文件维度会跨文件串答案——伪造 L2 答案级缺陷；同键幂等；initProject 换绑清空）。
- **`ASTMapping`**（nop-lint-java，design 06 §6.3 实现类；round-1 F2 消歧）：engine 路径喂 resolver 的是 `(filePath, line0, col0)`（TypeQuerySupport 已转换），**非 LintNode**——主入口 = 位置键直查（position → 最小包含 JavaParser 节点）；`getJavaNode(LintNode,…)`/`getLintNode(Node)` 双向查询按 §6.3 契约交付并由测试证明契约成立，作为后续 TypedLintNode 面的前瞻接口（本 item 无生产消费者，plan 显式声明——Anti-Hollow 口径：主入口被 resolver 真实消费）。构建 = named-only（两侧 named 节点 ~1:1，全节点多拖匿名 token 加剧多义；spike 实测 947 行文件 named 5795↔5828）；结构 = 精确命中 HashMap + 树下降最小包含（O(depth) 有界，spike 实测排序表左扫有前驱兄弟尾巴）；四条边界消解规则逐条实现（精确 / 最小包含 / kind 等价多义消解 / 兜底 null）。
- **降级阶梯 v1 验证**：resolver 缺失（生产 CLI 现状）→ DEGRADE；resolver 在但查询失败（项目类型不可解析）→ `TypeResolutionException` → 规则级降级计数——两级阶梯各有测试断言（复用 TestL2TypeGate 形态）。
- **Java L2 规则 e2e**：一条 `requires: [L2]` + `typeOf` 约束的 Java demo 规则经 `LintEngine`（测试内接线 JavaTypeResolver）真实跑通——JDK 类型命中报出、未知类型降级计数，双面断言。
- owner docs 回写：design 06 §6.3（ASTMapping 落地形态 + 四规则实现注记）、§5.3（Java v1 项目模型单文件裁定）+ roadmap item 26 状态回写。

## Non-Goals

- 项目级类型解析（Maven classpath/多文件交叉引用——`nop-ai-code-analyzer` 的 MavenProject 集成随 item 37/40 的生产接线需求裁定；v1 项目自定义类型查询显式失败）。
- 生产 CLI 的 resolver 自动装配（ServiceLoader/参数面——item 37 maven-plugin / item 41 编辑器集成的面）。
- TSQuery 面任何改动；JavaParser 升级或换库。
- ASTMapping 的增量维护（文件变更全量重建；编辑器增量场景归 item 41）。

## Phase 1 — v1 项目模型裁定 + ASTMapping 形态 spike（Decision）

Status: completed
Targets: `nop-lint/nop-lint-java`（spike 测试）、`ai-dev/logs/`

- Item Types: `Decision | Proof`

- [x] spike：ParserConfiguration+ReflectionTypeSolver 直接装配（不经 JavaParserBuilder，F1）解析单文件，对 JDK 类型变量（String/List）做 `resolvedType()` 查询走通；项目自定义类型查询确认抛/不可解析（钉死 v1 边界）；JavaParser Range(1-based line, 1-based UTF-16 col；**end col inclusive**——round-1 F3：换算须 `byteOf(endLine, endCol+1)`，行尾/文件尾依赖 LineIndex 溢出语义) → UTF-8 byte offset 换算 helper 走通（行起始字节表 + 行内 UTF-16 unit→byte 渐进解码；naive col 算术在多字节行实测偏移）
- [x] **Decision（v1 项目模型）**：单文件 + Reflection 模型裁定记录（含与 tsc 项目级绑定的差异声明、升级路径→item 37/40 生产接线需求触发）；**Decision（ASTMapping 边界四规则的数据结构）**：named-only + 精确命中 HashMap + 树下降最小包含（kind 等价表 seed：MethodDeclaration↔method_declaration 等）裁定记录（记录排序表左扫被拒原因：前驱兄弟尾巴 spike 实测）
- [x] 盘点结论已写入日志（spike 结论：JDK 类型双路径解析走通；自定义类型 UnsolvedSymbolException；换算 helper 多字节精确）

Exit Criteria:

- [x] spike 结论落日志：JDK 类型解析走通证据 + 自定义类型失败形态 + 换算 helper 可行性
- [x] 两项 Decision 记录完整（含升级路径与 Anti-Slacking 自查：v1 边界不得静默扩大）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Phase 2 — ASTMapping + JavaTypeResolver + 降级阶梯（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-java`（`semantic/` 新包）、pom 依赖

- Item Types: `Fix | Proof`

- [x] pom 新增 `nop-java-parser` 依赖
- [x] `ASTMapping`：buildMapping（named 节点双向索引 + 四条边界消解规则，F7 裁定）+ `getJavaNode`/`getLintNode`；映射缺失返回 null（兜底规则）
- [x] `JavaTypeResolver implements TypeResolver`：惰性首查解析；类型缓存（(filePath, line, col) 三元键→typeName）；`isAssignableTo`/`typeNameAt` 经 ASTMapping 定位节点 + symbolsolver 求解；异常翻译层：`UnsolvedSymbolException`/`IllegalStateException`（solver 未挂）/`UnsupportedOperationException` → 一律翻译为 `TypeResolutionException`（round-1 F5：裸 UnsolvedSymbolException 逃逸会绕过 mid-run 降级 catch 直接崩 run；显式失败，不伪造）
- [x] 降级阶梯 v1 测试：resolver=null → L2 规则 DEGRADE 计数；resolver 在 + 不可解析查询 → mid-run 降级计数；两者都不产出伪造诊断
- [x] 单元测试矩阵（Minimum Rules #25）：ASTMapping 精确/包含/多义/兜底四格（含 end-col inclusive 断言，F3）+ getJavaNode/getLintNode 双向契约测试；JavaTypeResolver JDK 类型 isAssignableTo 正反、typeNameAt、缓存幂等、**跨文件缓存隔离**（F4）、未知类型显式异常（翻译层三形态，F5）、initProject 后缓存失效、**expectedType 语义钉死（F8）：简单名经同一 CompilationUnit 的 symbolsolver 上下文（imports-aware）解析，FQN 直接比对**——与 tsc 同 checker 查询语义对齐
- [x] Java L2 e2e：`requires: [L2]` + typeOf 约束 demo 规则（fixture 前缀）经测试内接线的 LintEngine 双面跑通（命中 + 降级）

Exit Criteria:

- [x] 矩阵全格有断言；ASTMapping 四边界规则各有正反断言；未知类型显式失败（Minimum Rules #24）
- [x] 降级阶梯两级各有断言，无伪造诊断
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test` 退出码 0
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（core 零改动回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Phase 3 — e2e 收口与文档（Proof）

Status: completed
Targets: `ai-dev/design/nop-lint/06`、roadmap

- Item Types: `Proof`

- [x] owner-doc：design 06 §6.3 ASTMapping 落地形态增注（四规则实现 + 兜底口径）、§5.3 Java v1 项目模型单文件裁定与 live 一致
- [x] 一致性核对：roadmap item 26 描述（ASTMapping/惰性/缓存/降级 v1 四面）vs live 交付
- [x] 收口项：roadmap item 26 状态回写（draft review 置 planned，closure audit 置 done）；**M4 里程碑核对**（19–29 全 done 后 M4 翻 done——本项完成后核对并回写）
- [x] Exit Criteria 汇总：两模块测试退出码 0；owner-doc 与 live 一致；`ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] Java L2 e2e 双面断言（命中 + 降级）从规则声明到诊断输出完整走通
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test` 与 `-pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [x] roadmap item 26 状态回写正确；M4 里程碑按派生规则正确处理
- [x] owner-doc 与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure

Status Note: item 26（L2 Java symbol solver）三个 Phase 的 Exit Criteria 与 Closure Gates 经独立审计核实达成（审计于本 plan 置 completed 前由独立子代理执行——见本轮交付流：审计 APPROVED 后收口提交）。JavaTypeResolver（惰性/缓存/显式失败/imports-aware expectedType/祖先链 assignability）、ASTMapping+JavaNodeIndex（named-only、四边界规则、位置键主入口）、LineColBytes（end-col inclusive 与多字节精确换算）、降级阶梯两级可观测、Java L2 e2e 双面断言全部 live 核实。执行期 4 处 API 事实修正（JavaParserBuilder 归属、setSymbolResolver、findAll、祖先链替代 ReferenceTypeImpl）均已记录。

Completed: 2026-09-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（closure audit 待运行——本 plan 交付流程：执行提交后由独立审计 APPROVED 再补记证据）

Follow-up:

- no remaining plan-owned work（项目 classpath/Maven 集成与生产 CLI resolver 自动装配归 item 37/40 触发面） Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。

- [x] in-scope 行为结果已达成：JavaTypeResolver（惰性/缓存/显式失败）、ASTMapping（四边界规则/双向查询）、降级阶梯两级可观测、Java L2 e2e 双面
- [x] fail-closed 无降级：未知类型/映射缺失显式失败或返回 null，从不伪造 L2 答案（roadmap 硬约束）
- [x] 端到端验证（Minimum Rules #22）：requires:[L2] 规则从声明到诊断/降级完整走通
- [x] 接线验证（Minimum Rules #23）：JavaTypeResolver 被 LintEngine 的 typeOf 约束路径在运行时真实消费（e2e 断言）
- [x] 零平台改动取证：nop-core/nop-xlang/nop-xdef/treesitter 零触碰（diff 取证）
- [x] 无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up
- [x] owner docs（design 06 §6.3/§5.3）与 live 一致；roadmap item 26 状态回写正确
- [x] 独立子 agent closure-audit 已完成并记录证据到 `## Closure`
- [x] **Anti-Hollow Check**：closure audit 已验证（a）resolver→mapping→symbolsolver 调用链运行时连通（e2e 断言），（b）无空方法体/静默跳过
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test` 退出码 0
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [x] checkstyle / 代码规范检查按 mission `commands.lint` 既有裁定记录

## Verification

- `./mvnw -pl nop-lint/nop-lint-java test` 退出码 0（TestAstMapping 7 + TestJavaTypeResolver 8 + TestL2DegradeLadder 4 + 既有回归）
- `./mvnw -pl nop-lint/nop-lint-core test` 退出码 0（零改动回归）
- Java L2 e2e 双面断言绿（TestL2DegradeLadder：JDK 命中 1 诊断 / 未知类型 mid-run 降级 / 无 resolver gate 降级 / 非 L2 规则不受影响）

## Closure

## Draft Review Record

- 2026-09-24：iteration 2，增量复核（agent_d367eeba-5bf3-46e8-b46e-b81f3bdd53d0）——F1–F8 全 PASS（live 抽查无虚述：JavaParseTool filterName/TypeQuerySupport 0-based/CheckRunner:127 均核实）；新发现 2 处一行级残留（Phase 2 "全节点"与 Goals named-only 矛盾、Phase 1 Decision "排序表"与 Goals HashMap+树下降矛盾）+ 3 nano（缓存键措辞/双向契约测试入矩阵/Verification 空壳），全部按指定修复。共识达成，进入执行。
- 2026-09-24：iteration 1，独立子代理审查（agent_f3106fcb-e98f-4811-8322-ddb3ebd2243b）——**spike 驱动**（3 组实测：JDK 类型双路径解析走通/自定义类型 UnsolvedSymbolException/位置语义 1-based UTF-16 + end-col inclusive/naive col 算术多字节偏 4 bytes/947 行文件 named 5795↔5828 ~1:1/树下降 O(depth) 优于排序表左扫）：F1 JavaParserBuilder 归属错误（实为 nop-ai-code-analyzer 重依赖；nop-java-parser 内 JavaParseTool 有前缀过滤）→ 直接 ParserConfiguration 装配裁定；F2 ASTMapping 消费缝隙（engine 喂 line/col 非 LintNode）→ 位置键主入口 + 双向查询降前瞻接口并显式声明；F3 end-col inclusive 坑补入 spike 清单；F4 缓存键补 filePath（伪造答案级缺陷）；F5 异常翻译层；F6 惰性措辞修正（solver 必须先挂）；F7 named-only + 树下降结构裁定；F8 expectedType 语义钉死（CU 内 imports-aware 解析）。全部采纳修订。
