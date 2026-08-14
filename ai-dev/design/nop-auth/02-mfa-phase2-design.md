# nop-auth MFA 二期设计（操作级 MFA + 角色级强制策略 + 因子扩展 + 可信设备）

**日期**：2026-08-14
**状态**：active
**范围**：`nop-auth`（nop-auth-service / nop-auth-dao / model）+ `nop-service-framework/nop-biz-auth-core`（challenge 存储扩展）+ `nop-service-framework/nop-graphql/nop-graphql-core`（操作级拦截元数据传播，见 §三）+ `nop-integration/nop-integration-api`（`IEmailSender` 复用，零变更）；消费方：W12-impl / W13-impl / W14-impl / W15-impl

---

> **粒度裁决（2026-08-14，Phase 1 Decision）**：四主题合入本单一文件 `02-mfa-phase2-design.md`，不拆分。理由：(a) 与姊妹二期设计先例一致（凭证库 `ai-dev/design/nop-credential/02-phase2-design.md` 同为四主题单文件）；(b) 四主题共享同一批一期契约锚点（两阶段 challenge / `completeLogin` 分界 / store 三实现装配 / mfaType 白名单）且交叉引用密集（可信设备 × 角色策略豁免、操作级 × 受限会话白名单复用同一拦截器、WebAuthn × challenge 存储场景化扩展），拆分后每篇都要重复声明兼容性基线；(c) roadmap（W12-design / W12-impl ~ W15-impl）与后续 impl plan 链均锚定本路径，拆分将造成引用断裂；(d) 预估规模单文档可控（凭证库先例 381 行）。按 roadmap 约束，每主题小节独立 review 门槛。

## 一、设计结论索引

- **§三 操作级 MFA**：`@MfaRequired` 方法注解（构建期校验约束）+ executor 层拦截（`ReflectionBizModelBuilder` → `GraphQLFieldDefinition.mfaRequiredMeta` → `IOperationMfaChecker` SPI，nop-graphql-core 内闭合接线）；操作级 challenge 两段式（验证转一次性短 TTL 票，票绑定 operation+sessionId）；`MfaChallengeStore` 场景化扩展（scene/payload/verifiedAt + `markVerified` 原子性契约，payload 一次写入）；`MfaFactorVerifier` 共享因子校验组件（TOTP 窗口统一推进）。
- **§四 角色级强制策略**：新实体 `NopAuthRoleMfaPolicy`（1:1 角色按需建行）；策略 = 因子**持有约束**（minMfaLevel 1/2/3 强度序 + 多角色 max 合并），不改变验证所用因子；三层判定矩阵（全局总闸 → 策略 → 用户级）；不达标 → 受限会话（`mfaRestricted` 标志先设后存 + Dao-cache 白名单两触点 + §三 拦截器白名单前置分支）；登记通道 OTP 防 enrollment attack；`confirmMfa` 策略校验防降级；OAuth 一期遗留绕过一并修复。
- **§五 因子扩展**：`MfaType` 保持 VARCHAR + 常量类（不枚举化）+ 新增显示字典；白名单 10 处校验点全清单 + 逐处变更标注（§5.3.0，W14/W15 变更输入）；WebAuthn 多 credential 模型（新实体 + cryptoChallenge create 时一次写入 + 注册/认证/解绑三 ceremony + 操作级联动）；邮件码平行 `EmailCodeStore`（key-based 同形 + 三实现 + W8 装配复刻 + 三层限流）；外部 MFA 服务 deferred（扩展点已预留）；单值 mfaType 保持。
- **§六 可信设备**：请求头指纹（`X-Nop-Mfa-Device-Id` + UA + AL → SHA-256，拒绝 canvas/IP）；新实体 `NopAuthMfaTrustedDevice`（（userId,deviceHash）唯一 + no-tenant 先例）；豁免 = 密码类登录路径 only（信道不适用不登记）+ 固定窗口 30d 不滚动；撤销矩阵（到期/移除/因子变更/reset/策略禁）；`allowTrustedDevice` AND 合并（fail-safe）；满员显式拒绝（max-count 仅计未过期）。
- **§七 deferred**：7 项裁定（WebAuthn attestation 完整信任链 / 外部 MFA 服务接入 / 信道路径可信设备豁免 / DB store 批量清理 / 前端交互 / @MfaRequired 标注治理 / signCount=0 克隆检测增强——均 classification + 理由；各主题"拒绝了什么"表中的终局拒绝项不重复登记）。
- **§八 impl 映射**：W12-impl（§三 + store 场景化）/W13-impl（§四 + 受限会话 + OAuth 修复）/W14-impl（§5.3.0/§5.3.1/§5.3.2）/W15-impl（§5.3.3 + §六），各附 Protected Area 清单。

## 二、一期契约兼容性总确认

四主题对一期五项契约锚点的影响矩阵（均"不变"或"显式增量"，无隐性变更）：

| 契约锚点 | §三 操作级 | §四 角色策略 | §五 因子扩展 | §六 可信设备 |
|---|---|---|---|---|
| 两阶段登录 challenge | 不变（scene=login 缺省，一期调用点零改动；场景重载向后兼容） | 增量：不达标新第三态（不建 challenge 走受限会话）；达标路径逐字节一致 | 不变（新 mfaType 取值复用生命周期；payload 可选增量） | 增量：豁免命中 = 一期"放行"同路径（返回 null）；未命中原样 |
| `ERR_AUTH_MFA_REQUIRED` 异常表达 | 不变（操作级用独立新错误码 `ERR_AUTH_OPERATION_MFA_REQUIRED`） | 不变（受限会话是成功登录非异常；新增 4 错误码不触碰一期编码） | 不变（`MfaVerifyRequest` 可选 assertion 字段向后兼容） | 不变（豁免无新异常） |
| `completeLogin` 分界裁决 | 不变（操作级不触碰 completeLogin） | 不变（受限签发经 completeLogin 受限变体；resetFailCount/notifyHook 差异裁决照旧） | 不变（webauthn 分支成功出口仍 completeLogin） | 不变（登记在 completeLogin 前纯 DB 写） |
| store 装配（collect-beans + 条件激活 + lessons 15） | 不变（场景化是数据结构增量，装配零变更；DB 加列走 ORM 迁移） | 不变（策略只读 DB；通道验证码复用既有 store + 场景化票） | 不变（EmailCodeStore 复制 W8 模式：新前缀 + 条件激活 + 类加载安全） | 不变（ORM 实体，无新 store） |
| 明文边界（secret/恢复码） | 不变（无新秘密） | 不变（策略实体无秘密；通道 OTP 一次性消费） | 不变（secret 加密/恢复码 BCrypt 沿用；WebAuthn publicKey 为公开材料按 masked 不展示；email 码短 TTL 明文对齐 W8 裁决） | 不变（指纹为不可逆哈希） |
| 一期零回归 | 缺省 false 零介入；无注解方法零介入 | 无策略行 = 一期全部行为 | 不绑定新因子用户全流程无感知 | 不勾选/无记录用户逐字节一致 |

## 三、操作级 MFA（会话内敏感操作二次验证）

### 3.1 设计结论

1. **敏感操作声明模型 = 方法级注解 `@MfaRequired`**：声明在 BizModel 公开方法上（与 `@Auth`/`@BizMutation` 同位），敏感操作由开发者在代码中标注，不做 URL 清单/配置文件。注解归属 `nop-biz-auth-api`（认证语义注解；`nop-graphql-core` 已依赖该工件——`GraphQLActionAuthChecker` 引用 `io.nop.auth.api.AuthApiErrors` 先例）。**注解约束在元数据构建期校验（fail-fast）**：不得标注于 `@BizSubscription` 方法（订阅路径无请求-响应语义，首批不支持——静默绕过等于 fail-open）；不得与 `@Auth(publicAccess=true)` 同用（匿名方法无会话可验，标注是静默 no-op）。
2. **拦截点 = GraphQL executor 层**：注解元数据经 `ReflectionBizModelBuilder`（biz 方法反射构建）传播到 `GraphQLFieldDefinition`（先例：`@BizMakerChecker` → `makerCheckerMeta`）；**biz 合并时元数据随 `GraphQLObjectDefinition` 搬运（live 先例：`makerCheckerMeta` 在对象定义合并处显式复制）**；执行判定在 executor 的 biz 调用前统一进行（先例：`GraphQLExecutor.java:64/146` 调用 `GraphQLActionAuthChecker.INSTANCE.check(context)`，覆盖 mutation/query 两检查点）。判定逻辑经 **`IOperationMfaChecker` SPI**（`nop-biz-auth-api` 接口）委托给 nop-auth-service 实现；**接线载体 = `GraphQLEngine` 可选注入 checker + `IGraphQLExecutionContext` 透出**（全部在 nop-graphql-core 内闭合，**不触碰** nop-core 的 `IServiceContext`——`IActionAuthChecker` 的注入链经 `IServiceContext` 传播，本设计不镜像该段，避免框架核心面扩大）；未注册实现时等价于操作级 MFA 关闭，框架零介入。
3. **验证流 = 操作级 challenge 两段式**：拦截 → 抛 `ERR_AUTH_OPERATION_MFA_REQUIRED`（携带 challengeToken/mfaType/operation）→ 客户端调 `mfaVerifyOperation`（**需登录态**，校验同会话）验证第二因子 → challenge 转为"已验证票"（短 TTL）→ 客户端凭同一 token 重试原操作 → 拦截器消费票放行（**一次性**）。
4. **challenge 存储 = `MfaChallengeStore` 场景化扩展**（本小节独立裁决；§5.3.2 WebAuthn 落地时复用同一扩展）：`MfaChallenge` 增加可选字段 `scene`（缺省 `login`）/`payload`（场景数据 JSON 字符串，简单类型）/`verifiedAt`（操作级票状态）；store 接口增量恰为两个方法——**`create` 场景重载**（钉定参数表：`create(scene, userId, mfaType, loginType, tenantId, phone, payload)`，老五参签名委托 `scene=login, payload=null`）与 **`markVerified(token)`**（一次性状态迁移：未验证→已验证；重复调用/已过期返回 false，票不续命——原子性契约见 §3.3）。**payload 一次写入、只读复用**（create 时由调用方构造；不提供后置更新原语——更新原语带来并发覆盖/TTL 刷新问题，收益仅省一次 create）。**拒绝独立 OperationChallengeStore**（理由见 §3.4）。
5. **共享因子校验组件 `MfaFactorVerifier`**：登录级（`verifySecondFactorAndComplete` 的因子分支）、绑定级（`verifyFactorForBind`）、操作级（`mfaVerifyOperation`）三处因子校验收敛为一个组件（nop-auth-service 内）；验证凭据用**统一载体**（可选字段 DataBean：`code`（totp/sms/email）或 `assertion`（webauthn 断言，W14），同一期 `MfaVerifyRequest` 的可选字段模式）——webauthn 断言验证同经组件收敛，操作级/登录级/解绑共用（§5.3.2）；**TOTP 防重放窗口统一推进**：任何场景验证成功都更新 `lastVerifiedWindow`（组件内聚该副作用、调用方不可选）——防止同一 30s 窗口码跨场景重放（如先过操作级再过登录级）；W14/W15 新因子只改组件与白名单（§5.3.0 清单），操作级自动受益。
6. **操作级不接受恢复码、不被可信设备豁免**：恢复码是"丢失验证器的登录恢复通道"（使用即强制重绑）；可信设备豁免仅登录级（§6.3 威胁模型）。
7. **未启用 MFA 的用户操作级不拦截**：无第二因子可验即无"二次验证"可言；"强制用户启用因子"归角色级策略（§四），两机制分层正交。
8. **操作级开关独立**：`nop.auth.operation-mfa.enabled`（缺省 `false`，灰度启用）+ `nop.auth.operation-mfa.op-ticket-expire-seconds`（缺省 `60`，验证后允许重试原操作的窗口）。

