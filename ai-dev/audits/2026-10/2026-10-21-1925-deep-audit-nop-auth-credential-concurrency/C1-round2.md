# C1 共享可变状态与可见性 — 深挖第 2 轮（WI 5.1 nop-auth-credential）

- 审计维度：C1（共享可变状态与可见性）深挖追加轮
- 日期：2026-10-21
- 审计员：C1 深挖子代理（盲审纪律：未读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 其余内容；未 git log；仅读本目录第 1 轮三文件 + `ai-dev/skills/concurrency-resource-audit-prompt.md`）
- 编号口径：C1 续接 C1-006；CL 续接 CL-016
- 复核状态：已复核（复核结论见文末「复核结论（C1-round2）」节：C1-006 保留 P3、CL-016 确认休眠面）

## 深挖范围与覆盖声明

第 1 轮两批次清单表已覆盖的站点不重复登记；本轮定向补齐第 1 轮清单表未逐点列名的 auth-service 站点与 nop-oauth 四子模块（逐文件读源码核实）：

| 深挖目标 | 站点 | 结论 |
|---|---|---|
| 指定回访点 | MfaTrustedDeviceManager 全方法（register/isExempted/touchRegister/removeBySid*/removeAllForUser/listForUser/countUnexpired/audit*） | 实例字段仅 `@Inject` 注入的 daoProvider/auditService（@Nullable），发布后只读——合格；C1-005 的锁面移交 C2 深挖（见 C2-round2） |
| MFA 校验链 | MfaFactorVerifier / MfaChallengeHelper / MfaCodeSender / MfaWebauthnSupport / WebAuthnAuthenticator / OperationMfaCheckerImpl / MfaAccessGuard / RoleMfaPolicyEvaluator / MfaLoginPolicyServiceImpl | **新发现 C1-006**（MfaFactorVerifier 对单例 TOTPAuthenticator.skew 的请求期写）；其余全部无状态（final 字段注入协作对象 / 静态工具类）或 per-request 局部态 |
| 登录/会话链 | LoginServiceImpl（余量字段 returnDeptName/allowMultipleSessionsSameUser/allowedLoginMethods 均为 @InjectValue/setter 启动期一次）、LoginApiBizModel（零实例可变字段）、DaoLoginSessionStore/DaoUserContextCache（仅注入 daoProvider） | 合格 |
| 权限/审计/渠道 | DefaultDataAuthChecker/DefaultActionAuthChecker（注入或 init-once）/AuditServiceImpl/GraphQLAuditLogger（第 1 轮清单表 #12 已判）/ChannelBindServiceImpl（providersByType 第 1 轮清单表 #9 已判）/UserChannelResolverImpl/MfaSensitiveTableBizModel（无字段，纯收口基类） | 合格 |
| entity BizModel 29 文件 | 全部为 retention 薄子类/空壳 CrudBizModel（无实例可变字段；NopAuthSessionBizModel/NopAuthUserBizModel 已在第 1 轮覆盖） | 合格 |
| nop-oauth 四子模块 | api（6 DTO + 3 crud 接口，无可变态）/service（3 空壳 CrudBizModel + configs/errors/constants）/dao（3 retention 实体 + 空接口 + `_gen` 排除）/app（NopOauthApplication.globalArgs——第 1 轮批次 1 清单表 #1/#2 同款 launcher 惯用法，同判合格） | 合格（零新发现，第 1 轮"空壳/DTO"判定经本轮逐文件复核成立） |
| 范围外边界 | LocalMfaChallengeStore/LocalLoginAttemptStore/LoginAttemptStores/AbstractLoginService/LocalUserContextCache（nop-biz-auth-core，非 14 ROOT，审到使用契约边界为止） | LoginAttemptStores.sharedLocalDefault 为 volatile DCL 正确先例；LocalMfaChallengeStore.peek 返回 map 内活 MfaChallenge 属消费方只读使用（调用点逐一核清：LoginMfaFlow/OperationMfaCheckerImpl/MfaCodeSender/MfaLoginPolicyServiceImpl 均只读）——本体审计归承载模块单元，本单元按使用面核清 |

## 发现

