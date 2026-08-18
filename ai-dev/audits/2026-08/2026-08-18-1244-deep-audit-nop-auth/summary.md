# A2-audit 汇总：MFA 二期安全审计（W12/W13/W14/W15 对抗审计 + finding 裁决收口）

> Audit Status: closed
> Plan: `ai-dev/plans/2026-08-18-0904-1-mfa-phase2-security-audit-a2.md`
> Charter: `audit-charter.md`（2026-08-18 12:44 live 锚点核对）
> Date: 2026-08-18
> 执行纪律：七维度均由 fresh 独立子 agent 探查（探查者不改产品代码）；P1 修复由编排 session 执行，修复后由另一 fresh 子 agent 独立复核（`fix-verification.md`）。

## 一、执行证据（子 agent task/session）

| 维度 | 报告 | 执行者（task） |
|---|---|---|
| D1 因子强度与绑定/登记生命周期 | `D1-factor-binding-lifecycle.md` | ses_feccead55ffeRPNuylg0ONScoI |
| D2 防重放与一次性消费 | `D2-anti-replay-one-time-consumption.md` | ses_fecce8437ffe655OYcAFkUiZh6 |
| D3 恢复通道 | `D3-recovery-channel.md` | ses_fecce66d2ffesuUZtQ7gEbAAWX |
| D4 角色级策略一致性与受限会话 | `D4-role-policy-restricted-session.md` | ses_fecc6bca7ffeBpstkP5UDuaOHv |
| D5 操作级 MFA 全路径覆盖 | `D5-operation-mfa-coverage.md` | ses_fecc68bcbffesvugIPebQCtVJ |
| D6 可信设备 | `D6-trusted-device.md` | ses_fecbf2987ffePqJpmpQjtPRrUP |
| D7 登录级链路一期零回归 | `D7-phase1-zero-regression.md` | ses_fecbefb3affe1YkimpQ1X5C7u3 |
| P1 修复独立复核 | `fix-verification.md` | ses_feca0ee11ffeoWYwYJ8WuqTXUL |

## 二、findings 总表

严重度定义：P0=可利用的认证破坏/秘密泄漏；P1=confirmed live defect（安全影响）；P2=纵深防御弱化；P3=加固/文档。裁决三态详见 `adjudication.md`。

