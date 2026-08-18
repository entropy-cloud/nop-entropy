# A2-followup-2 操作级 MFA 补全 + WebAuthn 管理面裁定落地（A2 successor-B）

> Plan Status: active
> Mission: nop-credential-mfa
> Work Item: A2-followup-2
> Last Reviewed: 2026-08-18
> Source: `ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/adjudication.md`（successor-B 裁决 + 路由项 1 终局裁定）+ `D2-anti-replay-one-time-consumption.md`（D2-F2）+ `D5-operation-mfa-coverage.md`（D5-F3 + 路由项 1 探查材料）；roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md`（A2-followup-2 工作项）
> Related: `ai-dev/plans/2026-08-18-0904-1-mfa-phase2-security-audit-a2.md`（A2-audit，本 plan 裁决输入的产出者）；`ai-dev/plans/2026-08-18-1924-1-mfa-sensitive-data-governance-a2-followup-1.md`（successor-A，执行序在前）

## Purpose

落地 A2-audit 裁决 **successor-B（操作级 MFA 补全 + webauthn 管理面裁定落地）** 的全部 4 项：关闭登录级公开端点 `mfaVerifyAsync` 的跨场景 token 挪用面（D2-F2，含设计 §3.5 再裁定）、落地路由项 1 终局裁定（`removeWebauthnCredential` 加 `@MfaRequired`）、补齐 enabled webauthn 用户添加第二把钥匙的正门端点（替代 A2 D3-F1 修复后关闭的"解绑保留行累积"旁路）、扩展 `@MfaRequired` 误标构建期 fail-fast（D5-F3）。

## Current Baseline

以下锚点为 2026-08-18 live 核对产物（行号允许执行期小幅漂移，以方法名/语义定位为准）：

- **D2-F2（P2，跨场景 token 挪用）**：`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java` 的 `mfaVerifyAsync`（约 :551-585）peek challenge 后只做 user/setting 复核，**无 scene 校验、无 verifiedAt 校验**；`verifySecondFactorAndComplete`（约 :603-643）仅 mfaType 白名单。后果（A2 D2 报告 B3 探查证实）：scene=operation / webauthn-register / webauthn-unbind 的 challenge 携带用户真实 mfaType，凭 `challengeToken + 有效因子码`可在**免第一因子（密码）**情况下经 `completeLogin` 签发全新会话；已转票（verifiedAt 非空、60s 窗口）的 operation token 同样可兑换。impl 侧已知并以 `TestOperationMfaE2E.testLoginLevelResidualWithOperationSceneTokenPinned`（约 :349-377）钉定为"设计残留 + 安全等价"——A2 审计人已提级异议（等价论证只覆盖第二因子、忽略第一因子降级），裁决表将其判归 successor-B 落地
- **scene 纪律对照面（仓内既有先例）**：`LoginApiBizModel.mfaVerifyOperation`（scene==operation + payload.sessionId）、`NopAuthUserBizModel.confirmWebauthnRegistration`（约 :461-469：scene=webauthn-register + userId + sessionId）、`verifyWebauthnUnbindAssertion`（约 :727-738：scene=webauthn-unbind + userId + sessionId）、`requireChannelProof`（scene=channel-proof + verifiedAt + userId）——登录级 `mfaVerifyAsync` 是唯一无 scene 纪律的验证端点
- **scene 常量**：`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/MfaChallenge.java`（:36-59）已有 login/operation/channel-proof/webauthn-register/webauthn-unbind 五常量
- **路由项 1（已终局裁定，待落地）**：`NopAuthUserBizModel.removeWebauthnCredential`（约 :831-848，@BizMutation + @BizAudit + `requireOwnCredential` 本人限定 + last-credential 守卫 + bulk 物理 DELETE + 审计事件）**未标注 @MfaRequired**；`renameWebauthnCredential`（约 :854-863）未标注且裁定为**不标注**（纯展示元数据变更，C1b 缩窄先例直接适用）。语义近邻 `unbindMfa` 已标注（约 :678，"删除全部钥匙"）；"remove 删一把不标注"形成强度倒挂——裁定 remove 应标注。nop-auth 现有标注清单 5 处（NopAuthUserBizModel:678 unbindMfa/:761 generateRecoveryCodes/:952 resetUserMfa/:1454 resetUserPassword/:1470 changeSelfPassword）+ nop-credential 4 处；容器级元数据断言先例：`TestOperationMfaE2E`（约 :133-146，nop-auth 容器内五动作元数据存活 + 负例 getMfaStatus）
- **add-key gap（D3-F1 修复的副作用）**：`bindMfa` 主入口 guard（约 :270-272）已 enabled → `ERR_AUTH_MFA_ALREADY_ENABLED`——enabled 用户不能经 bindMfa 发起注册；A2 D3-F1 修复后 `unbindMfa` 物理删除全部 credential 行（约 :706-710）+ 删除恢复码 + 撤销可信设备。net 效果：enabled webauthn 用户添加第二把硬件钥匙的唯一路径 = 全解绑重绑（丢失全部既有钥匙/恢复码/可信设备）——多设备 UX 正门缺失，旧"解绑保留行累积"旁路已按安全正确方向关闭
- **D5-F3（P3，构建期 fail-open 错觉）**：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/reflection/ReflectionBizModelBuilder.java` 仅在 BizQuery/BizMutation/BizSubscription → `buildActionField` 路径（约 :348-364）读取 @MfaRequired；@BizAction 路径（约 :164-172）与 @BizLoader 路径（约 :175-203）不读取——误标静默忽略。构建期 fail-fast 先例：`GraphQLErrors.java:299-307`（subscription / publicAccess 两种非法组合拒绝，`TestMfaRequiredMetadata.java:97-110` fail-fast 断言先例，测试位于 `nop-graphql-core/src/test/java/io/nop/graphql/core/reflection/`）
- **测试基线**：本 plan 回归基线——nop-auth-service(+sso) 363 tests 0 failures（2026-08-18 W16-impl-ext 收口口径，A2 收口 360 + 当日后续 +3）、nop-credential-service 209 tests 0 failures、nop-graphql-core 80 tests 0 failures