### [C1-006] 容器单例 TOTPAuthenticator 的 skew 字段被请求线程运行期 check-then-set 写入（非 volatile、无同步、配置热更高并发写）

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaFactorVerifier.java:144-150`；`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/totp/TOTPAuthenticator.java:45-65`
- **证据片段**:
  ```java
  // MfaFactorVerifier.verify TOTP 分支（每个 TOTP 第二因子验证请求执行）
  int skew = CFG_AUTH_MFA_TOTP_WINDOW_SKEW.get();
  if (totpAuthenticator.getSkew() != skew) {
      totpAuthenticator.setSkew(skew);   // 容器单例上的普通 int 写
  }
  // TOTPAuthenticator :47  private int skew = DEFAULT_SKEW;   非 final 非 volatile
  ```
- **严重程度**: P3
- **并发场景**: `nopTOTPAuthenticator` 为 NopIoC 单例 bean；写者 = 执行 TOTP 因子校验的任意 HTTP worker 线程（登录级 mfaVerify / 绑定级 confirmMfa / 操作级 mfaVerifyOperation 三入口收敛于 MfaFactorVerifier.verify）；读者 = 同一单例上并发执行 `verifyRaw` 的其他请求线程（`verifyRaw(secret, code, now, skew, lastWindow)` 以普通传参读 skew，:122）。无任何锁/volatile 建立 happens-before。
- **触发时序**: 配置热更 `nop.auth.mfa.totp-window-skew` 3→1（`varRef` 动态配置引用，代码注释自证"配置热更时恰一次纠偏"为设计预期）→ T1/T2 两并发 TOTP verify 均执行到 :148 `getSkew()` 读得 3≠1 → 双双 :149 `setSkew(1)`（同值双写，良性）→ 但 T3 正在 :122 `verifyRaw(...)` 内以旧值 3 参与窗口计算（`floor=max(currentWindow-skew, last+1); ceil=currentWindow+skew`）且与写之间无 HB → T3 按已废止的 ±3 窗口完成校验。交叠后果 = 单请求使用过宽/过窄窗口，配置变更不即时全局生效。
- **现状**: 该写点是"check2 P3 修复"的残余形态——修复把"每请求无条件写"收敛为"仅漂移时写"（注释原文自认"变异容器单例共享可变状态……是并发隐患模板"），常态零写入；残余面 = 配置热更瞬间与配置漂移期间读侧无可见性保障。容器初始 `setSkew`（beans 装配）与请求期写共存于同一非 volatile 字段。
- **风险**: 后果有界——最坏单个（或漂移窗口内少数）请求按旧 skew 校验：skew 收紧时旧值更宽 = 多接受 ±(旧-新) 窗口的历史码（TOTP 30s 窗口下至多数十秒重放面扩大，且防重放 lastVerifiedWindow 推进仍生效，重放需同窗口码未被消费）；skew 放宽时旧值更窄 = 合法用户校验失败（fail-closed）。无持久化状态损坏。
- **建议**: skew 收敛为只读镜像——`verify` 内改为 `totpAuthenticator.verify(secret, code, lastWindow, skew)` 四参直传（verifyRaw 已是 skew 参数化签名 :135，最小改动消除全部运行期写）；或字段加 `volatile`。对齐代码注释自身的"并发隐患模板"自评收口。
- **失效面枚举**（F3 同族清扫）: 批次 1+2 + 本轮全范围"请求线程运行期写容器单例实例字段"清单——`MfaFactorVerifier`→`TOTPAuthenticator.skew`（本条，唯一命中）；`LoginServiceImpl.mfaFlow/mfaCodeSender/defaultRateLimiter` + `NopAuthUserBizModel.selfService/defaultRateLimiter`（惰性初始化族，第 1 轮 C1-003 已登记）；`NopCredentialBizModel.lastNonCv1SkippedCount`（第 1 轮 C1-001 已登记）；`DefaultCredentialKeyProvider`/`VaultCredentialKeyProvider`/`DefaultCredentialTypeRegistry`（init-once，第 1 轮清单表 #3/#4/#6 合格）；beans 装配期 setter 全部 init-once。同族无漏登兄弟。
- **信心水平**: 确定（字段可变性、双端站点、无同步、热更可达性均经源码与注释原文核实）
- **误报排除**: 非"无状态单例被并发调用"——登记对象是真实的运行期共享字段写；非低频路径——TOTP verify 为生产请求路径，写点在配置漂移/热更时必达；不按 P2 报因写不出超出生平级后果的具体损害链（JMM 下 stale 读最坏偏移一档窗口），如实降 P3。
- **复核状态**: 已保留（P3；复核员回码核实 TOTPAuthenticator:45-65 skew 非 final 非 volatile + getSkew/setSkew 公开、MfaFactorVerifier:144-150 请求期 check-then-set、代码注释自认"并发隐患模板"；varRef 动态配置引用核实）

## 维度集外线索（overlay C4 检查-然后-插入族 / F3 清扫增量）

### nop-auth-credential-CL-016（C4 幂等契约 / 检查-然后-插入竞态，CL-003/CL-007 同族第 3 面）ChannelBindServiceImpl.completeBinding 并发再绑定输家收到裸唯一约束异常

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/channel/ChannelBindServiceImpl.java:126-180`
- **事实**: 三分支裁定（同 user 活行幂等 / 同 user 软删行物理删后插新行 / 跨 user 软删行物理删后插新行）覆盖了顺序语义，但 (a)→(b/c)→insert 全程无锁无条件写；(loginType, extId) 唯一索引（javadoc 自述"unconditional"）只兜数据一致性。触发时序：T1/T2 并发 completeBinding 同一 extId（渠道扫码回调重试/双投递）→ 均走过 active 预检（null）→ 均物理删软删行（幂等）→ 均插入 → 败者违反唯一约束收裸异常，无 duplicate-key 归一（对照 `DbSmsCodeStore.send:86-101` 归一先例与 CL-003/CL-007 同族建议）。当前生产 channel providers 未装配（javadoc 自述 W5-2 到货），为**休眠面**——到货即活，登记条件化。建议：与 CL-003/CL-007 统一捕获约束冲突转幂等成功（本案 "same user + active row" 分支语义已就位，归一即闭环）。