| ID | 维度 | 严重度 | 锚点 | 一行描述 | 裁决 |
|---|---|---|---|---|---|
| D2-F1 | D2 | **P1** | RedisSmsCodeStore.java:90 / RedisEmailCodeStore.java:89 | VALID 分支忽略 removeIfMatch 返回值——并发双 verify 同码双双 VALID（Lua CAS 删除原子但裁决不原子） | **fixed**（+`TestRedisCodeStoreCasRace`） |
| D3-F1 | D3 | **P1** | NopAuthUserBizModel.java:941/:677 + LoginServiceImpl.java:665 | resetUserMfa/unbindMfa/恢复码使用三边界不删 webauthn credential 行——重绑后旧（被窃）硬件钥匙复活 | **fixed**（三边界 bulk 物理 DELETE + `testUnbindAndAdminResetDeleteCredentialRows`） |
| D1-1 | D1 | P2 | NopAuthUserBizModel confirm/unbind TOTP 分支 | confirmMfa/unbindMfa 的 TOTP 分支无尝试上限/失败计数（SMS/EMAIL/webauthn/登录级均有 cap） | successor-A |
| D2-F2 | D2 | P2 | LoginServiceImpl.java:548-582 | mfaVerifyAsync 不校验 scene==login——operation 场景 token 可在公开登录端点完成会话（设计 §3.5 一期零改动裁定残留，`TestOperationMfaE2E:349-377` 钉定） | deferred（设计再裁定 successor-B 输入） |
| D3-F2 | D3 | P2 | LoginServiceImpl.java:686-704 | 恢复码 used 置位为无条件 read-modify-write，并发双 verify 同码双过 | successor-A |
| D3-F3 | D3 | P2 | NopAuthMfaRecoveryCodeInputBean.java:43-68 | 恢复码表通用 CRUD（codeHash/used 可写）可植入/复活码 | successor-A（治理族） |
| D5-F1 | D5 | P2 | 4 MFA 敏感表裸 CrudBizModel | Setting/Credential/TrustedDevice/RoleMfaPolicy 通用 mutation 通道无 @MfaRequired——`NopAuthRoleMfaPolicy__save` 绕过 requireAdmin+校验+审计 | successor-A（治理族） |
| D6-1 | D6 | P2 | NopAuthMfaTrustedDeviceBizModel + xmeta | 通用 CRUD 可伪造 (userId,deviceHash) 豁免行，绕过登记不变式/上限/审计（需 mutation 权限，默认未授） | successor-A（治理族） |
| D1-2 | D1→D2 | P2 | （同 D2-F2，路由合并） | — | 并入 D2-F2 |
| D1-3 | D1 | P3 | LoginApiBizModel.verifyChannelProof | 失败路径无审计事件（W13 声明的 channel-proof-fail 缺失） | successor-A（低优先） |
| D1-4 | D1 | P3 | removeWebauthnCredential:840 | 软删 credential 的 credentialId 占唯一键，复注册撞 DB 约束 | **fixed**（V-F1：改 bulk 物理 DELETE） |
| D1-5 | D1 | P3 | confirmWebauthnRegistration | 无策略防降级校验（当前 webauthn=3 恒最强，结构性安全；level-4 因子引入时成缺口） | watch-only |
| D1-6 | D1 | P3 | maskPhone | ≤4 位号码原样返回 | deferred |
| D1-7 | D1 | P3 | setting.phone 通用 OutputBean | 通用 CRUD 面未脱敏（permission 门禁 + 管理面同级） | successor-A（治理族） |
| D2-F3 | D2 | P3 | RedisMfaChallengeStore:112/:168 | 操作级票过期后 challenge 在 Redis 实现可再验证（Local/Db 删除），三实现契约分歧（test-pinned） | deferred |
| D2-F4 | D2 | P3 | auth-service.beans.xml:58/:79 | Redis SMS/Email store beans 未接配置——运营调参静默失效 | deferred |
| D2-F5 | D2 | P3 watch | channel-proof ticket | 不绑会话（设计 §4.6 裁定 3 既有；一次性 + userId 绑定成立） | watch-only |
| D3-F4 | D3 | P3 | LoginServiceImpl.java:655 | USED 不计数 vs INVALID 计数——challenge 存活度 oracle | deferred |
| D3-F5 | D3 | P3 | 恢复码 10 位数字 | ≈33.2 bits，安全依赖 max-attempts=5+密码门禁 | deferred |
| D3-F6 | D3 | P3 | EXPIRE_AT 列 | 死列（永不写/永不查） | deferred |
| D3-F7 | D3 | watch | 登录级复核缺 challenge.mfaType 比对（可利用性≈0） | watch-only（转 D2 记录） |
| D4-1 | D4 | P3 | 受限会话 unbind 码 | 已启用 sms/email 弱因子用户受限会话内 unbind 码无生产端点可送达（升级路径断链，fail-safe） | deferred |
| D4-2 | D4 | P3 | OAuth 受限上下文 userId=userName | 本地 userName≠userId 时引导流 fail-closed | deferred |
| D4-3 | D4 | P3 watch | 受限拦截不受 mfa.enabled 总闸门控 | 快照语义可辩护（存量受限会话在全局关 MFA 后仍被拒直至重登录） | watch-only |
| D4-4 | D4 | P3 | 两副本 evaluator null 防御不对称 | B 侧 :1020 有 NONE 回退、A 侧 :88 必注入——路由项 2 唯一未登记差异 | watch-only（登记进设计 watch 列表） |
| D4-5 | D4 | P3 watch | executor 受限分支 fragment continue | 当前 fail-closed；未来 auth 层 fragment-tolerant 即成静默绕过 | watch-only |
| D4-6 | D4 | P3 info | saveMfaPolicy 不校验 roleId 存在 | 孤儿策略行（无 FK 属设计选择） | deferred |
| D5-F2 | D5 | P3 | GraphQLExecutor.java:189 + 测试 javadoc | 批量语义 javadoc 漂移（写"逐 field error"，实为整批预执行中止） | **fixed**（注释更正） |
| D5-F3 | D5 | P3 | @MfaRequired 误标于 @BizAction/@BizLoader | 被静默忽略——建议构建期 fail-fast 扩展 | deferred |
| D5-F4 | D5 | P3 | 票头读取大小写 | 仅匹配原样/全小写/全大写（fail-closed 方向） | deferred |
| D5-F5 | D5 | P3 watch | 批量中止烧票 | 同批已消费票不退（fail-closed UX） | watch-only |
| D6-2 | D6 | P3 watch | isExempted 读-改-写无版本列 | 并发复活刷新可被陈旧写覆盖（仅缩短方向 fail-safe） | watch-only |
| D6-3 | D6 | P3 watch | 满员判定与 insert TOCTOU | 异 hash 并发可超限 +1（同 hash 已正确归一） | watch-only |
| D7-F1 | D7 | P3 | sendMfaCode totp | 一期隐式发 SMS → 显式 `ERR_AUTH_MFA_CODE_UNSUPPORTED`（设计 §5.3.0 #8 已裁定，无一期断言钉定旧行为） | 设计内偏差（记录） |
| D7-F2 | D7 | P3 | 3 个一期 E2E 测试 tenant 开关移除 | 测试基建（commit 验证全绿） | 记录 |
| D7-F3 | D7 | P3 | FakeNosqlService.putIfAbsentExAsync | 为 W12 Redis 票键补实现（测试基建） | 记录 |
| D7-F4 | D7 | P3 info | checkMfaRequired headers 增参 | W15 设计 §6.1 预声明（protected in-module） | 记录 |
| V-F1 | 复核 | P3 | removeWebauthnCredential:840 | 软删占键 + 守卫不可见（= D1-4） | **fixed** |
| V-F2 | 复核 | P3 | NopAuthUserBizModel.java:708 注释 | deleteEntityDirectly 措辞 vs 实际 deleteByQuery | **fixed**（注释更正） |

