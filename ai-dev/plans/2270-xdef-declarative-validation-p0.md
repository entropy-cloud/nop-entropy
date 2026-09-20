# 2270 XDef 描述式校验 P0：解析打通 + unique/mutex/require 执行

> Plan Status: active
> Last Reviewed: 2026-09-20
> Source: `ai-dev/design/xdef-declarative-validation-design.md`（3-agent 对抗审查 CONSENSUS，审计记录 `ai-dev/audits/2026-09/2026-09-20-0854-adversarial-review-xdef-declarative-validation.md`）
> Related: 无

## Purpose

把设计文档的 **P0 范围**落地为可执行代码：xdef 约束声明（`xdef:check-unique` / `check-mutex` / `check-require` / `check-ref`）从"元模型已声明、解析器静默跳过、字段恒空"收口到"解析填充 + 声明期 fail-fast + unique/mutex/require 真实执行"，并消灭"声明了但什么都不发生"的静默中间态（check-ref 执行、def-type、scope=global 显式报未实现）。

## Current Baseline

- 约束语法已在 `xdef.xdef` 声明（`:125-127` check-unique、`:137-140` check-ref、`:143-145` check-mutex、`:152-153` check-require、`:107-109` def-type），模型字段已生成（`_XDefinition.java:47-86`），但 `XDefinitionParser.parseChildren:654-661` 跳过所有 keys.NS 子元素，五类字段恒空、getter 无消费点（设计文档 §2.2，已经两轮审查核实）。
- `XDslValidator.validateNode`（`:85-173`）执行结构/局部唯一性校验；公有入口 `validate(XNode, IXDefNode, boolean)`（`:81-83`）是全部现存调用方（DslNodeLoader 经 `SchemaLoader.validateNode:41-43`、DslModelParser、AiCoderHelper、LocalFileOperator、ModelBasedPromptTemplate）的单点收敛，且都传根 defNode，`XDefinition.getRootNode()` 返回 this（设计 §3.1）。AiCoderHelper 在 AI 修复循环中对新树反复调 validate——阶段二必须只读幂等（设计已保证）。
- `XDefCheckRequire` 因元模型漏 `unique-attr="id"` 生成为单数字段；`XDefCheckRef` 缺 targetSelect/targetProp/keyProp 三字段；`XDefAbstractCheck.errorCode` 现为必填。四类 `_XDefCheck*` 生成基类的 setter 齐全（`_XDefAbstractCheck.setId/setSelect/setErrorCode/setMessage`、`_XDefCheckUnique.setProp/setScope` 等），手写空壳类无需扩展即可填充。
- **再生成机制（对抗审查核实的硬事实）**：exec-maven-plugin（CodeGenTask）只存在于 `nop-kernel/pom.xml` 的 `<pluginManagement>`（:291-380），`nop-kernel/nop-xlang/pom.xml` 未声明该插件——`./mvnw generate-sources -pl nop-kernel/nop-xlang` **不会执行任何生成**（`docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` 常见坑 8）。有效命令：先 `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests`（CodeGenTask 从 classpath 构件解析 `/nop/schema/xdef.xdef`，不 install 会读到旧版），再 `./mvnw exec:java@precompile -pl nop-kernel/nop-xlang`（`@executionId` 吃到 pluginManagement 中 id=precompile 的配置）。
- 无任何业务 xdef 使用 check-*/def-type（全仓库 grep 仅 xdef.xdef 自身，其内 `xdef:` 为业务名字空间、keys.NS 为 `xdef-meta`，自举天然安全，设计 §3.6.4）。
- 可复用设施（对抗审查核实）：`XPathHelper.parseXSelector`（静态 LocalCache(1000)）+ `XNode.selectMany(selector)` 返回 `Collection<?>` 逐元素 cast（范式见 `TestXPath`）；`XDslParseHelper.parseAttrEnumValue`（scope 枚举转换）；`XLangCompileTool.compileSimpleExpr` 编译单表达式；`_XDefCheckRequire._condition` 字段类型即 `IEvalAction`（编译一次直接存字段，与 `_XDefinition._xdefPreParse` 同构）；`XLang.newEvalScope()` + `setLocalValue` 绑定 `node` 变量；`ErrorCode` 有 public 构造器 `ErrorCode(int, String, String, String...)`。
- 测试载体范式：`TestXDefParse`（CoreInitialization + 解析全部内置 xdef）、`TestXDefMergeLoader`（`assertThrows(NopException)` + `e.getErrorCode()` 断言）、测试资源置于 `nop-kernel/nop-xlang/src/test/resources/_vfs/test/`。