### 核清清单（本轮检查通过，非发现）

| 站点 | 结论 |
|---|---|
| `MfaTrustedDeviceManager.fingerprint/header` | 纯静态工具，无共享态 |
| `MfaAccessGuard.requireCurrentUserId/requireAdmin` | 无状态静态守卫 |
| `OperationMfaCheckerImpl` | RESTRICTED_SESSION_WHITELIST 为 immutable `Set.of`；票核验 `consume(token) != null` 原子（CL-005 对照先例） |
| `WebAuthnAuthenticator` | `RANDOM` 为 static final SecureRandom（线程安全设计意图）；RelyingParty 按次构造（javadoc 自述"避免组件持有可变全局状态"） |
| `RoleMfaPolicyEvaluator` / `MfaLoginPolicyServiceImpl` / `MfaChallengeHelper` / `MfaCodeSender` / `MfaWebauthnSupport` | 无状态或 final 字段注入 |
| `AuditServiceImpl` / `GraphQLAuditLogger` | 平台批量设施 + init-once 模式集（第 1 轮清单表 #11/#12 同判） |
| nop-oauth 四子模块 | 逐文件复核零共享可变态（见覆盖声明表） |
| `LoginAttemptStores.sharedLocalDefault()`（范围外边界） | volatile + 双检锁正确先例，使用面合格 |

## 维度继续判定

**判定：C1 维度深挖收敛，建议终止追加轮。** 依据：(a) 本轮对第 1 轮清单表未逐点列名的全部站点（约 40 文件）补齐逐点核清，仅新增 1 条 P3（且为请求期写点的收敛残余，非新缺陷族）与 1 条休眠面 CL；(b) C1 执行步骤 1–6 的五类载体在 14 ROOT 内的静态 grep 基线（第 1 轮 R1"并发原语命中稀疏"预校准）与两轮人工审读结论一致，边际收益已低于 1 条/轮；(c) 残余疑点（LocalMfaChallengeStore 活引用逃逸、TOTPAuthenticator 本体）均落位于范围外承载模块，按跨单元关联移交，不在本单元扩面。若修复批次引入新的运行期可变态（如热重载密钥、动态 provider 注册），按"修实例不修类别"重开 C1 抽查。

---
*（本文件为 C1 维度深挖第 2 轮产出；发现与 CL 均待独立复核。全程未读 ai-dev/audits、plans、bugs、lessons、backlog 其余内容，未 git log。）*

---

## 复核结论（C1-round2）

- C1-006：**保留 P3**。写点/字段/热更可达性三要素独立回码成立；F3 清扫（同族仅此一处运行期单例字段写）复核无遗漏；CL-016 休眠面判定经 `auth-service.beans.xml` providers 集合现状核实（W5-2 到货前零装配）。
- 本轮核清清单（nop-oauth 四子模块、MFA 校验链无状态组件、entity 空壳 29 文件）经复核员抽验 6 处一致，无异议。
- 维度继续判定（终止追加轮）复核认可：静态基线与两轮人工审读一致，边际收益低于 1 条/轮成立。