**P0：0 条。P1：2 条（D2-F1/D3-F1），均已本 plan 内修复 + focused 测试 + 独立复核 PASS。**

## 三、一期契约回归锚点结论表（设计 §二矩阵六行 + 三个附加项）

| 锚点 | 归属维度 | 结论 | 证据 |
|---|---|---|---|
| 两阶段 challenge（行 1） | D2/D7 | **PASS** | 三创建触点经 MfaChallengeHelper 收敛（grep 无散点）；一期分支序保持（checkMfaRequired:1012-1054）；非 webauthn 走老五参 create 字节等价 |
| ERR_AUTH_MFA_REQUIRED 异常表达（行 2） | D4/D7 | **PASS** | code+3 errorParams 未变（NopAuthErrors:88-89）；操作级 ERR_AUTH_OPERATION_MFA_REQUIRED 独立码独立参数 |
| completeLogin 分界（行 3） | D4/D7 | **PASS** | 先设后存 + resetFailCount/notifyHook 差异保持；password=accessToken/channel=accessCode；getLoginResultAsync 兑换路径不变 |
| store 装配（行 4） | D2 | **PASS** | EmailCodeStore 三实现完整复刻 W8 模式（prefix + autowire-candidate=false + ioc:condition + collect-beans）；provider 零 nosql 引用（F4 配置传播缺口单列） |
| 明文边界（行 5） | D1 | **PASS** | secret AESTextCipher 加密、恢复码 BCrypt salt:hash、publicKey/credentialId not-pub 不展示、provisioning URI 一次性返回 |
| 一期零回归（行 6） | D7 | **PASS** | 未启用 MFA 用户 early-return:1033；无策略行 → maxLevel=0 → 第三态不可达；refresh/logout 零触碰；operation-mfa.enabled 缺省 false |
| 既有 MFA 套件断言零修改（附加 1） | D7 | **PASS** | 14 个一期 MFA 测试文件 git 核对：零断言删除/改写/弱化（8 个仅有 additive wiring/新增方法/签名适配/测试基建） |
| 双零介入（附加 2） | D4/D5 | **PASS** | checker :130-131 早退于一切副作用；无策略行=一期行为（三测试锚点 + SPI 缺席变体） |
| C1b 容器级元数据断言（附加 3） | D5 | **PASS** | 静态一致性：4 正例+3 负例与 live 标注逐一吻合、pom 无 nop-auth 依赖前提成立；live 重跑 nop-credential-service 全量 209 tests 0 failures（本 plan 收口验证） |
| OAuth 副本"不加豁免分支"专项（路由项 2 关联面） | D6 | **PASS** | MfaLoginPolicyServiceImpl.checkMfaForUserName:73-115 无豁免分支；专项断言 TestTrustedDeviceE2E:441-461 在位 |

