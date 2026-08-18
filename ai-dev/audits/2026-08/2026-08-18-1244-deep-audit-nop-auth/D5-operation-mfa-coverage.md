# D5 操作级 MFA 全路径覆盖审计报告（MFA 二期安全审计 A2）

> Dimension: D5 操作级 MFA 全路径覆盖（annotation 传播链 / executor 检查点 / 批量语义 / mfaVerifyOperation / SPI 可选注入 / 开关缺省 / 标注分布 / 路由项 1）
> Executor: fresh 独立对抗审计子 agent（task: D5-operation-mfa-coverage，research + report only，未修改任何产品/测试代码，未运行 maven）
> Date: 2026-08-18
> Charter: `audit-charter.md` §二 D5（对抗用例 E1-E4 + 威胁假设 + 路由项 1 探查输入）
> 设计基准: `ai-dev/design/nop-auth/02-mfa-phase2-design.md` §3.1-§3.6（操作级 MFA 全部 + impl 裁定回写）/ §4.6 裁定 1-2（受限分支 executor/checker 双触点）
> 审计性质: RESEARCH + REPORT ONLY。全部 file:line 锚点为 2026-08-18 live 核对产物。

---

## 一、Findings 总表

| ID | Severity | file:line anchor | description | suggested fix direction |
|---|---|---|---|---|
| D5-F1 | **P2** | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthMfaSettingBizModel.java:10-14`；`NopAuthMfaCredentialBizModel.java:10-14`；`NopAuthMfaTrustedDeviceBizModel.java:10-14`；`NopAuthRoleMfaPolicyBizModel.java:10-14`；`_nop-auth.action-auth.xml:83-136`（四表 query/mutation 资源声明）；`_NopAuthMfaTrustedDevice.xmeta:28-32`（userId/deviceHash 可写，deviceHash 仅 `var,not-pub` 无 insertable=false）；`_NopAuthMfaSetting.xmeta:26-35`（mfaType/secret/status 可写，secret 仅 masked） | **MFA 敏感表四个裸 CrudBizModel 通用 mutation 通道绕过 @MfaRequired 与专项端点防护**（D3-F3 同族，D3 报告显式路由给 D1/D5 裁定）。四个 phase-2 敏感表（Setting/Credential/TrustedDevice/RoleMfaPolicy）均注册了无任何 override 的 `CrudBizModel`，暴露继承的 save/update/delete/batchDelete mutation——这些继承动作不携带 `mfaRequiredMeta`（标注机制只覆盖显式声明的方法，设计 §3.6 裁定 5 已知"方法级注解无法覆盖共享基类动作"并 deferred 联系方式修改，但这四张表是二期**新增**敏感面，未做任何收窄）。可达后果（持对应 `*:mutation` permission 的调用方）：(a) `NopAuthMfaTrustedDevice__save` 直接插入任意 `(userId, deviceHash)` 行 → **注入 30 天登录级 MFA 豁免**（绕过"仅 mfaVerify 密码类路径 + 指纹服务端计算"的登记不变式，`MfaTrustedDeviceManager` 的登记/满员/策略 AND 判定全部旁路）；(b) `NopAuthMfaSetting__update` 改任意用户 secret/status/mfaType（绕过绑定状态机与 proof 前置）；(c) `NopAuthRoleMfaPolicy__save` 绕过 `saveMfaPolicy` 的 requireAdmin + minMfaLevel 1/3 校验 + 审计；(d) `NopAuthMfaCredential__delete` 无 last-credential 守卫。缓解：mutation permission 缺省不授（admin 菜单资源）；与 `resetUserMfa`（@MfaRequired + requireAdmin + 审计）的保护基线不一致，与 D3-F3（恢复码表，P2）同severity。 | 对四表 BizModel override save/update/delete 显式拒绝（"标准 save 已禁用"先例：`NopCredentialBizModel.java:123-134` 禁用标准 save 强制走专项端点）；或 xmeta/orm 源将敏感列改 insertable=false/updatable=false（保留只读管理视图）；至少 TrustedDevice 与 RoleMfaPolicy 必须收紧（豁免注入/策略绕过直接削弱登录级防护）。属 ORM/xmeta + biz model 变更，successor plan-first。 |
| D5-F2 | P3 | `GraphQLExecutor.java:189`（javadoc）；`TestOperationMfaExecutorWiring.java:61-62`（类 javadoc） | **批量语义 javadoc 漂移**。两处 javadoc 仍写"该 operation 单独报错（GraphQL 逐 field error 原生语义）/批量中其他 operation 不受影响"，与实现及测试不符：live 语义 = **整批预执行中止**（`checkOperationMfa` 在 `executeAsync` :152 于 `invokeOperations` :166 之前同步抛错，无部分执行；设计 §3.6 裁定 4 已按此理解回写；测试 :256-259 断言 `data == null`）。行为是更安全一侧，纯文档漂移。 | 更新两处 javadoc 为"预执行检查点抛错使整批请求失败（无部分执行副作用），错误即该 operation 的错误"。 |
| D5-F3 | P3 | `ReflectionBizModelBuilder.java:348-364`（仅 buildActionField 路径读取注解）；`:164-172`（@BizAction 路径）、`:175-203`（@BizLoader 路径）不读取 | **@MfaRequired 误标于 @BizAction/@BizLoader 方法时静默忽略**。注解只在 BizQuery/BizMutation/BizSubscription → `buildActionField` 路径被读取；BizAction（内部动作，注册为 biz action 非顶层 operation）与 BizLoader（嵌套字段装载器）路径完全不读。开发者误标会得到"已受保护"的错觉（fail-open 错觉，实际无外部可达面故非直接漏洞）。现有两项构建期约束（subscription/publicAccess 组合拒绝）未覆盖"标注于不可拦截位置"。 | 构建期 fail-fast 扩展：@MfaRequired 出现在 @BizAction/@BizLoader 方法上时抛构建错误（镜像 GraphQLErrors:301/:306 模式）。 |
| D5-F4 | P3 | `OperationMfaCheckerImpl.java:199-208`（getHeader） | **票头读取仅匹配三种拼写**（原样/全小写/全大写）。HTTP 头名按 RFC 大小写不敏感，任意混合大小写（如 `X-Nop-Op-MFA-Token`）未命中 → 票被忽略 → **fail-closed**（重新 challenge，非安全洞），但设计 §3.6 裁定 3 声称"大小写不敏感读头"。仓内已有一般化先例：`MfaTrustedDeviceManager` 按 §6.6 裁定 2 的全 key 集 `equalsIgnoreCase` 遍历。 | getHeader 改全 key 集 equalsIgnoreCase 遍历（对齐先例）。 |
| D5-F5 | P3（观察） | `GraphQLExecutor.java:226-233` + `OperationMfaCheckerImpl.java:148-158` | **批量预执行中止会烧掉同批已消费的票**。检查点按 selection 顺序逐 field 调 checker：若 field A 票有效（consume 成功、放行）而 field B 无票 → B 抛 `ERR_AUTH_OPERATION_MFA_REQUIRED` → 整批中止，**A 的票已被消费但操作未执行**，客户端须为 A 重新走验证。方向 fail-closed、与"票一次性"语义自洽，但前端契约需明确：应先为批内**全部**敏感操作完成验证再整批重发。 | 前端交互契约登记（设计 §7.5 前端 deferred 项注记）；或 checker 批模式两遍扫描（先全部核验后统一 consume）——非必需，登记即可。 |

裁决建议（charter §四三态）：D5-F1 → **successor 所有权**（P2 不静默降级；修复面涉 ORM/xmeta/BizModel，plan-first）；D5-F2/F3/F4 → successor 低优先（doc/构建期加固/健壮性）；D5-F5 → watch-only（Why Not Blocking：fail-closed 方向，一次性票语义自洽）。

---

## 二、对抗探针逐项裁决

### E1 绕过注解传播的调用路径 — **未探查到外部可达绕过；进程内直调面存在（结构性，与 @Auth 同信任假设）**

**四触点链 live 核对（全部成立）**：
1. 注解定义：`nop-biz-auth-api/src/main/java/io/nop/auth/api/mfa/MfaRequired.java:31`（RUNTIME/METHOD、无属性；任务书提示 `io.nop.api.core` 路径与 live 不符，实际归属 nop-biz-auth-api——与设计 §3.3 "注解归属 nop-biz-auth-api"一致）。
2. 读取 + 构建期约束：`ReflectionBizModelBuilder.java:348-364`（subscription 组合 → `GraphQLErrors.java:299-302`；publicAccess 组合 → `:304-307`，fail-fast 测试钉定 `TestMfaRequiredMetadata.java:97-110`）。
3. 字段元数据：`GraphQLFieldDefinition.java:74-77`（mfaRequiredMeta）+ `deepClone():98`（深克隆拷贝）。
4. 合并搬运：`GraphQLObjectDefinition.mergeField` 两分支（replace `:227-228` / keep `:266-267`）+ `BizObjectBuildHelper.mergeBizModel:73-74`（nop-biz 触点）。测试：`TestMfaRequiredMetadata.java:112-167`（deepClone/mergeField 三分支/null 不清除）、`TestBizObjectManager.java:74-91`（容器级 merge + document deepClone）、`TestOperationMfaE2E.java:133-146`（nop-auth 容器内 BizObjectBuildHelper 链路五动作元数据存活 + 负例 getMfaStatus 无元数据）、`TestCredentialMfaRequiredAnnotations.java:270-284`（nop-credential 容器级四动作）。

**外部传输面枚举（全部收敛于 executor 两检查点）**：
- GraphQL HTTP（`GraphQLWebService.java:84/94` → `executeGraphQLAsync` → `executeAsync` 检查点 :152）；
- RPC/REST 单操作（`GraphQLWebService.java:240-254` → `executeRpcAsync` → `executeOneAsync` 检查点 :69；gRPC `GraphQLServerCallHandler.java:100-104`、JSON-RPC `JsonRpcService.java:108-109`、消息订阅 `GraphQLMessageSubscriptionRegistrar.java:91`、文件服务 `AbstractGraphQLFileService.java:50/60`、Java 代理 `RpcServiceOnGraphQL.java:72` 同收敛）；
- 上下文工厂唯一：main 代码中 `new GraphQLExecutionContext` 仅在 `GraphQLEngine.newGraphQLContextFromContext:360`（grep 全仓核对），checker 经 `:376-378` 透出——**无绕过工厂自建上下文的生产路径**（fail-open 接线风险不存在）；
- 订阅：`subscribeGraphQL/subscribeRpc`（:677/:740）刻意不接检查——由构建期约束闭环（@MfaRequired+@BizSubscription 不可声明，见 E2）；
- MCP：`GraphQLToolCallbackProvider.java:58` 经 `BizActionInvoker.invokeGraphQLSync` → `newRpcContext` → `executeRpc`（过检查点）；
- maker-checker tryAction：检查点在 `invokeOperationOrTry` 之前（executeOneAsync :69 / executeAsync :152），try 分支同样被门禁（且现有 9 个标注动作均无 BizMakerChecker）。

**进程内直调面（存在、结构性、非本审计新增缺口）**：`BizActionInvoker.invokeActionSync/invokeActionAsync`（`BizActionInvoker.java:34-76`，javadoc 明言"绕过NopGraphQL引擎调用BizModel上的方法"，工作流引擎场景）直调 `IBizObject.invoke`，不过 executor（同样不过 `GraphQLActionAuthChecker`——与 @Auth 权限模型完全同信任假设）；同类：`DefaultBizAuthChecker:48`（行级读）、`DelayedRelationAction:200`（crud 级联）、`BizObjectQueryProcessorAdapter`（doFind* 内部）、nop-metadata 内部 `bizObject.invoke`（`AutoClassificationProcessor.java:267` 等四引用，均 NopMetaTagLabel 非敏感面）、直接 bean 注入。**live 核对：九个标注动作（nop-auth 5 + nop-credential 4）无任何仓内调用方经上述内部通道路由**。裁决：受信服务端代码信任假设成立（@Auth 先例），登记 watch（部署指引：敏感动作不得经 BizActionInvoker 接入 job/workflow 而无补偿控制）。

### E2 直接 GraphQL 字段选择绕过 executor — **未探查到**

- **嵌套字段**：@MfaRequired 仅在 BizQuery/BizMutation/BizSubscription → 顶层 operation 构建路径读取（`ReflectionBizModelBuilder.buildActionField`）；BizLoader 嵌套字段路径（`buildFetcherField:181`）不读取注解——嵌套字段无敏感语义可达面（误标忽略见 D5-F3）。
- **别名**：检查点用 `selection.getFieldDefinition()`（resolver 按字段名解析的 definition），非 alias；`TestOperationMfaExecutorWiring.java:241-247` 以 `a: MfaBiz__sensitiveAction(...)` 别名查询断言 checker 收到全名 `MfaBiz__sensitiveAction`。
- **fragment**：顶层 selection 只能是 field selection（`_invokeOperations:327` 直接强转）；fragment 在嵌套层展开，不承载顶层 operation。
- **@skip/@include**：检查点遍历 AST selection set（`GraphQLExecutor.java:226`），被 skip 的字段仍在检查集内——**过检方向**（fail-closed，非绕过）。
- **operation-name 混淆**：单文档单 operation 强制（`GraphQLEngine.parseOperationFromText:247-248` `ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE`）；RPC 路径按全名查 schema（`initRpcContext:418-420`，未知即拒），检查的 definition 即执行的 definition。
- **introspection（__schema/__type）**：解析到 builtinSchema（`GraphQLEngine.getOperationDefinition:339-355`），无 biz fetcher、不触发 biz 方法；mfaRequiredMeta 不在 GraphQL 规范自省面内，无泄漏。
- **subscription**：`executeRpcAsync:518-522` / `executeGraphQLAsync:561-565` 显式拒绝 subscription 类型；订阅路径本身不接检查（`GraphQLExecutor.java:190-191` javadoc 声明），由构建期约束拒绝 @MfaRequired+@BizSubscription 组合闭环（`ReflectionBizModelBuilder.java:351-356` + `TestMfaRequiredMetadata.java:97-102`）。
- **fetchResult 路径**（`GraphQLExecutor.fetchResult:101-118` / `GraphQLEngine.fetchResultWithSelection:840-855`）：仅对已产出结果做嵌套 selection 装配（BeanPropertyFetcher），不调用顶层 biz fetcher，非 biz 方法执行入口。

### E3 同会话票跨 operation 挪用 — **未探查到**

- **名匹配面单源闭合**：挑战创建（`OperationMfaCheckerImpl.java:162-164` payload.operation ← executor 传入的 `fieldDef.getOperationName()`）与票核验（`:153` `isTicketFor(c, operationName, ...)` → `:189` `operationName.equals(payload.get(PAYLOAD_OPERATION))`）用同一字符串；`operationName` 在 builder 三路径统一由 `GraphQLNameHelper.getOperationName(bizObjName, action)` 设置（`ReflectionBizModelBuilder.java:124/:141/:159`；nop-biz 路径 `BizModelToGraphQLDefinition.java:75` 同源），**Async 尾缀剥除发生在 action 名生成阶段**（`getActionName:294-298`，仅 async 方法）——不存在"创建侧与核验侧剥除不一致"的窗口（两侧读同一 fieldDef 属性）。
- 票四条件 + 原子消费：scene==operation + verifiedAt 非空（peek 不变式保证窗口）+ payload.operation 匹配 + payload.sessionId 匹配 + `consume` 原子成功者放行（`:148-158`，并发双花防护）。
- E2E 钉定：`TestOperationMfaE2E.java:244-264`（`testTicketBoundToOperation`：换操作用同票 → 重新拦截 REQUIRED，票未消费、原操作仍可用）；`:267-288`（`testTicketBoundToSession`：换会话/跨会话验证拒绝）；`:337`（login scene token 调操作级端点拒绝）；`:382`（跨场景 TOTP 重放拒绝——TOTP 窗口统一推进）。

### E4 enabled=false 零介入（双零介入，shared with D4）— **未探查到行为变化**

- 开关声明：`NopAuthConfigs.java:129-131`（`nop.auth.operation-mfa.enabled` 缺省 **false**）+ 票窗口 `:133-135`（缺省 60s）。
- checker 判定序（`OperationMfaCheckerImpl.java:117-137`）：受限分支（:121-128，W13 设计序——**不受本开关门控**）→ 开关早退（:130-131）→ null 用户（:133）→ store 未装配（:136）。**开关关闭时在任何 DB 读取/审计/challenge 创建之前返回**——已标注方法零副作用、未标注方法 executor 侧不调用 checker（`GraphQLExecutor.java:230-231` mfaRequiredMeta 判定）。
- 双零（enabled=false + 无策略行）：无策略行 → 无受限会话可达（受限签发仅由策略评估触发）→ 受限分支不可达 → checker 恒早退 = 一期行为。
- SPI 缺席零介入：`GraphQLEngine.java:160-163`（@Inject @Nullable setter）+ `:376-378`（仅非 null 透出）+ bean 装配 `auth-service.beans.xml:125-129`（ioc:type 按类型注入，未部署 nop-auth-service 即无 bean）；`GraphQLExecutor.java:201-203` checker null 直接返回。
- 测试锚点：`TestOperationMfaE2E.java:160-172`（enabled=false 敏感操作直通）、`:214-226`/`TestOperationMfaExecutorWiring.java:214-226`（无 checker bean 零介入）、`TestCredentialMfaRequiredAnnotations.java:309-343`（nop-credential 容器 checker=null + 四标注动作 GraphQL 路径行为与标注前一致）、`TestMfaRestrictedSessionE2E.java:256`（对照锚点：受限分支不受开关门控）。

### E5 批量请求整批预执行中止 — **确认整批中止（无部分执行）；附带观察 D5-F5**

- 代码路径：`GraphQLExecutor.executeAsync:151-152`——`checkOperationMfa(context)` 在 response map 创建（:155）与 `invokeOperations`（:166，经 `operationInvoker.invokeAsync:249` 的全部 operation 调度）**之前同步执行**；批内任一敏感 field 无有效票 → `ERR_AUTH_OPERATION_MFA_REQUIRED` 同步抛出 → `GraphQLEngine.toGraphQLResponse:584-598` 捕获构建纯错误响应。**任何 biz 方法均未被调用**。
- 检查点遍历批内全部顶层 field selection（:226-233），逐 field 调 checker——含多个敏感 field 时每个都核验。
- 测试钉定：`TestOperationMfaExecutorWiring.java:249-259`（敏感 field 拦截 → `response2.getData() == null`"pre-execution interception must not partially execute the batch"）。
- HTTP 层无请求数组批处理面（`GraphQLWebService.runGraphQL:79` 单 `GraphQLRequestBean` 解析）；"批"= 单 operation 多顶层 field（单文档单 operation 强制，见 E2）。
- 观察（D5-F5）：检查按序消费——field A 票已消费后 field B 失败，A 的票被烧但未执行（fail-closed UX 成本）。

---

## 三、Regression anchors（D5 所有权）

### 1. C1b 容器级元数据断言（静态一致性核对；live 测试运行归 orchestrator Phase 3）— **PASS**

- 断言正例四动作 = live 标注四动作，逐一比对：`TestCredentialMfaRequiredAnnotations.java:272-283` 断言 `NopCredential__reencryptAll`/`NopCredential__delete`/`NopCredentialAuth__grant`/`NopCredentialAuth__revoke` 携带 `MfaRequiredMeta.INSTANCE` ↔ live 标注 `NopCredentialBizModel.java:650`（reencryptAll）`:771`（delete）、`NopCredentialAuthBizModel.java:108`（grant）`:148`（revoke）。**完全一致，无漂移**。
- 断言负例三动作 = live 未标注：`saveCredential`（`NopCredentialBizModel.java:168` 声明，无 @MfaRequired）、`maskList`（`:541`）、`CredentialOAuthApi__beginOAuthFlow`（`CredentialOAuthApiBizModel.java:47`）——与缩窄裁定（高频 UX/读动作/state 一次性绑定）一致，未被执行期放宽。
- 全仓 nop-credential main 代码 @MfaRequired 恰 4 处（grep 核对），无第 5 处漏登记。
- 零介入前提成立：`nop-credential-service/pom.xml` 无 nop-auth 依赖（grep 仅注释提及）→ 该模块测试容器无 checker bean，`zeroInterventionWithoutCheckerAssembled` 前提静态成立。

### 2. 双零介入（enabled=false + 无策略行，shared with D4）— **PASS**

证据见 E4 节（checker 早退序 + executor 元数据判定 + 三处测试锚点 + SPI 缺席变体）。一期零回归锚点齐备：`TestOperationMfaE2E.testEnabledFalseZeroIntervention:160` / `TestMfaLoginE2E.testZeroRegressionMfaDisabledUser:405` / `TestCredentialMfaRequiredAnnotations:309`。

### 3. Routed item 1（webauthn 管理动作破坏性评估）— 见下节

---

## 四、Routed item 1：removeWebauthnCredential / renameWebauthnCredential 标注裁定建议

**live 现状**：两动作均未标注（`NopAuthUserBizModel.java:822-836` remove / `:838-851` rename；均 @BizMutation + @BizAudit + 本人数据限定 `requireOwnCredential:853-862`）。

**建议（供 Phase 3 裁定，带倾向）**：
- **renameWebauthnCredential：不标注**。纯展示元数据改名（name 列），无任何安全状态变更、无因子集合影响；本人限定 + 登录态已足。完全落入 C1b 缩窄先例的管理面非破坏动作类（beginOAuthFlow/saveCredential/maskList）。
- **removeWebauthnCredential：倾向标注**。论证：
  - **破坏性**：删除认证凭证行 = 修改认证因子集合——设计 §3.3 首批判定标准"修改认证因子"字面命中；劫持会话的攻击者可删除受害者全部备份硬件钥匙（保留最后一把 enabled 被拒），削弱冗余、破坏 signCount 克隆检测基线、制造恢复摩擦（受害者只剩单点钥匙）。
  - **结构性守卫不足**：`ERR_AUTH_MFA_LAST_CREDENTIAL`（:828-830）防的是**用户自锁死**，不是会话劫持；对比 unbindMfa（整体解绑，@MfaRequired :676，语义近邻——remove 实为"部分解绑"）与凭证库 delete（数据级不可逆删除，@MfaRequired NopCredentialBizModel:771）。
  - **成本可控**：该动作仅 webauthn 用户可达 → setting 必为 enabled → challenge 流总有可用因子；低频管理动作，一次额外交互可接受（unbindMfa 的 webauthn 双 ceremony 先例 `NopAuthUserBizModel.java:669-671` 已接受同款 UX）。
  - **反方材料**（若 Phase 3 裁定不标注）：last-credential 守卫 + 本人限定 + @BizAudit 审计 + 不解除 MFA 启用状态（setting 不变）→ 攻击者无法借此关闭 MFA 或登录，最坏为 DoS 型备份钥匙削减；C1b 缩窄先例可延伸适用。若按此裁定，应登记 watch-only 并写明 Why Not Blocking。
  - **附带对照**：`removeTrustedDevice:895-906` 建议不标注（删除豁免行是收紧方向，攻击者无收益；与 D6 裁定面衔接）。

---

## 五、覆盖率声明

- Charter §二 D5 目标锚点全量核对：四触点链（含 nop-biz merge 触点 + deepClone）、executor 两检查点（RPC :69 / GraphQL :152，均在 biz 调用前）、批量整批预执行中止、`mfaVerifyOperation`（:294-356：登录态 :300-302 / 同会话 :309-312 / 票不续命 :314-316 / runWithTenant setting 复核 + mfaType 硬校验 :318-328 / markVerified 一次性 :350-352 / 无凭证签发 :354-355）、`IOperationMfaChecker` SPI 可选注入（引擎/上下文/bean 三层）、`nop.auth.operation-mfa.enabled` 缺省 false 及 checker 尊重路径、标注分布 9 处 live 比对、四测试面（TestMfaRequiredMetadata / TestOperationMfaExecutorWiring / TestBizObjectManager+MyObjectBizModel / TestCredentialMfaRequiredAnnotations）静态活性与一致性核对。
- 对抗探针 E1-E5 全部执行并给出显式裁决（未探查到 / 确认闭合；发现 5 项 findings，其中 1 项 P2 为 D3-F3 路由的同族 CRUD 面扩展）。
- 限制：本审计为静态核对 + 既有测试证据引用，未运行 maven（orchestrator Phase 3 统一执行 nop-credential 测试树与全模块回归）；运行时行为以既有 E2E 断言为证据基线。
