# 350 nop-ai API 契约快修 + 零散 P3

> Plan Status: completed
> Last Reviewed: 2026-09-12
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/`（03-nop-ai-findings AI-3/AI-4/AI-5/AI-6/AI-20/AI-22/AI-23、02-repo-wide-scans H3、AI-24/AI-25 裁定）
> Related: 后续 351-357 系列修复计划。Plan 已过一轮独立对抗审查（agent_bba75266，3 Blocker + 4 Major 已吸收，见各 Phase 修订说明）。

## Purpose

把审计 P1-6（nop-ai API 契约三连缺陷）与同模块零散 P3（HTTP 魔数、getBytes 字符集、BeanContainer in BizModel、Jackson 注解裁定）一次性收口：对外 API 语义正确（写操作走 @BizMutation、参数名正确、错误走 ErrorCode）、工具约定合规。

## Current Baseline

- `SequentialThinkingBizModel.clearHistory`（`nop-ai-tools/.../sequential_thinking/service/SequentialThinkingBizModel.java:110-115`）为写操作却标 `@BizQuery`（AI-3）。仓库内无 GraphQL/RPC 形式调用方；唯一测试 `TestSequentialThinkingBizModel:133` 为直接 Java 调用；GraphQL RPC 路径（`GraphQLEngine.initRpcContext` opType=null）对 query/mutation 均可解析，改注解不断链。
- `FileToolBizModel.saveFiles`（`.../file/FileToolBizModel.java:96`）第二参数 `@Name("String")`（AI-4），与 @Description 文案及 tool.json 描述（已用 `fileContents`）矛盾。全仓无 Java 调用方。
- `FileToolBizModel`（245 行）有 15 个 public 方法：**13 个 Biz 注解方法** + 2 个 `@InjectValue` setter（setBaseDir/setDefaultMaxLengthPerFile）。无 `IServiceContext` 末参、无 I*Biz 接口（AI-5）。biz 框架机制已核验：`ReflectionBizModelBuilder.buildAction` 对 IServiceContext 参数自动注入、`ReflectionGraphQLTypeFactory.getArgDefinitions` 跳过该参数不进 schema——补末参安全。
- **框架机制约束（审查 Blocker 1）**：`ClassModelBuilder` 只对抽象类合并接口方法注解；具体类 `FileToolBizModel` 若把注解只放接口，13 个方法会从 GraphQL/MCP 注册面静默消失。因此注解必须保留在实现类方法上。
- `AiFileTool.loadNopFileXDef`（`nop-ai-mcp-server/.../AiFileTool.java:50-59`）缺 `@BizQuery`；`loadNopFileXDef` 空值路径与 `saveNopFile` 不支持 merge 路径以 `"ERROR: ..."` 字符串作正常返回值（AI-6）。`McpServerErrors` 仅有 `ERR_MCP_FILE_NOT_FOUND`。**审查新发现 live defect（Major 4）**：`saveNopFile` merge 分支条件反转——`if (xdefPath != null) return "ERROR..."` 后在 `xdefPath==null` 路径调 `SchemaLoader.loadXDefinition(xdefPath)` 必炸；正确语义应为 `xdefPath == null` 才不支持 merge。异常经 `BizActionInvoker.invokeGraphQLSync`（nop-biz :94-100）转为 ApiResponse 错误，不会变裸 500。
- `AiGatewayFailoverInterceptor.classifyResponseStatus`（:353-362，429/401||403/5xx/4xx）与 `ChatServiceFailoverAdapter`（`nop-ai/nop-ai-gateway/.../failover/ChatServiceFailoverAdapter.java:467`，429/5xx/4xx 区间，无 401/403）内联 HTTP 魔数（AI-23）。
- `JavaMethodReplacer.java`（nop-ai-coder）读（:21 `new String(Files.readAllBytes(path))`）写（:49 `newContent.getBytes()`）两侧均无字符集；`HttpRequestExecutor.java:168`（nop-ai-toolkit，Basic 认证）`.getBytes()` 无字符集（AI-20）。**审计勘误（审查 Blocker 3）**：`LanguageStatsAggregator.java:121,129` 的 `stats.getBytes()` 是领域 getter（返回统计字节数），非 `String.getBytes()`——AI-20 该两处为误报，移出范围。
- H3：`NopJobFireBizModel:62`（getBeanByType(IJobTaskStore)）、`:81`（tryGetBeanByType(IJobCancelHandler)）、`NopJobTaskLogBizModel:60,106`、`NopMetaQualityCheckpointBizModel:100`（tryGetBean(scheduler)）。已核验：IJobTaskStore/JobTaskStoreImpl/装配都在 nop-job-dao 内，nop-job-service 依赖它，`@Inject` 编译期与测试容器均可行（TestNopJobFireBizModel 已注入三个 store 成功）；NopJobFireBizModel:56-59 注释断言"BizModel 注入不生效"与同类 :101-108 `@Inject` 生效事实矛盾（审查 Major 5）；NopMetaQualityCheckpointBizModel 的懒查找是 **P2-02 已裁定的断环设计**（scheduler setter-@Inject 本 BizModel，反向注入成环，审查 Major 6）——该处保留懒查找。必须项 `getBeanByType`→`@Inject` 的语义变化：失败时点从调用期前移到启动期（fail-fast，可接受并注释说明）。
- AI-22（审查已预置证据）：sequential_thinking 的 8 个 `@JsonProperty` 文件全部是输出 DTO（输入 ProcessThoughtRequest 无注解）；序列化入口是 `GraphQLToolProvider.callTool` → `JsonTool`（DefaultJsonTool 基于 BeanModel），而 **Nop 的 BeanModelBuilder 本身消费 Jackson 注解**（构造器参数命名 :240、`getJsonPropName` :421、`processPropAnnotations` :447）——这些 DTO 无默认构造器，@JsonProperty 为 BeanTool Map→bean 构建提供参数名，功能上非死注解。
- AI-24/AI-25：审计已裁定可接受/合规，无需代码变更；AI-24 中 `ISourceCodeAnalyzer` 静态定位器待定性。
- `nop-ai-mcp-server` 无测试基建（pom 无 junit，无 src/test）；`nop-ai-skills` 是 pom 聚合器（`-pl` 不会跑子模块测试）。

## Goals

- AI-3：`clearHistory` 改 `@BizMutation`（权限串保持 `SequentialThinking:delete`）。
- AI-4：`@Name("String")` → `@Name("fileContents")`（与 tool.json 描述对齐）。
- AI-5：`FileToolBizModel` 的 13 个 Biz 注解方法补 `IServiceContext` 末参（setter 不动）+ 新增 `IFileToolBiz` 接口做能力契约；**注解保留在实现类方法上**（接口侧按平台 ICrudBiz 先例同步重复声明，但实现类注解是生效面）。
- AI-6：`loadNopFileXDef` 补 `@BizQuery`；两个 ERROR 字符串返回改为 `NopException` + 新增 ErrorCode（英文消息）；**修复 `saveNopFile` merge 条件反转 live defect**；`"SUCCESS"` 返回保留。
- AI-23：429/401/403/5xx 提取为命名常量。
- AI-20（修正范围）：两处真实 `String.getBytes()` 补 `StandardCharsets.UTF_8`（JavaMethodReplacer 读写两侧 + HttpRequestExecutor）；LanguageStatsAggregator 按审计勘误移出。
- H3（修正口径）：nop-job 三处改注入（必须项 `@Inject`、可选项 `@Inject @Nullable`）+ 修正 NopJobFireBizModel 错误注释；NopMetaQualityCheckpointBizModel 按断环裁定保留懒查找（登记裁定，不改代码）。
- AI-22：基于预置证据裁定保留 @JsonProperty（登记理由）。

## Non-Goals

- 不迁移 FileToolBizModel 到 IToolFileSystem（P2-MA1-012 已裁定保留双抽象）。
- 不处理 351-357 计划范围的发现。
- 不改变任何方法的业务行为/返回值结构（除 ERROR 字符串→异常、merge 条件反转修复）。

## Scope

### In Scope

- `nop-ai-tools`（SequentialThinkingBizModel、FileToolBizModel+新接口、sequential_thinking model 裁定）
- `nop-ai-mcp-server`（AiFileTool、McpServerErrors、+最小测试基建）
- `nop-ai-gateway`（failover 两文件常量化）
- `nop-ai-coder`、`nop-ai-toolkit`（getBytes）
- `nop-job-service`（H3 两处 BizModel 改注入）、`nop-metadata-service`（H3 一处登记裁定）

### Out Of Scope

- 上述模块的其他审计发现（分属 351-357）。
- FileToolBizModel 的伪 BizModel 治理（工具型 BizModel 无聚合根实体，本 plan 只按审计修复方向补接口与 context）。
- LanguageStatsAggregator（审计误报，见 Deferred）。

## Execution Plan

### Phase 1 - SequentialThinking 写操作注解修正（AI-3）

Status: completed
Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/SequentialThinkingBizModel.java`

