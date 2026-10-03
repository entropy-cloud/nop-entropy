# WI9 Closure Audit——14-wi9-aggregate-eval.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**PASS**（Phase 1/2 已勾选项全部 live 落地、24/24 实跑绿、Anti-Hollow 编译链核验通过、三大工具门禁退出码全 0；附 5 项 Minor 均不阻塞，见 §4）

## 1. Exit Criteria 逐条核验（plan 14 Phase 1/2 已勾选项）

| 勾选项 | 裁定 | Live 证据 |
|---|---|---|
| Phase 1：TestStreamSqlAggregations 11 用例 | PASS | `nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/eval/TestStreamSqlAggregations.java` 实读：11 个 `@Test`（resolve 五 id+未知名 null、sum 提升/空集、浮点 sum、count 两形态+空集 0、avg、min/max 含字符串自然序、merge、distinct fail-fast、arity、resultType、Serializable 往返） |
| Phase 1：TestStreamSqlExprCompiler 13 用例 | PASS | 同目录 `TestStreamSqlExprCompiler.java` 实读：13 个 `@Test`（列访问/限定名末段、缺失列 fail-fast、字面量四类、算术提升+null 传播、除法恒 double、比较含 `!=`/字符串自然序、三值逻辑短路、IS NULL 两形态、BETWEEN/IN 含 not、bean 形态、子集外五类、resolveType D7、Serializable 往返） |
| Phase 1：确认红 | PASS（过程性） | 红态为历史过程声明，live 树不可复现（src 从未中途提交）；以绿态全量落地+用例存在性替代核验（见 §4 M-5） |
| Phase 2：StreamRecordEvaluator + RecordColumnAccess | PASS | `eval/StreamRecordEvaluator.java`（@FunctionalInterface extends Serializable）、`eval/RecordColumnAccess.java`（Map containsKey 优先、bean getter 反射 ConcurrentHashMap 缓存、缺失列/不可比较/强转失败一律 `ERR_STREAM_INVALID_ARG` fail-fast） |
| Phase 2：compileScalar v1 子集 | PASS | `eval/StreamSqlExprCompiler.java:57-93` getASTKind 分派 13 种节点；default 与 null AST 走 `RecordColumnAccess.unsupported`（AST 节点类型+文本进 ARG_DETAIL） |
| Phase 2：StreamSqlAggregation + StreamSqlAggregations | PASS | `eval/StreamSqlAggregation.java`（四内部累加器类）、`eval/StreamSqlAggregations.java`（五 id 不可变目录、resolve 未知名返回 null） |
| Phase 2：resolveType | PASS | `StreamSqlExprCompiler.java:118-166`：列经 `Function<String,BasicTypeInfo<?>>`、字面量按 parse 值、`SqlAggregateFunction` 分支（count→LONG/avg→DOUBLE/sum 提升/min-max 透传） |
| Phase 2：24/24 绿 + 日志条目 | PASS | 实跑 `./mvnw test -pl nop-stream/nop-stream-sql`：Tests run 24, Failures 0, Errors 0（11+13），BUILD SUCCESS；`ai-dev/logs/2026/10-02.md` L3-8 条目与 live 事实逐项相符 |

### Phase 2 Exit Criteria 五条

- [x] 五聚合在流记录（Map 形态）求值正确且各有具名单测——ROADMAP 完成判定第一句成立（见 §2 判别性抽查）
- [x] 子集内 EQL 投影与聚合表达式不抛 `ERR_EQL_UNSUPPORTED_EVAL_EXPR`——13 编译器用例全部经 `EqlExprSupport.parseExpr`（`EqlExprASTParser.parseFromText`）→ `compileScalar` 转换链（唯一例外：null-AST 子用例按 plan m7 直测 `compileScalar(null)`，即 parse 空文本返回 null 的 fail-fast 契约点）；全程零 EQL 错误码
- [x] 子集外 fail-fast 且码串钉住——`missingColumnFailsFast` 与 `outOfSubsetFailsFastWithStreamSideCode`（concat 分支）均 `assertEquals("nop.err.stream.invalid-arg", e.getErrorCode())`；单一 throw 源（`RecordColumnAccess.unsupported`/`invalidArg`）覆盖其余四类
- [x] resolveType 列类型经 D7 映射落 BasicTypeInfo——`resolveTypeUsesD7ColumnMapping` 断言 STRING/INT→LONG 提升/count→LONG/avg→DOUBLE/max 透传，全绿
- [x] `./mvnw test -pl nop-stream/nop-stream-sql` 绿 24/24——实跑复验；-am 全量门按 plan 归 Phase 3 收口复核（未虚勾）

