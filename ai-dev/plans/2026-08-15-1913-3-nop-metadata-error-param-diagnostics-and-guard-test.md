# nop-metadata 错误码识别性参数收口 + INV-LIMIT 守卫修复（2026-08-15 multi-audit P1-6/7/9）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 3 / 再审计 remediation（执行顺序 3/3）
> Source: `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P1-6 错误码识别性参数漂移家族、P1-7 方言白名单参数键错配、P1-9 INV-LIMIT 守卫测试假绿）
> Related: 前置 `2026-08-15-1913-1`、`2026-08-15-1913-2`（执行顺序先行，无硬依赖）
> Draft Review: R1（Major×3+Minor×5）→ 修订（扫描器前置、三轨修法、点位分类）→ R2（R1 全 Resolved；新增 Major×1 变量形态错误码盲区 + Minor×3——R2 预定义 consensus 条件项，均已修订：UNRESOLVED 清单口径 + 3 疑似点入分母、轨 3 换码预裁定（:209 影响面）、分类勘误、断言计数）→ consensus 达成，2026-08-15 转 active

## Purpose

修复错误消息中识别性参数不渲染/错配的家族缺陷（P1-6 的识别性占位符漏传 + P1-7 的键错配），使失败对象的身份信息在最终用户可见错误消息中真实可定位；修复 INV-LIMIT 不变式守卫测试中一行对"移除负 limit 检查"变异假绿的缺陷（P1-9）；沿 invariant-loop 方法论先落地占位符↔调用点一致性静态门禁（作为权威分母驱动类别清扫），防同族复发。

## Current Baseline

> 事实为 2026-08-15 live repo 实测（代表点逐条核对 + R1 审查子代理独立复核，含全部行号）。

- **P1-6（audit 计 11 个识别性参数缺失点；live 枚举为 13 个 throw 语句 = 11 活点 + 2 死点，另有 3 处变量形态疑似点，见下）**：ErrorCode 描述占位符与 throw 点 `.param()` 键漂移，框架行为（`ErrorMessageManager.java:143-153`）对缺参渲染字面 `{placeholder}`；**附加事实**：param 值为 null 时渲染**空串**——传 null 是"键覆盖占位符但消息仍无信息量"的空壳修复，禁止。点位按修法分类（R1/R2 核实的可得性事实）：
  - **可直接补齐（上下文有值）**：`NopMetaTableJoinBizModel.java:164-166`（注意：`validateTableEndpoint(:152)` 签名与 save(:65)→validateJoin(:78)→validateJoinSide(:97) 全链路**不持有 joinId**——joinId 只在 save 的 data map 中且 create 路径下尚不存在；须从 data 下沉或换码，见 Phase 2 分类）、`MetaTableFieldResolver.java:383`（`{elementIndex}`，同方法 :367 传齐——自相矛盾参考实现；位于增强 for 循环内，取下标需改循环结构，归入"值语义需改造"档）、`NopMetaReconciliationResultBizModel.java:159/165`（**死代码**：`toInt/toStr`（:154-172）main 零调用、throw 不可达，对应 P2-23 死码登记）；
  - **需穿参（静态工具方法无身份值，多跳签名传参）**：`MetaTableQueryExecutor.java:132`（`{metaTableId}`，方法体首行 :101 起的 `executeQuery`）、`AggregationHelper.java:135`（`requireName` static，`{metaTableId}`）、`CrossDbJoinMerger.java:171`（`firstNonNullKeyType` static，`{joinId}`）；
  - **null 防御分支（值语义上无身份可传）**：`MetaTableFieldResolver.java:84`、`MetaTableReferenceResolver.java:87`（table==null 时无 metaTableId）——应换用无必需占位符的错误码或调整该错误码占位符；
  - **值语义不存在**：`MetaTableFieldResolver.java:348/355/359`（buildSql null/空分支、整段 JSON 解析失败/非数组——无"元素下标"可传）；
  - **键名澄清**：`MemoryFilterEvaluator.java:86` 缺的是 define 中的 `{name}`（`{op} name={name}`）——同文件 :184 已示范 `node.getAttr(FILTER_ATTR_NAME)` 可取 name，归"可直接补齐"档；
  - **变量形态错误码（R2 发现，须入分母）**：错误码为方法参数/局部变量的 throw 点共 4 处——`MetaTableFieldResolver.java:214/223/234`（`errOnInvalid` 参数，调用方传 `ERR_MEASURE_FIELD_NOT_FOUND`/`ERR_DIMENSION_FIELD_NOT_FOUND`）与 `NopMetaLineageEdgeBizModel.java:115`（`errorCode` 参数）。其中 :214/:223/:234 为**疑似真实缺参点**（两 define 声明 5 个占位符含 `availableFields`/`allowedEntityIds`，:214 仅传 3 参、:223 缺 availableFields、:234 缺 allowedEntityIds）——静态扫描不可解析，须经调用方映射人工归类后并入 red list 分母。
- **P1-7**：`service/sync/ExternalTableStructureReader.java:139-143`（live 核对）`requireSupportedProductName` 抛 `ERR_DATASOURCE_TYPE_NOT_SUPPORTED` 传 `.param(ARG_DATABASE_PRODUCT_NAME, ...)`，而错误码声明占位符 `{datasourceType}`（`DataSourceErrors.java:13-15`，声明 `ARG_DATASOURCE_TYPE`）——被拒产品名永不渲染；旁证：同码在 `service/connection/MetaDataSourceConnectionProcessor.java:216` 传 `.param("datasourceType",...)` 用法正确。**方案 B（新增 product-name 语义错误码）的隐藏成本（R2 精确核实）**：`TestExternalTableStructureReader.java:107` 精确断言抛 `ERR_DATASOURCE_TYPE_NOT_SUPPORTED`（:54/:57 为宽断言不受影响）——换码将打破该精确断言并变更 GraphQL 客户端可见错误码标识。
- **P1-9**：`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitNegativeValueInvariant.java:65-91`（live 核对）——`inline-searchMetadata` 行直接 `new NopMetaSearchBizModel().searchMetadata(null, null, -1, null)`，被测侧 `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java` 分支顺序为 **limit 检查（:66-75）在前、`searchEngine==null`（:83-86）在后**；limit=-1 时必抛 `ERR_SEARCH_LIMIT_INVALID`（`nop.err.metadata.search-limit-invalid`，`MiscErrors.java:166-168`）——**精确错误码断言在直接 new 下可行**（移除 limit 检查后落到 `ERR_SEARCH_ENGINE_UNAVAILABLE`，精确断言变红）。测试只断言 `assertThrows(NopException.class)`（:68 为 4 行共享的单一断言）→ 删除 limit 检查后仍绿，区分力为零。该测试类是 CI invariant-gate 指定防线（经 `ai-dev/tools/run-nop-metadata-invariants.sh` 调用，INV-LIMIT guard 4 入径之一）。现有真守卫：`service/search/` 下 `TestNopMetadataSearchIntegration.java:147-155`。
- **防复发土壤（audit P2-10/P2-11 记载）**：17 个 ErrorCode define 声明参数与描述占位符不一致 + `.param()` 键 454 处字面量 vs 199 处 ARG_* 常量双轨混用——本计划落地"throw 点 ↔ 占位符"一致性门禁（P1-6 建议项），define 面漂移与双轨治理留 backlog。
- **门禁先例与扫描器现实（R1 核实）**：`ai-dev/tools/run-nop-metadata-invariants.sh`（5 guards fail-fast 链）+ `.github/workflows/maven.yml:45-73` invariant-gate；`check-silent-wrong-result.mjs`（注释剥离 + `// invariant-ok:` 豁免机制 + mode b 快照对账）、`check-sensitive-literal-leak.mjs`（行级共现 + `--fixture` 自验）为实现模式参考。**扫描器必须处理的假阳性/口径现实**：main 中有 5 处 `catch` 块 `e.param(...)` 重抛增补（如 `AggregationHelper.java:170`、`ExternalAggregationProcessor.java:52`——audit 复核时已排除的假阳性类）；`.param()` 键有字面量、限定 ARG 常量（`NopMetadataErrors.ARG_X`）、**非限定常量**（`NopMetaTagLabelBizModel.java:138` 直接用裸 `ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED`）三种形态；ErrorCode define 分散在 10 个子接口经 `NopMetadataErrors` 组合；main 范围 `new NopMetadataException(` 共 341 处。错误消息断言先例：`TestMetaQualityRuleExecutorErrorParams.java:36-50`（"renders real value + no literal {placeholder}"）。