- Item Types: `Fix`

- [x] `clearHistory` 的 `@BizQuery` 改为 `@BizMutation`（`@Auth(permissions = "SequentialThinking:delete")` 保留）
- [x] rg `clearHistory` 全仓核对：唯一调用测试为直接 Java 调用（TestSequentialThinkingBizModel:133），无需改动（若发现其他调用形式则同步更新）

Exit Criteria:

- [x] `clearHistory` 方法带 `@BizMutation` 且不再带 `@BizQuery`
- [x] `./mvnw test -pl nop-ai/nop-ai-tools -am` 通过（含 sequential_thinking 全部测试与 TestGraphQLToolSetConditionalRegistration，surefire 5/5 绿）
- [x] No new test required: 注解类别变化无行为分支，由既有测试覆盖（RPC 路径 opType=null 对两类均兼容，已核验）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - FileToolBizModel 契约修复（AI-4 + AI-5）

Status: completed
Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/file/FileToolBizModel.java`、同包新增 `IFileToolBiz.java`、`nop-ai-tools/src/test/`

- Item Types: `Fix`

- [x] `saveFiles` 第二参数 `@Name("String")` → `@Name("fileContents")`
- [x] **13 个 Biz 注解方法**（readFiles/readFilePart/saveFile/saveFiles/mergeFile/glob/globGrep/grep/grepFiles/loadDslSchema/loadDslSchemaForFileType/loadDslFile/saveDslFile）补 `IServiceContext context` 末参；**2 个 @InjectValue setter 不动**
- [x] 新增 `IFileToolBiz` 接口：13 个方法声明（按平台 ICrudBiz/INopJobScheduleBiz 先例，接口方法同步声明 @BizQuery/@BizMutation/@Name/@Optional/@Description）；`FileToolBizModel implements IFileToolBiz` 并 @Override；**实现类方法上注解原样保留**（生效面在实现类，接口仅契约——ClassModelBuilder 对具体类不合并接口注解）
- [x] 新增/扩展注册守护测试：断言 FileTool 的 13 个操作仍全部注册（形态按既有测试基建选择：GraphQL 引擎 operation definition 断言，或 BizObjectManager 方法清单断言——执行时以 TestGraphQLToolSetConditionalRegistration 现有基建可支撑的最小形态为准），堵住"注解丢失静默失效"测试网缺口
- [x] 全仓 rg `FileTool__` 核对无参数名依赖遗漏

Exit Criteria:

- [x] `rg '@Name\("String"\)' nop-ai` 为空
- [x] `FileToolBizModel implements IFileToolBiz`；接口 13 个方法与实现一一对应；实现类 13 个 Biz 方法末参为 `IServiceContext`、2 个 setter 签名未变
- [x] 新注册守护测试通过且能捕捉"某方法注解丢失"（人工核验：临时去掉任一方法注解测试应红——执行时以断言形态等价核验，不强制提交红例）
- [x] `./mvnw test -pl nop-ai/nop-ai-tools -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - AiFileTool 注解、typed errors 与 merge 条件修复（AI-6 + live defect）