### 3.2 背景与动机

一期 Vision Non-Goals #7 将"会话内二次验证"（如转账、改绑手机、重置凭证前再验一次）显式留给二期。登录级 MFA 只保证"登录时刻的操作者持有第二因子"；长效会话（accessToken 有效期内）内的敏感操作不再受任何因子保护。合规场景（支付、管理面高危操作）要求敏感操作时刻重新验证操作者身份。

平台既有拦截机制盘点（拦截点选型输入）：

| 机制 | 位置 | 适用性 |
|---|---|---|
| `@Auth` → `ActionAuthMeta` → `GraphQLActionAuthChecker.check`（`GraphQLExecutor.java:64/146` 两检查点） | executor 层，方法级权限 | **先例**：注解声明 + executor 层统一判定 + checker 委托 |
| `@BizMakerChecker` → `ReflectionBizModelBuilder` → `GraphQLFieldDefinition.makerCheckerMeta` → executor 分支 | 元数据传播 + executor 分支 | **先例**：biz 方法注解如何到达 executor 层（含 `GraphQLObjectDefinition` 合并搬运） |
| `CrudBizModel` 的 `BizFilter`（`io.nop.biz.crud`） | 仅 CrudBizModel 子类的实体操作过滤器 | 不通用（非 Crud BizModel 方法无法声明），排除 |
| Web Filter 链 | HTTP 层 | 只见 URL/GraphQL 查询文本，不见方法语义，排除 |

### 3.3 核心设计

**注解与元数据传播**：

- `@MfaRequired`：RUNTIME retention、METHOD 目标、无必需属性（存在即敏感）。W12-impl 落 `nop-biz-auth-api`。
- `ReflectionBizModelBuilder` 读取注解（并执行结论 1 的两项构建期约束校验，违规即构建报错）→ `GraphQLFieldDefinition` 新增 `mfaRequiredMeta`；`GraphQLObjectDefinition` biz 合并处同步搬运（对齐 `makerCheckerMeta` 先例）。`nop-graphql-core` 框架核心增量，Protected Area，plan-first。
- checker 接线：`nop-biz-auth-api` 定义 `IOperationMfaChecker`；nop-auth-service 提供实现 bean；`GraphQLEngine` 可选注入（无 bean 时不启用，零介入）并经 `IGraphQLExecutionContext` 透出到 executor 检查点。

**`payload.operation` 契约**：GraphQL operation 全名（`bizObjName__action`，与 `ReflectionBizModelBuilder` 的 operation 命名口径一致），同时进 `errorParams` 供前端定位弹窗目标。挑战创建端（拦截器侧）与消费端（重试校验侧）用同一字符串比较。

**拦截判定语义**（每请求、仅对声明了 `@MfaRequired` 的 mutation/query 方法生效；批量请求中含敏感操作时该 operation 单独报错（GraphQL 逐 field error，框架原生语义），客户端凭票整批重发，票一次性仅约束敏感 operation 本身）：

```pseudocode
operationMfaCheck(fieldDef, serviceContext, requestHeaders):
    # （受限会话分支【W13 交付，前置于下方 hasMfaRequired 早退——对**所有** operation 生效，
    #   不只 @MfaRequired 方法】：mfaRestricted 会话 → 仅放行白名单 mutation（§4.3）+
    #   全部 query + publicAccess mutation（token 刷新等会话基建）；其余 mutation 拒绝。
    #   W12 实现中等价于该分支不存在——角色策略未落地时无受限会话。）
    if !fieldDef.hasMfaRequired(): return                    # 非敏感操作零介入
    if !operationMfaConfig.enabled: return                   # 操作级总开关（缺省 false）
    user = serviceContext.getUserContext()
    if user == null: return                                  # 构建期已禁止 publicAccess 组合，此处兜底
    setting = mfaSettingDao.getByUserId(user.userId)         # 请求上下文内（含租户）
    if setting == null or setting.status != enabled: return  # 未启用 MFA → 不拦截（§3.1 结论 7）
    token = requestHeaders["X-Nop-Op-Mfa-Token"]
    if token != null:
        c = mfaChallengeStore.peek(token)
        if c != null and c.scene == operation and c.verifiedAt != null      # 已验证票
           and c.payload.operation == fieldDef.operation                    # 票绑定原操作
           and c.payload.sessionId == user.sessionId                        # 票绑定原会话
           and c 在票窗口内（verifiedAt + op-ticket-expire-seconds > now）:
            if mfaChallengeStore.consume(token) != null:
                return                                       # 仅原子消费成功者放行（并发双花防护）
    challenge = mfaChallengeStore.create(scene=operation, userId, setting.mfaType,
            payload={operation, sessionId: user.sessionId}, tenantId)     # TTL 同 challenge-expire-seconds；
                                                                          # webauthn 用户 payload 增含 cryptoChallenge（§5.3.2）
    throw ERR_AUTH_OPERATION_MFA_REQUIRED(challengeToken, mfaType, operation)
```

**验证端点**（`LoginApiBizModel` 新增 mutation，**需登录态**——操作级 challenge 产生自登录会话内，验证必须同会话，防跨会话重放；与登录级 `mfaVerify` 的公开访问形成对照）：

```pseudocode
mfaVerifyOperation(challengeToken, code):        # 需登录态
    c = mfaChallengeStore.peek(challengeToken)
    if c == null or c.scene != operation: throw CHALLENGE_EXPIRED
    if c.payload.sessionId != currentSessionId: throw CHALLENGE_EXPIRED     # 同会话校验
    if c.verifiedAt != null: throw CHALLENGE_EXPIRED       # 已是票：拒绝重复验证（票不续命）
    setting 复核（runWithTenant(c.tenantId) 内：status==enabled 且 c.mfaType==setting.mfaType，
                 镜像登录级复核语义——换绑必经 disabled 态，状态复核即已作废换绑前签发的票；
                 mfaType 硬校验防未来状态机演进破坏该隐式依赖）
    ok = mfaFactorVerifier.verify(setting, c.mfaType, code)                 # 共享因子校验（§3.1 结论 5）
    if !ok: mfaChallengeStore.incrFailCount(challengeToken)（超限作废）; throw MFA_FAIL
    if !mfaChallengeStore.markVerified(challengeToken): throw CHALLENGE_EXPIRED   # 一次性迁移失败=并发已验证
    return OK                                  # 客户端凭同一 challengeToken 重试原操作
```

成功出口**不签发任何凭证**（无 accessToken/accessCode/会话变更）——这是与登录级 `mfaVerify`（成功 = `completeLogin`）的本质区别。

**`markVerified` 的原子性契约**（一次性状态迁移，三实现各按平台原语落地，对齐一期 baseline 的原子性书写标准）：

| 实现 | 原语 | 语义 |
|---|---|---|
| Local | JVM 内原子 compute | 状态迁移单线程可见 |
| DB | 条件 `UPDATE ... SET verified_at=now WHERE challenge_token=? AND verified_at IS NULL AND expire_at>now` + affected-row 判定 | 仅首个验证者迁移成功；票窗口由 `verified_at + op-ticket-expire-seconds > now` 推导（免新增列） |
| Redis | **派生票键**（`{challengeToken}:v`）经 `putIfAbsentExAsync`（SETNX+PX 原子）写入，票键独立 TTL=`op-ticket-expire-seconds` | `INosqlKeyValueOperations` 无通用 CAS（`putIfAbsentOrMatchExAsync` 为幂等去重语义——仅当现值==新值时重写，不能表达"未验证→已验证"条件迁移），读-改-写有并发双验证竞态；派生票键用 SETNX 原子承载一次性，票键残留自然过期 |

**平台首批敏感操作建议清单**（判定标准：修改认证因子/修改联系方式/凭证库写操作/权限与角色变更/管理员重置类）：`NopAuthUserBizModel.resetUserMfa`、用户改密/改手机/改邮箱类 mutation、凭证库 `reencryptAll`/save/delete 类（W12-impl 时与各模块 owner 最终确认；本设计只定标注机制与判定标准）。

**审计**：操作级 challenge 发起/验证成功/失败/票消费四事件写 `NopAuthOpLog`（复用一期 `@BizAudit` + `LoginServiceImpl` 审计模式），记录 operation 与 sessionId。

### 3.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| HTTP Filter 链拦截 | Filter 层只见 URL/GraphQL 查询文本，不见 biz 方法语义；敏感操作是方法级声明，清单维护在 web 层必然失真 |
| 各 BizModel 手工调用 mfaCheck | 声明式退化为命令式；漏检不可发现（A2-audit 要求操作级钩子全路径覆盖，手工调用无法静态审计覆盖面） |
| 独立 `OperationChallengeStore`（新接口 + 三实现） | 每个新场景 ×（Local/Db/Redis）三实现的组合爆炸；TTL/失败计数/一次性生命周期三处重复实现必然漂移；`MfaChallengeStore` 场景化增量向后兼容（老调用点零感知），WebAuthn（§5.3.2）复用同一扩展 |
| 验证后时间窗免验证（GitHub sudo 式 15 分钟会话窗） | 窗口内**任意**敏感操作免验证（一次验证换取 N 次放行），违反最小权限；一次性票（票绑定 operation+sessionId+单次消费）与一期 challenge 一次性先例一致 |
| 已验证票可重复 `markVerified` 续命 | 可续命票 = 变相时间窗方案；一次性状态迁移 + 窗口内拒绝重复验证（§3.3） |
| Redis `markVerified` 用读-改-写 | `INosqlKeyValueOperations` 无通用 CAS 原语（一期 baseline §3.3 已裁决），读-改-写在并发双验证下竞态；派生票键 SETNX 原子承载（§3.3 表） |
| 操作级接受恢复码 | 恢复码语义 = 丢失验证器时的登录恢复通道（使用即强制重绑）；操作级复用会频繁触发强制重绑，语义错位 |
| 操作级拦截未启用 MFA 的用户 | 无第二因子可验，"拦截"无出口（把用户锁死在操作外）；强制启用归角色策略（§四），分层正交 |
| 在登录级 `mfaVerify` 上加 operation 参数复用端点 | 登录级成功 = `completeLogin` 签发会话；混入操作分支污染一期两阶段语义（一期零回归红线） |
| `IUserContext` 加"已验证"标记 | 会话级可变状态污染 `IUserContext` 契约（跨模块公共 API）；验证状态属服务端 store 数据，不属用户上下文身份 |
| checker 经 `IServiceContext` 接线（完全镜像 `IActionAuthChecker`） | 该路径触碰 nop-core 框架核心（`IServiceContext` 接口变更）；`GraphQLEngine` + `IGraphQLExecutionContext` 注入在 nop-graphql-core 内闭合即可满足，变更面更小 |
| `@MfaRequired` 允许 subscription 方法 / publicAccess 组合（静默 no-op） | 静默绕过 = fail-open，且无静态手段发现；构建期校验拒绝（§3.1 结论 1） |
| 验证票不过期/长 TTL | 票是"验证凭证"的等价物，TTL 必须短（缺省 60s，仅覆盖一次重试）；长 TTL 等价于时间窗方案 |

### 3.5 与一期契约兼容性

