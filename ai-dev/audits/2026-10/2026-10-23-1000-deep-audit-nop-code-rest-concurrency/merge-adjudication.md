# merge-adjudication — WI 6.4 nop-code REST 面（lang-* + api/dao/app）并发审计合并裁定

- 裁定人：CRA WI 6.4（nop-code-rest）独立合并裁定子代理（非 C1/C6 初审与复核人、非 C10 盘点及其复核人）。裁定日期：2026-10-23。
- 裁定输入（只读，未修改）：本目录 `C1-shared-mutable-state.md`（零发现 + 终止声明 + 复核节 + 最终保留项 0）、`C6-resource-lifecycle.md`（零发现 + 终止声明 + 复核节 + 最终保留项 0）、`C10-test-protection.md`（零缺陷面盘点 + 复核结论）、`dispatch-log.md`（Phase 1–5 全记录）、`ai-dev/audits/cra-index.md`（WI 1.1–6.3 台账）、姊妹单元 `2026-10/2026-10-23-0900-deep-audit-nop-code-index-concurrency/merge-adjudication.md` 及其四维文件 CL 节、`2026-10/2026-10-22-1500-deep-audit-nop-tools-parse-concurrency/merge-adjudication.md` 与 C1/C6（CL-003 同站点查重用）、2026-09 全仓审计 `2026-09-30-1726-deep-audit-nop-entropy-full/11-code-graph.md` G11-03-01 原文（:69 立案 + :422 复核行）、plan 2491（基线 b8552f11d1）、6.3 merge-adjudication.md（§一~§六结构先例）。
- 裁定纪律：本单元为 **campaign 首例零立案单元**，无实体三态表可裁；本裁定对象为①零发现终裁定与复核可信度链、②CL 线索 5 条终处置、③与姊妹单元 6.3 的跨单元边界核对、④结构性结论单列、⑤数字对账与通道。不新增发现、不重写两维/C10 文件、不修改 cra-index 与产品代码（cra-index 回填文本仅提供，代录归主 agent）。

---

## §一 零发现终裁定

### 裁定输入与对账表（零发现单元形态）

| 维度文件 | 审计面 | 判定粒度 | 初审发现 | 复核 | 最终保留项 |
|---|---|---|---|---|---|
| C1 共享可变状态 | 9 个 src/main 根 126f/12,614（lang-* 22f/5,041 + api 75f/6,967 + dao 手写 17f + app 1f） | 五类载体逐项判定（表行 16：I×4 / S×1 / SF×6 / E×5）+ 零命中声明（ThreadLocal、static 线程不安全工具、检查-然后-行动、复合递增/拆分写） | **0**（显式终止声明） | 抽样成立、安全判定失败 0，C1-R-001 未触发 | **0**（watch-only 备注 3 项不属保留缺陷） |
| C6 资源生命周期 | 同上（解析器资源面 6 文件为主审面） | 资源获取点配对 12 类逐项判定 | **0**（显式终止声明） | 配对抽样 3 + 专项 2 成立，C6-R-001 未触发 | **0** |
| C10 测试基建 | 同模块 src/test 全量 35f/2,615 + 单元外锚点登记 | 零缺陷面盘点（清单/断言扫描/CL 测试面/契约锚现状） | **立案 0** | 抽样 7 项成立、勘误 1 处（非载荷，已就地修正）——可信 | 0（watch-only W-01~05 建议，非缺陷非工单） |

### 终裁定声明

**本单元终裁定：P0=0、P1=0、P2=0、P3=0，立案数=0，工单=0。** 两维（C1/C6）零发现经独立复核确认为终局；C10 以零缺陷面盘点形态收口（campaign 首例——前 30 个已审单元均有 P1–P3 立案，最低为 6.1 零 P1 但 P2×4/P3×13）。P0 即修通道显式空执行（沿 campaign 惯例记录）；P1/P2 台账与 P2 裁决台账本轮 **0 新增行**。