## Goals

- `xdef.xdef` 完成三处修订并重新生成 `_gen`：check-require 补 `xdef-meta:unique-attr="id"`（字段变 KeyedList）、check-ref 补 targetSelect/targetProp/keyProp 可选属性、errorCode 改可选。
- `XDefinitionParser` 在**根级**按 keys.NS 判别识别四类 check-* 元素并填充 `IXDefinition` 字段（非根级 keys.NS 子元素维持现状跳过——约束只在根声明，设计 §3.2）；`xdef:def-type` 与 `scope="global"` 在声明期显式报"未实现"错误。
- 声明期 fail-fast：规则 id 唯一、select 可编译（XPathHelper 试编译）、condition 可编译（compileSimpleExpr，结果存 `_condition: IEvalAction`）。
- 新组件 `XDefConstraintValidator` 执行 unique（document/siblings 分桶 + prop 缺省回退）/ mutex / require（condition 已编译，求值绑定 `node` 变量）；挂在 `XDslValidator.validate` 尾部，`defNode instanceof IXDefinition` 触发；只读幂等。
- check-ref 规则被声明时执行期显式报"check-ref 执行未实现"（P1 范围）——解析期成功（元数据可机读）、实例校验期 fail-loud，与 def-type 的声明期报错时点不同：def-type 在 P0 完全不解析无从填充，check-ref 解析填充正是工具链消费面（设计 §4.1）。
- 新增错误码族（统一 `_VIOLATION` 后缀）+ `ERR_XDEF_CHECK_NOT_IMPLEMENTED` + `ARG_RULE_ID` 参数；动态 errorCode 的 status 取 -1。
- 新功能测试 + 端到端测试 + 自举回归全绿。

## Non-Goals

- check-ref 的**执行**、def-type 局部类型表与执行、scope=global 执行（P1，设计 §3.7）。
- 空 key 漏检修补、check-cardinality（P1）。
- IDEA annotator、deprecated 强制化、收集式报告、JSON Schema 导出（P2）。
- 任何业务 xdef（beans.xdef / orm 等）开始使用约束语法——本计划只交付机制与测试样例。

## Scope