## Goals

1. `mfaVerifyAsync` peek 阶段引入 scene 纪律：仅接受 `scene==login`（或 null，一期存量兼容）且 `verifiedAt==null` 的 challenge，不符即显式拒绝（D2-F2）；钉定测试 `testLoginLevelResidualWithOperationSceneTokenPinned` 改写为拒绝断言；设计 §3.5 再裁定结论回写
2. `removeWebauthnCredential` 加 `@MfaRequired`（路由项 1 裁定落地，javadoc 注记裁定依据）；容器级元数据断言同步（remove 入正例、rename 入负例钉定）
3. 新增 add-key-while-enabled 正门端点（enabled webauthn 用户在不解绑的前提下添加第二把钥匙）：会话绑定 ceremony + 既有 enabled 钥匙持有证明前置，setting 状态/恢复码/可信设备零副作用
4. `@MfaRequired` 误标 @BizAction/@BizLoader 方法时构建期 fail-fast（D5-F3，GraphQLErrors 新错误码镜像既有两组合拒绝模式）
5. 一期/二期既有契约零回归（登录链全 loginType、操作级 MFA、角色策略、webauthn 三 ceremony、可信设备）

## Non-Goals

- **不重开路由项 1 终局裁定**：rename 不标注（已裁定，本 plan 以负例测试钉定而非重新论证）
- **不重开 A2 adjudication.md 已终局裁定的 deferred/watch-only 项**（D2-F3/D2-F4/D2-F5/D4-*/D5-F4/D5-F5 等，均带 Why-Not-Blocking，所有权在裁决表）
- 不变更 `nop.auth.operation-mfa.enabled` 缺省值（false）与检查点判定序
- 不做 webauthn 之外因子的 add-key（TOTP/SMS/EMAIL 为单秘密模型，无多设备概念；扩展属新 feature 另行立项）
- 不做 `sendMfaCode` 的 scene 校验（A2 记录为 info 级、三层限流覆盖，非 finding；如需收紧另行评估）
- 不做前端 AMIS 页面与用户引导文案（API 面为交付边界；前端对接另行）
- 不做 successor-A 范围项（MFA 敏感表 CRUD 收口/脱敏/失败计数等——见 `2026-08-18-1924-1-*` plan）

## Scope