- **Protected Area**：nop-code-api 75f/6,967 命中 nop-*-api plan-first——审计只读，零工单自然零触碰；dao 生成物（`_NopCodeDaoConstants.java`、`entity/_gen/`）按硬停规则只读未作为修复面。
- **复核可信度链（零保留的依据）**：C1 复核抽样 8 项（6 载体 + 2 专项）逐项成立、安全判定失败 0；C6 复核配对抽样 3 项 + 专项核验 2 项（compat 桥 delete() no-op 独立核读、零命中 grep 口径复跑）成立；C10 复核抽样 7 项复演成立、1 处计数笔误就地修正。两级复核（Phase 4 维度复核 + Phase 5 C10 复核）均为独立子代理、file:line 独立重取，零发现结论在两代证据间无漂移。
- **勘误登记（3 处，均非载荷、不影响终态）**：① analyzer 获取点「7 个」实为 8 个调用点（初审行号自列 8 个，计数标注笔误，C1 复核勘误并经 C6 复核节交叉记录）；② TestGoImportResolver 3→4 @Test（C10 复核发现，已在 C10 §3 表就地修正）；③ 基线「C1 static mutable: 0」与本轮实测「1」差异（`globalArgs`，基线 grep 管道排除逻辑归因）——判定为平台标准启动器模式（S-01，write-once 启动），非缺陷，两维文件与 dispatch-log 披露一致。

## §二 跨单元预登记对账：G11-03-01 已修闭环（cra-index 回填注记文本）

- **G11 预登记清点结论**（dispatch-log Phase 1 + plan 2491 跨单元预登记）：2026-09 G11 对本单元 lang-*/api/dao 面**无并发/资源类三态裁定项**——G11-03-01（装配面，P1）已于 df4e4f8fa7（2282-WS4 六语言 adapter bean 接线）修复；G11-09-03（JavaFileAnalyzer 降级日志级别）属 error-handling 域非并发，不入圈维持原轨。
- **已修事实独立确认**：本单元 C1 维度复核独立核读 `nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:11-16`——六个 `_lang-*.beans.xml` import 齐全，六语言 adapter 全部装配激活，无休眠降级口径；C6 据此按六语言全激活口径判级（本单元零发现故无判级差异面）。
- **闭环归属**：G11-03-01 关闭为**已修闭环**（非「仍成立」、非并入工单——本单元无工单）； cra-index 回填注记最终文本（主 agent 抄录，沿 6.2 G10 / 6.3 G11-15-02 回填块先例；裁定权威 = 本节 + C1 复核节，两处一致）：

> **2026-09 G11 承接闭环回填（2026-10-23，WI 6.4）**：2026-09-30 全仓审计 `11-code-graph.md` G11-03-01（Go/C#/Rust 三个 ILanguageAdapter bean 从未被任何 app-*.beans.xml 装载——多语言索引组装后静默失效，P1）经 CRA WI 6.4（plan 2491）复核确认**已修复**：commit df4e4f8fa7 后 `app-service.beans.xml:11-16` 六个 `_lang-*.beans.xml` import 齐全，六语言 adapter 全部装配激活、无休眠降级口径（C1 维度复核独立核读确认）；G11-03-01 关闭为已修闭环；G11 对 lang/api/dao 无并发/资源类预登记项（G11-09-03 属 error-handling 非并发，维持原轨）。

## §三 CL 线索 5 条终处置

编号唯一性核对：C1 批 CL-001~003（前缀 `nop-code-rest-CL-0NN`）+ C6 批 CL-101~102（C6 自声明 CL-101 起编号），单元内**无撞号、无重编**；5/5 经两维复核确认、0 驳回。CL 不计实体、不占工单（工单=0）。**处置总口径**：本单元裁定维度集仅 C1/C6（C2/C3/C4/C5/C7/C8/C9 为 overlay 随批 CL 登记，未开维）——凡指向未开维的线索按 campaign 惯例**域留档**于 cra-index WI 6.4 CL 小节，7.1 跨模块合并裁定时作为对应域候选线索参与 F 族聚类（唯一升格路径 = 7.1 裁定或后续 C9/C5 专项轮），本轮不立工单、不入 P1/P2 台账。