- **两阶段登录 challenge 不变**：`scene=login` 为缺省值，一期全部调用点（`checkMfaRequired`/`mfaVerify`）零改动；`MfaChallengeStore` 老签名行为不变。
- **`ERR_AUTH_MFA_REQUIRED` 异常表达不变**：操作级用独立错误码 `ERR_AUTH_OPERATION_MFA_REQUIRED`（新错误码，不混用一期编码——前端可区分"登录期"与"会话期"弹窗）。
- **`completeLogin` 分界裁决不变**：操作级链路完全不触碰 `completeLogin`/`resetLoginFailCountForUser`/`onLoginSuccess`。
- **store 装配不变**：collect-beans 前缀（`nopMfaChallengeStore_`/`nopSmsCodeStore_`）、`ioc:condition` 条件激活、ai-dev/lessons/15 类加载安全不变式全部保持；场景化是数据结构增量，装配零变更。DB 实现新增列（SCENE/PAYLOAD/VERIFIED_AT）走 ORM 源 → codegen → DDL 迁移；Redis 实现 `MfaChallenge` 新字段为可选简单类型，JSON 序列化兼容（W12-impl 迁移注意：跨版本滚动升级时老进程读新 JSON 字段的反序列化配置需验证，migration note 登记）。
- **明文边界不变**：操作级不引入新秘密；TOTP secret 仍仅绑定流程 provisioning URI 一次性返回。
- **一期零回归声明**：`nop.auth.operation-mfa.enabled` 缺省 `false`——不开启时拦截器零介入；开启后无 `@MfaRequired` 标注的方法零介入；一期登录级 E2E 行为不变。

## 四、角色级强制策略引擎（角色 → 强制因子映射）

### 4.1 设计结论

1. **策略模型 = 新 ORM 实体 `NopAuthRoleMfaPolicy`（1:1 角色，按需建行）**：无行 = 该角色无策略；字段域：`roleId`（唯一）+ `minMfaLevel`（因子强度下限）+ `allowTrustedDevice`（是否允许可信设备豁免登录级 MFA，§6.3 消费）+ 通用审计字段。
2. **策略语义 = "用户因子持有约束"，不改变验证流程所用因子**：策略约束的是用户必须**启用**什么强度以上的因子；登录/操作验证永远用用户已启用的因子（`NopAuthMfaSetting.mfaType`，一期单值约束保持）。评估点唯一（登录链路 `checkMfaRequired`），操作级（§三）与可信设备（§六）只消费策略结果，不做独立策略评估。
3. **因子强度序（`minMfaLevel` 取值）**：`1` = OTP 拥有通道类（sms/email）；`2` = TOTP（共享秘密 + 本地设备计算，免疫 SIM swap）；`3` = WebAuthn（硬件保护私钥 + 源绑定，抗钓鱼）。多角色合并 = **取最强**（`max(minMfaLevel)`）——最严格角色胜。
4. **三层判定顺序（兼容矩阵）**：全局开关 `nop.auth.mfa.enabled`（总闸，false 时 MFA 子系统整体旁路——含角色策略与操作级）→ 角色策略（持有约束）→ 用户级启用（一期语义）。矩阵见 §4.3。
5. **不达标用户的引导 = 受限会话（restricted session）**：`IUserContext` 新增可选 `mfaRestricted` 属性（跨模块公共 API 增量，plan-first + migration note；**必须在会话持久化之前写入**——见 §4.3 持久化机制）；受限会话仅放行 MFA 绑定类操作（含 `unbindMfa`）+ 登出，白名单由 §三 executor 拦截器同一拦截点实现（**前置于 `hasMfaRequired` 早退、对所有 operation 生效**，W13 交付该分支）。**受限会话内发起 `bindMfa` 前必须先通过"登记通道验证"**（向 `NopAuthUser` 已登记 phone/email 发一次性码验证——防 enrollment attack，验证状态载体为服务端 store 的一次性票，见 §4.3）。
6. **`confirmMfa` 校验策略（防因子降级）**：确认绑定的因子强度 < 用户角色策略 `minMfaLevel` → 拒绝（新错误码 `ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`）；解绑仍允许（用户自主权 + 审计；下次登录即受限兜底）。
7. **受限会话内 `confirmMfa` 成功后不原位升级会话**：引导重新登录走完整两阶段（拒绝会话原位升级，理由 §4.4）。
8. **无策略部署零回归**：策略表为空时三层判定退化为一期行为（逐字节等价路径）；策略开关不新增（有行即生效、status 字段不引入——删行即撤策略，避免"行存在但禁用"的双态歧义）。
9. **不达标一律直接受限（不建 challenge）**：无论"未启用"还是"已启用弱因子"，策略不达标路径统一为"第一因子通过 → 受限会话"（不创建第二因子 challenge——多验一次弱因子不改变受限结果，统一路径实现与审计都更简单）；已启用弱因子用户的升级路径 = 受限会话内先 `unbindMfa`（本身要求验证当前因子）再 `bindMfa` 强因子。
10. **一期遗留绕过路径（OAuth）一并修复**：`nop-auth-sso` 的 `OAuthLoginServiceImpl.loginAsync` 自行 `buildUserContext` + `saveUserContextAsync` 签发会话，**既不走一期 MFA 拦截也不走策略评估**（一期遗留 gap：用户级 MFA 对该入口同样不生效）。W13-impl 将该入口接入与 `loginAsync` 同语义的判定（MFA 拦截 + 策略受限），跨模块变更 plan-first（见 §八）。

### 4.2 背景与动机

一期 Vision Non-Goals #8 明确"按角色强制 MFA 留二期"：一期只有全局开关 + 用户级自愿启用，管理员无法要求"财务/运维角色必须启用 MFA"。合规场景（等保/内控）要求高危角色强制多因子。

设计输入的另一面是约束：一期 `checkMfaRequired`（`LoginServiceImpl.java:743-760`）的三分支判定（全局开关 → setting 启用检查 → 因子等同）已稳定，二期策略必须**增量插入**而不重排既有判定；且用户单值 `mfaType`（一期设计约束）下"策略强制用某因子验证"无多因子可选空间——策略只能约束"持有"，不能约束"使用"。

### 4.3 核心设计

**实体字段域（`NopAuthRoleMfaPolicy`，W13-impl 落 `nop-auth/model/nop-auth.orm.xml`）**：

| 字段 | 说明 |
|---|---|
| roleId | 角色 ID（唯一约束，1:1 `NopAuthRole`；无外键约束，对齐 nop-auth 既有关系惯例） |
| minMfaLevel | 因子强度下限：1/2/3（§4.1 结论 3 强度序） |
| allowTrustedDevice | 是否允许登录级可信设备豁免（缺省 `true`；`minMfaLevel=3` 的角色建议 `false`，§6.3） |
| 通用审计字段 | createdBy/createTime/updatedBy/updateTime/version/delFlag（软删除对齐 nop-auth 惯例） |

管理入口：`NopAuthRoleBizModel` 新增 `saveMfaPolicy`/`removeMfaPolicy` mutation（策略是独立实体，非角色实体字段；admin 权限走运行时 `requireAdmin` 校验——`resetUserMfa` 先例，非注解模式；`@BizAudit` 审计）。

**三层判定兼容矩阵**（`checkMfaRequired` 扩展后的完整真值表；"一期行为"= 现行 `checkMfaRequired` 路径逐字节等价）：

| 全局开关 | 角色策略（合并后 maxLevel） | 用户 setting | 行为 |
|---|---|---|---|
| off | 任意 | 任意 | **一期行为**：直接放行（MFA 子系统整体旁路） |
| on | 无策略（无行） | 任意 | **一期行为**：现行判定不变 |
| on | 有策略（L） | enabled 且 level(因子) ≥ L | **一期行为**：按一期三分支判定（含因子等同：`PHONE_SMS` 登录 + sms 因子直接放行且不受限——验证码即第二因子，已满足持有约束） |
| on | 有策略（L） | enabled 且 level(因子) < L | **直接受限**（不建 challenge，结论 9）：第一因子通过 → 受限会话（`mfaRestricted=true`），引导解绑弱因子 + 绑强因子 |
| on | 有策略（L） | 未启用（无行/pending/disabled） | **直接受限**（不建 challenge）：第一因子通过 → 受限会话，引导绑定 |

`factorLevel` 的 fail-closed 语义：未知 `mfaType`（白名单外的值）强度视为 `0`——策略下即不达标（受限），白名单外值不扩散为可验证因子（与 §5.3.0"未知值不扩散"纪律一致）。

评估点伪代码（`checkMfaRequired` 内增量插入，一期分支保持原位原序；一期尚有 store 装配检查分支 `mfaChallengeStore == null → return null`（:746-747），策略评估插入点在其**之后**——无 store 部署 = MFA 功能整体不可用 = 放行，一期语义不变）：

```pseudocode
checkMfaRequired(user, loginType):                      # 一期方法签名不变
    if !mfaConfig.enabled: return null                  # 一期分支 1（总闸，原样）
    if mfaChallengeStore == null: return null           # 一期分支 1b（store 未装配，原样）
    policy = roleMfaPolicyEvaluator.maxLevelFor(user)   # 增量：角色策略合并（无策略=0）；evaluator 产出
                                                         # {maxLevel, allowTrustedDevice} 复合结果——maxLevel
                                                         # 取 max、allowTrustedDevice 取 AND（任一 false 即禁，
                                                         # §6.3 消费该布尔实现可信设备豁免联动）
    setting = loadMfaSetting(user.userId)               # 一期分支 2（原样）
    enabled = setting != null and setting.status == enabled and !isEmpty(setting.mfaType)
    if policy > 0 and (!enabled or factorLevel(setting.mfaType) < policy):
        return MFA_RESTRICTED                            # 新第三态：非 null 非 challenge（结论 9：不建 challenge）
    if !enabled: return null                             # 一期分支 2 放行（原样，无策略时零变化）
    if loginType == PHONE_SMS and mfaType == sms: return null   # 一期分支 3 因子等同（原样）
    return createChallenge(...)                          # 一期 challenge 创建（原样）

loginAsync / createSessionForUserAsync / mfaVerify 完成路径（调用侧增量）:
    decision = checkMfaRequired(user, loginType)
    if decision == MFA_RESTRICTED:
        return completeLogin(user, request, headers, resetFailCount, notifyHook, restricted=true)
                                          # 受限签发：标志在会话持久化之前写入（见下"持久化机制"）
    ...（一期 challenge/放行路径原样）
```

**`mfaRestricted` 的持久化机制（关键，W13-impl 迁移清单）**：会话上下文经 `userContextCache.saveUserContextAsync` 持久化，且 Dao-cache 部署（`DaoUserContextCache`）**每请求从 `NopAuthSession.cacheData` 反序列化全新对象**、序列化是**手工白名单**（`UserContextImpl.serializeToJson` + `DaoUserContextCache.saveUserContextAsync` 两处）——因此：(a) 标志必须在 `completeLogin` 内部 `saveUserContextAsync` **之前**写入 context（`completeLogin` 增加受限签发变体，一期三处调用点行为不变）；(b) 两处序列化白名单必须同步纳入 `mfaRestricted`，否则 Dao-cache 部署下第二请求起标志丢失 = **fail-open**（Local cache 存对象引用不会暴露此缺陷——测试必须覆盖 Dao-cache 路径）。此项列入 §八 W13-impl 的 Protected Area/migration 清单。

**受限会话的权限面与拦截**：`mfaRestricted=true` 的会话经 §三 executor 拦截器的预留分支拦截（W13 交付该分支，**前置于 `hasMfaRequired` 早退**）——对所有 operation 生效：白名单 mutation 放行（绑定类 `bindMfa`/`confirmMfa`/`unbindMfa`/`getMfaStatus`/登记通道验证端点 + 登出 + 会话基建类 publicAccess mutation 如 token 刷新——受限会话中途 token 过期不能被打断死锁）；全部 query 放行（只读无害，便于前端渲染引导页）；其余 mutation 拒绝（新错误码 `ERR_AUTH_MFA_RESTRICTED_SESSION`，前端引导完成绑定）。响应契约：`LoginResult` 与 `ScanLoginResult` 均增加可选 `mfaRestricted` 字段（跨模块公共 API 增量，plan-first + migration note；密码路径登录响应与扫码 PC 端 `getLoginResultAsync` 在登录时刻即可感知受限，对称先例 = W6 `ScanLoginResult.mfaRequired`）。