## Goals

- 13 处中 11 个活点全部收口：识别性占位符在最终渲染消息中真实呈现（补齐/穿参/换码三轨，见 Phase 2）；2 个死点显式裁定（随 P2-23 死码删除收口，不伪造补齐）；3 处变量形态疑似点（`MetaTableFieldResolver:214/223/234`）经人工归类确认后并入同三分母收口（确认缺参则修，误报则记录）。
- `requireSupportedProductName` 的被拒产品名在最终错误消息中真实渲染。
- INV-LIMIT `inline-searchMetadata` 行对"移除负 limit 检查"变异变红（精确错误码断言）。
- 新增占位符↔调用点一致性静态门禁（`check-error-param-consistency.mjs`）：作为类别清扫的权威分母先行落地；修复后全模块退出码 0（豁免面仅限显式裁定项）；接入 `run-nop-metadata-invariants.sh` 聚合链。

## Non-Goals

- 不处理 P2-09（6 处 `{error}` 附注参数缺失——识别性参数齐备，仅附注性，独立 backlog；扫描器对 `{error}` 占位符显式豁免，见 Phase 1 口径）。
- 不处理 P2-10/P2-12（define 面声明漂移 17 处的批量治理、错误码 i18n 零覆盖——后者需 ask-first 裁定）。**边界澄清**：本计划允许对"值语义不存在/null 防御分支"的个别错误码做**定点占位符调整**（记录裁定）；禁止的是 P2-10 式的批量 define 面重命名治理。
- 不做 `.param()` 字面量→ARG_* 常量的全量双轨收敛（P2-11，结构治理项）。
- 不删除 P2-23 死代码（ReconciliationResultBizModel toInt/toStr——死点只裁定不修参数；删除归 backlog 独立收口）。
- 不修改 `run-nop-metadata-invariants.sh` 既有 5 guards 的行为（只追加第 6 个 guard）。