| 编号 | 载体（file:line 见 C1/C6） | 形态 | 终处置 | 一句裁定理由与测试先置 |
|---|---|---|---|---|
| CL-001 | `JavaFileAnalyzer.java:87-95,140` 构造器重建 CombinedTypeSolver+ReflectionTypeSolver、每 analyze `new JavaParser` | C9 分配放大（热路径） | **确认 · C9 域留档**（7.1 候选线索，量化归 C9 口径） | 结构事实两代复核独立核实；索引千文件级仓库线性重建，收益量化未做故不判级；测试先置 = C10 W-03（若立案共享 type-solver 栈，TestJavaFileAnalyzerConcurrent 为现成线程安全金丝雀，无需新增测试） |
| CL-002 | `GoImportResolver.java:70-79` / `RustImportResolver.java:65-89` / `CSharpImportResolver.java:64-90` 每 import 对项目文件集 O(n) 线性扫描（整仓 O(files×imports×projectFiles)，C# 双重循环放大更明显；同族六 resolver 三种复杂度档并存） | C9/C5 | **确认 · C9/C5 域留档**（7.1 候选线索） | 三处独立核读成立；若立案按 F3 同族三连修复（前缀 Map 预索引），测试先置 = C10 W-04（三语言 ImportResolver 测试为现成回归锚，**无先置条件**） |
| CL-003 | `nop-treesitter/.../language/Language.java:762-769` 单调 max、`:777-784` 幂等记忆化 | C1 良性竞争（跨单元） | **确认（良性）· watch-only，与 6.2 CL-205 同站点 cross-ref 收口，不另立台账项** | **同站点查重命中**：6.2（nop-treesitter 所属单元）已审同一对站点——其 C1 载体表行 7/8（`observedNodesPerByte` :762-769、`scannerProgramValidated` :777-784，均判安全）+ CL-205（6.2 merge-adjudication §六「CL-205→C1 注记（observedNodesPerByte 良性）」）；两单元独立判定**结论一致（良性）**，按 cra-index「重叠登记 cross-ref 后关闭，不重复登记」收口；本单元价值 = 第三方消费方视角的独立复证（经共享 `TS_LANGUAGE` 使用面核验），留档为 nop-treesitter 未来复审的前置结论 |
| CL-101 | `CSharpCodeFileAnalyzer.java:41-42,69,76` 实例字段 `source`/`lastNamespace` 承载 per-call 状态（六分析器同族孤例） | C1 结构脆弱（理论级） | **确认 · watch-only（域内登记），附升 P1 触发条款** | C1 复核专项裁定不升级：registry 每次转发 `new`（LanguageAdapterRegistry.java:19-28）+ 8 获取点每文件新建 ⇒ 字段等效方法局部态，现行接线下无交错序列，按「须有结构性原因或可量化风险」不立案；**升 P1 条款**：analyzer 缓存化或跨文件并行复用接线（含 6.3 W-02 修复若引入缓存/并行）即升 P1，先置 = C10 W-01（adapter 双调用 `assertNotSame` 断言 + CSharp 交错并发断言，今日全缺）；契约缺口侧（`ICodeFileAnalyzer` 无线程安全契约、`getFileAnalyzer()` 无新实例契约，接口在 nop-code-core = 6.3 域）作边界注记不立案——6.3 已闭单元 40 工单 + 18 CL 无此登记，补 javadoc 契约可随任一触碰 nop-code-core 的批次搭车，非新工单 |
| CL-102 | Python/CSharp/Go/Rust 四分析器 `nodeText()` 每调用全文重编码 UTF-8（计数 Python 12/CSharp 11/Go 19/Rust 17，TS 分析器为同族正确参照） | C5/C9 分配放大（预期 P2 性能候选） | **确认 · C5/C9 域留档**（7.1 候选线索） | 计数口径两代复核逐字吻合（TS 助手命名 `getNodeText` 故 0 命中）；无正确性问题（截断护栏齐备），GC 可达性回收非泄漏；修复模板 = TS 分析器「analyze 入口一次编码、byte[] 下传」机械迁移；测试先置 = C10 W-02（go/rust/csharp 先补 `TestTreeSitterByteOffset` 对应物再动 nodeText） |

**与 6.3 姊妹单元 CL 同族归并判定：不归并。** 6.3 唯一编号 18 条 CL 与本单元 5 条**零同站点重复**（6.3 面 = nop-code-core/flow/service 长驻索引与图分析；本单元面 = 六语言 analyzer/api/dao/app，载体零交集）——campaign 惯例为 CL 留各单元小节、跨单元同族由 7.1 F 族聚类统一处理，故仅登记域族关联注记（不合并条目）：CL-001/CL-102 ↔ 6.3 CL-305 同属 C9 域「重复分配/放大」候选族；CL-101 契约缺口侧 ↔ 6.3 C6-006（W-30，预留字段无所有权/关闭契约）同属「接口契约缺口」形态族；CL-002 无跨单元族（同族清扫 F3 已在本单元六 resolver 内闭合）。

## §四 跨单元关联与边界核对（与 6.3）