### In Scope

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef`（三处修订）
- `nop-kernel/nop-xlang` 的 `_gen` 再生成、`io/nop/xlang/xdef/IXDefinition.java`、`XLangErrors.java`、`io/nop/xlang/xdef/parse/XDefinitionParser.java`、`XDefKeys.java`（如需常量）、新类 `XDefConstraintValidator`、`io/nop/xlang/xdsl/XDslValidator.java`（挂载）
- 新测试资源与测试类（nop-xlang，`src/test/resources/_vfs/test/` + `src/test/java/`）
- `docs-for-ai/02-core-guides/xdef-and-xdsl.md` 补"约束元素"节、`ai-dev/logs/`、本 plan 收口

### Out Of Scope

- P1/P2 全部内容（见 Non-Goals）
- 修改任何 `XDslExtender` 合并逻辑、`DeltaJsonLoader` 链路（设计 §3.5 边界：不受影响）
- 修改 nop-xlang/pom.xml 以永久声明 exec-maven-plugin（regen 用一次性 `exec:java@precompile` 调用，不动构建配置）

## Execution Plan

### Phase 1 - 元模型修订与 `_gen` 再生成

Status: completed
Targets: `nop-kernel/nop-xdefs/.../xdef.xdef`、`nop-kernel/nop-xlang/.../xdef/impl/_gen/*`、`IXDefinition.java`、`XLangErrors.java`

- Item Types: `Fix | Decision`

- [x] `xdef.xdef` 三处修订：① `xdef:check-require` 元素补 `xdef-meta:unique-attr="id"`；② `xdef:check-ref` 补 `targetSelect="string" targetProp="string" keyProp="string"` 三个可选属性（含注释说明语义与缺省回退链）；③ `XDefAbstractCheck` 的 `errorCode` 从 `!string` 改为 `string`（可选，缺省用平台默认错误码；注明该修订对生成物**无字段 diff**——mandatory 性不进 bean 字段）
- [x] 再生成（按 Current Baseline 核实的两步命令）：`./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` → `./mvnw exec:java@precompile -pl nop-kernel/nop-xlang`（前置：nop-codegen 等已在本地仓库，必要时先 `./mvnw install -pl nop-kernel/nop-codegen -DskipTests -am`）。**禁止手改 `_gen`**；regen 会执行 `precompile/` 下全部 3 个 xgen，但另两个（ast/parser）的源模型未动，其生成物应无 diff——有 diff 即按 Risks 条目处置
- [x] 审查 `_gen` diff 与预期一致：`_XDefinition._xdefCheckRequire` 单数 → KeyedList（getter 变复数族 `getXdefCheckRequires()/getXdefCheckRequire(id)/contains...`，形态对照 `_xdefCheckMutexs` :245-281；全仓库无外部消费点）；`_XDefCheckRef` 增加 3 字段 + setter；无其他漂移
- [x] `IXDefinition` 接口补五类约束 getter 声明（`getXdefCheckUniques()` / `getXdefCheckRefs()` / `getXdefCheckMutexs()` / `getXdefCheckRequires()`（以 regen 后 `_XDefinition` 签名为准）/ `getXdefDefTypes()`）；签名风格裁定：接口用 `List<Xxx>`（`KeyedList implements List`，协变兼容），接口引入 `impl.XDefCheck*` 类型（这些数据类无独立接口，与既有 `IXDefinition.getXdefPreParse()` 返回 `IEvalAction` 的宽接口风格一致）
- [x] `XLangErrors` 新增：`ERR_XDSL_CHECK_UNIQUE_VIOLATION`、`ERR_XDSL_CHECK_REF_VIOLATION`、`ERR_XDSL_CHECK_MUTEX_VIOLATION`、`ERR_XDSL_CHECK_REQUIRE_VIOLATION`、`ERR_XDEF_DEF_TYPE_VIOLATION`、`ERR_XDEF_CHECK_NOT_IMPLEMENTED`（消息风格随 `XLangErrors` 文件现状，与既有条目一致），参数常量 `ARG_RULE_ID`
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am` 通过

Exit Criteria:

- [x] `_gen` diff 仅含预期两类变化（check-require 列表化、check-ref 三字段），errorCode 修订无生成物 diff（属预期），ast/parser 生成物无 diff（`git diff --stat` 佐证）
- [x] `IXDefinition` 五类 getter 与 `_XDefinition` 生成签名协变一致，编译通过
- [x] 错误码常量消息风格与既有 `ERR_XDSL_*` 条目一致（语言随文件现状）、无 status（默认 -1）、参数含 `ARG_RULE_ID`
- [x] No owner-doc update required（使用面文档统一在 Phase 4 落地后再同步，避免半成品语法入文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 解析填充与声明期校验（含显式未实现报错与负例测试）

Status: completed
Targets: `XDefinitionParser.java`、`XDefKeys.java`（如需常量）、新增解析负例测试类与测试资源

- Item Types: `Fix | Decision | Proof`

- [x] 挂点裁定（对抗审查 M1）：**根级扫描**——在 `doParseNode`（def 与根 node 均在作用域）解析根的 keys.NS 子元素，或等价地在 parseChildren 增加 root 上下文参数；非根级 keys.NS 子元素维持现状跳过（约束只在 xdef 根声明，设计 §3.2）。判别符 `StringHelper.startsWithNamespace(name, keys.NS)` + 标签匹配（建议在 `XDefKeys` 增加 check-unique/check-ref/check-mutex/check-require/def-type 标签常量，与 `PRE_PARSE` 动态拼装同构），**不得使用字面 `"xdef:"` 前缀**
- [x] 四类 check-* 解析填充：读属性构造 bean 并入 `XDefinition` 对应 KeyedList（按 id 键）。属性读取用 `XDslParseHelper` 既有助手（scope 用 `parseAttrEnumValue(node, name, XDefCheckScope.class, XDefCheckScope::valueOf)`，非法值由该助手抛 `ERR_XDEF_ATTR_NOT_VALID_ENUM_VALUE`；boolean 用 `parseAttrBoolean`；csv 属性按生成 setter 的集合类型）
- [x] `xdef:def-type` 根级元素显式抛 `ERR_XDEF_CHECK_NOT_IMPLEMENTED`（带 SourceLocation 与元素名），不再静默跳过
- [x] 声明期校验（解析后统一执行）：① 四类规则 id 合并唯一，重复抛错；② 每条规则 `select` 经 `XPathHelper.parseXSelector` 试编译，失败抛错（带 SourceLocation）；③ `check-require` 的 `condition` 经 `getCompileTool().compileSimpleExpr(loc, text)` 编译并存入 `_condition`（IEvalAction 字段，编译一次天然成立），失败抛错；④ `scope="global"`（check-unique/check-ref 的 scope 属性——mutex/require 元模型无该属性）抛 `ERR_XDEF_CHECK_NOT_IMPLEMENTED`（global 属 P1，堵住 Phase 3 未处理枚举分支）
- [x] 自举安全验证：加载 `/nop/schema/xdef.xdef` 自身（其 check-*/def-type 在业务名字空间 `xdef:` 下、keys.NS 为 `xdef-meta`）不触发新逻辑——`TestXDefParse.testParse`（解析全部 `/nop/schema/*.xdef`）即覆盖
- [x] 解析负例测试（本 Phase 内交付，测试类建议 `TestXDefConstraintParse`，资源置于 `_vfs/test/`，范式照 `TestXDefParse`/`TestXDefMergeLoader`）：① 重复 id；② 不可编译 select；③ 不可编译 condition；④ def-type 元素；⑤ scope="global"（挂在 check-unique 声明上——唯一有 scope 属性且 P0 解析的元素）——各自 `assertThrows(NopException)` 且断言错误码与 SourceLocation；⑥ 正例：带四类声明的 xdef 加载后 `SchemaLoader.loadXDefinition` 取到非空规则列表且字段值正确
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 既有测试全绿（无回归）

