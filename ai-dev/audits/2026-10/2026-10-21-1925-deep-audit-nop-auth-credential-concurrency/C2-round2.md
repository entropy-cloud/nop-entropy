# C2 锁与同步原语 — 深挖第 2 轮（WI 5.1 nop-auth-credential）

- 审计维度：C2（锁与同步原语）深挖追加轮
- 日期：2026-10-21
- 审计员：C2 深挖子代理（盲审纪律：未读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 其余内容；未 git log；仅读本目录第 1 轮三文件 + `ai-dev/skills/concurrency-resource-audit-prompt.md`）
- 编号口径：C2 续接 C2-007
- 复核状态：已复核（复核结论见文末「复核结论（C2-round2）」节：C2-007/C2-008 均保留 P3）

## 深挖范围与覆盖声明

### 指定深挖目标逐项落位

| 指定目标 | 落位结论 |
|---|---|
| **MFA/权限检查中的锁面（C1-005 MfaTrustedDeviceManager register 竞态的 C2 面）** | 逐面核清：register 的"无 JVM 锁 + 无 DB 侧防线"本体已由第 1 轮 C2-004 完整登记（P2），本轮**不重复立案**；register 同 hash upsert 路径的 findByHash→null→insert 窗口由 (userId, deviceHash) 唯一约束 + catch 归一（:168-179）承接，核清合格；`isExempted` lastUsedAt 实体写与撤销删除并发（update-on-deleted → ORM 异常）已由 C2-005(b) 登记；权限检查面（OperationMfaCheckerImpl 票消费 / MfaAccessGuard 角色判定 / DefaultDataAuthChecker 缓存）核清无锁缺陷。增量新面 = **C2-008**（TOTP 窗口推进的乐观锁败者异常面，属 MFA 校验链锁语义缺口）。 |
| **Session 表多写者的锁语义（C2-003 深化）** | 本轮补全第 1 轮未覆盖的两个增量面：(a) **C2-007**——autoLogout/revokeUserSessionsAsync 的 getActionSessions→doLogout 批量挤下线为无互斥 check-then-act，并发登录互相漏杀；(b) `logoutSession`/`checkExpired` 的实体变异无显式 update 调用，写持久化完全依赖 ORM 会话脏检查 flush（@SingleSession 范围关闭时）——该依赖本身为平台 ORM 契约（有测试覆盖，不立案），但与 C2-003 的"无 version 列"叠加后，logout 写与 saveUserContext 写的串行化完全依赖 DB 行锁持有窗口（事务边界），本轮作为 C2-003 的证据补强记录，不另立案。 |

### 锁序图增量

```
第 1 轮锁序图（2 条叶子边）无变化——本轮新增站点均无 JVM 锁：
[LoginServiceImpl.autoLogout] ──→ DB 查询 getActionSessions → 逐条 doLogout（无锁 check-then-act）   [无锁边]
[MfaFactorVerifier.verify TOTP] ──→ setting 实体写（DB 行锁窗口内，version 条件兜底）                 [DB 条件写]
[ChannelBindServiceImpl.completeBinding] ──→ DB check-then-insert（CL-016，C1 轮登记）              [无锁边]
```

无环、无新增持锁调外部回调、无锁对象替换。全模块锁序结论维持第 1 轮判定。

## 发现