1. **消费关系与获取点跨域核验**：本单元六语言 `XxxCodeFileAnalyzer` 的唯一消费链位于 6.3 域——`ProjectAnalyzer`（nop-code-service，5 个获取点 L173/256/441/592/667）与 `CodeIndexService`（3 个获取点 L382/458/1122），合计 8 处 `registry.getAnalyzer(...)` 逐文件获取后立即 `analyze` 即用即弃；`LanguageAdapterRegistry` 本体在 nop-code-core（亦 6.3 域）。本单元 C1 复核**跨域核读**上述消费面确认「每文件新建」契约成立——即 6.3 域消费面对本单元 analyzer 的使用方式是零发现结论的组成部分，两单元证据互为支撑、无缺口。
2. **上游保证注记（W-02 修复面划界，cra-index 6.3 行可引用）**：6.3 W-02（`nop-code-index-C5-002`，P1——ProjectAnalyzer 死守卫 + 全仓库 sourceCode 驻留巨事务）的驻留失效面**完全位于消费侧**（BatchQueue 消费端全量累积）：本单元核验的「analyzer 每文件新建 + 实例封闭 + 即用即弃」契约保证 analyzer 层既不复用实例也不驻留跨文件 sourceCode（CSharp `source`/`lastNamespace` 随实例即弃），故 W-02 的修复面限于 nop-code-service 消费侧，**无需延伸至 lang-* 六模块**。
3. **反向约束注记（随 W-02/并行化接线生效）**：W-02 修复或任何索引并行化若引入 analyzer 缓存或跨文件复用，即触发本单元 CL-101 升 P1 条款——须先满足 C10 W-01 测试先置（`assertNotSame` 双调用断言 + CSharp 交错并发断言；今日该契约测试全缺，接线失败将是静默数据损坏）。此注记建议随 7.1 对 W-02 的裁决一并宣读。
4. **查重结论**：cra-index 对本单元 **0 提及**（CRA 内首报，dispatch-log「历史审计对照口径」已核）；对 6.3 的 40 工单 + 18 CL **零同站点重复立项**；对 6.2 仅 CL-003 一处同站点（`Language.java:762-784`），两单元结论一致良性，cross-ref 收口零重复登记。**同站点重复立项总计：0。**
5. **G11-03-01 已修闭环**：见 §二（回填注记文本随附）；本单元无其他跨单元未结项——姊妹单元 6.3 的两项跨单元承接（G11-15-02、5.6 消费面）均已在 6.3 闭环，与本单元无交叠。

## §五 结构性结论（单列）

1. **零发现的结构性成因（两条事实 + 一条契约）**：① **并发原语九类全零**——ThreadLocal/synchronized/volatile/Atomic*/并发容器/锁/线程池/异步链/调度器在 9 个 src/main 根 grep 全零（基线预注经两维独立复核成立），单元内不存在显式同步需求点；② **并发安全由双结构性契约保证**——「analyzer 每文件新建」（adapter 工厂模式，8 获取点 + 6 个 `getFileAnalyzer()` `new` 工厂全核验）×「装配期一次写入、运行期只读」（registry + beans.xml 一次性注入 + static 面）。二者叠加 = 本单元零发现是**结构性结果而非审计未及**：无共享可变状态载体、无跨调用实例态、无资源释放义务（纯堆对象 + compat 桥 delete() 文档化 no-op）。
2. **双契约的脆弱性同条登记（watch-only，非缺陷）**：任一契约被后续重构打破（analyzer 改单例注入/缓存、registry 运行期热注册），I-03/SF 系列即转化为真实竞争面——C1 复核节已确认两契约成立并列为 watch-only。
3. **契约测试缺口 = watch 项非缺陷**：`assertSame/assertNotSame` 全测试面 **0 命中**、六个 adapter 测试各仅单次 `assertInstanceOf`——「每文件新建」契约今日**仅由实现现状保证、无回归保护**（35 个测试无一能在契约被打破时变红）；CSharp 并发锚为 0（全单元唯一并发测试 TestJavaFileAnalyzerConcurrent 在 java 模块，且为共享实例错误冒烟口径）。安全现状 = 契约约束而非测试保护；C10 watch-only W-01~05 即为此登记的先置条件清单，**均不要求本轮行动、不入工单**。
4. **C10 形态注记**：零缺陷面盘点形态为 campaign 首例（零立案 ⇒ 无矩阵 ⇒ 无「会红」判定，campaign 会红序数链止于 6.3 第 12 例，本单元不计序）；api/dao/app 三模块零测试面与其纯契约形态一致（XGEN 契约接口/空壳实体/35 行启动器，无行为逻辑可测），**非缺口**；五个 `Test*TreeSitterMemoryRelease` 名实不符（无内存断言）为命名诚实性观察项（W-05）——C6 已裁定 compat 桥无释放义务，现状冒烟口径可接受，仅在未来资源语义变化时先升级或更名。
5. **正面结论（修复模板在仓）**：compat 桥 `delete()` no-op 语义文档化清晰（迁移桥接镜像 JNI 签名）；TS 分析器「入口一次编码、byte[] 下传」= CL-102 的同族正确参照；Go 分析器 per-call `WalkContext` 封装 = CL-101 的同族状态封装正确参照；TestJavaFileAnalyzerConcurrent = CL-001 若立案的现成线程安全金丝雀——四条线索的修复/守护参照全部无需跨仓引入。

