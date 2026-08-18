# P1 修复独立验证报告（D2-F1 + D3-F1）

> Executor: fresh independent verification auditor（未参与发现、未参与修复；research-only + 只读测试运行）
> Date: 2026-08-18
> Verified fixes: D2-F1（Redis 码 store CAS 双花）、D3-F1（因子失效边界 webauthn credential 行物理删除）
> Source findings: `D2-anti-replay-one-time-consumption.md` §一 D2-F1；`D3-recovery-channel.md` §一 D3-F1
> Evidence base: `git diff HEAD`（未提交变更）+ live 代码阅读 + 焦点测试运行（39 tests green）+ 运行时 SQL 日志核对

---

## Task 1 — 因子失效边界完备性 — **PASS**

**三处修复点逐一核实（均为物理 DELETE，见 Task 5）**：

| 边界 | 证据 | verdict |
|---|---|---|
| `unbindMfa` webauthn 分支 | `NopAuthUserBizModel.java:694-712`：if/else 仅包裹"验证当前因子"（webauthn ceremony vs 一期因子码），尾部 `setStatus(DISABLED)` → `deleteRecoveryCodes` → **`deleteWebauthnCredentials(userId)`**（:709）→ `revokeTrustedDevices` 为两分支共享 | ✓ |
| `unbindMfa` 非 webauthn 分支 | 同上（共享尾部代码，无分叉） | ✓ |
| `resetUserMfa` | `NopAuthUserBizModel.java:960-971`：status=disabled + secret/mfaType/phone 清空 → `deleteRecoveryCodes` → **`deleteWebauthnCredentials(userId)`**（:968-970） | ✓ |
| `verifyRecoveryCodeAndComplete`（恢复码路径） | `LoginServiceImpl.java:667-673`：consume challenge + status=disabled + `updateEntityDirectly` → **`deleteWebauthnCredentials(challenge.getUserId())`** | ✓ |

**其他可能置 status=disabled / 清 mfaType 的路径全量枚举**（grep `MFA_STATUS_DISABLED|setStatus(MFA|setMfaType(null)`，nop-auth main 代码全量）：

| 路径 | 性质 | 是否漏边界 |
|---|---|---|
| `NopAuthUserBizModel:513/517/582/590/650`（confirmWebauthnRegistration/upsertPending/confirmMfa） | 置 **enabled/pending**（启用方向），非失效 | 否 |
| `NopAuthUserBizModel:783`（getMfaStatus） | 只读 DTO，不落库 | 否 |
| `bindMfa:276-279` ALREADY_ENABLED 拒绝 | 保证 upsertPending 仅在 status != enabled 时可达（换绑必经 disabled 态），故 pending 覆盖写不构成 enabled 因子失效 | 否 |
| `confirmMfa`/`mfaVerifyAsync` 失败路径 | 不修改 setting（失败计数在 challenge/store 侧） | 否 |
| `LoginServiceImpl:566-572`（登录级复核） | 仅 consume challenge，不改 setting | 否 |
| 生成 CRUD BizModel（`NopAuthMfaSettingBizModel`/`NopAuthMfaCredentialBizModel`/`NopAuthMfaRecoveryCodeBizModel`，均裸继承 CrudBizModel） | 管理员持 mutation 权限可直接 UPDATE setting.status / credential 行，**不经 deleteWebauthnCredentials** | **非本修复回归**：即 D3-F3（P2，独立 finding，已单列跟踪）；pre-existing |
| 直接 DB 写 | 运维域 | 豁免 |

结论：审计威胁模型（用户可达 + 管理员重置语义）内**无遗漏边界**；CRUD 通道为 D3-F3 既有独立缺口，不属本修复验收面。

---

## Task 2 — CAS 修复语义与调用方 — **PASS**

**修复本体**：`RedisSmsCodeStore.java:90-98` / `RedisEmailCodeStore.java:89-97` —— `if (!nosql.removeIfMatch(...)) return CodeVerifyResult.EXPIRED;`（CAS 败者 fail-closed）。与建议修复方向逐字一致。

**跨实现一致性**（三实现"败者语义"矩阵）：

| 实现 | 败者判定 | 结果 |
|---|---|---|
| DbSmsCodeStore / DbEmailCodeStore | `conditionalDelete` affected==0 → EXPIRED（`DbSmsCodeStore.java:104-106`） | EXPIRED |
| LocalSms/EmailCodeStore | `ConcurrentHashMap.compute` 原子消费（`LocalSmsCodeStore.java:69-88`），无败者 | — |
| Redis Sms/Email（修复后） | `removeIfMatch`==false → EXPIRED | EXPIRED |

三实现语义收敛：**VALID ⟺ 原子消费胜出**。

**verify() 全部调用方 EXPIRED 处理核对**（nop-auth main 代码穷举，无遗漏调用方）：