Exit Criteria:

- [x] 解析负例测试 ①-⑥ 全部落地且绿（新增测试类与资源列出）
- [x] 加载 `xdef.xdef` 自身及全部内置 xdef 无异常（`TestXDefParse.testParse` 通过即证据）
- [x] **无静默跳过**：`xdef:def-type` 与 `scope="global"` 声明显式报错（`ERR_XDEF_CHECK_NOT_IMPLEMENTED`），不再是静默 return
- [x] No owner-doc update required（同 Phase 1 理由）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - XDefConstraintValidator 执行器与挂载

Status: completed
Targets: 新类 `io.nop.xlang.xdsl.XDefConstraintValidator`、`XDslValidator.java`、新增规则执行测试

- Item Types: `Fix | Decision | Proof`

- [x] `XDefConstraintValidator` 实现三类规则执行，语义按设计 §3.2：
  - **check-unique**：`XPathHelper.parseXSelector(select)` 编译（静态缓存）→ `node.selectMany(selector)` 求值（结果逐元素 cast XNode）→ 按 scope 分桶（缺省 document；siblings 按父节点分组）→ prop 缺省回退（选中节点 def 的 unique-attr 优先，其次父 def 的 key-attr 在子 def 上声明的同名属性；defNode 定位：从选中节点沿 `getParent()` 上行收集 tag 链，从根 def 逐层 `getChild` 下探，未命中回落 unknown-tag 且处理 null）→ 空 key 跳过（与既有 `checkUniqueAttr` 一致）→ 重复抛 `ERR_XDSL_CHECK_UNIQUE_VIOLATION`（nodeA/nodeB/ruleId）
  - **check-mutex**：选中节点上 props 非空计数 ≤ 1；`atLeastOne=true` 时 ≥ 1；违例抛 `ERR_XDSL_CHECK_MUTEX_VIOLATION`
  - **check-require**：`condition`（Phase 2 已编译的 `_condition: IEvalAction`）以 `XLang.newEvalScope()` + `setLocalValue(null, "node", 当前XNode)` 求值；为真时 requiredProps 全非空、forbiddenProps 全空；违例抛 `ERR_XDSL_CHECK_REQUIRE_VIOLATION`