**登记通道验证（enrollment attack 防御，受限会话内 bindMfa 的前置门槛；验证状态载体 = 服务端 store 一次性票，非会话标记——与 §3.4"IUserContext 不承载验证状态"原则一致）**：

```pseudocode
# 受限会话内 bindMfa 前置（正常会话 bindMfa 不受影响）：
if context.mfaRestricted:
    proofTicket = mfaChallengeStore.peekVerified(scene=channel-proof, userId)   # §三 场景化票
    if proofTicket == null:                                   # 无有效通道验证票
        channel = 用户登记的 phone 或 email（服务端解析，不接受客户端指定；缺省 phone，
                  phone 与 email 均登记时 W15 后允许用户选择，W13 仅 phone）
        if channel 为空: throw ERR_AUTH_MFA_NO_RECOVERY_CHANNEL   # 无法自助脱困，管理员介入
        向该通道发送一次性码（SmsCodeStore/EmailCodeStore，key=proof:{userId}）
        throw ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED(channel 脱敏提示)
    mfaChallengeStore.consume(proofTicket)                    # 消费通道验证票（一次性）

# verifyChannelProof(code)（受限会话内、白名单端点）:
#   校验 proof:{userId} 码 → 成功即创建 channel-proof 场景已验证票（短 TTL，同 §三 票语义）
```

威胁模型：无此防御时，仅持有密码的攻击者进入受限会话后可 `bindMfa(totp)` 拿到 provisioning URI 并绑定**自己的**验证器 → 重新登录 → 完全接管（经典 enrollment attack）。登记通道 OTP 证明攻击者同时控制受害者登记手机/邮箱——将攻击门槛从"仅密码"提升到"密码 + 登记通道"。`resetUserMfa` 后被策略强制的用户同理受此保护（管理员重置仍是丢失验证器+恢复码的最终出路）。

**引导流程编排（状态流）**：

```pseudocode
登录（第一因子通过）→ 受限会话（mfaRestricted）
  → [登记通道 OTP 验证]（受限会话内 bindMfa 的前置）
  → 已启用弱因子用户：unbindMfa（验证当前弱因子，攻击者无因子不可解绑）→ 解绑成功
  → bindMfa(level ≥ minMfaLevel 的类型) → confirmMfa（策略校验：因子强度不足 → FACTOR_TOO_WEAK 拒绝）
  → 登出 → 重新登录（完整两阶段：第一因子 + 新因子 challenge）→ 完整会话
```

**策略评估的时点语义**：(a) 角色策略在**登录时**评估（用户角色变更/策略变更在下次登录生效——角色快照随会话建立，对齐 nop-auth 既有权限快照语义；"下次登录"以 challenge 签发/受限签发时刻为登录起点，进行中的 challenge（TTL 300s）在旧策略下完成有效——对齐一期"全局开关中途关闭，已发 challenge 继续有效"先例）；(b) 会话中期角色回收不回溯改写已签发会话的受限状态；(c) 角色合并口径 = 与 `buildUserContext` 角色快照一致（直接角色 + `childRoleIds` 继承展开 + 隐式 user 角色及其继承链——策略挂 user 角色 = 全员强制，属显式用法而非漏洞）。

### 4.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| `NopAuthRole` 加列存策略 | 核心实体加列触碰全环境迁移与全部既有 NopAuthRole 消费面；按需建行的独立表让"无策略角色零成本"，策略 CRUD 不混入角色管理状态机 |
| 允许因子集合（set）+ 交集/并集合并 | 空交集 = 策略冲突（拒绝登录还是放行？两难）；level + max 的合并语义直观且单调（最严格角色胜），覆盖合规场景 |
| 策略改变验证流程所用因子（登录时强制用特定因子验证） | 用户单值 mfaType 下无"选用"空间；策略=持有约束 + 评估点唯一，避免验证流程多路分支 |
| 拒绝登录直到绑定完成（门外 enrollment） | 预登录匿名绑定 API（凭 enroll token）扩大公开攻击面且需复制整套匿名绑定状态机；受限会话复用登录态绑定 API + §三 拦截器白名单 |
| 受限会话内 confirmMfa 后**原位升级**会话 | 会话安全等级原地变更的透明性差（审计/并发/中间件快照全要处理等级切换）；重新登录走完整两阶段，安全边界清晰、实现零 hack |
| 受限会话内 bindMfa 无前置通道验证 | enrollment attack：仅密码攻击者绑定自己的验证器接管账户（§4.3 威胁模型）；登记通道 OTP 把门槛提升到"密码 + 登记通道" |
| 解绑后当前会话立即降权/拦截 | 解绑本身已通过当前因子验证且有审计；下次登录受限兜底；"解绑即降权"需会话安全等级动态重评，复杂度与边际收益不成比 |
| 策略实体加 status（enabled/disabled 双态） | "行存在但禁用"与"删行"双出口必然漂移；删行即撤策略（操作可审计），单态最简 |
| 已启用弱因子用户先完成弱因子两阶段再受限 | 多验一次弱因子不改变受限结果；统一"不达标 → 直接受限"路径，实现/审计/矩阵皆简单（结论 9） |
| 通道验证状态放会话标记（`hasPassedChannelProof` 会话 attr） | 与 §3.4"IUserContext 不承载验证状态"原则冲突；Dao-cache 白名单序列化下会话 attr 还有持久化时序陷阱；服务端 store 一次性票（§三 场景化票复用）语义与操作级票一致 |
| 受限会话白名单不含 `unbindMfa` | 已启用弱因子用户升级必经"解绑 → 绑强因子"；`unbindMfa` 本身要求验证当前因子（攻击者无因子不可解绑），放行无风险；漏掉即用户锁死 |
| 会话中期角色变更实时重评受限状态 | 与 nop-auth 权限快照语义不一致（角色回收本就下次登录生效）；实时重评需每请求查策略表，登录时评估 + 会话携带标志足够 |
| 角色策略直接豁免/限制操作级 MFA | 策略是登录期持有约束（§4.1 结论 2）；操作级是会话期敏感操作保护，两者正交，组合仅经 `allowTrustedDevice` 单点（§6.3） |

### 4.5 与一期契约兼容性

- **两阶段登录 challenge 不变**：策略不达标路径**不创建 challenge**（走受限会话，结论 9）；达标路径 challenge 创建与一期逐字节一致；一期"全局开关 → store 装配检查 → setting 检查 → 因子等同"分支原位原序保留（伪代码标注"原样"）。
- **`ERR_AUTH_MFA_REQUIRED` 异常表达不变**：受限会话不是异常路径（是成功登录 + 受限标志）；新增错误码仅 4 个（`ERR_AUTH_MFA_RESTRICTED_SESSION`/`ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED`/`ERR_AUTH_MFA_NO_RECOVERY_CHANNEL`/`ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK`），不触碰一期编码。
- **`completeLogin` 分界裁决不变，签发路径增量**：受限签发经 `completeLogin` 的受限变体（`restricted=true`），`resetFailCount`/`notifyHook` 差异裁决照旧（第一因子成功仍属登录成功，审计不失真）；**标志在 `saveUserContextAsync` 之前写入**（持久化机制见 §4.3——`UserContextImpl.serializeToJson` 与 `DaoUserContextCache` 序列化白名单两触点列入 W13-impl migration note）。
- **store 装配不变**：策略评估只读 DB（`NopAuthRoleMfaPolicy`），无新存储组件；登记通道验证码复用 `SmsCodeStore`/`EmailCodeStore`（W15 前仅 phone 通道可用，email 通道 W15 解锁）+ §三 场景化 challenge 承载通道验证票。
- **明文边界不变**：策略实体无秘密；登记通道 OTP 不落明文（store 内一次性消费，同一期短信码）。
- **跨模块公共 API 增量声明（均为 Protected Area，W13-impl 需 plan-first + migration note）**：`IUserContext.mfaRestricted`（可选属性，缺省 false/absent，老消费方零感知）、`LoginResult.mfaRestricted` 与 `ScanLoginResult.mfaRestricted`（可选字段，W6 先例）、`OAuthLoginServiceImpl` 登录路径接入 MFA/策略判定（nop-auth-sso 跨模块，一期遗留 gap 的二期修复，结论 10）。
- **一期零回归声明**：无策略行时矩阵前两行即一期全部行为；`checkMfaRequired` 第三态 `MFA_RESTRICTED` 仅在"有策略且不达标"时出现（无策略部署不可达该路径）；OAuth 入口接入前该路径行为与一期一致（一期本就未拦截）。

## 五、因子扩展（MfaType 扩展 + WebAuthn/FIDO2 + 邮件验证码 + 外部服务评估）

### 5.1 设计结论

1. **`MfaType` 形态保持"VARCHAR 列 + 常量类"不枚举化**：W14/W15 新增因子 = `NopAuthConstants` 新增常量（`MFA_TYPE_WEBAUTHN`/`MFA_TYPE_EMAIL`）+ §5.3.0 白名单校验点逐处扩展；同步新增**显示字典** `mfa-type.dict.yaml`（落 `nop-biz-auth-core` 的 `_vfs/dict/auth/`——与 `login-type.dict.yaml` 同目录同先例；供管理界面/i18n 显示——校验源在代码常量，字典仅作显示，对齐一期 loginType"以代码常量为准，dict 仅作显示"裁决）。
2. **WebAuthn/FIDO2 = 多 credential 模型**：新 ORM 实体 `NopAuthMfaCredential`（1:N 用户，硬件凭证行）；用户级仍是单值 `mfaType`（`webauthn` 为取值之一）；ceremony 挑战复用 `MfaChallengeStore` 场景化扩展（§三）的 `payload` 载 WebAuthn 密码学挑战（**create 时一次写入，无后置更新原语**，见 §5.3.2）；验证器组件 `WebAuthnAuthenticator` 落 **nop-auth-service**（承载第三方依赖；`nop-biz-auth-core` 保持零第三方依赖），经 §三 `MfaFactorVerifier` 接入。
3. **邮件验证码 = 平行 `EmailCodeStore`**：与 `SmsCodeStore` 同形接口（key-based：`send(key)` 生成存储并返回明文码 / `verify(key, code): CodeVerifyResult` / `consume(key)`）+ 三实现（Local/Db/Redis，collect-beans `nopEmailCodeStore_` 前缀 + `ioc:condition` 条件激活，复制 W8 装配模式）；发送通道复用 `IEmailSender`（`TencentEmailSender` 已在）；三层限流对齐 sms-code 先例；**不泛化改名** `SmsCodeStore`。
4. **外部 MFA 服务（Authy/Duo）= deferred**（out-of-scope improvement，理由 §5.3.4）：平台因子谱系（TOTP/SMS/EMAIL/WebAuthn）覆盖主流强度带；接入路径已预留（`MfaFactorVerifier` 新实现 + 常量 + 白名单），无架构阻塞。
5. **恢复码因子无关、防重放分因子**：恢复码语义不变（绑定任意因子即生成、仅登录级、使用即作废+强制重绑）；防重放：TOTP = per-user 窗口推进（§三 统一推进裁决）/ SMS/EMAIL = 一次性原子消费 / WebAuthn = challenge 一次性 + `signCount` 单调递增（克隆检测）。
6. **单值 `mfaType` 约束保持**：`webauthn` 是单值因子类型之一（其下多 credential 设备行）；多因子并存（如 TOTP+WebAuthn 同时启用）不做（§5.4）。

### 5.2 背景与动机

一期 vision §三 Non-Goals #2/#3/#5 将 WebAuthn、外部 MFA 服务、邮件验证码显式留给二期；roadmap W14（WebAuthn + MfaType 扩展）/W15（邮件码 + 可信设备）是本小节的直接消费方。