## Scope

### In Scope

- `ai-dev/tools/` 下新建 `check-error-param-consistency.mjs` 及其口径裁定。
- P1-6 的 11 个活点修复（三轨分类）+ 2 个死点裁定记录。
- `ExternalTableStructureReader.requireSupportedProductName` 参数键/错误码修正。
- `TestLimitNegativeValueInvariant` 假绿行修复。
- `run-nop-metadata-invariants.sh` 追加 guard + `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md` 登记 INV-ERROR-PARAM 条目。

### Out Of Scope

- F2 SSRF（`2026-08-15-1913-1`）；API 写路径与契约语义（`2026-08-15-1913-2`）。
- 错误码 i18n、define 面批量重命名、死错误码清理（P2-10/P2-12/P2-23）。

## Execution Plan

### Phase 1 - INV-ERROR-PARAM 扫描器先行（权威分母 + 口径裁定）

Status: completed
Targets: `ai-dev/tools/` 下新建 `check-error-param-consistency.mjs`

- Item Types: `Proof | Decision`

- [x] **[Proof]** 新扫描器落地：对 nop-metadata main 范围内 `new NopMetadataException(...)` throw 点与其后同语句链的 `.param(...)` 键，交叉 ErrorCode define 描述占位符，"识别性占位符无对应 `.param` 键" → 命中。实现要求：
  - `.param()` 键解析支持三种形态：字面量字符串、限定常量（`NopMetadataErrors.ARG_X`）、非限定常量（裸 `ARG_X`/裸 ErrorCode 常量引用）；
  - ErrorCode define 解析覆盖 10 个子接口（`*Errors.java`）经 `NopMetadataErrors` 组合的占位符描述（含 `{xxx}` 提取）；
  - 沿 `check-silent-wrong-result.mjs` 的注释剥离与 `// invariant-ok:` 豁免机制、`check-sensitive-literal-leak.mjs` 的文件遍历与 `--fixture` 自验模式；
  - **假阳性/盲区口径（硬要求）**：(a) `catch` 块内 `e.param(...)` 重抛增补形态（live 实测 5 处，如 `AggregationHelper.java:170`、`ExternalAggregationProcessor.java:52`）不命中——上游已带参数的重抛不是缺参点；(b) `{error}` 占位符**显式豁免**（P2-09 附注性参数，Non-Goal）；(c) 死代码点位经豁免标注处理（见 Phase 2）；(d) **变量形态错误码**（错误码为方法参数/局部变量，共 4 处：`MetaTableFieldResolver.java:214/223/234`、`NopMetaLineageEdgeBizModel.java:115`）静态不可解析——扫描器输出 **UNRESOLVED 清单**强制人工归类（经调用方错误码映射核对是否缺参），不得静默跳过；其中 3 处疑似真实缺缺点（见 Current Baseline）须并入 red list 分母。