## §六 数字对账与通道

### 对账过程（五步，全部闭合）

1. **两维保留项**：C1「最终保留项：0（watch-only 备注 3 项不属保留缺陷）」+ C6「最终保留项：0」⇒ 两维保留 **0 + 0**；C1-R-001/C6-R-001 复核驳回项均未触发。✓
2. **C10**：立案 **0**（零缺陷面声明）；watch-only 建议 W-01~05（编号为建议序号非工单号）；复核抽样 7 项成立、勘误 1 处（非载荷，就地修正）。✓
3. **CL**：5/5 确认、0 驳回；唯一编号 5（CL-001/002/003/101/102，C1/C6 分段声明无撞号）；处置 = 域留档 3（CL-001→C9、CL-002→C9/C5、CL-102→C5/C9）+ watch-only 2（CL-003 cross-ref 收口、CL-101 域内登记附升 P1 条款）+ 随工单 0 + 移交 0。✓
4. **dispatch-log 口径一致性**：Phase 2 汇总（双零 + CL 5 + campaign 首例双零单元）✓；Phase 4 汇总「抽样 11 项 0 失败」与分维文件自计（C1 8 项 + C6 5 项，合计 13）差异为**汇总归类口径**——analyzer 每文件新建（=C1 抽样第 3 项）与 CL-101 可达性两项在 dispatch-log 单列为「专项核验」不计入抽样数（11 = C1 6 载体 + C6 5 项），抽样并集与「0 失败、均可信」结论两口径完全一致，**非滑差**；Phase 5 汇总（35f/2,615 行、并发断言 1、资源断言 0、勘误 1、watch-only 5）✓；「载体 18 项」为批次级汇总口径（可对账为 C1 判定表行 16 + 零命中判定类 2[载体 4 ThreadLocal、复合操作/非原子原语节]），权威粒度以 C1 判定表为准，非滑差；基线 static mutable 0→1 差异披露在 C1 §一与 dispatch-log 两处一致。✓
5. **跨单元计数**：对 6.3（40 工单 + 18 CL）同站点重复 0；对 6.2（19 工单 + 19 CL）同站点仅 CL-003 一处且结论一致良性、cross-ref 收口；G11 承接 = 已修闭环 1 项（§二）、三态裁定项 0。✓

### 通道与后续（主 agent 待办，本裁定不代改）

1. **P0**：0，即修通道显式空执行（沿 campaign 惯例记录）。
2. **P1/P2/P3 台账**：**0 新增行**——cra-index P1 发现汇总与 P2 裁决台账本轮无追加。
3. **cra-index 回填待办**：① 报告清单新增 WI 6.4 行（P0×0/P1×0/P2×0/P3×0、零立案、campaign 首例零立案单元；C10 零缺陷面盘点形态无缺保护矩阵）；② 新增 6.4 计数口径块（结构性成因 = 并发原语九类全零 + 双契约；契约测试缺口 watch-only；正面结论四条）；③ 新增 WI 6.4 CL 小节（5 条，确认 5/5 驳回 0，处置见 §三，附与 6.3/6.2 的同站点/域族关联注记）；④ G11-03-01 已修闭环回填注记（最终文本见 §二 blockquote）；⑤ roadmap Phase 6 表 6.4 行状态更新 + 本目录 summary.md 归档。
4. **7.1 输入**：本单元零工单入池；7.1 聚类时本单元仅提供三类输入——CL 域留档 3 条（C9/C5 域候选，量化归 C9 口径）、W-02 反向约束注记（§四.3，随 6.3 W-02 裁决宣读）、双契约 watch-only 脆弱性登记（任何触碰 nop-code analyzer 接线的批次须先满足 C10 W-01）。
5. **closure audit 输入**：本文件 §一/§六 为计数权威表；零立案单元的 closure audit 核对面 = 复核可信度链完整性（§一）、CL 处置与查重（§三/§四）、G11-03-01 回填文本一致性（§二）；dispatch-log 两处汇总归类口径注记（§六.4）随本裁定生效，不改 dispatch-log 本体。