现状盘点（§5.3.0 基线）：`mfaType` 为 VARCHAR(10) 列 + 两常量 + 一处入参白名单 + 多处 if/else 分支；挑战在于**新因子接入的变更面必须可枚举**（W14-impl 只读本设计即可列出全部触点），且不得破坏一期"未知值不扩散"兜底（`verifySecondFactorAndComplete` else fail-closed）。

邮件通道现状：`IEmailSender`（`sendEmail(EmailMessage)`，无模板概念——subject/text 由调用方组装，对照 `SmsMessage.templateCode`）；用户 email 列已存在（`NopAuthUser.email`，orm.xml:63）。WebAuthn 现状：平台零 WebAuthn 能力，需完整引入（ceremony/COSE/attestation）。

### 5.3 核心设计

#### 5.3.0 mfaType 白名单校验点盘点基线与变更标注（Phase 1 落盘 + Phase 2 标注，W14/W15-impl 的变更输入）

一期 live 代码中 mfaType 取值的全部校验/分支/承载点全清单（`MFA_TYPE_` 常量全量 grep + `"totp"`/`"sms"` 字面量兜底核查，2026-08-14 复核）。W14（webauthn）/W15（email）新增因子时逐处对照本清单更新：

| # | 锚点 | 现状行为 | W14/W15 需要的变更 |
|---|---|---|---|
| 1 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/NopAuthConstants.java:42-43` | 字符串常量定义 `MFA_TYPE_TOTP="totp"` / `MFA_TYPE_SMS="sms"`（唯一定义点） | 新增 `MFA_TYPE_WEBAUTHN="webauthn"`（W14）与 `MFA_TYPE_EMAIL="email"`（W15）常量；`factorLevel` 强度常量表**随 §四 W13 落地**（全量 1/2/3 映射，W14/W15 仅核对新常量已入表） |
| 2 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:135` | `bindMfa` 入参白名单校验：硬编码两值（非 totp/sms 即抛 `ERR_AUTH_INVALID_LOGIN_REQUEST`，消息 "only totp/sms supported"） | 白名单扩容：两常量加入合法值集合；错误消息去掉 "only totp/sms supported" 硬编码（改动态拼接合法值列表） |
| 3 | `NopAuthUserBizModel.java:156-159`（`bindMfa` 内分派） | TOTP → `bindTotp`（secret + provisioning URI）/ 其余（已过白名单必为 sms）→ `bindSms`（向用户手机发码）二分派 | 分派扩展：email 走 sms 同形路径（发码到登记 email）；webauthn 走 registration ceremony 发起（创建 pending setting + registration challenge，返回 creation options，见 §5.3.2） |
| 4 | `NopAuthUserBizModel.java:274`（`confirmMfa` 内） | 仅 `mfaType==totp` 时更新 `lastVerifiedWindow`（防重放窗口，sms 无此语义） | 不动（仅 totp 有窗口语义；webauthn 的 confirm 是独立端点，见 §5.3.2） |
| 5 | `NopAuthUserBizModel.java:395-415`（`verifyFactorForBind`，`confirmMfa`/`unbindMfa` 共用） | totp 分支（`TOTPAuthenticator.verify` 解密校验 + 窗口防重放）/ sms 分支（`SmsCodeStore.verify(key="mfa:{userId}")` 原子消费，EXPIRED 抛 `ERR_AUTH_SMS_CODE_EXPIRED`）/ 其余返回 false（fail-closed） | 收敛进 §三 `MfaFactorVerifier` 后扩展分支：email 同 sms（`EmailCodeStore.verify(key="mfa-email:{userId}")`）；webauthn 不经此方法（registration ceremony 独立验证 attestation）；兜底 return false 保留 |
| 6 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:465-479`（`verifySecondFactorAndComplete`，登录级第二因子验证） | totp 分支（`verifyTotp`）/ sms 分支（`SmsCodeStore.verify("mfa:{userId}")`）/ **else 分支 fail-closed：未知 mfaType 作废 challenge 并抛 `ERR_AUTH_MFA_CHALLENGE_EXPIRED`** | 扩展分支：email 同 sms 形态；webauthn 走断言验证（`MfaVerifyRequest` 增加可选 assertion 字段，见 §5.3.2）；**else fail-closed 分支保留**（未知 mfaType 仍作废 challenge——未来新值未接入时的安全兜底） |
| 7 | `LoginServiceImpl.java:755`（`checkMfaRequired` 因子等同分支） | `loginType==PHONE_SMS(5) && mfaType==sms` → 放行（验证码即第二因子，Vision Non-Goals #9） | 不动（无 email 登录 loginType、webauthn 无对应登录方式——无新等同分支，显式声明） |
| 8 | `LoginServiceImpl.java:647-675`（`sendMfaCode`，MFA 验证码重发） | **隐式假设 SMS**：peek challenge → 解析手机号（setting.phone 回退 user.phone）→ 限流 → `SmsCodeStore.send("mfa:{userId}")`；**不检查 challenge.mfaType** | **需重构为按 `challenge.mfaType` 分派**：sms → 现行为；email → `EmailCodeStore.send("mfa-email:{userId}")` + 发送至登记 email；totp/webauthn 类型 challenge → 返回明确错误（无码可发，新增错误码 `ERR_AUTH_MFA_CODE_UNSUPPORTED`） |
| 9 | `nop-auth/model/nop-auth.orm.xml:1069`（`NopAuthMfaSetting.mfaType`）与 `:1200`（`NopAuthMfaChallenge.mfaType`） | VARCHAR(10) 列，comment "totp/sms（空=未启用）"；非枚举、无字典、无 FK | comment 更新（"totp/sms/email/webauthn"）；列类型/精度不变（VARCHAR(10) 容纳 `webauthn`(8)/`email`(5)） |
| 10 | 生成物链：`_NopAuthMfaSetting.xmeta` / `_NopAuthMfaChallenge.xmeta` 的 mfaType prop、`_vfs/i18n/*/_nop-auth.i18n.yaml` 标签、`_app.orm.xml`、`deploy/sql/**/_create_nop-auth.sql` | 全部由 ORM 源生成（**禁止手编**，AGENTS.md 生成物纪律） | ORM 源变更后 codegen 再生；`mfa-type.dict.yaml` 为**新增源文件**（非生成物，落 `nop-biz-auth-core` 的 `_vfs/dict/auth/`，仅显示用） |

补充事实（二期设计输入）：`mfaType` 的取值域校验只有 #2 一处入参门禁；#6 的 else fail-closed 与 #5 的返回 false 是"未知值不扩散"的两道兜底。`MfaBindResult`/`MfaStatusResult`（绑定/状态查询响应）携带 mfaType 为透传字符串，无取值分支。

#### 5.3.1 MfaType 形态裁决（枚举化问题的正式裁定）

**裁决：保持"VARCHAR 列 + 常量类集中定义"，不收敛为 Java enum，不做字典驱动校验。**

- 新增值接入方式（W14/W15 的标准动作）：(a) `NopAuthConstants` 新增常量；(b) §5.3.0 清单逐处扩展（入参白名单/绑定分派/因子校验组件分支/登录验证分支）；(c) `mfa-type.dict.yaml` 补显示条目；(d) ORM 列 comment 更新（语义文档，列类型/精度不变——VARCHAR(10) 容纳 `webauthn`(8)/`email`(5)）。
- 取值域校验仍单点收敛在 `bindMfa` 入参白名单（#2），未知值兜底仍靠 #5/#6 的 fail-closed 分支——与一期"白名单 + fail-closed 兜底"双层结构一致。
- `factorLevel`（§4.3 强度序）常量表与常量类同点维护（`sms`/`email`=1、`totp`=2、`webauthn`=3、未知=0 fail-closed）。

#### 5.3.2 WebAuthn/FIDO2 设计

**协议与依赖**：W3C WebAuthn Level 2。**不自研协议栈**（CBOR/COSE 解析、attestation 格式验证、断言签名验证的安全敏感协议面）——W14-impl 引入成熟 Java 库（选型基准：java-webauthn-server 级别的社区标准实现、许可兼容、依赖面小；**具体库 POC 后在 W14-impl plan 定稿**，本设计只锁边界：库由 `WebAuthnAuthenticator`（nop-auth-service）封装，不外溢到接口签名）。`nop-biz-auth-core` 不引入该依赖（core 零第三方依赖约束，TOTP 50 行自研先例不可复制于 WebAuthn）。

**密码学挑战（cryptoChallenge）的承载裁定（关键）**：WebAuthn 要求服务端记住自己签发的随机挑战并在断言验证时比对（防客户端自造挑战）——本设计将其作为 **`payload` 的一部分在 `create` 时一次写入**（32 字节随机，base64url）：登录级在 `checkMfaRequired` 创建 challenge 处（webauthn 类型时构造 payload）、操作级在 §三 拦截器创建处、解绑经 `webauthnBeginVerify` 端点。**不提供 payload 后置更新原语**——多次取 options 幂等（读同一 payload），challenge 自身的一次性（consume）即防重放，无需刷新挑战；后置更新原语会引入并发覆盖与 TTL 刷新问题（§三 store 扩展面保持两方法）。

**数据模型**：新实体 `NopAuthMfaCredential`（W14-impl 落 `nop-auth/model/nop-auth.orm.xml`）：

| 字段 | 说明 |
|---|---|
| sid | 主键（seq） |
| userId | 用户 ID（索引） |
| credentialId | WebAuthn credential ID（base64url，**唯一约束**） |
| publicKey | COSE 公钥（验证断言用） |
| signCount | 最近断言签名计数（克隆检测，单调递增校验） |
| transports | 传输方式（internal/hybrid/usb/nfc/ble，审计与 UI 提示） |
| name | 设备命名（用户自定义，如 "YubiKey 5C"） |
| status | enabled/disabled（禁用单把钥匙不解绑整体） |
| lastUsedAt / 通用审计字段 | 审计 |

`NopAuthMfaSetting` 结构不变：`mfaType=webauthn` 时 `secret` 为空（WebAuthn 无共享秘密），setting 承担"用户级启用状态"（pending/enabled/disabled 状态机复用），credential 行承担密钥材料——**setting 与 credential 是"启用状态 + 密钥集"分工**，与 TOTP 的"secret 内嵌 setting"不同但外部的状态机语义（bindMfa → pending → confirm → enabled）一致。

**注册 ceremony（绑定，适配一期绑定状态机）**：

```pseudocode
bindMfa(webauthn):                                  # 受限会话内同样受 §4.3 通道验证前置
    创建 pending setting（mfaType=webauthn, secret=null）          # 覆盖写语义同一期
    cryptoChallenge = 随机 32B
    challenge = mfaChallengeStore.create(scene=webauthn-register, userId, mfaType=webauthn,
            payload={sessionId, cryptoChallenge})                   # §三 场景重载，一次写入
    return { challengeToken, creationOptions }    # rp/rpId/user/pubKeyCredParams/
                                                   # excludeCredentials(已有 credential 防重复注册)/
                                                   # challenge=cryptoChallenge
confirmWebauthnRegistration(challengeToken, attestationResponse):   # 需登录态，新端点
    c = peek(challengeToken)（scene=webauthn-register + c.payload.sessionId==当前会话）
    验证 attestation（clientData.challenge 匹配 c.payload.cryptoChallenge / origin / rpId / 格式）
    失败 → incrFailCount(challengeToken)（超限作废）+ MFA_FAIL（同一期失败模式，不消费 challenge）
    credential 落库（credentialId 唯一冲突=重复注册，拒绝）
    setting.status = enabled（换绑原子性同一期：仅此刻生效）+ 生成恢复码（对齐一期 confirmMfa）
    consume(challengeToken)