- [x] `check-ref` 规则存在时抛 `ERR_XDEF_CHECK_NOT_IMPLEMENTED`（"check-ref 执行属 P1"），不静默。**时点注记（对抗审查 3.2 裁定）**：check-ref 解析期成功、实例校验期 fail-loud——与 def-type 声明期报错的差异是设计意图（check-ref 元数据填充正是工具链消费面，设计 §4.1）；副作用是声明了 check-ref 的 xdef 其全部 DSL 实例加载失败，当前零业务使用（已核实），无回归面
- [x] 错误契约：声明了 `errorCode` 时用 public 构造器 `new ErrorCode(-1, 声明值, 文案)` 构造动态码（无声明用默认码）；`message` 声明覆盖默认文案；所有错误带 `ARG_RULE_ID` 与首个违规节点的 SourceLocation（设计 §3.4）
- [x] `XDslValidator.validate(XNode, IXDefNode, boolean)` 尾部挂载：`defNode instanceof IXDefinition` 时执行阶段二；xdef 无约束声明时零开销直通（不触碰 XPath、不分配映射）；阶段二只读不改树（AiCoderHelper 修复循环会反复 validate 同域树，只读幂等是硬要求）
- [x] 规则执行测试（本 Phase 内交付，测试类建议 `TestXDefConstraintValidation`，入口 `DslXmlResourceLoader.loadDslNodeFromResource` 或 `DslNodeLoader`，范式照 `TestXDefMergeLoader` 的 assertThrows + 错误码断言）：三类规则 × 正/负例、scope 两档、prop 缺省回退、动态 errorCode

Exit Criteria:

- [x] **接线验证**：以端到端测试断言调用链连通——DSL 实例含违例 → 从 `DslXmlResourceLoader` 入口加载 → 抛规则错误码（证明 `XDslValidator.validate` 运行时确实调用 `XDefConstraintValidator`，而非仅单元直调）
- [x] 三类规则各有正例（合法实例加载成功）与负例（违例抛对应错误码 + ruleId + 定位）测试
- [x] prop 缺省回退路径有测试（select 命中节点带 unique-attr 声明时不写 prop 也能查重）
- [x] 无约束声明的 xdef 加载路径行为与改动前完全一致（既有全量测试回归即证据）
- [x] **无静默跳过**：check-ref 声明 → 执行期显式报错测试
- [x] No owner-doc update required（Phase 4 统一）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试完备、文档同步与收口

Status: planned
Targets: 测试覆盖矩阵补全、`docs-for-ai/02-core-guides/xdef-and-xdsl.md`、`ai-dev/`

- Item Types: `Proof | Follow-up`