Status: completed
Targets: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java`、`McpServerErrors.java`、pom.xml（测试依赖）、新增 src/test

- Item Types: `Fix`

- [x] `loadNopFileXDef` 补 `@BizQuery`
- [x] `McpServerErrors` 新增 `ERR_MCP_NO_XDEF_FOR_FILE_TYPE`（ARG_FILE_TYPE）、`ERR_MCP_MERGE_NOT_SUPPORTED`（ARG_FILE_TYPE），消息英文（遵循 AGENTS.md；既有中文码不动）
- [x] `loadNopFileXDef` 空值路径：`return "ERROR: ..."` → `throw new NopException(ERR_MCP_NO_XDEF_FOR_FILE_TYPE).param(ARG_FILE_TYPE, fileType)`
- [x] `saveNopFile` merge 分支：**条件反转为 `xdefPath == null` 时抛 `ERR_MCP_MERGE_NOT_SUPPORTED`**；`xdefPath != null` 走原有 DeltaMerger 逻辑；`"SUCCESS"` 返回保留
- [x] 为 `nop-ai-mcp-server` 增加最小 JUnit 测试基建（pom 加 junit 测试依赖），新增 `TestAiFileTool`：①loadNopFileXDef 空值路径断言 NopException+新码；②saveNopFile merge 不支持路径断言 NopException+新码；③merge 支持路径（已有 xdef 的 fileType，如 xml）走通到文件写入断言（若 VFS/DocumentConverterManager 初始化成本过高，③可降级为 ②的条件边界断言并在日志说明）

Exit Criteria:

- [x] `AiFileTool` 中 `rg '"ERROR:'` 为空；三个暴露方法均有 Biz 注解；merge 分支条件为 `xdefPath == null` 抛错、非 null 走合并
- [x] 两个新 ErrorCode 集中声明于 `McpServerErrors` 且带 ARG 常量
- [x] `TestAiFileTool` 新增且 `./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 通过
- [x] New test required: 上列 ①②（③尽力）——验证新错误路径与 merge 修复行为
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Gateway HTTP 状态码常量化（AI-23）