```

**认证 ceremony（登录第二因子，复用一期 `mfaVerify` 端点族）**：

```pseudocode
# 登录链路（增量仅在 checkMfaRequired 创建处）：webauthn 类型 challenge 的 payload 含 cryptoChallenge
# → ERR_AUTH_MFA_REQUIRED（一期异常表达不变）
webauthnAuthOptions(challengeToken):                # 通用 options 读取端点（任意 scene 的 webauthn challenge）
    c = peek(challengeToken)
    scene 为 login：公开可访问（登录期无会话）
    scene 非 login：需登录态且 c.payload.sessionId==当前会话（操作级/解绑场景）
    return requestOptions                           # rpId / allowCredentials(该用户 enabled credentials) /
                                                    # challenge=c.payload.cryptoChallenge（只读，不更新）
mfaVerify(request 含可选 assertion 字段):            # 一期端点扩展（MfaVerifyRequest 可选增量字段）
    ... 一期 peek/复核流程原样 ...
    if mfaType == webauthn:
        credential = 按 assertion.credentialId 查 NopAuthMfaCredential（enabled）
        验证断言（公钥验签 / clientData.challenge 匹配 c.payload.cryptoChallenge / origin / rpId）
        失败 → incrFailCount + MFA_FAIL（一期失败模式）
        防重放：signCount 严格递增则更新；signCount=0 的认证器（无计数实现）跳过单调校验（协议允许），仅记审计
        consume(challengeToken) → completeLogin（一期出口不变）
```

**解绑 ceremony（webauthn 用户的 `unbindMfa` 适配——webauthn 无"验证码字符串"可输，断言验证需 fresh challenge）**：

```pseudocode
webauthnBeginVerify():                              # 需登录态，新端点（解绑等会话内 webauthn 验证的挑战发起）
    cryptoChallenge = 随机 32B
    challenge = mfaChallengeStore.create(scene=webauthn-unbind, userId, mfaType=webauthn,
            payload={sessionId, cryptoChallenge})
    return { challengeToken, requestOptions }       # allowCredentials 同上
unbindMfa 请求扩展（可选 challengeToken + assertion 字段，向后兼容）:
    if setting.mfaType == webauthn:
        按 challengeToken 取 scene=webauthn-unbind challenge（sessionId 校验）→ 验断言
        （同认证 ceremony 语义：失败 incrFailCount + MFA_FAIL；成功 consume）
    else: 一期 verifyFactorForBind 路径原样
    成功 → status=disabled + 删除恢复码（一期语义）
```

§4.3/§4.4 依赖的"unbindMfa 本身要求验证当前因子（攻击者无因子不可解绑）"语义在 webauthn 下由断言 ceremony 等价保持。

**操作级联动（§三 收敛承诺的兑现）**：`mfaVerifyOperation` 的验证凭据用 §3.1 结论 5 统一载体（`code` 或 `assertion` 可选字段）——webauthn 用户触发 `@MfaRequired` 操作时：拦截器创建的 scene=operation challenge 的 payload 含 cryptoChallenge → 客户端经 `webauthnAuthOptions` 取 options → `mfaVerifyOperation(challengeToken, assertion)` → `MfaFactorVerifier` webauthn 分支 → markVerified 转票（§三 流程不变）。W14 只改组件与白名单，操作级链路零结构变更。

**RP 配置**：`nop.auth.mfa.webauthn.rp-id` / `rp-name` / `origins`（W14 落 `NopAuthConfigs`）；origins 不匹配 = 验证拒绝（fail-closed，防钓鱼域）。

**credential 管理**：`NopAuthUserBizModel` 扩展 `listWebauthnCredentials`/`removeWebauthnCredential`/`renameWebauthnCredential`（W6 并入先例）；**移除最后一把 enabled credential 被拒绝**（`ERR_AUTH_MFA_LAST_CREDENTIAL`——enabled 但零 credential = 自锁死；整体解绑走 `unbindMfa` 的 webauthn ceremony）。

#### 5.3.3 邮件验证码设计

**存储**：新接口 `EmailCodeStore`（`nop-biz-auth-core`，与 `SmsCodeStore` 同形——key-based：`send(key)` 生成 6 位码存储并**返回明文码**（实际发送由调用方经 `IEmailSender` 完成，同一期 sms 模式）/ `verify(key, code): CodeVerifyResult`（校验 + 成功原子消费 + 失败内部计数）/ `consume(key)`）+ 三实现（Local 在 core；Db/Redis 在 nop-auth-service，Db 表 `nop_auth_email_code` 结构对齐 `nop_auth_sms_code`）；装配复制 W8 模式：collect-beans `nopEmailCodeStore_` 前缀（`ioc:ignore-depends` + `autowire-candidate=false`）+ Redis bean `ioc:condition` 条件激活（lessons 15 不变式）。`MfaStoreProvider` 扩展收集第三组 map（或平行 `EmailStoreProvider`——W15-impl 按最小改动裁定，语义等价）。

**key 约定（通道隔离，同一期 key 隔离纪律）**：绑定与验证共用 `mfa-email:{userId}`（对照 sms 的 `mfa:{userId}`）——通道间 key 前缀隔离，防 pending 期类型切换的码互窜。

**发送链路**：`EmailMessage` 无模板概念（subject/text 直排）→ 邮件码文案配置化：`nop.auth.email-code.subject-template`/`text-template`（含 `{code}` 占位符，服务端替换）；发送目标一律服务端从 `NopAuthUser.email` 解析（不接受客户端指定邮箱，防枚举/骚扰——同 sms bindMfa 先例）；`IEmailSender` 未装配时 email 因子绑定 fail-closed（对齐 sms 无通道部署行为）。

**限流/防滥用（对齐一期 sms-code 三层限流先例）**：`nop.auth.email-code.*` 配置组：`enabled`（缺省 false）/`expire-seconds`（300）/`send-interval-seconds`（60）/`daily-limit`（20，email 维度）/`ip-daily-limit`（50）/`max-attempts`（5）。**无独立公开发码端点**（email 码仅服务 MFA 绑定/验证/通道验证，无邮箱登录场景）——发码入口为 `bindMfa(email)`/`sendMfaCode`（按 mfaType 分派，§5.3.0 #8）/登记通道验证，限流在这些入口生效（email+IP 双维度，复用一期 `checkSmsRateLimit` 模式）。

#### 5.3.4 外部 MFA 服务评估（Authy/Duo）

**结论：deferred（out-of-scope improvement）**。理由：(a) 因子谱系已覆盖主流强度带——TOTP（离线共享秘密）/SMS+EMAIL（拥有通道）/WebAuthn（硬件抗钓鱼），外部推送式服务（Duo Push/Authy）的增量价值主要在"带外推送 + 批准交互"，属体验优化而非能力缺口；(b) 引入即增加运行时外部依赖（可用性/延迟/费用）与数据出境合规面；(c) 接入路径已预留且无架构阻塞——外部服务验证器 = `MfaFactorVerifier` 新实现 + 常量 + 白名单扩展 + 挑战 payload 载服务侧状态，本设计的扩展点（常量类/MfaFactorVerifier/场景化 challenge）足以承载，无需现在设计。Successor Required: no（三期出现真实需求时按扩展点另行设计）。

#### 5.3.5 恢复码/防重放在多因子下的一揽子语义

| 机制 | totp（现状） | sms（现状） | email（W15） | webauthn（W14） |
|---|---|---|---|---|
| 恢复码 | 因子无关：绑定任意因子即生成 10 个一次性恢复码（BCrypt 加盐），**仅登录级**接受，使用即作废 + status=disabled 强制重绑（一期语义，四因子统一，零增量） | 同左 | 同左（W15 邮箱用户丢邮箱同样需要恢复通道） | 同左（丢硬件钥匙的恢复通道） |
| 防重放 | per-user `lastVerifiedWindow` 推进（§三 统一推进裁决：任何场景成功验证都推进） | 一次性原子消费（`SmsCodeStore.verify` removeIfMatch）+ 内部失败计数 | 同 sms（`EmailCodeStore` 同形语义） | challenge 一次性（consume）+ `signCount` 单调递增（克隆检测；count=0 认证器跳过+审计） |
| 失败计数 | challenge `incrFailCount` 超限作废（§三） | 双计数（store 内部 + challenge），一期语义保持 | 同 sms 双计数 | challenge `incrFailCount` 同语义 |

### 5.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| `MfaType` 收敛为 Java enum | ORM/xmeta/i18n/SQL/GraphQL 全生成链按 string 生成；一期常量引用点全量改写无编译期检查外的收益（取值域校验本就单点收敛在 bindMfa 白名单）；DB 列类型无需变更 |
| 字典驱动取值校验 | 字典是显示层制品；校验源必须在代码常量（一期 loginType 裁决先例：代码常量为准、dict 仅显示）——双源必然漂移 |
| 泛化改名 `SmsCodeStore` → `OtpCodeStore`（合并 email） | 改名即跨模块公共 API 变更（接口 + 三实现 + 表语义迁移 + 装配前缀），收益仅省一套平行代码；平行 `EmailCodeStore` 一期零触碰 |
| `nop-biz-auth-core` 引入 WebAuthn 库 | core 零第三方依赖约束（TOTP 50 行自研先例不可复制于 WebAuthn——协议面大且安全敏感）；验证器在 nop-auth-service 承载依赖 |
| 自研 WebAuthn 协议栈 | CBOR/COSE/attestation 验证安全敏感，自研风险不可接受；封装成熟库（§5.3.2） |
| WebAuthn challenge 独立存储（不复用 `MfaChallengeStore`） | §三 场景化 payload 已提供载体（TTL/失败计数/一次性复用）；独立存储重复三实现 |
| 多因子并存（`mfaType` 多值/因子集合） | setting 单值状态机（pending/enabled/disabled）+ 登录 UI 复杂化；"多设备"真实需求由 WebAuthn 多 credential 覆盖；强度升级路径由 §四 策略约束 |
| WebAuthn attestation 完整信任链验证 | 企业设备认证场景（设备指纹/制造商链）；消费级部署标准做法为 `attestation=none`（直接信任 self-attestation）+ origin/rpId 强校验；完整链验证 deferred §七 |
| 允许移除最后一把 enabled WebAuthn credential | enabled 但零 credential = 用户自锁死；强制走 `unbindMfa` 整体解绑（有恢复码兜底） |
| email 码接受客户端指定收件邮箱 | 打开发码骚扰/枚举通道；收件目标一律服务端从用户档案解析（sms bindMfa 先例） |
| `IEmailSender` 增加模板概念（对齐 `SmsMessage.templateCode`） | `EmailMessage` subject/text 直排已够用；模板属发送方（TencentEmailSender 实现层）能力，API 层不引入新概念 |

### 5.5 与一期契约兼容性

- **两阶段登录 challenge 不变**：WebAuthn/email 是 `mfaType` 新取值，challenge 创建/消费/失败计数生命周期完全复用（payload 为可选增量）；一期 totp/sms 路径分支不动。
- **`ERR_AUTH_MFA_REQUIRED` 异常表达不变**：webauthn 类型 challenge 同样经该异常携带 challengeToken/mfaType/loginType；`MfaVerifyRequest` 增加可选 assertion 字段（向后兼容，老调用方零感知）。
- **`completeLogin` 分界裁决不变**：`mfaVerify` webauthn 分支成功出口仍是 `completeLogin`（按 challenge.loginType 签发，与一期一致）。
- **store 装配不变式保持**：`EmailCodeStore` 三实现复制 W8 装配模式（collect-beans 前缀 + `ioc:condition` + lessons 15 类加载安全——classpath 无 nosql 时 Redis 实现不加载）；`MfaChallengeStore` 装配零变更（场景化仅数据结构增量）。
- **明文边界不变**：WebAuthn publicKey 是公开密钥材料（本就不需加密，但对齐 secret 列的 `masked,not-pub` 标签策略，UI 不展示）；TOTP secret 加密语义不变；恢复码 BCrypt 不变；email/sms 码短 TTL 明文对齐一期 DB store 裁决（瞬态数据，BCrypt 收益不显著——一期 W8 裁决沿用）。
- **"未知值不扩散"兜底保留**：`verifySecondFactorAndComplete` else fail-closed 与 `verifyFactorForBind` 兜底 false 在扩展后保留（§5.3.0 #5/#6 标注）——未来白名单遗漏新值时安全侧失效。
- **一期零回归声明**：不绑定 webauthn/email 的用户全流程无感知；`mfa-type.dict.yaml` 为纯新增显示制品；ORM 列 comment 更新经 codegen 再生（列结构零变更）。

## 六、可信设备（记住此设备）

### 6.1 设计结论

1. **设备指纹 = 非隐私敏感输入的 SHA-256 哈希**：客户端 device-id（前端生成并持久化的 UUID——非秘密、仅命名器，经**自定义请求头 `X-Nop-Mfa-Device-Id` 显式传输**，不走 cookie——避免 cookie 解析与 localStorage 不自动携带问题，登记与豁免两侧读取同一契约）+ `User-Agent` + `Accept-Language` 三输入（header 读取大小写不敏感——live `extractClientIp` 双大小写先例）；**拒绝 canvas/硬件/行为指纹**（隐私合规）与 IP 绑定（移动网络 IP 飘移误伤）。
2. **新 ORM 实体 `NopAuthMfaTrustedDevice`**（W15-impl 落 `nop-auth/model/nop-auth.orm.xml`）：`sid` 主键 + `userId` + `deviceHash`（（userId, deviceHash）唯一约束）+ `deviceName` + `expireAt` + `lastUsedAt` + TENANT_ID 数据列与 `tagSet="...,no-tenant"`（对齐 `NopAuthMfaSetting`/`NopAuthMfaChallenge` 姊妹实体先例）+ `createdBy` 等通用审计字段。**过期行生命周期**：upsert 按（userId, deviceHash）**含过期行**匹配——同设备重新登记即复活刷新（`expireAt` 重算，审计字段按平台惯例更新）；`max-count` 仅计**未过期**行；`listTrustedDevices` 展示全部行（含过期标记）支持自助清理。
3. **豁免范围 = 密码类登录路径 only（loginType 1/2/3/5），固定窗口不滚动续期**：命中可信设备则跳过登录级第二因子 challenge（`checkMfaRequired` 放行）；操作级 MFA（§三）**永不豁免**（可信设备证明"这台设备最近通过完整 MFA"，不等于"当前操作者仍是本人"）。TTL `nop.auth.mfa.trusted-device.ttl-days`（缺省 30），自登记日起算**固定窗口**（命中只更新 `lastUsedAt` 审计，不续 `expireAt`——滚动续期让"记住"变永久）。**信道类路径（SSO 4 / 信道 20-23 / OAuth）豁免不适用且登记不开放**：`createSessionForUserAsync` 无 HTTP headers 上下文（合成 LoginRequest + 空 headers），豁免判定不可达；信道 challenge 的 `mfaVerify` 成功**不登记**（登记的指纹来自手机端但密码路径才可豁免，登记即误导）——信道登录用户始终完整验证第二因子（设计边界，非 deferred；扩 `ISessionBootstrap` 传 headers 属跨模块公共 API 变更，收益不抵成本）。
4. **登记入口 = `mfaVerify` 请求可选 `rememberDevice` 参数**（仅密码类 challenge.loginType 生效；服务端从请求头计算指纹登记——`mfaVerify` 是公开端点且有完整 headers，同一期先例）；**恢复码登录成功不登记**（恢复通道不应产生长期豁免）。管理入口 = `NopAuthUserBizModel` 扩展 `listTrustedDevices`/`removeTrustedDevice`（W6 并入先例）。`rememberDevice`（`MfaVerifyRequest` 可选字段）与 `trustedDeviceRegistered`（`LoginResult` 可选字段，仅密码类路径返回）均为 `nop-biz-auth-api` 跨模块公共 API 增量（Protected Area，W15-impl 需 plan-first + migration note，对齐 §4.5 同款声明手续）。
5. **撤销条件矩阵**：到期（自然失效）/ 用户主动移除 / `unbindMfa` 或换绑（因子变更 = 信任前提失效，全量撤销该用户；换绑判定点 = `confirmMfa` 成功）/ `resetUserMfa` / 角色策略禁豁免（该用户任一策略行 `allowTrustedDevice=false` 时豁免分支跳过，已登记行保留待策略放宽）。
6. **数量上限**：`nop.auth.mfa.trusted-device.max-count`（缺省 5，仅计未过期行），满员时**新增**登记失败返回提示（`trustedDeviceRegistered=false`，不阻断登录——豁免是优化不是功能）；**已存在同 hash（含过期行）的重新登记无条件放行**（覆盖刷新，不受 max-count 限制）；**拒绝静默 LRU 淘汰**（用户无感知失去豁免，显式管理优于隐式驱逐）。
7. **豁免判定位置 = `checkMfaRequired` 内**（一期"因子等同"分支之后、challenge 创建之前增量插入；**仅 `loginAsync` 路径传入真实 headers**，`createSessionForUserAsync` 路径无 headers 即不豁免）；无策略且无可信设备记录时零行为变化。**前向注记**：本节使 §4.3 "一期方法签名不变"注记自 W15 起失效（`checkMfaRequired` 增加 headers 参数，protected 单模块内变更）。

### 6.2 背景与动机

一期 Vision 设计收敛路径 E（可信设备）；每次登录都验证第二因子对高频用户是持续摩擦（TOTP 需掏手机、SMS 有成本与延迟），业界标配"记住此设备 30 天"（GitHub/GitLab/Google）。设计约束：豁免不得破坏一期两阶段语义（未启用 MFA 用户本就无 challenge，豁免只作用于"已启用用户的登录级验证"）。

### 6.3 核心设计

**指纹算法**：

```pseudocode
fingerprint(requestHeaders):
    deviceId = header("X-Nop-Mfa-Device-Id", 大小写不敏感)   # 前端生成（UUID）并持久化，请求时显式携带
    if deviceId 为空: return null                             # 无 device-id 不豁免（降级为正常 MFA，非错误）
    input = deviceId + "|" + header("User-Agent") + "|" + header("Accept-Language")
    return sha256Hex(input)