| 调用方 | 锚点 | EXPIRED 处理 |
|---|---|---|
| LoginServiceImpl SMS 一因子登录 | `:332-338` | → `ERR_AUTH_SMS_CODE_EXPIRED`，登录失败 ✓ |
| MfaFactorVerifier sms 分支 | `:164-168` | throw `ERR_AUTH_SMS_CODE_EXPIRED`；仅 `r == VALID` 返回 true ✓ |
| MfaFactorVerifier email 分支 | `:177-182` | throw `ERR_AUTH_EMAIL_CODE_EXPIRED`；仅 `r == VALID` 返回 true ✓ |
| LoginApiBizModel.verifyChannelProof email | `:413-418` | EXPIRED → throw；`r != VALID` → throw MFA_FAIL ✓ |
| LoginApiBizModel.verifyChannelProof sms | `:431-436` | 同上 ✓ |

**无任何调用方将 EXPIRED 当成功**。败者错误面（EXPIRED 专属错误码、不进失败计数）与 Db 实现败者完全同构——跨实现行为一致。

---

## Task 3 — 回归测试审查 — **PASS（无安全相关削弱；1 处证据形态重构，语义等价）**

| 测试 | 原断言面 | 重构后 | 削弱判定 |
|---|---|---|---|
| `TestWebAuthnMfaE2E.testDuplicateRegistrationRejected`（:508-531） | ERR_AUTH_MFA_FAIL + "already registered" + 同 credentialId 单行 | 断言面逐条保留；前置行改为 `saveCredentialDirectly` 直接种子（不再依赖"解绑保留行"） | **未削弱**——守卫代码路径相同（`confirmWebauthnRegistration:504-512` step 4 全局唯一检查，与行来源无关）；被移除的"解绑后行保留"断言正是修复目标行为 |
| `TestWebAuthnMfaAdvancedE2E.testDualCredentialLifecycle`（:402-446） | allowCredentials 仅含 enabled（size=1 + contains A）+ A 可登录 + 移除 disabled B 允许 + 移除最后 enabled A → LAST_CREDENTIAL | 全部断言保留；第二把 keyB 改直接落库 | **未削弱**——纯 plumbing（多钥匙状态构造方式），enabled 过滤与 last-credential 守卫断言原样 |
| `TestWebAuthnMfaAdvancedE2E.testDoubleCeremonyUnbindWithOperationMfaEnabled`（:492-544） | 两 ceremony 后读回 signCount=7（5→6→7） | ceremony 1 后读回 6L（显式断言）+ ceremony 2 用 count=7 断言 **unbind 成功**（条件 UPDATE `WHERE SIGN_COUNT < 7` 通过即传递性证明 DB 值=6、严格单调）+ 新增行已删除 assertNull | **证据形态重构，安全语义等价**：第二写的单调性由"接受即证明"（conditional UPDATE 语义）替代读回（行已删除不可读）；回退/重放仍会被同一条件 UPDATE 拒绝——防线在产品代码，不在断言 |
| `testUnbindAndAdminResetDeleteCredentialRows`（新增，:548-588） | — | (a) unbind → 行删除；(b) **同钥匙复注册成功**（唯一键随物理删除释放——直接证伪"逻辑删除撞唯一约束"）；(c) admin reset → 行删除 + setting disabled | 新增强覆盖 ✓ |

新探针 `TestRedisCodeStoreCasRace`（4 用例）：`CasLoserNosql` 恒 false 且不动存储（键仍在——败者 EXPIRED 裁决可归因于 CAS 结果而非键缺失，排除了假阳性路径）+ winner 正常 VALID/复验 EXPIRED。测试设计正确。

---

## Task 4 — 能力变更评估（多钥匙累积移除） — **PASS**

**多钥匙累积的唯一生产路径正是被修复的漏洞**：`bindMfa:276-279` 对 enabled 状态抛 ALREADY_ENABLED → 追加第二把钥匙必经"unbind（旧行为：行保留）→ rebind"。修复后经 API 每用户至多 1 行 credential 行。不存在任何"保持 enabled 追加钥匙"的生产 API（无 addWebauthnCredential 端点）。

**`listCredentials`/`countEnabledCredentials` 消费方全量核对**（0/1/N 行均正确工作）：

| 消费方 | 锚点 | 评估 |
|---|---|---|
| `listWebauthnCredentials`（UI 列表） | `NopAuthUserBizModel.java:806` | 1 行正常展示 |
| `removeWebauthnCredential`（last-credential 守卫） | `:834-835` | 见下 |
| `webauthnBeginVerify`（allowCredentials） | `:564` | 任意 N 正常 |
| `bindWebauthn`（exclude 已注册） | `:421` | 任意 N 正常 |
| `defaultCredentialName`（"Key #N" 命名） | `:1036` | 化妆性问题，无依赖 |
| `findEnabledCredential`（验证路径） | `MfaFactorVerifier.java:256` | 按 credentialId+userId+enabled 匹配，任意 N 正常 |