### In Scope

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/LoginServiceImpl.java`（mfaVerifyAsync scene/verifiedAt 校验）
- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java`（removeWebauthnCredential 标注 + add-key 双端点 + javadoc 裁定注记）
- `nop-service-framework/nop-biz-auth-core/.../mfa/store/MfaChallenge.java`（若裁定新增 scene 常量——仅加常量，非破坏增量）
- `nop-service-framework/nop-graphql/nop-graphql-core/.../GraphQLErrors.java` + `.../reflection/ReflectionBizModelBuilder.java`（D5-F3 构建期 fail-fast）
- 测试：`TestOperationMfaE2E`（钉定测试改写 + 容器断言同步 + 新增负例/正例）、`TestMfaRequiredMetadata`（fail-fast 断言）、add-key 新端点 E2E/组件测试（新文件）
- 文档：`docs-for-ai/03-modules/nop-auth.md`（WebAuthn 管理动作标注 + add-key 端点说明）+ 设计 `ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§3.5 再裁定回写 + §5.3.2/§十 successor-B 落地裁定节）
- roadmap 与 daily log 收口条目

### Out Of Scope

- successor-A 全部范围（见 Non-Goals 最后一条）
- A2 deferred/watch-only 既有裁决项
- add-key 的前端页面、通知/邮件告知流程
- nop-credential 侧代码变更（本 plan 仅回归验证其不受影响）

## Execution Plan

执行顺序：本 plan（N=2）后于 `2026-08-18-1924-1-*`（N=1）。两 plan 无硬依赖，但共同触碰 `NopAuthUserBizModel.java` 与 nop-auth 测试树（`TestOperationMfaE2E` 同文件），串行执行避免冲突。

### Phase 1 - D2-F2：mfaVerifyAsync scene/verifiedAt 校验（含设计 §3.5 再裁定）

Status: completed
Targets: `LoginServiceImpl.java`、`TestOperationMfaE2E.java`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§3.5）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（§3.5 再裁定）**：设计 §3.5"一期全部调用点（checkMfaRequired/mfaVerify）零改动"红线与验证端 scene 校验的相容性——**默认裁定：相容，采纳校验**。论证：红线的语义是**创建侧**调用点零改动（老五参 create 签名/缺省 scene=login 不变——live 事实保持）；`mfaVerifyAsync` peek 后的 scene/verifiedAt 复核是**验证端增量**，与 `mfaVerifyOperation`/`confirmWebauthnRegistration` 既有 scene 纪律对齐，且不改变任何一期合法流的行为（一期合法流只会送 scene=login 或 null 的 challenge 到该端点）。A2 审计人的提级异议（challenge token 替代第一因子 = 无密码账户接管路径）作为翻案证据采纳；原"安全等价"钉定测试的论证缺陷（只覆盖第二因子）在设计回写中显式记录
- [x] **Decision（拒绝语义）**：不符 scene/verifiedAt 条件时的行为——**默认裁定：抛 `ERR_AUTH_MFA_CHALLENGE_EXPIRED` 且不消费 challenge**（错误场景的 token 在其自身场景与 TTL 内仍合法可用，烧毁属过度副作用；与 `mfaVerifyOperation` 对 login token 的既有拒绝行为对齐——执行期核对 live 行为一致后钉定）
- [x] **Fix**：`mfaVerifyAsync` peek 后增加校验：`scene ∈ {login, null}` 且 `verifiedAt == null`，不符即按拒绝语义抛错；一期合法流（checkMfaRequired 创建的 login challenge，含 SSO/信道 loginType 变体）零改动通过
- [x] **Proof**：改写钉定测试 `testLoginLevelResidualWithOperationSceneTokenPinned` 为拒绝断言（`CHALLENGE_EXPIRED`），并确认仓内无其他测试依赖"登录级接受他场景 token"行为（grep 全测试树）

Exit Criteria:

- [x] E2E 测试矩阵：scene=operation / webauthn-register / webauthn-unbind / channel-proof 的 token 送 `mfaVerifyAsync` 全部被拒（错误码断言）；verifiedAt 非空（已转票）的 operation token 被拒；scene=null（一期兼容）与 scene=login 的正常两阶段登录（密码类 + SSO/信道变体）零回归
- [x] 原钉定测试已完成改写且无残留"residual 钉定"注释/命名（测试名与断言一致表达拒绝语义）
- [x] **接线验证**：`mfaVerifyOperation`/`confirmWebauthnRegistration`/`verifyWebauthnUnbindAssertion`/`requireChannelProof` 既有 scene 纪律测试零修改通过（收紧不波及正确场景）
- [x] **无静默跳过**：不符条件是显式错误码（有审计可见的错误响应），非静默返回
- [x] 设计 §3.5 再裁定结论（含翻案证据与论证缺陷记录）已回写 `02-mfa-phase2-design.md`；owner docs 若有"设计残留"表述同步清理（执行期 grep nop-auth.md——无残留表述；登录验证流程描述已同步 scene 纪律）
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿（基线 363，新增用例计入）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 标注补全族：路由项 1 落地 + D5-F3 构建期 fail-fast

Status: completed
Targets: `NopAuthUserBizModel.java`、`TestOperationMfaE2E.java`、`GraphQLErrors.java`、`ReflectionBizModelBuilder.java`、`TestMfaRequiredMetadata.java`

- Item Types: `Fix | Proof`

- [x] **Fix（路由项 1）**：`removeWebauthnCredential` 加 `@MfaRequired`，javadoc 注记裁定依据（A2 路由项 1 终局裁定 + A1 §二#4 缩窄先例的区分论证："删除一把钥匙 = 修改认证因子集合，与 unbindMfa 同族"；last-credential 守卫防自锁死非劫持面）；`renameWebauthnCredential` 保持不标注（javadoc 可注一句"路由项 1 裁定：展示元数据变更不标注"）
- [x] **Fix（容器断言同步）**：`TestOperationMfaE2E` 容器级元数据断言（约 :133-146 先例）正例清单加入 removeWebauthnCredential（5→6），负例加入 renameWebauthnCredential（钉定"不标注"为预期而非遗漏）
- [x] **Fix（D5-F3）**：`GraphQLErrors` 新增两个错误码（@MfaRequired+@BizAction / @MfaRequired+@BizLoader 非法组合，文案与结构镜像 :299-307 既有两码的定义模式——语言随既有形态，不新开语言惯例）；`ReflectionBizModelBuilder` 在 @BizAction 与 @BizLoader 构建路径检测到 @MfaRequired 即抛构建错误（镜像 buildActionField 路径 :348-364 的读取与拒绝模式）
- [x] **Proof**：`TestMfaRequiredMetadata` 增补两类误标的 fail-fast 断言（对齐 :97-110 先例）；grep 全仓确认现无 @MfaRequired 误标于 @BizAction/@BizLoader 的真实用法（fail-fast 不误伤既有 9 处标注）

Exit Criteria:

- [x] 容器级测试：nop-auth 容器内 removeWebauthnCredential 元数据存活断言 + rename 无元数据断言均通过；`operation-mfa.enabled=false` 下 removeWebauthnCredential 行为零变化（零介入负例）
- [x] `TestMfaRequiredMetadata` 两类新 fail-fast 断言通过；既有 subscription/publicAccess fail-fast 断言零修改
- [x] 全仓无既有误标（build 不被 fail-fast 误伤）：`./mvnw compile` 通过；nop-graphql-core 全量测试全绿
- [x] **无静默跳过**：两类误标在 BizModel 注册时构建期显式失败（异常 + 错误码），不再静默忽略
- [x] `./mvnw test -pl nop-graphql/nop-graphql-core -am` 与 `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿
- [x] 设计回写节含路由项 1 落地记录（remove 标注/rename 负例钉定）与 D5-F3 裁定（构建期约束从两组合扩至四组合）；owner docs（nop-auth.md 敏感操作标注清单）同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - add-key-while-enabled 正门端点