```

前端配合（业务层，同 W6 provisioning URI 二维码先例）：首次 mfaVerify 前生成并持久化 device-id，勾选"记住此设备"时随请求提交（自定义 header）。

**豁免判定（`checkMfaRequired` 内增量插入；仅 `loginAsync` 密码类路径传入真实 headers，`createSessionForUserAsync` 信道路径无 headers → 跳过豁免）**：

```pseudocode
checkMfaRequired(user, loginType, requestHeaders):    # headers 为增量参数（信道路径传 null）
    ... 一期分支 1/1b + §四 策略评估 ...
    ... 一期 setting 检查 + 因子等同（原样）...
    # 可信设备豁免（新增，位于因子等同之后、challenge 创建之前）：
    if requestHeaders != null and !policy.disallowTrustedDevice:   # 任一策略行 false 即禁（AND 合并，
                                                                    # evaluator 产出 {maxLevel, allowTrustedDevice} 复合结果）
        deviceHash = fingerprint(requestHeaders)
        if deviceHash != null:                                     # 短路：无 device-id 不查库
            trusted = trustedDeviceDao.findByUserIdAndHash(user.userId, deviceHash)
            if trusted != null and trusted.expireAt > now:
                trusted.lastUsedAt = now                          # 审计更新，不续 expireAt（固定窗口）
                return null                                       # 登录级豁免放行
    return createChallenge(...)                                   # 一期 challenge 创建（原样）
```

**`allowTrustedDevice` 多角色合并规则（与 §四 minLevel 合并并列）**：`minLevel` 取多角色 **max**（最严格胜）；`allowTrustedDevice` 取 **AND**（任一策略行 `false` 即禁豁免——fail-safe，高危角色一票否决）。§4.3 的 `roleMfaPolicyEvaluator` 产出复合结果 `{maxLevel, allowTrustedDevice}`（W13 落地 minLevel 时同点扩展该布尔，字段读取在 W15 消费）。

**登记（`mfaVerify` 成功路径增量；仅密码类 challenge.loginType（1/2/3/5）生效）**：

```pseudocode
mfaVerify 成功（TOTP/SMS/EMAIL/WebAuthn 分支，不含恢复码分支，不含信道类 loginType）:
    if request.rememberDevice == true:
        deviceHash = fingerprint(requestHeaders)
        if deviceHash == null: 响应 trustedDeviceRegistered=false（原因：无 device-id）
        else if 存在同 hash 行（含过期）: upsert 覆盖刷新（expireAt = now + ttl-days，不受 max-count 限制）
        else if 未过期行数 < max-count: insert 新行（deviceName = UA 摘要缺省）
        else: 响应 trustedDeviceRegistered=false（原因：满员）
    → completeLogin（一期出口不变；登记失败不阻断登录）