### [C2-007] autoLogout/revokeUserSessionsAsync 的"读活动会话列表→逐条挤下线"为无互斥 check-then-act——并发登录互相漏杀，单会话契约失效

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java:753-766`（autoLogout，调用点 `completeLogin` :554）；`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractLoginService.java:96-109`（revokeUserSessionsAsync 同构）
- **证据片段**:
  ```java
  // LoginServiceImpl.autoLogout —— allowMultipleSessionsSameUser=false 时每次登录执行
  List<String> sessionIds = loginSessionStore.getActionSessions(userContext.getUserName());
  for (String sessionId : sessionIds) {
      doLogout(AuthApiConstants.LOGOUT_TYPE_RELOGIN, new SessionInfo(userContext.getUserName(), sessionId));
  }
  // 此后 completeLogin :555 才 saveSession（INSERT 本会话行）
  ```
- **严重程度**: P3
- **并发场景**: 同一 userName 的两个并发登录（HTTP worker T1/T2），部署 `nop.auth.login.allow-multiple-sessions-same-user=false`（缺省）；写者集 = logoutSession（置 logoutType=RELOGIN）与 saveSession（INSERT 新行）；无 JVM 锁、无 DB 条件写、`NopAuthSession` 无 version 列（C2-003 实测）。
- **触发时序**: T1 完成第一因子校验进入 completeLogin → :761 getActionSessions 读得 {A}（既有会话）→ T2 同样读得 {A} → T1 逐条 doLogout(A) + INSERT(B) 提交 → T2 逐条 doLogout(A)（幂等无害）+ INSERT(C) 提交。交叠后果：T2 的列表快照不含 T1 尚未插入的 B → **B 与 C 同时存活**——"不允许同一用户名同时存在多个登录会话"（:282 字段注释为不可动态修改的部署契约）在并发登录窗口被突破；每次竞态多存活 (并发度−1) 个会话。
- **现状**: 挤下线语义实现为"登录前快照全杀"，快照与自身 INSERT 之间无任何串行化手段；字段注释声明契约（"不可动态修改，默认 false"）但未声明并发口径。
- **风险**: 单用户并发双登录（双端同时提交密码）各持一个活会话，旧会话不再被挤下线；后果有界（两会话均通过完整认证、均属同一用户），但使"单会话"安全假设（如"改密后 revokeUserSessions 只留当前"）对竞态窗口内的会话失效——revokeUserSessionsAsync 同构（:102 列表→逐条 KILL），改密场景下并发再登录同样漏杀。
- **建议**: 以 userName 为粒度串行化"列表→杀→插"序列（平台 `IResourceLockManager` 用户级锁，或 `INSERT ... WHERE NOT EXISTS(活动会话)` 条件写 + 冲突重试）；最低限度在 owner doc 声明"单会话为尽力语义，并发窗口可短暂多会话"。不建议 DB 触发器级强制（与平台 ORM 事务模型冲突）。
- **失效面枚举**（F3 同族清扫）: "读列表→逐条处置"全仓清单——`LoginServiceImpl.autoLogout`（本条）；`AbstractLoginService.revokeUserSessionsAsync`（本条同构，KILL 语义，改密/resetUserPassword 路径）；`MfaTrustedDeviceManager.removeAllForUser`（listForUser→逐条 delete，C2-005 F3 已 watch 其半个成功面——同一读列表面，漏删面因列表为全量快照且删除幂等而弱于本案，维持 watch）；`LoginMfaFlow`/store 族无列表批处理形态。同族无漏登。
- **信心水平**: 确定（快照窗口与 INSERT 顺序经源码与 ORM 语义核实）
- **误报排除**: 非"看到共享表就报竞态"——给出了具体 T1/T2 交错与契约违反后果；非已声明边界——字段注释只声明"不允许"，未声明并发放宽口径；不按 P2 报因后果有界（同用户、全认证会话）且触发需同用户毫秒级并发提交。
- **复核状态**: 已保留（P3；复核员回码核实 autoLogout :761-765 列表快照→逐条 doLogout→:555 saveSession 顺序、allowMultipleSessionsSameUser :283/:294-297 契约字段、revokeUserSessionsAsync :102-108 同构；全台账无同型先例）

### [C2-008] TOTP 窗口推进为带乐观锁的实体写——并发同码双 verify 的败者收到 ORM 版本冲突异常（500）而非归一 MFA_FAIL；同时构成 CL-005"无双花拦截"判定的未登记修正面

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java:155-159`；`nop-auth/model/nop-auth.orm.xml:1062-1065`（NopAuthMfaSetting `versionProp="version"`）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:318-322,343-349`（UPDATE 带 `version=version+1` SET + `AND version=?` WHERE）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:485-508`（checkUpdateResult count==0 → ERR_ORM_UPDATE_ENTITY_NOT_FOUND）
- **证据片段**:
  ```java
  // MfaFactorVerifier.verify TOTP 分支——防重放窗口推进（"唯一推进路径"）
  setting.setLastVerifiedWindow(window);
  setting.setLastVerifiedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
  daoForSetting().updateEntityDirectly(setting);   // 带版本条件写
  ```