Status: planned
Targets: `NopAuthUserBizModel.java`、`MfaChallenge.java`（若裁定加常量）、新测试文件（E2E + 组件）

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision（ceremony 形态）**：**默认裁定：双端点 + 新 scene + 双 challenge 行 + 既有钥匙持有证明**——(a) 发起端点：前置校验（登录态 + setting enabled + mfaType=webauthn + ≥1 把 enabled credential，不符显式报错），创建**两行** scene=`webauthn-add` challenge（**持有证明行**——供既有钥匙 webauthn.get 断言；**注册行**——供新钥匙 webauthn.create attestation；各自独立 cryptoChallenge、payload 含 sessionId 一次写入。理由：get/create 两个 ceremony 各需匹配的 `clientData.challenge`，`MfaFactorVerifier.verifyWebauthn` 从 challenge 行取期望值，单行无法同时服务两 ceremony），返回 verifyChallengeToken + assertionOptions（既有钥匙）+ addChallengeToken + creationOptions（excludeCredentials=既有钥匙，防同钥匙重复注册——bindWebauthn 同构）；(b) 确认端点四参（addChallengeToken/attestation/verifyChallengeToken/assertion，`unbindMfa` 的 `challengeToken+assertion` 参数形态先例）：持有证明（校验链对齐 `verifyWebauthnUnbindAssertion` 形态：scene/userId/sessionId 绑定 + `MfaFactorVerifier` 统一 webauthn 分支——其按 assertion.credentialId + userId + status=enabled 定位凭证行，无钥匙会话不可伪造 + 失败计数不消费）+ add-challenge 校验（scene=webauthn-add + userId + sessionId）+ setting 复核（仍 enabled + webauthn）+ attestation 验证 → 新 credential 落库（status=enabled）→ consume 两 challenge + 审计事件（持有证明与钥匙新增分别落审计）。**部分失败语义（默认裁定）**：持有证明失败仅计数 verify 行、attestation 失败仅计数 add 行（各自先例：`incrWebauthnFailCountOrDiscard` per-token）；verify 行消费时机 = 整体成功时与 add 行一并收口（双行一次性消费，对齐"票在动作成功时才消费"纪律），任一环节失败后重试需重新发起。备选（执行期若裁定采纳须记录取舍）：单 challenge 行双 ceremony 共用 cryptoChallenge——少一行但偏离"一行一用途"scene 纪律。若 draft review 翻案其他形态（如持有证明前置到发起端点），须记录取舍理由
- [ ] **Decision（副作用边界）**：**默认裁定：setting 状态/恢复码/可信设备零副作用**——add-key 不经 pending 状态机（禁止 upsertPending 触碰 enabled setting——防锁死）、不重生成恢复码（对齐"仅 confirmMfa/恢复码重置动作管理恢复码"惯例）、不撤销可信设备（新钥匙增加不降低既有信任前提）。credentialId 全局唯一冲突 → 拒绝（MFA_FAIL，bindWebauthn 同语义）
- [ ] **Decision（标注与白名单）**：**默认裁定：确认端点标注 @MfaRequired（对齐 unbindMfa"修改认证因子集合"族 + 操作级票为第二重验证）；发起端点不标注（只读准备动作，C1b 缩窄先例）。受限会话白名单：不入**——受限用户 setting 不可能 enabled+webauthn（webauthn=当前 factorLevel 上限，W14 同构裁定先例），白名单入口为不可达死代码
- [ ] **Fix**：按上述裁定落地双端点（命名执行期定，语义对齐"addWebauthnKey"族）；与 Phase 1 的交互自动成立：scene=webauthn-add 的 token 在 `mfaVerifyAsync` 被 scene 校验拒绝（无需额外代码，测试钉定）
- [ ] **Proof**：E2E 全链——enabled webauthn 用户（双钥匙场景）经发起+确认添加第三把钥匙：新钥匙落库 enabled、既有钥匙/恢复码/可信设备/setting 状态逐项断言不变、审计事件落库；持有证明失败（错误断言/他人 challenge/跨会话）→ 拒绝 + 失败计数；非 webauthn 用户/未 enabled 用户调发起端点 → 显式拒绝