```

**撤销矩阵**：

| 触发 | 动作 |
|---|---|
| `expireAt` 自然到期 | 惰性失效（判定时过期即不豁免；行保留供审计，清理为 Follow-up） |
| `removeTrustedDevice(sid)`（用户自助） | 物理删除行 |
| `unbindMfa` 成功 / 换绑（pending 覆盖写不算，confirmMfa 成功才算因子变更） | 删除该用户全部可信设备（信任前提 = 特定因子持有，因子变更即失效） |
| `resetUserMfa`（管理员重置） | 同上全量删除 |
| 策略 `allowTrustedDevice=false` 命中 | 不删除行，豁免判定跳过（策略放宽后恢复生效） |

**威胁模型（安全边界显式声明）**：可信设备豁免防的是"**异地攻击者使用盗取的密码**"（无受害者设备指纹即无法豁免，退回完整 MFA）；**不防**"本机恶意软件/同设备攻击者"（UA/Accept-Language 可伪造、device-id cookie 可被同机读取）。高敏角色应以策略 `allowTrustedDevice=false` 关闭豁免（§4.3 建议矩阵）。

### 6.4 拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| canvas/硬件/行为指纹 | 隐私合规风险（GDPR/个保法敏感度）；请求头哈希已达成"区分浏览器实例"的目标 |
| IP 进入指纹输入 | 移动网络/旅行 IP 飘移导致豁免高频失效，误伤远大于安全收益 |
| 滚动续期（每次命中刷新 TTL） | "记住 30 天"退化为"永久记住"；固定窗口保证周期性完整 MFA 重新验证 |
| 操作级 MFA 豁免 | 可信设备 ≠ 当前操作者仍是本人（会话劫持/共用设备场景）；操作级保护的对象是高危动作本身 |
| 静默 LRU 淘汰满员设备 | 用户无感知失去已登记设备的豁免；显式管理（提示清理）优于隐式驱逐 |
| 恢复码登录登记可信设备 | 恢复码是应急通道（使用后强制重绑）；应急通道不应产生 30 天长期豁免 |
| 服务端 set-cookie 管理 device-id | device-id 需跨登录方式/跨会话持久且非秘密；前端自管理最简（服务端无 cookie 状态机） |
| 可信设备豁免跳过"未启用 MFA"检查 | 豁免只作用于"已启用用户的 challenge 创建"；未启用用户本就无 challenge，无豁免语义（不混淆两层） |

### 6.5 与一期契约兼容性

- **两阶段登录 challenge 不变**：豁免命中 = `checkMfaRequired` 返回 null（与一期"放行"同路径）；未命中/无记录/信道路径（无 headers）= 一期 challenge 流程原样；`checkMfaRequired` 增加 headers 参数是 protected 方法单模块内签名变更（W15-impl；信道路径传 null 不豁免——见 §6.1 结论 3）。
- **`ERR_AUTH_MFA_REQUIRED` 异常表达不变**：豁免不产生新异常/新错误码（满员/无 device-id 是登记提示非错误）。
- **`completeLogin` 分界裁决不变**：登记发生在 `mfaVerify` 成功路径、`completeLogin` 之前（纯 DB 写，不影响会话签发语义）。
- **store 装配不变**：可信设备为 ORM 实体（DB），无新 store 组件。
- **明文边界不变**：指纹哈希非秘密（不可逆哈希）；deviceName 仅展示。
- **跨模块公共 API 增量声明（Protected Area，W15-impl 需 plan-first + migration note）**：`MfaVerifyRequest.rememberDevice`（可选字段）与 `LoginResult.trustedDeviceRegistered`（可选字段，仅密码类路径返回）均在 `nop-biz-auth-api`——对齐 §4.5 同款声明手续。
- **一期零回归声明**：不勾选 rememberDevice / 无 device-id / 无记录的用户登录行为与一期逐字节一致；`mfaVerify` 可选参数向后兼容（老客户端零感知）。

## 七、跨主题 out-of-scope / deferred 裁定

以下延期项均带 classification、Why Not Blocking 与 Successor Required，无悬挂；各主题小节"拒绝了什么"表中的条目是**终局拒绝**（非 deferred），不在此重复。

### 7.1 WebAuthn attestation 完整信任链验证

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 消费级部署标准做法为 `attestation=none` + origin/rpId 强校验（本设计采用，§5.3.2）；完整信任链（设备认证/制造商链）仅企业设备治理场景需要，不影响 WebAuthn 因子的安全成立（断言验证与防重放不依赖 attestation 链）。
- Successor Required: no（企业需求出现时按扩展点另行设计）

### 7.2 外部 MFA 服务（Authy/Duo）接入

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 平台因子谱系（TOTP/SMS/EMAIL/WebAuthn）覆盖主流强度带；外部服务属体验优化而非能力缺口，且引入运行时外部依赖与合规面；接入路径已预留（`MfaFactorVerifier` 新实现 + 常量 + 白名单 + 场景化 challenge payload），无架构阻塞（§5.3.4）。
- Successor Required: no（三期真实需求出现时按扩展点另行设计）

### 7.3 信道路径的可信设备豁免

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `createSessionForUserAsync` 无 HTTP headers，豁免判定不可达；信道登录（扫码/SSO）用户在移动端完整验证第二因子，体验成本低于密码高频登录；扩 `ISessionBootstrap` 传 headers 属跨模块公共 API 变更，收益不抵成本（§6.1 结论 3 已裁为设计边界）。
- Successor Required: no（若三期信道豁免成为真实需求，走 `ISessionBootstrap` 扩展 plan-first）

### 7.4 challenge/验证码 DB store 的批量清理任务

- Classification: `optimization candidate`
- Why Not Blocking Closure: 一期 W8 已裁决惰性清理（peek/consume 过期即删），行级残留仅占存储不占安全；批量清理是运维优化，不阻塞任何二期功能（nop_auth_mfa_challenge/nop_auth_sms_code 及未来的 nop_auth_email_code 同口径）。
- Successor Required: no（可随任一 impl plan 顺带或独立运维任务）

### 7.5 前端交互设计（绑定页/操作级弹窗/受限会话引导页/设备管理页）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 前端属业务层（W6 provisioning URI 二维码先例：服务端返回数据契约，前端自行渲染）；本设计已钉定全部服务端契约（errorParams/可选字段/端点签名），前端可实现。
- Successor Required: no（各 impl 落地时前端自行处理）

### 7.6 `@MfaRequired` 标注分布的平台级治理工具

- Classification: `watch-only residual`
- Why Not Blocking Closure: 标注机制有构建期约束校验（subscription/publicAccess 组合拒绝）；跨模块标注分布的全面性由 A2-audit（操作级钩子全路径覆盖）审计把关，无需前置治理工具。
- Successor Required: no（A2-audit 覆盖；如审计发现标注面失控再立工具项）

### 7.7 signCount=0 WebAuthn 认证器的克隆检测增强

- Classification: `watch-only residual`
- Why Not Blocking Closure: 协议允许 count=0 认证器（部分平台认证器不维护计数）；本设计跳过单调校验 + 审计记录（§5.3.2），challenge 一次性仍保证会话级防重放；克隆检测增强（如 origin+时间窗启发式）属纵深防御增量。
- Successor Required: no（A2-audit 评估实际风险后再议）

## 八、设计 → impl 映射（W12-impl / W13-impl / W14-impl / W15-impl）

> 每个 impl 工作项 = 一个 execution plan（roadmap 约束：5-15 文件、200-500 行、1-4 phases；预估超限先行拆分裁定 plan-first）。**所有触碰跨模块公共 API / ORM 模型结构 / 框架核心的条目均为 Protected Area：plan-first + owner doc + migration note**（同 W6 `ScanLoginResult` 先例）。

### W12-impl（操作级 MFA）

- **消费小节**：§三 全部（3.1-3.5）+ §5.3.0 #5/#6（`MfaFactorVerifier` 抽取的收敛对象）。
- **交付物**：`@MfaRequired` 注解 + 构建期校验；executor 层拦截 + `IOperationMfaChecker`；`MfaChallengeStore` 场景化扩展（scene/payload/verifiedAt + create 重载 + markVerified 三实现原子性）；`MfaFactorVerifier` 抽取（登录级/绑定级收敛）；`mfaVerifyOperation` 端点；配置组 `nop.auth.operation-mfa.*`；错误码；平台首批敏感操作标注；审计四事件。
- **Protected Area 清单**：`nop-biz-auth-api`（注解 + checker 接口）；`nop-biz-auth-core`（`MfaChallenge` 字段 + `MfaChallengeStore` 两方法 + 三实现同步）；`nop-graphql-core` 框架核心（`ReflectionBizModelBuilder`/`GraphQLFieldDefinition`/`GraphQLObjectDefinition` 合并搬运/`GraphQLEngine` 注入/`IGraphQLExecutionContext` 透出——plan-first，需理解元数据传播链路）；`nop_auth_mfa_challenge` 加列 DDL 迁移（ORM 源 → codegen，禁手编生成物）；Redis 滚动升级兼容 note（老进程读新 JSON 字段）。

### W13-impl（角色级强制策略引擎）

- **消费小节**：§四 全部（4.1-4.5）+ §三 拦截器受限分支（W12 预留接缝）。
- **交付物**：`NopAuthRoleMfaPolicy` 实体 + `saveMfaPolicy`/`removeMfaPolicy`；`roleMfaPolicyEvaluator`（含 `factorLevel` 强度表全量落地 + 复合结果产出）；`checkMfaRequired` 第三态 + 受限签发；`mfaRestricted` 持久化（先设后存 + 两处白名单）；受限会话白名单拦截分支；登记通道 proof（场景化 challenge + SmsCodeStore）；`confirmMfa` 策略校验；OAuth 入口接入；错误码 ×4。
- **Protected Area 清单**：`nop-auth/model/nop-auth.orm.xml`（新实体——ORM 模型结构 plan-first）；`IUserContext.mfaRestricted` + `UserContextImpl.serializeToJson` + `DaoUserContextCache` 序列化白名单（跨模块公共 API + migration note，**Dao-cache 路径测试必须覆盖**否则 fail-open）；`LoginResult`/`ScanLoginResult` 可选字段；`nop-auth-sso` `OAuthLoginServiceImpl`（跨模块 plan-first）。

### W14-impl（WebAuthn/FIDO2 + MfaType 扩展）

- **消费小节**：§5.1 结论 1/2 + §5.3.0 全表（变更标注）+ §5.3.1 + §5.3.2 + §5.3.5。
- **交付物**：`MFA_TYPE_WEBAUTHN` 常量 + factorLevel 表核对；白名单 #1-#10 逐处变更（含 `sendMfaCode` 按 mfaType 分派重构 + `ERR_AUTH_MFA_CODE_UNSUPPORTED`）；`NopAuthMfaCredential` 实体；WebAuthn 库 POC 选型 + `WebAuthnAuthenticator`（nop-auth-service）；注册/认证/解绑三 ceremony + `webauthnAuthOptions`/`webauthnBeginVerify`/`confirmWebauthnRegistration` 端点；`unbindMfa` assertion 扩展；credential 管理 API；`mfa-type.dict.yaml`；`nop.auth.mfa.webauthn.*` 配置组。
- **Protected Area 清单**：`nop-auth/model/nop-auth.orm.xml`（新实体）；`MfaVerifyRequest` assertion 字段（`nop-biz-auth-api`）；第三方依赖引入（依赖面 POC 定稿）；`checkMfaRequired` webauthn payload 增量（webauthn 类型 challenge 含 cryptoChallenge）。

### W15-impl（邮件验证码 + 可信设备）

- **消费小节**：§5.1 结论 3 + §5.3.3 + §5.3.5 + §六 全部（6.1-6.5）。
- **交付物**：`MFA_TYPE_EMAIL` 常量 + factorLevel 表核对；白名单 email 侧变更；`EmailCodeStore` 接口 + 三实现 + `nop_auth_email_code` 表 + collect-beans 装配（`nopEmailCodeStore_` 前缀 + 条件激活）；`IEmailSender` 接线 + 文案模板配置；`nop.auth.email-code.*` 配置组；`NopAuthMfaTrustedDevice` 实体；指纹计算 + 豁免判定 + 登记路径 + 撤销矩阵；`listTrustedDevices`/`removeTrustedDevice`；`checkMfaRequired` headers 参数；`nop.auth.mfa.trusted-device.*` 配置组。
- **Protected Area 清单**：`nop-auth/model/nop-auth.orm.xml`（新实体 ×2 + 新表）；`EmailCodeStore` 接口（`nop-biz-auth-core` 跨模块公共 API）；`MfaVerifyRequest.rememberDevice` + `LoginResult.trustedDeviceRegistered`（`nop-biz-auth-api`）；邮件码限流对齐先例验证。

## 九、与已有设计的关系

- **上游复用**：一期 MFA 全部基线（`01-architecture-baseline.md`：两阶段 challenge/`ERR_AUTH_MFA_REQUIRED`/`completeLogin` 分界/store 三实现与装配/绑定状态机/配置与错误码体系）；`nop-nosql`（Redis 原语——SETNX/putEx/INCR，扩展点原子性沿用）；`IEmailSender`（`nop-integration-api`，零变更）；`@Auth`/`@BizMakerChecker` 注解传播先例（executor 层拦截的机制模板）；`NopAuthOpLog` 审计机制；ai-dev/lessons/15（类加载安全不变式，EmailCodeStore 装配沿用）。
- **同层协作**：`ai-dev/design/nop-credential/02-phase2-design.md`（姊妹二期设计——操作级 MFA 的敏感操作建议清单含凭证库操作，W12-impl 时与其 owner 对齐；单文件四主题 + 分节独立 review 的组织方式同构）。
- **下游影响**：W12-impl ~ W15-impl 四个 impl plan 直接消费本设计（§八映射）；`docs-for-ai/03-modules/nop-auth.md` 与 `docs-for-ai/02-core-guides/auth-and-permissions.md` 的二期章节由各 impl 落地后补充（本 plan Non-Blocking Follow-ups 已登记）；A2-audit 以本设计的兼容性矩阵（§二）与各主题 x.5 为回归基准。
- **参照**：GitHub sudo mode（操作级重验证——其时间窗方案被 §3.4 拒绝，改为一次性票）；WebAuthn Level 2 规范（ceremony 模型）；一期 vision §四 设计收敛路径（本设计即该路径的展开）。