- [x] **[Proof]** 全量首跑：输出全模块 red list + 变量形态 UNRESOLVED 清单（预期含 P1-6 的 11 活点 + 3 疑似变量形态点 + 可能的清扫增量），UNRESOLVED 逐点人工归类后并入分母，全部记录于 daily log。
- [x] **[Proof]** 注毒自验：对 1 个已知历史形态（如临时在测试 fixture 中构造缺参 throw）扫描器变红（anti-hollow，非空壳脚本）。
- [x] **[Decision]** 退出模式裁定：修复后若零命中（豁免仅 `{error}` + `// invariant-ok:` 标注 + 死点）→ 零命中 hard-gate；若存在不可消除的已裁定豁免点 → mode b 基线棘轮（沿 Cycle 2 先例）。裁定记 daily log。

Exit Criteria:

- [x] 扫描器存在且 `node ai-dev/tools/check-error-param-consistency.mjs` 可运行，`--fixture` 自验通过（注毒变红/修复变绿）。
- [x] 全量首跑 red list 已记录（daily log），至少覆盖 audit 列示 11 活点。
- [x] 假阳性/盲区口径（catch 增补/`{error}` 豁免/死点豁免/变量形态 UNRESOLVED 清单）在脚本 README 或头部注释中写明。
- [x] `ai-dev/logs/` 对应日期条目已更新；No owner-doc update required: 工具脚本不影响 owner 行为（catalog 登记在 Phase 3）。

### Phase 2 - P1-6 + P1-7：识别性参数收口（三轨修法，扫描器驱动）

Status: completed
Targets: Current Baseline 分类的 13 点所在文件 + `ExternalTableStructureReader.java` +（清扫增量）

- Item Types: `Fix | Decision | Proof`

- [x] **[Fix]** 按 Phase 1 red list（权威分母 = audit 11 活点 ∪ 清扫增量）三轨修复，每点归类记录（daily log）：
  - **轨 1 补齐**（上下文有值）：直接补 `.param()`；`NopMetaTableJoinBizModel:164` 的 `{joinId}` 需从 `save` 的 data map 下沉穿参（update 路径可用）；create 路径 joinId 尚不存在时：**禁止传 null**（渲染空串 = 空壳修复），**预裁定换用无 joinId 必需占位符的错误码而非调整该码占位符**——`ERR_JOIN_TABLE_TYPE_NOT_ALLOWED` 有第二个 throw 点 `MetaJoinExecutor.java:209`（joinId 传齐），定点削占位符会使 :209 的 joinId 不再渲染、在另一点重引入本族缺陷；若执行时推翻预裁定须记录 :209 影响面分析；
  - **轨 2 穿参**（静态工具方法多跳签名）：`MetaTableQueryExecutor`/`AggregationHelper`/`CrossDbJoinMerger` 三处经调用链把身份值传入（调用方均持有 metaTableId/joinId）；
  - **轨 3 换码**（null 防御分支或值语义不存在）：`MetaTableFieldResolver:84`、`MetaTableReferenceResolver:87`、`MetaTableFieldResolver:348/355/359/383`、create 路径的 `NopMetaTableJoinBizModel:164`（如轨 1 下沉不可行）——换用无必需占位符的错误码；定点调整既有错误码占位符仅在该码**无其他已传齐 throw 点**时允许（与 Non-Goal 边界一致：定点、记录裁定、非批量治理）。
- [x] **[Decision]** 死点裁定：`NopMetaReconciliationResultBizModel:159/165`（`toInt/toStr` 死代码，throw 不可达）**不补参数**（对不可达代码补参无用户可见收益），经 `// invariant-ok:` 豁免标注 + 理由（P2-23 死码删除后豁免随之消除）；裁定记 daily log。
- [x] **[Fix]** P1-7（预裁定方案 A）：`requireSupportedProductName` 改传与 `{datasourceType}` 一致的键且值为被拒产品名（`.param("datasourceType", productName)` 或 ARG_DATASOURCE_TYPE）——不换错误码（方案 B 会打破 `TestExternalTableStructureReader` 3+ 处断言并变更客户端可见错误码标识，成本高收益同）；若执行时推翻预裁定须记录完整理由。
- [x] **[Proof]** 回归测试：至少 3 个代表点（`NopMetaTableJoinBizModel:164`、`requireSupportedProductName`、`MetaTableFieldResolver` elementIndex 族）断言最终渲染消息含真实身份值、不含字面 `{placeholder}`（沿 `TestMetaQualityRuleExecutorErrorParams.java:36-50` 既有模式）；`MetaDataSourceConnectionProcessor.java:216` 正确用法不回退。
- [x] **[Proof]** Phase 1 扫描器复跑：修复 + 豁免标注后退出码 0（活点零命中；豁免面 = `{error}` + 死点 `// invariant-ok:` + 已裁定项）。