## 2. 实跑与判别性抽查（24 用例）

实跑命令与结果：`./mvnw test -pl nop-stream/nop-stream-sql` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS（exit 0）。

判别性抽查（逐条读断言源码确认非恒真）：

| 语义点 | 断言 | 判别性 |
|---|---|---|
| sum 整型提升 long | `assertTrue(r instanceof Long)`（sum 1+2） | 强：若返回 Integer 即失败 |
| 空集 null | sum/avg/min 各 `assertNull`（全 null 输入） | 强：与 count 空集 0 形成对照 |
| count(*) vs count(expr) | 同三行（含一 null）：count(expr)=2、count(*)=3、空集=0 | 强：两形态数值可区分 |
| avg double | `assertEquals(2.0, …)`（1+3 均值） | 强：Double 相等，非 2L 伪装 |
| min/max 字符串自然序 | min("b","a")="a"、max 数值序 | 强 |
| DISTINCT fail-fast | `withDistinct(true).create(arg)` assertThrows | 通过（该用例仅钉异常类型未钉码串，见 §4 M-2） |
| 子集外码串 | `nop.err.stream.invalid-arg` 两处显式 assertEquals | 强 |

## 3. Anti-Hollow：编译链与消费契约

1. **AggregateFunction 四方法 + Serializable**：`StreamSqlAggregation.BaseAgg implements io.nop.stream.core.common.functions.AggregateFunction<Object,Object,Object>`（import 于 ：10）；core 接口实读确认 `extends Serializable` 且声明 createAccumulator/add/getResult/merge 四方法；CountStarAgg/CountAgg/SumAgg/AvgAgg/MinMaxAgg 五类各自 override 全部四方法（无空体、无 super 占位）；均有 serialVersionUID；`serializationPreservesAggregation` 实做 ObjectOutputStream 往返并断言反序列化后 `getResult=2L`。**Serializable 断言为运行时实症，非仅类型声明。**
2. **WindowedStream.aggregate 兼容性（WI8c 零适配声明）**：`WindowedStreamImpl<T,K,W>.aggregate(AggregateFunction<T,ACC,R>)`（core `datastream/WindowedStreamImpl.java:247`）。`StreamSqlAggregation.create` 返回 `AggregateFunction<Object,Object,Object>`——当窗口流元素类型为 Object（stream SQL 记录模型即 Map/bean 无类型形态）时直接作实参，无需任何适配层；泛型不变性下若 WI8c 未来用特化 T 需在调用点做一次类型声明，属 WI8c 消费面事务。**「返回类型即 core 算子面契约、无并行接口、无 adapter 类」成立，零适配声明属实。**
3. **ERR_EQL_UNSUPPORTED_EVAL_EXPR 零泄漏**：grep `nop-stream/nop-stream-sql/src` 仅 2 处命中，均为 javadoc 行文（编译器类注释与测试类注释陈述「不泄漏」），主代码零功能引用、零 EQL 错误码 import。
4. **fnId 绑定关系（WI8c 消费契约，对照 roadmap WI8c 行）**：`StreamSqlAggregations.resolve` 未知名返回 null（A4「bean 前置、目录兜底」全序可成立）；`builtinIds()` 五 id 与 BaseRule.g4 小写闭集对齐（grammar 实读：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` sqlAggregateFunction :218-220 / sqlIdentifier_agg_ :272-274，MAX/MIN/SUM/COUNT/AVG + distinct + selectAll，与 plan baseline 一致）；WI8c 行要求的四项 fail-fast 全部有支撑——未知 fnId（resolve null → 调用方 fail-fast）、参数个数（`allowsNoArg()` count=0..1 其余恰 1，`create(null)` 对恰 1 函数抛 invalid-arg）、类型不符（`resultType(argType)`）、表达式编译失败（compileScalar 子集外抛码）；累加语义独占于 `StreamSqlAggregation` 内部类，**WI8c 只声明 id 与参数形状、不复制聚合语义的边界成立**。
5. **D7 桥接（对照 WI8b）**：`StreamSchemaRegistry`（nop-stream-flow `io/nop/stream/flow/builder/StreamSchemaRegistry.java`）FieldSpec 暴露 `getName():String` / `getType():BasicTypeInfo<?>`（九名闭集经 resolveManagedType）；`resolveType(SqlExpr, Function<String,BasicTypeInfo<?>>)` 形参与之精确同构（测试即以 `Map<String,BasicTypeInfo<?>>::get` 镜像 FieldSpec 形态），桥接为调用方一行 map 构建，无阻抗失配。

## 4. Minor 发现（均不阻塞 PASS）

- **M-1**：`SumAgg` 采用整型/浮点双轨累加器，同列混型输入（如 Integer 与 Double 混杂，或浮点轨恰好抵消 0.0）时 getResult 只返回非零浮点轨，产生静默错误结果。裁定：**plan 类型模型之外**——D7 每列单一 BasicTypeInfo，混型列在 schema 化模型下不可达，无 Exit Criterion 覆盖；但与「无静默近似」精神有张力，建议 WI16/WI17 落类型系统时改为单轨累加或混型 fail-fast。登记为 roadmap 级 residual 建议，非本 plan 缺陷。
- **M-2**：`distinctFailsFast` 仅 assertThrows RuntimeException 未钉 `nop.err.stream.invalid-arg` 码串（同一 throw 源在编译器测试两处已钉）。建议后续顺带补钉。
- **M-3**：plan Goals 行写目录条目暴露 `arity()`，live API 为 `allowsNoArg()`——命名漂移，语义等价（count 0..1 / 其余恰 1，`create(null)` fail-fast 兜底恰 1）；plan guide 规则 #10 本就不该在 plan 写签名，不构成偏差缺陷。
- **M-4**：`check-doc-links --strict` 退出码 0（0 errors），另有 4 条 warning：3 条为 nop-bytecode 旧 plan 既存，1 条为本 plan :20 的行内代码 `_gen/_SqlAggregateFunction.java` 被链接解析启发式误判（实际是生成文件位置引注，非链接）。纯外观，不阻塞。
- **M-5**：Phase 1「确认红」为不可从 live 树复现的历史过程声明；以绿态全量落地与用例存在性替代核验，后续 WI 惯例可提交红态 stub 或记录编译失败输出。

## 5. 工具门禁（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 4 warnings（见 M-4） |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-sql --severity high` | 0 | Critical/High/Medium/Low 全 0 |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK` |
| `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md --strict`（备案） | 0 | 非 completed plan 且 Phase 3 未勾 → warnings only，符合收口前预期态 |

## 6. 无静默跳过检查

- 主代码（6 文件）grep：无 TODO/FIXME、无空方法体、无吞异常 catch（唯一 catch 在 `RecordColumnAccess.get` 反射调用处，wrap 后抛 `ERR_STREAM_INVALID_ARG`）。
- `return null` 命中 9 处：8 处为标量三值逻辑 null 传播（plan 显式裁定语义），1 处为 `SumAgg.getResult` 空集 null（roadmap 完成判定明文「空集 null」）——均为规格行为，非 placeholder。
- 子集外每条路径（正则函数/CASE/CAST/标量位聚合/null AST/未知名 fnId/DISTINCT/参数缺失/不可比较/强转失败）均显式抛错，有测试或单一 throw 源背书。

## 7. plan 文本一致性 + git 纪律

- Plan Status: **active**；Phase 1 Status **completed**（4/4 勾）；Phase 2 Status **completed**（6/6 勾，全部 live 落地）；Phase 3 Status **planned** 全未勾——本 audit 即 Phase 3 第 1 项，属预期；Closure Gates 未勾属预期。**未发现「已勾选但实际未做」。**
- `ai-dev/logs/2026/10-02.md` WI9 条目（L3-8）与 live 事实逐项一致（含 NopException.param 强转等编译期发现记录）。
- git status：仅 `M ai-dev/logs/2026/10-02.md`（+7 行）+ 未跟踪新文件 `ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md` 与 `nop-stream/nop-stream-sql/src/`；`git ls-files` 确认模块内已跟踪文件仅 pom.xml 与 `_vfs/nop/schema/.gitkeep`（WI8b 遗产，本次零改动）；**零 `_gen`/`_` 前缀文件 diff**；改动范围 = nop-stream-sql 模块 + ai-dev 文档，与 scope 完全吻合。

## 8. 结论

Phase 1/2 的全部 Exit Criteria 与勾选项在 live repo 逐一成立；24/24 实跑绿且关键语义断言具判别性；编译链 Anti-Hollow（core AggregateFunction 四方法 + Serializable 实症 + WindowedStream.aggregate 签名兼容）通过；ERR_EQL_UNSUPPORTED_EVAL_EXPR 零功能泄漏；A4 fnId 绑定关系与 WI8c 消费契约成立；D7 桥接形态吻合；三大门禁退出码 0；无静默跳过；git 纪律干净。

**裁定 PASS**。可进入 Phase 3 剩余收口（roadmap WI9 → done 单层无嵌套括注、parseRoadmapMarkdown 31+7 复核、plan Closure 段落证据写入、check-plan-checklist --strict）。§4 五项 Minor 均不阻塞；建议 M-1 作为 residual 登记至 WI16/WI17 的后续考量。