- **严重程度**: P3
- **并发场景**: 同一 userId 并发 mfaVerify 同一 TOTP 码（30s 窗口内码可复用提交——双击/重试风暴）；两请求各自装载 setting 实体（同 version v），TOTP `verify(secret, code, lastWindow)` 对同窗口码**双双通过**（窗口推进在 verify 之后才写库，判定本身无 DB 互斥）。
- **触发时序**: T1 :157-159 updateEntityDirectly → UPDATE ... WHERE userId=? AND version=v 成功（version→v+1，行锁持有至 T1 事务提交）→ T2 同语句阻塞于行锁 → T1 提交后 T2 以 version=v 执行 → 命中 0 行 → `checkUpdateResult` 抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` → T2 整个 mfaVerify 事务回滚，用户收到 **500 级 ORM 异常**而非 `ERR_AUTH_MFA_FAIL`。交叠后果：(a) 败者无归一错误语义（错误码契约缺口）；(b) **对第 1 轮 CL-005 的事实修正**——TOTP 路径的双 verify 双签发会话风险被该乐观锁**偶发拦截**（T2 在 :202 consume 与 completeMfaLogin 之前即失败），CL-005 的"TOTP 场景双方均 completeLogin"时序在此交错下不成立——但拦截是版本碰撞的副作用而非设计守卫：若 T2 的实体装载与写落在 T1 提交之后（串行化正常路径），T2 仍会因窗口判定（window>lastVerifiedWindow 已被 T1 推进）而 `verify` 返回 false → 归一失败；仅"同窗口读-写交错"这一窄带产生裸异常。
- **现状**: 窗口推进的并发语义无显式设计——恰好好处的部分（双花拦截）与代价部分（败者 500）都依赖平台乐观锁的偶然行为；与同文件 webauthn 分支对照：signCount 推进为显式条件 UPDATE + `affected==0 → return false`（:235-248）的归一先例，TOTP 分支未对齐。
- **风险**: 双击 TOTP verify 的用户约一次交错即收到未归一 500（可观察的体验/监控噪声面）；安全面无缺口（双签发被拦截、失败方向 fail-closed）。同族 `UserMfaSelfService.confirmMfa/unbindMfa` 对 setting 的实体写（:1512/:1533 等）在同实体上并发时同样产生裸版本异常（输家语义未归一，量级低——管理面低频）。
- **建议**: 对齐 webauthn 先例：窗口推进改条件 UPDATE（`UPDATE ... SET lastVerifiedWindow=?, lastVerifiedAt=? WHERE userId=? AND lastVerifiedWindow<?`，affected=0 归一为 false/USED 语义），既显式化双花拦截又消除败者裸异常；`resetTotpVerifyFail` 已是条件 SQL 先例（:1519-1527），语句形态同仓可得。
- **失效面枚举**（F3 同族清扫）: "带版本实体上的并发实体写"清单——`MfaFactorVerifier` TOTP 窗口推进（本条）；`UserMfaSelfService` setting 实体写族（confirmMfa :1512 / resetTotpVerifyFail 退化路径 :1533 / bindMfa 流程，:480/:547/:1036 credential 写——带 version 实体，并发输家同型裸异常，管理面低频随本案同修）；`LoginMfaFlow.verifyRecoveryCodeAndComplete` :242 setting setStatus 实体写（恢复码分支，同实体同面）；对照组合格面——webauthn signCount 条件写（ormTemplate 主路径）、恢复码 markRecoveryCodeUsed 条件写、MFA store 族条件 SQL/Lua CAS（C2 核清清单已钉）。无 version 实体（NopAuthSession/NopAuthMfaTrustedDevice）的写点已由 C2-003/C2-005 承载，不混排。
- **信心水平**: 确定（版本条件、异常抛出点、窗口判定双通过交错均经源码逐一核实）
- **误报排除**: 非"乐观锁异常=框架缺陷"——平台行为正确，登记对象是业务侧对败者路径无归一语义（对照同文件 webauthn 分支先例即可证非风格偏好）；非夸大双花风险——本案同时修正 CL-005 的过宽时序，两案在复核阶段应联动裁定。
- **复核状态**: 已保留（P3；复核员独立核验完整平台链：`GenSqlHelper.genUpdateSql:318-322` version+1 SET 与 :343-349 `AND version=?` WHERE、`EntityPersisterImpl:485-486` checkUpdateResult→:508 count==0 抛 ERR_ORM_UPDATE_ENTITY_NOT_FOUND、orm.xml:1065 NopAuthMfaSetting versionProp 在场；同文件 webauthn 条件写先例 :235-248 对照成立）

## 核清清单（C2 检查项通过，非发现）

| 检查项 | 结论 | 证据 |
|---|---|---|
| C1-005 register 竞态的 C2 面 | 已由 C2-004 完整承载；同 hash upsert 唯一约束 + catch 归一核清合格 | `MfaTrustedDeviceManager.java:143-179` |
| `logoutSession`/`checkExpired` 无显式 update | 写持久化依赖 ORM 会话 flush 平台契约（有测试覆盖）；与 C2-003 无 version 叠加的证据补强，不另立案 | `DaoLoginSessionStore.java:75-84`；`DaoUserContextCache.java:87-107` |
| `OperationMfaCheckerImpl` 票核验 | peek 四条件 + `consume(token) != null` 原子消费，并发双花恰一放行 | `OperationMfaCheckerImpl.java:148-158` |
| `LoginMfaFlow.verifyRecoveryCode` 条件写 | `markRecoveryCodeUsed` affected-row 判定 + USED 归一；败者/过期行同走 MFA_FAIL | `LoginMfaFlow.java:259-310` |
| `incrTotpVerifyFail` 独立事务 | REQUIRES_NEW + 原子 SQL，防外层回滚吞计数（javadoc 事务语义声明与实现一致） | `UserMfaSelfService.java:1466-1513` |
| `LoginAttemptStores.sharedLocalDefault`（范围外边界） | volatile DCL 正确先例，attemptStore() 回退链无锁面缺陷 | `LoginAttemptStores.java:23-40` |
| `MfaLoginPolicyServiceImpl.ensureSessionRow` | 第 1 轮 C2-003 F3 已核清（PK 预定无碰撞面），维持 | `MfaLoginPolicyServiceImpl.java:140-163` |
| MFA/权限检查全链锁面 | 除 C2-008 增量面外，与第 1 轮核清清单结论一致，无新锁缺陷 | OperationMfaCheckerImpl/MfaAccessGuard/DefaultDataAuthChecker 逐点复核 |

## 维度继续判定

**判定：C2 维度深挖收敛，建议终止追加轮。** 依据：(a) 两个指定深挖目标均已落位——register 竞态的 C2 面确认由 C2-004 完整承载、Session 多写者语义在 C2-003 之上补出 C2-007 快照漏杀面与 C2-008 乐观锁败者面；(b) 本轮新增 2 条均 P3、无新锁载体（锁序图零新增边），与"DB 条件写承接并发"的全模块架构一致，进一步轮次的边际收益集中在无锁 DB 面的逐点枚举，而该面已两轮全覆盖；(c) 修复批次落地时（尤其 C2-004 用户级锁方案与 C2-003 version 列变更）按 F3 重开同族抽查一次即可。

---
*（本文件为 C2 维度深挖第 2 轮产出；发现均待独立复核。全程未读 ai-dev/audits、plans、bugs、lessons、backlog 其余内容，未 git log。）*

---

## 复核结论（C2-round2）

- C2-007：**保留 P3**。快照漏杀交错与单会话契约违反独立回码成立；F3（autoLogout + revokeUserSessionsAsync 两站点全列）无遗漏。
- C2-008：**保留 P3**。乐观锁败者异常的完整平台链路（SQL 生成→count 校验→异常抛出）经复核员逐层独立核实；对 CL-005 的时序修正已同步登记至 C1 文件复核节——两案联动裁定落 merge-adjudication.md §四。
- 指定深挖目标落位（C1-005 的 C2 面 = C2-004 已承载、Session 多写者 = C2-003 补强 + 2 条增量）复核认可；锁序图零新增边核清一致。
- 维度继续判定（终止追加轮）复核认可。