**ERR_AUTH_MFA_LAST_CREDENTIAL 仍可达且语义成立**：单钥匙用户 `removeWebauthnCredential` → 守卫拒绝（enabled 且 count≤1）→ 引导走 unbindMfa ceremony（恢复码兜底）。测试双重覆盖：`TestWebAuthnMfaE2E:497-503`、`TestWebAuthnMfaAdvancedE2E:434-445`。1:N 模型保留为未来"追加第二把钥匙"特性的前向兼容（届时需配套 purge 语义）。

无生产代码路径依赖多钥匙累积。**能力收缩无破坏面**。

---

## Task 5 — 静默 no-op / 物理删除核实 — **PASS**

- **deleteByQuery 是物理 DELETE**：`OrmEntityDao.java:552-563` → `DaoQueryHelper.queryToDeleteSql:251-257` 构造 `DELETE FROM entity WHERE filter`——代码路径上无 delFlag→UPDATE 转换（逻辑删除转换仅存在于实体级 `deleteEntity` flush 路径）。**运行时实证**（`testUnbindAndAdminResetDeleteCredentialRows` 单测日志）：
  ```
  delete from NOP_AUTH_MFA_CREDENTIAL where USER_ID = 'wa-inval-user'   -- executeUpdate:count=1
  ```
  无 `DEL_FLAG` 谓词、无 UPDATE 形态；紧随的 re-register INSERT 成功（唯一键已释放）。
- **过滤字段名匹配**：`FilterBeans.eq("userId", userId)` → 实体属性 `userId`（ORM `nop-auth.orm.xml:1337` `name="userId"`，列 USER_ID）；运行时 SQL 绑定值正确。
- **无扩展拦截**：`NopAuthMfaCredential` 无 IEntityDaoExtension（supportDeleteQuery 分支不触发）；实体 `tagSet` 含 `no-tenant`，无租户过滤错配。
- 对 totp/sms/email 用户调用为合法 0 行 no-op（deleteByQuery 按 userId 条件，affected=0 无副作用）——恢复码路径对非 webauthn 用户无害（TestMfaLoginE2E 恢复码族全绿佐证）。

---

## Task 6 — 焦点测试运行 — **PASS**

```
./mvnw test -pl :nop-auth-service -Dtest='TestRedisCodeStoreCasRace,TestWebAuthnMfaE2E,TestWebAuthnMfaAdvancedE2E,TestMfaLoginE2E'
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```
（TestWebAuthnMfaE2E 12/12；含 4 个 CAS 对抗用例与全部 D3-F1 新断言。）

---

## 新发现（本次验证额外识别）

| ID | Severity | 锚点 | 描述 |
|---|---|---|---|
| V-F1 | **P3**（pre-existing，非本修复回归） | `NopAuthUserBizModel.removeWebauthnCredential:837`（`dao.deleteEntity` 逻辑删除） | removeWebauthnCredential 仍为**逻辑删除**——软删行继续占用 credentialId 全局唯一键，且 `findCredentialByCredentialId`（findAllByExample 带 DEL_FLAG=0 过滤）对软删行不可见 → 移除后复注册同把钥匙将绕过守卫、在 INSERT 处撞唯一约束抛未归一 DB 异常（fail 仍 fail-closed，属错误归一面而非安全洞）。与本修复声明的"物理删除释放唯一键"理由不一致。修复后多钥匙状态经 API 不可构造，该边沿几乎不可达——建议 successor 顺带改物理删除以收敛语义。测试 `TestWebAuthnMfaE2E:485-492` 钉定的"logical delete"注释随行为一并更新。 |
| V-F2 | P3（注释漂移） | `NopAuthUserBizModel.java:708`（unbindMfa 内联注释） | 注释写"故用 deleteEntityDirectly（物理删除…"，实际实现为 `deleteByQuery`（方法级 javadoc 正确）。二者均为物理删除，无行为影响；建议措辞对齐防误读。 |
| V-F3 | 观察（D3-F3 重申） | `NopAuthMfaSettingBizModel` 等三个裸 CrudBizModel | 管理员 CRUD 通道可绕过 deleteWebauthnCredentials 直接改 setting.status——Task 1 枚举中确认存在，归属 D3-F3（P2）独立跟踪，非本修复验收缺口。 |
| V-F4 | 观察（工作树噪音） | `nop-format/nop-ooxml/.../generated-pie-chart/_rels/.rels` | 未提交 diff 混入一个与本修复无关的 ooxml 样例文件变更（疑构建产物），建议提交前剔除。 |

---

## 最终裁决

两项 P1 修复逻辑正确、边界完备、语义跨实现一致、测试覆盖等价无安全削弱、焦点测试全绿。新发现均为 P3/观察级，不阻塞。

**FIX VERIFICATION: PASS**