Exit Criteria:

- [ ] E2E 测试：add-key 全链正例（含双 challenge consume 断言）；持有证明负例矩阵（无断言/错钥匙断言/跨会话 challenge/跨用户 challenge）至少 4 用例；发起前置守卫负例（非 enabled/非 webauthn/零 enabled credential）至少 3 用例
- [ ] E2E 测试：add-key 后原 enabled 钥匙仍可正常通过 webauthn 登录（新钥匙不破坏既有认证路径）；scene=webauthn-add token 送 `mfaVerifyAsync` 被拒（Phase 1 交互钉定）；`last-credential` 守卫与 `removeWebauthnCredential`（Phase 2 已标注）在新钥匙语境下行为正确
- [ ] **接线验证**：add-key 确认端点在容器级元数据断言中入正例清单（若标注）；@MfaRequired 零介入回归（enabled=false 行为不变）
- [ ] **无静默跳过**：所有前置不符/校验失败路径显式错误码；空实现/静默 return 不存在
- [ ] **端到端验证**：从发起端点到新钥匙可用（登录链验证新钥匙断言成功）的完整用户路径已验证
- [ ] 设计回写节含 add-key ceremony 三项裁定（形态/副作用边界/标注与白名单）；owner docs（nop-auth.md WebAuthn 章节 add-key 说明）同步
- [ ] `./mvnw test -pl nop-auth/nop-auth-service -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量回归 + 文档收口 + closure

Status: planned
Targets: 全模块测试、`docs-for-ai/03-modules/nop-auth.md`、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§3.5 再裁定 + successor-B 落地裁定节）、roadmap、daily log

- Item Types: `Fix | Proof | Follow-up`

- [ ] `./mvnw test -pl nop-auth/nop-auth-service -am`（含 sso）全量回归；`./mvnw test -pl nop-graphql/nop-graphql-core -am` 全量回归；`./mvnw test -pl nop-credential/nop-credential-service -am` 回归（C1b 容器元数据断言等跨模块面不受影响）
- [ ] 设计文档回写：`02-mfa-phase2-design.md` §3.5 再裁定结论 + successor-B 落地裁定小节（scene 校验语义 / 路由项 1 落地 / add-key ceremony / D5-F3 四组合构建期约束）
- [ ] `docs-for-ai/03-modules/nop-auth.md` 同步（WebAuthn 管理动作与 add-key 章节、敏感操作标注清单更新）
- [ ] roadmap `ai-dev/backlog/nop-credential-mfa-roadmap.md` A2-followup-2 状态与 plan 链接更新
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 退出码 0（无 NEW 高危空壳）
- [ ] 独立 fresh 子 agent closure audit（证据写入本 plan Closure 段）

Exit Criteria:

- [ ] 上述命令全部退出码 0 / 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] Closure 段已记录独立 audit 证据（Reviewer/task id + 逐 Gate 验证结果 + Anti-Hollow 结论）
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：本 section 与各 Phase Exit Criteria 全部 `[x]` 后才能将 Plan Status 改为 `completed`。关闭流程见 guide `When Closing The Plan`。

- [ ] successor-B 全部 4 项（路由项 1 / D2-F2 / add-key-while-enabled / D5-F3）落地且有 focused 测试，或经显式裁定移出 scope（裁定记录在案）
- [ ] 设计 §3.5 再裁定已回写且与 live 行为一致（"一期调用点零改动"红线的创建侧/验证侧区分成文）
- [ ] 一期/二期契约零回归：nop-auth-service(+sso) 全量 0 failures、nop-graphql-core 0 failures、nop-credential-service 回归 0 failures
- [ ] 无 in-scope live defect 被降级到 deferred / follow-up
- [ ] owner docs（nop-auth.md）与设计文档（02-mfa-phase2-design.md）与 live 行为一致
- [ ] Anti-Hollow：scene 校验/add-key 端点/fail-fast 在运行时与构建期真实可达（E2E 从 GraphQL 入口断言 + fail-fast 测试）；无空方法体/静默跳过作为实现
- [ ] 独立 fresh 子 agent closure audit 完成且证据写入 Closure 段
- [ ] `./mvnw compile` + `./mvnw test -pl nop-auth/nop-auth-service -am` + `-pl nop-graphql/nop-graphql-core -am` + `-pl nop-credential/nop-credential-service -am` 通过
- [ ] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 三方 → java.*）

## Deferred But Adjudicated

无预置项。执行期若出现需延期项，必须按 guide Anti-Slacking 规则落到本节并写明 Classification / Why Not Blocking Closure / Successor Required，否则视为未完成。

## Non-Blocking Follow-ups

- A2 adjudication.md deferred 表中与本 plan 无 ownership 关系的项（D2-F3/D2-F4/D2-F5/D4-*/D5-F4/D5-F5 等）维持原裁定，不在本 plan 重开
- add-key 前端页面与用户引导（API 面交付后前端另行对接）
- `sendMfaCode` scene 收紧（info 级记录项，如需收紧另行评估）

## Closure

Status Note: <<待关闭时填写>>
Completed: <<待填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填写>>
- Evidence: <<待填写>>

Follow-up:

- <<待关闭时填写>>