## 四、路由项裁决摘要（详见 adjudication.md）

1. **webauthn 管理动作标注**：rename 不标注（终局裁定——纯展示元数据，C1b 缩窄先例适用）；remove 裁定"应标注"，落地归 successor-B（行为变更 + 容器断言同步，不在本审计 plan 内实施）。
2. **两副本同构不变式**：当前同步（8 共享逻辑块逐字等价 + 3 处登记在案裁定漂移 + 1 处新登记 D4-4 evaluator null 防御不对称）——watch-only 维持，D4-4 补入设计 watch 列表。
3. **联系方式修改通用 CRUD 敏感化**：并入 successor-A（MFA 敏感数据治理族），平台治理 successor 登记。

## 五、修复与验证记录

- **修复 1（D2-F1）**：`RedisSmsCodeStore`/`RedisEmailCodeStore` VALID 分支检查 `removeIfMatch` 返回值，CAS 败者返回 EXPIRED（与 Db 实现 affected==0 → EXPIRED 语义对齐）。新增探查测试 `TestRedisCodeStoreCasRace`（4 用例：sms/email 败者 + 双胜者正常路径）。
- **修复 2（D3-F1）**：`NopAuthUserBizModel.unbindMfa`/`resetUserMfa` + `LoginServiceImpl.verifyRecoveryCodeAndComplete` 三边界 bulk 物理 DELETE credential 行（`deleteByQuery`，避免会话缓存实体乐观锁冲突）。配套：`testUnbindAndAdminResetDeleteCredentialRows` 新增；`testDuplicateRegistrationRejected`/`testDualCredentialLifecycle`/`testDoubleCeremonyUnbindWithOperationMfaEnabled` 重排（不再依赖"解绑保留行"旧语义，断言面保持——独立复核确认无安全弱化）。
- **修复 3（V-F1/D1-4）**：`removeWebauthnCredential` 改 bulk 物理 DELETE（credentialId 唯一键释放 + 守卫/约束判定一致）。
- **修复 4（D5-F2/V-F2）**：GraphQLExecutor/测试 javadoc 批量语义更正。
- **能力变更声明**：多钥匙累积原依赖"解绑保留行 + 重绑累积"，修复后不可达（安全语义优先）；多设备 UX 恢复路径 = successor-B 的 add-key-while-enabled 端点候选。独立复核确认无生产消费方依赖 N≥2、LAST_CREDENTIAL 守卫仍可达。
- **验证**：nop-auth-service+sso `-am` 全量 **360 tests 0 failures**（基线 355 + 新增 5 审计测试）；nop-credential-service `-am` 全量 **209 tests 0 failures**；独立复核 `fix-verification.md` **FIX VERIFICATION: PASS**。