Status: completed
Targets: `nop-ai/nop-ai-gateway/.../failover/AiGatewayFailoverInterceptor.java`、`.../failover/ChatServiceFailoverAdapter.java`

- Item Types: `Fix`

- [x] 两文件按各自实际魔数提取常量：Interceptor（429/401/403/5xx 区间）、Adapter（429/5xx/4xx 区间，无 401/403）；常量命名如 `HTTP_TOO_MANY_REQUESTS`、`HTTP_UNAUTHORIZED`、`HTTP_FORBIDDEN`（两文件各自私有或按最小 diff 共用）
- [x] `rg -n '== 429|== 401|== 403' nop-ai/nop-ai-gateway/src` 复查无裸数字命中

Exit Criteria:

- [x] 上述 rg 仅命中常量定义/常量名比较，无裸魔数
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过（含 failover 既有测试）
- [x] No new test required: 纯常量提取行为不变
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - getBytes 字符集补齐（AI-20 修正范围）

Status: completed
Targets: `nop-ai/nop-ai-coder/.../JavaMethodReplacer.java`、`nop-ai/nop-ai-toolkit/.../HttpRequestExecutor.java`

- Item Types: `Fix`

- [x] `JavaMethodReplacer` 写侧（:49）`.getBytes()` → `.getBytes(StandardCharsets.UTF_8)`；读侧（:21）`new String(bytes)` → `new String(bytes, StandardCharsets.UTF_8)`（读写一致）
- [x] `HttpRequestExecutor` Basic 认证处补 `StandardCharsets.UTF_8`（ASCII 凭据行为不变，注释说明）

Exit Criteria:

- [x] `rg '\.getBytes\(\)' nop-ai/nop-ai-coder/src/main nop-ai/nop-ai-toolkit/src/main` 为空；全 nop-ai 范围复查时 LanguageStatsAggregator 的 `stats.getBytes()`（领域 getter，审计误报）为唯一允许残留
- [x] `./mvnw test -pl nop-ai/nop-ai-coder,nop-ai/nop-ai-toolkit -am` 通过
- [x] No new test required: 字符集显式化，ASCII 行为不变
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - H3 BeanContainer 定性与注入改造（nop-job 两文件 + metadata 裁定）

Status: completed
Targets: `nop-job/nop-job-service/.../entity/NopJobFireBizModel.java`、`NopJobTaskLogBizModel.java`、`nop-metadata/nop-metadata-service/.../entity/NopMetaQualityCheckpointBizModel.java`

- Item Types: `Fix | Decision`

- [x] `NopJobFireBizModel`：`getBeanByType(IJobTaskStore)` → `@Inject`（protected 字段或 setter）；`tryGetBeanByType(IJobCancelHandler)` → `@Inject @Nullable` + 调用处判空保持原语义；**修正 :56-59 与事实矛盾的注释**（同类 :101-108 @Inject 已生效）；注释说明必须项失败时点前移为启动期 fail-fast
- [x] `NopJobTaskLogBizModel`：两处 getBeanByType → `@Inject`
- [x] `NopMetaQualityCheckpointBizModel`：按 P2-02 断环裁定**保留懒查找**，在注释与本 plan Deferred 段登记裁定理由（scheduler↔BizModel 双向注入成环）
- [x] 复查三文件 `rg 'BeanContainer'`：nop-job 两文件仅剩注释或消除；metadata 文件保留但注释已升级为裁定引用

Exit Criteria:

- [x] `./mvnw test -pl nop-job/nop-job-service -am` 通过（TestNopJobFireBizModel 等）
- [x] metadata 文件无代码变更或仅注释强化（断环裁定登记于 Deferred）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过（若有变更）或注明无变更
- [x] No new test required: 注入方式等价重构，由既有 BizModel 测试覆盖
- [x] No owner-doc update required（裁定在代码注释与 plan）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - AI-22/AI-24 裁定落地

Status: completed
Targets: `nop-ai-tools/.../sequential_thinking/model/*.java`（注释）、`nop-ai-core/.../ISourceCodeAnalyzer.java`

- Item Types: `Decision`

- [x] AI-22：基于已核验证据（BeanModelBuilder :240/:421/:447 消费 Jackson 注解；DTO 无默认构造器）**保留 @JsonProperty**，在其中 1-2 个代表性 DTO 类注释登记裁定（"Nop BeanModelBuilder 消费此注解做构造器参数命名，勿删"）
- [x] `ISourceCodeAnalyzer` 静态定位器（:11-15）实地定性：可注入化则改（Fix），确属 SPI 定位器惯例则登记 watch-only（Decision）

Exit Criteria:

- [x] 裁定结论与证据引用写入 Deferred But Adjudicated
- [x] 若有代码变更：模块编译+测试通过；无代码变更则注明
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - 全量验证与收口

Status: completed
Targets: 全部受影响模块

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-ai/nop-ai-tools,nop-ai/nop-ai-mcp-server,nop-ai/nop-ai-gateway,nop-ai/nop-ai-coder,nop-ai/nop-ai-toolkit,nop-job/nop-job-service,nop-metadata/nop-metadata-service -am`（可分批执行）全部通过
- [x] 复跑验证扫描：`@Name("String")` 归零；clearHistory 带 @BizMutation；AiFileTool 无 "ERROR: 字符串返回；上述模块 `.getBytes()` 仅剩领域 getter
- [x] 日志条目收口；独立 closure audit（fresh subagent）

Exit Criteria:

- [x] 验证命令退出码 0（分批全绿）并记录于日志
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/350-nop-ai-contract-quick-fixes.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-tools --severity high` 退出码 0
- [x] 独立 closure audit 证据写入 Closure 段

## Closure Gates