- [ ] 覆盖矩阵补全（Phase 2/3 已交付大部分，本 Phase 核对缺口并补齐）：三类规则 × 正/负例、scope 两档、prop 回退、errorCode 动态码与 message 覆盖、id 重复、select/condition 编译失败、def-type 与 check-ref 与 scope=global 未实现报错、自举、**x:extends 继承**（父 xdef 声明的 check-* 对子 xdef 的 DSL 实例校验生效——设计 §3.2 承诺，`doParseResource:131` 合并路径）——形成清单逐项对应测试方法
- [ ] `./mvnw test -pl nop-kernel/nop-xlang -am` 全绿
- [ ] 下游抽验（直接 new XDslValidator 的模块）：先 `./mvnw install -pl nop-kernel/nop-xlang -DskipTests`，再 `./mvnw test -pl nop-ai/nop-ai-coder,nop-ai/nop-ai-core`（不带 `-am`，用已 install 的上游构件，避免全量上游测试）——全绿
- [ ] `docs-for-ai/02-core-guides/xdef-and-xdsl.md` 在"### 8. `xdef:bean-*` 属性族"节（:148 起）之后新增"约束元素（check-*）"节：四类规则语义、scope（document/siblings；global 未实现）、prop 回退、errorCode/message、check-ref/def-type 未实现边界、指向设计文档；`docs-for-ai/04-reference/source-anchors.md` 增加新实现锚点（XDefConstraintValidator 等）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang --severity high` 退出码 0
- [ ] `ai-dev/logs/` 收口条目 + 本 plan 各 Phase 状态、Exit Criteria、Closure Gates 文本一致性核对
- [ ] 独立子 agent closure audit（fresh session），证据写入 plan `Closure` 段落

Exit Criteria:

- [ ] 覆盖矩阵逐项落地且绿（测试方法清单写入日志或本 plan 收口注记）
- [ ] 端到端验证：从 DSL 资源文件加载入口（`DslXmlResourceLoader.loadDslNodeFromResource` 或等价 `ResourceComponentManager` 入口）到规则错误抛出的完整路径已验证
- [ ] 使用面文档与落地行为一致（约束元素节所述语法/语义/边界与代码一致）
- [ ] closure audit 证据（含 Anti-Hollow 检查、checklist 工具退出码）已写入 `Closure` 段落
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 四类 check-* 约束解析填充 + 三类执行（unique/mutex/require）落地，check-ref 执行/def-type/scope=global 显式报未实现（无静默中间态残留）
- [ ] 自举安全：`xdef.xdef` 自身加载不受影响；全部内置 xdef 加载正常（测试全绿佐证）
- [ ] 全部既有行为无回归（`./mvnw test -pl nop-kernel/nop-xlang -am` + 下游抽验全绿）
- [ ] P0 边界与设计 §3.7 一致：未提前实现 P1/P2 内容
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2270-xdef-declarative-validation-p0.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang --severity high` 退出码 0
- [ ] 独立子 agent closure-audit 完成，Anti-Hollow 检查通过（调用链运行时连通 + 无空壳/静默跳过），证据已写入 plan
- [ ] `./mvnw compile -pl nop-kernel/nop-xlang -am` 通过
- [ ] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 三方 → java.*、4 空格缩进、英文错误消息）

## Deferred But Adjudicated

### P1：check-ref 执行、def-type 全量、global v-path 存在性、空 key 修补、check-cardinality

- Classification: `out-of-scope improvement`（设计 §3.7 已排期 P1）
- Why Not Blocking Closure: 本计划目标即 P0 机制打通；上述各项在 P0 端态均有显式 fail-fast（check-ref 执行报未实现、def-type/scope=global 声明期报错）或不在 P0 声明面内，不构成静默缺陷
- Successor Required: yes
- Successor Path: 设计文档 P1（尚未立项，落地时新建 plan 并引用本 plan 与设计文档）

### P2：IDEA annotator、deprecated 强制化、收集式报告、JSON Schema 导出

- Classification: `out-of-scope improvement`（设计 §3.7 已排期 P2）
- Why Not Blocking Closure: 工具链扩展，不影响 P0 契约成立
- Successor Required: yes
- Successor Path: 设计文档 P2（尚未立项）

## Non-Blocking Follow-ups

- 设计文档 §3.6.2 的"props 对应已声明属性"静态检查仅做 best-effort（select 命中节点类型可静态确定时）；完整静态确定依赖 select 语义分析，若成本过高降为运行期回退报错——Phase 2 执行时裁定并记录
- regen 两步命令在本地环境的耗时/稳定性记录进日志，供 P1 复用

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 填写>>
- Evidence: <<待填写>>

Follow-up:

- P1/P2 见 Deferred But Adjudicated；无其他 plan-owned work

## Risks And Rollback

- **再生成环境风险**：`exec:java@precompile` 依赖 pluginManagement 配置与本地仓库中的 nop-codegen 构件。若命令失败：先 `./mvnw install -pl nop-kernel/nop-codegen -DskipTests -am`；仍失败则按 runbook `docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` 3.1 节处置（临时在 nop-xlang/pom.xml 声明 exec-maven-plugin，生成后回退 pom 改动）。若完全不可用，plan 转 `blocked` 上报，**不得手改 `_gen`**（受保护区）。
- **regen 意外漂移**：diff 审查发现超出预期两类变化（check-require 列表化、check-ref 三字段）时——ast/parser 生成物出现 diff 先排查环境（antlr 版本）再决定继续或回滚；对照上次生成提交 `3f6aa07a92` 的形态。
- **回滚**：全部改动按 Phase 分 commit，任一 Phase 失败可 `git revert` 该 Phase 提交，不影响既有 DSL 加载（无约束声明的 xdef 路径行为不变是硬约束）。