Exit Criteria:

- [x] red list 全部点位归入三轨之一或死点裁定，无一悬挂；分类清单记录于 daily log。
- [x] P1-7 被拒产品名在渲染消息中可见（方案 A 落地或推翻预裁定有完整记录）。
- [x] 代表点回归测试全绿；无"传 null 凑键覆盖"的空壳修复（代码审查项）。
- [x] `node ai-dev/tools/check-error-param-consistency.mjs` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新（含分类清单）。
- [x] owner-doc 裁定：错误处理约定如 owner 文档涉及（`docs-for-ai/03-modules/nop-metadata.md` 错误处理叙述），定点换码项同步；否则显式记录 No owner-doc update required。

### Phase 3 - P1-9 守卫修复 + 门禁接入聚合链

Status: completed
Targets: `TestLimitNegativeValueInvariant.java`、`run-nop-metadata-invariants.sh`、`invariant-catalog.md`

- Item Types: `Fix | Proof`

- [x] **[Fix]** `inline-searchMetadata` 行改为精确错误码断言（断言 `ERR_SEARCH_LIMIT_INVALID`/`nop.err.metadata.search-limit-invalid`；R1 已核实分支顺序保证 limit=-1 必抛此码且直接 new 下可行）。实现注意：:68 的 `assertThrows` 为 4 行共享，需重构为仅 searchMetadata 行钉精确码（另 3 行错误码不同，保持现有宽断言或各自钉码，取实施成本低者）。
- [x] **[Proof]** 变异验证（手工 Proof，记录于 daily log）：临时移除 `NopMetaSearchBizModel` 的负 limit 检查 → 该测试行变红；恢复后全绿。其余 3 行（normalizeQueryLimit/normalizeJoinQueryLimit 反射路径）确认不受影响。
- [x] **[Proof]** `check-error-param-consistency.mjs` 接入 `run-nop-metadata-invariants.sh` 为第 6 个 guard（fail-fast 链追加，更新脚本头注释 guards 清单；核对 `.github/workflows/maven.yml` 是否有 guard 枚举需同步）。
- [x] **[Proof]** `invariant-catalog.md` 登记 INV-ERROR-PARAM 条目（陈述 / 覆盖失败族 = 2026-08-15 P1-6+P1-7 / 历史 audit-finding-ID 证据 / 检测方法，沿既有条目格式）。

Exit Criteria:

- [x] 该测试行对"移除负 limit 检查"变异变红（变异验证记录存在）。
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest=TestLimitNegativeValueInvariant` 全绿。
- [x] `run-nop-metadata-invariants.sh` 全链（6 guards）退出码 0；guard 清单注释同步。
- [x] `invariant-catalog.md` 新条目存在且引用源 audit 路径。
- [x] `ai-dev/logs/` 对应日期条目已更新；No owner-doc update required: 守卫与工具变更不影响 owner 契约叙述（catalog 属审计工件）。

## Closure Gates

> 本计划含代码与工具变更，构建验证条目适用。

- [x] P1-6：11 活点 + 清扫增量按三轨收口，渲染消息无字面 `{placeholder}`、无 null 空壳修复（confirmed live defect 已修复）；2 死点有显式裁定
- [x] P1-7：被拒产品名真实渲染（confirmed contract drift 已收敛）
- [x] P1-9：INV-LIMIT 假绿行修复且有变异验证证据（confirmed 无效负面测试已修复）
- [x] INV-ERROR-PARAM 门禁存在、注毒自验通过、修复后退出码 0、入聚合链、入 catalog
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证扫描器注毒变红（非空壳脚本）+ 代表点渲染断言测试真实存在且非恒真
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle 对本计划改动文件零新增违规（上游 `nop-api-core` pre-existing 基线，整体 `checkstyle:check` 历史性 exit 1——以改动文件零新增为基准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

### P2-09 `{error}` 附注参数 6 处

- Classification: `optimization candidate`
- Why Not Blocking Closure: 识别性参数齐备、信息不丢（cause 链保留），仅描述尾部附注渲染 `-- {error}` 字面量；扫描器显式豁免 `{error}` 占位符；与本计划"识别性参数"closure 面不同。
- Successor Required: `no`
- Successor Path: roadmap Follow-up Backlog（P2-09 条目）

### P2-10/P2-11 define 面漂移与双轨治理；P2-23 死码删除

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: define 面批量治理无用户可见行为收益（运行时未断裂）；ReconciliationResultBizModel 死码删除是独立清理项（死点经豁免标注，删除后豁免自然消除）。
- Successor Required: `no`
- Successor Path: roadmap Follow-up Backlog（P2-10/P2-11/P2-23 条目）

## Non-Blocking Follow-ups

- P2-12 错误码 i18n（需 ask-first 裁定语言契约方向）——见 roadmap Follow-up Backlog。

## Closure

Status Note: 3 Phase 全部完成且经独立 closure audit approved——P1-6 的 11 活点 + 1 清扫增量 + 3 变量形态真实缺参点全部按三轨收口（渲染消息真实呈现身份值、零 null 空壳、零占位符削除），P1-7 方言门禁键错配修正（方案 A，错误码标识不变），P1-9 守卫假绿行修复（4 行全钉精确错误码 + 变异验证）；INV-ERROR-PARAM 零命中 hard-gate 沉淀入 6-guard 聚合链与 catalog；豁免面 6 条（2 死点 P2-23 + 4 变量形态人工归类）全部显式列出无静默盲区。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，review-only 零文件修改）`ses_ff9667230ffefnp26Et6dzLiWD`
- Evidence:
  - Phase 1（3/3 PASS）：扫描器存在 + `--fixture` exit 0（16 样例）+ `--module` exit 0；四项口径（catch 增补排除/`{error}` 豁免/死点豁免/UNRESOLVED 强制归类）头注释 :34-54 在位；注毒自验（fixture violation 样例命中 + live 删参变红记录）非空壳。
  - Phase 2（3/3 PASS）：11 活点 + 增量逐点 live 核对（NopMetaTableJoinBizModel:183-192 双路径换码/下沉、MetaTableFieldResolver 5 处 + 3 变量形态 5/5 参数、MetaTableQueryExecutor/AggregationHelper/CrossDbJoinMerger/MemoryFilterEvaluator/MetaTableReferenceResolver/ExternalTableStructureReader 全部到位，`Set.of()` 非 null）；MetaJoinExecutor:210-212 joinId 传齐不回退（预裁定约束成立）；代表点测试 3+6+8 例全绿（正断言含真实值 + 负断言无字面占位符，非恒真）。
  - Phase 3（3/3 PASS）：TestLimitNegativeValueInvariant 4 行精确码 `assertEquals` + 4/4 绿；6-guard 聚合链 live 复跑 exit 0；invariant-catalog INV-ERROR-PARAM 条目引用源 audit 路径。
  - Deferred 诚实性：P2-09/P2-10/P2-11/P2-23 均为计划内 Non-Goal/预裁定项，无 in-scope defect 降级。
  - `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（service 1227/0/0；一次 nop-stream 时序基准测试负载竞态 flake 与本计划无关——0 个 nop-stream 文件被改动，复跑绿）。
  - `check-plan-checklist.mjs --strict` 退出码 0（本 Closure Evidence 写入后复跑）；`scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0。
  - checkstyle：仓库权威 qa 配置（`checkstyle.xml` + `-Pqa`）逐条比对，16 个改动 main 文件 HEAD vs now 违规条目完全一致 + 新增测试文件零违规 = 零新增。
  - 附注（非阻塞）：源审计 `2026-08-15-0559-multi-audit`（Audit Status: planned）的两个后继计划（1913-2/1913-3）现均 completed，其收口留待 mission 审计收口流程处理（本计划 front matter 无 `> Source Audits:` 行，按 mission 约定不在此改动）；Phase 3 首跑全链时暴露并修复先在 guard 1 红点（`MetaDataSourceConnectionProcessor.percentDecode`，plan 1913-1 提交引入，benign-miss 形式化修复，daily log 留痕）。

Follow-up:

- 无剩余 plan-owned work。Non-blocking：P2-09（`{error}` 附注参数 6 处）、P2-10/P2-11（define 面漂移与双轨治理）、P2-23（ReconciliationResultBizModel 死码删除——删除后 2 处 `// invariant-ok:` 豁免自然消除）、P2-12（错误码 i18n，ask-first）均已在 roadmap Follow-up Backlog 登记。