- [x] AI-3/AI-4/AI-5/AI-6/AI-20(修正范围)/AI-23/H3(nop-job 部分) 修复在 live repo 可观察
- [x] saveNopFile merge 条件反转 live defect 已修复且有测试
- [x] AI-22（保留+登记）、AI-24（定性）、metadata H3（断环保留+登记）、AI-20 误报（LanguageStatsAggregator）均已裁定且理由有证据
- [x] AI-25 无需动作（审计原文"确认合规"）
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] 受影响模块 `./mvnw test` 通过；checkstyle/编译通过
- [x] 独立 closure audit 完成且证据已写入

## Deferred But Adjudicated

### AI-22 保留 sequential_thinking DTO 的 @JsonProperty

- Classification: `out-of-scope improvement`（裁定为保留，非遗留债务）
- Why Not Blocking Closure: Nop BeanModelBuilder 消费 Jackson 注解（构造器参数命名），DTO 无默认构造器时为 BeanTool Map→bean 构建必需；非死注解。
- Successor Required: no

### H3-metadata：NopMetaQualityCheckpointBizModel 保留 BeanContainer 懒查找

- Classification: `watch-only residual`
- Why Not Blocking Closure: P2-02 已裁定的断环设计（scheduler setter-@Inject 本 BizModel，反向注入成循环依赖）；BeanContainer.tryGetBean 为该约束下的正解。
- Successor Required: no

### AI-20-误报：LanguageStatsAggregator.getBytes()

- Classification: `watch-only residual`
- Why Not Blocking Closure: `stats.getBytes()` 为领域 getter（返回统计字节数），非 String.getBytes() 无字符集调用；审计 AI-20 该两处为误报（对抗审查实地核验）。已在日志登记审计勘误。
- Successor Required: no

### AI-24：ISourceCodeAnalyzer 静态定位器保留（watch-only）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `ISourceCodeAnalyzer.getAnalyzer(lang)` 是按动态 bean 名（`sourceCodeAnalyzer_<lang>`）的多语言多态注册表查询——每个语言一个实现 bean，无法用静态单依赖注入表达；静态接口方法承担注册表门面角色属合法 registry 模式（closure audit 核验，git diff 为空确认未改）。
- Successor Required: no

## Non-Blocking Follow-ups

- 无（本 plan 关闭后不保留 plan-owned 工作）

## Closure

Status Note: 全部 8 个 Phase 完成；独立 closure audit（agent_b3ce6782）判定"无任何代码返工项"，3 项簿记（日志条目/AI-24 裁定入 Deferred/本 Closure 段）已补齐。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: agent_b3ce6782（独立 closure audit subagent，fresh session）
- Evidence:
  - Phase 1-6 全部 Exit Criterion PASS（逐条附 live 证据：SequentialThinkingBizModel.java:110-115、IFileToolBiz 13 方法/实现末参/2 setter 未变、AiFileTool.java:54/60/136-151 merge 条件反转修复方向核验正确、FailoverConstants 7 常量+gateway 零裸数字、JavaMethodReplacer:22/50 双侧 UTF_8、NopJobFireBizModel @Inject+@Nullable/注释修正、metadata 断环保留）
  - Phase 7：AI-22 裁定注释与 BeanModelBuilder:240/421/447 证据链核验属实；AI-24 registry 模式裁定成立
  - Phase 8：surefire 全绿（mcp-server 3、ai-tools 27、gateway 178、coder 69、toolkit 202、job-service 48、metadata-service 1339，Failures/Errors 全 0，报告时间 2026-09-12 22:27-22:50）
  - `scan-hollow-implementations --module nop-ai-tools --severity high` 退出码 0；Anti-Hollow：TestAiFileTool merge 用例真实走通全链（条件反转旧代码会使 assertEquals("SUCCESS") 失败）；TestFileToolBizRegistration 为强形态守护（任一注解丢失/错型即红）
  - Deferred 三条（AI-22/H3-metadata/AI-20 误报）+ AI-24 分类诚实性核验：无被降级的 live defect
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/350-nop-ai-contract-quick-fixes.md --strict` 退出码 0（见下方勾选项）

Follow-up:

- no remaining plan-owned work
