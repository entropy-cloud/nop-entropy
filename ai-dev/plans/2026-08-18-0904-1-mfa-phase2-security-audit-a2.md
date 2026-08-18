# A2-audit MFA 二期安全审计（W12/W13/W14/W15 独立对抗审计 + finding 裁决收口）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: A2-audit（MFA 二期安全审计）——MFA 二期组审计工作项，依赖 W12-impl/W13-impl/W14-impl/W15-impl（均已 done）
> Last Reviewed: 2026-08-18
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` A2-audit 条目 + stage 18；`ai-dev/design/nop-auth/02-mfa-phase2-design.md`（§二 兼容性矩阵 = 回归基准；§3.6/§4.6/§5.3.6/§5.3.7/§6.6 impl 裁定回写）；W12/W13/W14/W15 四份 impl plan 的 Closure 证据与 Deferred/Follow-up 登记；A1-audit plan（`2026-08-17-0447-1`，审计方法学与阶段结构先例）
> Related: W12-impl `2026-08-16-2321-2-mfa-operation-level-stepup.md`；W13-impl `2026-08-17-0447-2-mfa-role-level-policy-engine.md`；W14-impl `2026-08-17-2212-1-mfa-webauthn-fido2-w14.md`；W15-impl `2026-08-17-2212-2-mfa-email-code-trusted-device-w15.md`；C1b `2026-08-17-1345-2-credential-sensitive-actions-mfa-required-c1b.md`（跨模块标注回归面）
> 执行顺序：本 plan 为本轮批次第 1 份（N=1）；与 W16-impl-ext（N=2）无依赖、审计面无交集（nop-auth vs nop-integration）。A2 done 是 A3-audit 的依赖项。

## Purpose

对 MFA 二期四个实现（W12-impl 操作级 MFA / W13-impl 角色级强制策略 / W14-impl WebAuthn/FIDO2 / W15-impl 邮件验证码 + 可信设备）执行独立安全审计：以设计文档（含 impl 裁定回写标注）与一期契约为基准，对 live code 做对抗性探查，产出带 P0-P3 分级的 findings + 零悬挂的裁决表；同时收口四份 MFA impl plan 显式路由到 A2-audit 的 deferred/follow-up 再裁定。审计本身不引入新功能；confirmed live defect（P0/P1）在本 plan 内修复或建立 successor 所有权，不允许静默降级。

## Current Baseline

（2026-08-18 live repo 核对）

- **MFA 二期交付面已全部落地并通过独立 closure audit**：W12-impl（`@MfaRequired` 注解 + 元数据四触点传播链 + executor 两检查点 + `IOperationMfaChecker` SPI 可选注入 + store 场景化 scene/payload/verifiedAt + `mfaVerifyOperation` 端点 + 首批五动作标注 + 审计四事件；`nop.auth.operation-mfa.enabled` 缺省 false 零介入）、W13-impl（`NopAuthRoleMfaPolicy` 策略模型 + `RoleMfaPolicyEvaluator` max/AND 合并 + `MFA_RESTRICTED` 登录第三态 + 受限会话拦截/白名单短路 + Dao-cache 白名单双触点 + 登记 channel proof 防 enrollment attack + OAuth/SSO 绕过修复三分支 + `IMfaLoginPolicyService` SPI）、W14-impl（Yubico `webauthn-server-core` 2.7.0（依赖只进 nop-auth-service）+ `NopAuthMfaCredential` 实体 + 三 ceremony（注册/认证/解绑）challenge 三触点同步 + `MfaFactorVerifier` 五参统一载体 + credential 管理三 API（masked 不暴露 credentialId/publicKey）+ `sendMfaCode` 按 mfaType 分派）、W15-impl（EmailCodeStore 三实现 + `nop_auth_email_code` 实体 + email 因子全链（白名单 6 触点 + dict 第四条目 + factorLevel 常量）+ 登记通道 email 解锁（phone 优先缺失回退 + 双通道 `channel` 参数 + 通道隔离 proof-email key）+ `NopAuthMfaTrustedDevice` + `MfaTrustedDeviceManager`（指纹/豁免固定窗口/登记/撤销）+ `checkMfaRequired` headers 增参 + `policy.allowTrustedDevice` 复合结果消费 + `rememberDevice`/`trustedDeviceRegistered` + 撤销矩阵四触发 + **OAuth 副本"不加豁免分支"专项回归断言**）。
- **跨模块回归面**：C1b 已给凭证库四动作（reencryptAll/delete/NopCredentialAuth.grant/revoke）加 `@MfaRequired` 标注，且容器级元数据断言/负例/零介入回归落在 nop-credential 侧——操作级钩子全路径覆盖审计必须包含该跨模块标注面（标注分布 + 元数据管线 + enabled=false 零介入）。
- **测试基线（live 测试树核实）**：nop-auth-service MFA 家族测试类清单——E2E 家族 `TestMfaLoginE2E`/`TestMfaUserSelfService`/`TestOperationMfaE2E`/`TestScanLoginMfa`/`TestMfaRestrictedLoginE2E`/`TestMfaRestrictedSessionE2E`/`TestMfaRestrictedDaoCache`/`TestRoleMfaPolicy`/`TestWebAuthnMfaE2E`/`TestWebAuthnMfaAdvancedE2E`/`TestEmailMfaE2E`/`TestChannelProofEmailE2E`/`TestTrustedDeviceE2E`；组件/store 家族 `TestMfaFactorVerifier`/`TestMfaConfigAndErrors`/`TestMfaStoreProvider`/`TestMfaStoreWiringDb`/`TestTrustedDeviceSupport`/`TestWebAuthnAuthenticator`/`service/mfa/store/` 下 `TestDbMfaChallengeStore`/`TestDbSmsCodeStore`/`TestDbEmailCodeStore`/`TestRedisEmailCodeStore`/`TestRedisStoreWiring`/`TestMfaChallengeJsonCompat`/`TestEmailCodeEntrySerialization`/`TestDbMfaChallengeStoreEntityRoundTrip`；meta 侧 `TestNopAuthMfaSettingXmeta`/`TestNopAuthMfaEntityRoundTrip`。W15 closure 记录全模块 355 tests 0 failures（W12-W15 各自声明"既有断言零修改"）——本 plan 执行时须 live 重跑核实而非沿用旧计数。
- **设计文档为回归基准**：`ai-dev/design/nop-auth/02-mfa-phase2-design.md` §二 一期契约兼容性矩阵（六行：两阶段 challenge / `ERR_AUTH_MFA_REQUIRED` 异常表达 / `completeLogin` 分界 / store 装配（collect-beans + 条件激活 + lessons 15）/ 明文边界 / 一期零回归）+ 各主题 x.5 兼容小节 + §3.6/§4.6/§5.3.6/§5.3.7/§6.6 impl 裁定回写。
- **四份 MFA impl plan 显式路由到 A2-audit 的 deferred/follow-up 项**（共 3 项，Phase 3 必须逐项再裁定，禁止悬空）：
  1. WebAuthn 管理动作（removeWebauthnCredential/renameWebauthnCredential）是否应加 `@MfaRequired` 标注——W14 Non-Blocking Follow-up「留 A2-audit 评估」（W12 首批清单未含，无既定裁定；C1b 凭证库侧缩窄裁定（管理面非破坏性动作不标注）可作对照先例）。
  2. `MfaLoginPolicyServiceImpl.checkMfaForUserName` 与 `LoginServiceImpl.checkMfaRequired` 判定逻辑同构（受控重复，为 nop-auth-sso 零依赖边 substrate 裁定 §4.6(5) 而存在）——W13 closure Advisory 登记「两者任何一方变更时必须同步另一方（A2-audit 回归点；watch-only residual）」；W14（challenge 创建三触点同步）与 W15（headers 增参 + OAuth 副本不加豁免分支专项断言）先后动过该家族，A2 须复核当前两副本仍同步。
  3. 联系方式修改（通用 CRUD 路径）敏感化——W12 Deferred（Successor Path: A2-audit 评估或后续平台治理裁定；live 无专用 changePhone/changeEmail mutation，EMAIL/PHONE 修改走继承 CrudBizModel 通用 save/update，方法级注解无法覆盖共享基类动作）。
- **审计基建**：审计目录惯例 `ai-dev/audits/YYYY-MM/YYYY-MM-DD-HHMM-{type}-{module}/summary.md`（`ai-dev/audits/README.md`）；审计 prompt 模板 `ai-dev/skills/deep-audit-prompts.md` 与 `ai-dev/skills/open-ended-adversarial-review-prompt.md`；roadmap 二期审计门禁「A1/A2/A3 审计工作项走独立 fresh session；finding 裁决表零悬挂；P0/P1 不静默降级」。
- **测试基建事实（2026-08-18 live 核对更新）**：nop-auth-service missing-tenant-id 顺序污染 flake **已于 2026-08-17 根因修复**（`ai-dev/bugs/2026-08/2026-08-17-missing-tenant-id-suite-flake.md` + 08-17 log；修复后 W15 收口 `-T 1C` 全 reactor 355/0 全绿为证）——旧"单独运行复核 + baseline 对照"豁免口径随之退役；本 plan 执行中若再现 missing-tenant-id 类失败，按新缺陷处置，不得沿用旧豁免。本 plan 新增探查测试如需全局配置开关，遵 `TestTenant` `@AfterAll` 恢复先例。仍存活的基建 flake：workspace 全量 test-compile 偶发 plexus javac 进程内 CME（`-Dmaven.compiler.fork=true` 绕过）。
- **A2-audit 依赖已满足**：W12-impl/W13-impl/W14-impl/W15-impl 均 `done`；本工作项为 MFA 二期组当前第一个 `todo`（roadmap 全序第一个 `todo`）。

## Goals

- 七个审计维度全部执行并留下可复核的 live 证据（file:line 锚点 + 探查测试/脚本输出）：因子强度与绑定/登记生命周期、防重放与一次性消费、恢复通道、角色级策略一致性与受限会话、操作级 MFA 全路径覆盖、可信设备、登录级链路一期零回归。
- 审计记录落盘 `ai-dev/audits/2026-08/`：summary.md + 分维度 detail 报告，每条 finding 标注 P0-P3 与建议修复方向。
- **finding 裁决表零悬挂**：每条 finding 落到且只落到一种处置——`fixed in this plan`（含 focused 测试）/ `successor plan 所有权`（给出路径，roadmap 为本 mission 的 successor 注册面）/ `watch-only/optimization deferred`（含 Why Not Blocking）；P0/P1 confirmed live defect 不得进入 deferred。
- 3 项路由 deferred/follow-up 全部完成再裁定（终局结论记录在审计报告裁决表与 daily log——按 guide 规则 20，来源历史 plan 不回改）。
- 一期契约锚点回归结论显式记录（PASS/FAIL + 证据），每个锚点有归属审计维度：设计 §二 矩阵六行（两阶段 challenge → D2/D7；`ERR_AUTH_MFA_REQUIRED` 异常表达 → D4/D7；`completeLogin` 分界 → D4/D7；store 装配 → D2；明文边界 → D1；一期零回归 → D7）+ 三个附加回归项：既有 MFA 套件断言零修改（git 层面核对 W12-W15 落地过程未改动一期断言——归 D7）、`nop.auth.operation-mfa.enabled` 缺省 false 与无策略行双零介入（归 D4/D5）、C1b 凭证库标注容器级元数据断言仍绿（归 D5，跨模块回归面）。

## Non-Goals

- 凭证库二期组审计（A1-audit 已 done；凭证侧 finding 不在本 plan 重开，涉及交叉面只登记不实施）。
- nop-integration/nop-metadata 深度迁移审计（W16 家族 + A3-audit 范围）。
- A3-audit（二期收口全量验证）的工作前置或替代。
- 性能、可用性、容量审计（安全属性之外的面）。
- 设计层已裁定 deferred 项的默认翻案（§七 7.1 attestation 信任链 / 7.2 外部 MFA 服务 / 7.3 信道路径豁免 / 7.4 批量清理任务 / 7.5 前端交互 / 7.6 治理工具 / 7.7 signCount=0 增强——仅在审计发现新证据时再裁定）。
- 前端 UX、管理面增强类 follow-up。

## Scope

### In Scope

- 审计执行：fresh 独立子 agent 对 live code 的对抗探查（可新增探查性质的安全测试落盘到 nop-auth 测试树，作为可回归的对抗断言；探查测试不算新功能，须标注审计来源）。
- finding 裁决与处置：P0/P1 现场修复（含 focused 测试 + 回归）；P2/P3 裁定处置路径。
- 3 项路由 deferred/follow-up 的再裁定（只裁定 + 登记，除非裁定为 P0/P1 defect）。
- 文档同步：审计报告落盘；发现 owner-doc/design drift 时修复 `docs-for-ai/03-modules/nop-auth.md` 与 `ai-dev/design/nop-auth/02-mfa-phase2-design.md` 的不一致；roadmap A2-audit 状态更新。
- `ai-dev/logs/` 收口记录。

### Out Of Scope

- 任何新功能交付（除 P0/P1 修复与对抗探查测试）。
- 通用 CRUD 动作敏感化机制的平台级实施（路由项 3 若裁定"应做"，产出的是 successor 登记与规模建议，不是本 plan 内实现）。

## Execution Plan

### Phase 1 - 审计章程锚定与探查清单

Status: completed
Targets: `ai-dev/audits/2026-08/`（新建审计目录）、本 plan

- Item Types: `Decision | Proof`

- [x] **Decision**：建立审计目录 `ai-dev/audits/2026-08/{YYYY-MM-DD-HHMM}-deep-audit-nop-auth/`，落盘 audit-charter.md：审计范围（模块/实体/端点全清单）、七个维度的探查清单（每维度列出目标文件锚点、威胁假设、对抗用例）、回归基准声明（设计 §二 矩阵 + 四份 impl plan Closure 段引用 + C1b 跨模块标注面）。（`ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/audit-charter.md`）
- [x] **Decision**：引用点/攻击面枚举核对：以 live code 复核 MFA 全部公开端点（登录族 sendSmsCode/sendMfaCode/mfaVerify/mfaVerifyOperation、受限会话白名单端点、登记通道 proof 验证）、`NopAuthUserBizModel` MFA 动作面（bindMfa/confirmMfa/unbindMfa/generateRecoveryCodes/getMfaStatus/resetUserMfa + webauthn credential 管理三 API + trusted device list/removeTrustedDevice）、`@MfaRequired` 标注分布全量（nop-auth 五动作 + 凭证库 C1b 四动作）、store 家族（challenge/sms/email × local/db/redis 装配与条件激活）、策略管理面（saveMfaPolicy/removeMfaPolicy）——与 charter 中的面清单一致。
- [x] **Proof**：charter 中每个攻击面给出 file:line 锚点（live 核对，非引用旧 plan 结论）。

Exit Criteria:

- [x] 审计目录与 charter 落盘，攻击面清单与 live code 一致（抽查锚点可对号）。
- [x] No owner-doc update required（Phase 1 仅产出审计章程，不改 owner 行为；发现物落审计目录）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 对抗审计执行（独立 fresh 子 agent）

Status: completed
Targets: `nop-auth/`（service/biz/core）、`nop-credential/`（C1b 标注回归面）、`nop-graphql-core`/`nop-biz`（仅审计面：D5 元数据传播链四触点所在框架模块；框架侧若需修复须显式裁定归属，不在本 plan 默认 Targets 内落地）、`ai-dev/audits/2026-08/.../`

- Item Types: `Proof | Fix`

> 执行约束（roadmap 二期审计门禁，沿 A1-audit 先例）：本 Phase 的探查执行必须由 fresh 独立子 agent（非本 plan 编排 session 自查、非原 impl session 复用）分维度执行；编排 session 负责 dispatch 与证据收集。每个维度产出 detail 报告（findings + P0-P3 + 锚点 + 探查证据）。**职责分工**：探查子 agent 只探查与报告（不改产品代码）；P0/P1 修复由编排 session（或其指定的独立 fixer 子 agent）执行，修复后由**另一个 fresh 复核子 agent** 验证修复有效且未引入回归——保持"发现者不复核自己发现项的修复"的独立性。

- [x] **Proof（D1 因子强度与绑定/登记生命周期）**：factorLevel 全量表与各因子强度归属；绑定状态机（pending+bindToken → confirmMfa enabled → unbindMfa/disabled）与 confirmMfa 防降级；登记通道 proof 防 enrollment attack（受限会话内 bindMfa 前置 + 通道验证码 phone 优先 email 回退 + 通道隔离 proof-email key + 邮箱脱敏形态）；明文边界（secret 加密/恢复码 BCrypt/publicKey 按 masked 不展示/getMfaStatus 不返回 secret）；对抗探查：跨用户 bindToken 冒用、pending 状态旁路 confirm、登记通道枚举/混淆攻击。（`D1-factor-binding-lifecycle.md`，task ses_feccead55ffeRPNuylg0ONScoI：7 findings（P2×2/P3×5），A1-A4 全部显式结论，明文边界锚点 PASS）
- [x] **Proof（D2 防重放与一次性消费）**：TOTP lastVerifiedWindow 防重放（通过推进/拒绝不更新）与窗口统一推进收敛点；WebAuthn signCount 条件 UPDATE 并发语义 + challenge 单次消费（三创建触点经 `MfaChallengeHelper` 收敛的同步不变式）+ cryptoChallenge payload；SMS/email 码三态（send/verify 原子 consume/失败内部计数）与 key 隔离（login:{phone}/mfa:{userId}/email 变体）；challenge peek 不刷新 TTL/incrFailCount 超限作废；操作级票（验证转一次性短 TTL 票，绑定 operation+sessionId）；对抗探查：已消费码/challenge 重放、并发同码验证、跨场景 token 挪用（login challenge 用于操作级、proof token 用于登录等）。（`D2-anti-replay-one-time-consumption.md`，task ses_fecce8437ffe655OYcAFkUiZh6：5 findings（**P1×1** D2-F1/P2×1/P3×3），B1-B7 全部显式结论，两阶段 challenge + store 装配锚点 PASS）
- [x] **Proof（D3 恢复通道）**：恢复码 BCrypt 加盐哈希存储、used 幂等（重放已用码统一 MFA_FAIL）、expireAt、mfaVerify recovery 分支成功后 status=disabled 强制重绑语义、generateRecoveryCodes 重置作废旧码、unbindMfa 作废恢复码、管理员 resetUserMfa（requireAdmin 运行时校验 + NopAuthOpLog 审计）；对抗探查：恢复码穷举与失败计数分界、重置窗口期旧码复活、管理员重置后旧因子复活。（`D3-recovery-channel.md`，task ses_fecce66d2ffesuUZtQ7gEbAAWX：7 findings（**P1×1** D3-F1/P2×2/P3×4），C1-C5 全部显式结论）
- [x] **Proof（D4 角色级策略一致性与受限会话）**：RoleMfaPolicyEvaluator max/AND 合并语义与 factorLevel 全量表；三层判定矩阵（全局开关/用户级启用/角色级策略）优先级与无策略行 = 一期行为零回归；MFA_RESTRICTED 第三态不建 challenge + completeLogin 受限变体（mfaRestricted 先设后存）；受限会话拦截 executor/checker 双触点 + 白名单短路 + `LoginApi__refreshToken` Async 尾缀类注册名核对；Dao-cache 白名单双触点；checkMfaRequired headers 增参（loginAsync 真实值/信道路径 null 结构性跳过——信道路径不豁免的裁定面）；OAuth/SSO 三分支绕过修复保持（MfaLoginPolicyServiceImpl.checkMfaForUserName）；saveMfaPolicy/removeMfaPolicy 管理面权限；对抗探查：受限会话白名单端点滥用、策略继承合并绕过、Dao-cache 与 DB 不一致窗口、跨入口（密码/SSO/信道/扫码）判定不一致。（`D4-role-policy-restricted-session.md`，task ses_fecc6bca7ffeBpstkP5UDuaOHv：6 findings 全 P3，D-1~D-5 全部未探查到可利用路径，同构比对表 = 路由项 2 证据，三回归锚点 PASS）
- [x] **Proof（D5 操作级 MFA 全路径覆盖）**：`@MfaRequired` 元数据四触点传播链（注解 → ReflectionBizModelBuilder → GraphQLFieldDefinition.mfaRequiredMeta → executor）+ 构建期约束校验（@BizSubscription 组合拒绝/@Auth(publicAccess=true) 组合拒绝）；executor 两检查点（受限会话拦截 + 操作级验证）；批量请求含敏感操作整批预执行中止语义；mfaVerifyOperation 登录态/同会话/不签发凭证；`IOperationMfaChecker` SPI 可选注入（未注册零介入）；`nop.auth.operation-mfa.enabled` 缺省 false 零介入回归；标注分布审计（nop-auth 五动作 + 凭证库 C1b 四动作的容器级元数据断言与负例仍绿——跨模块回归面，探查含 nop-credential 测试树重跑）；**路由项 1 探查输入**：webauthn 管理动作（removeWebauthnCredential/renameWebauthnCredential）的破坏性评估（凭证删除/改名与 C1b 缩窄先例的对比论证材料）；对抗探查：绕过注解传播的调用路径、直接 GraphQL 字段选择绕过 executor、同会话票跨 operation 挪用。（`D5-operation-mfa-coverage.md`，task ses_fecc68bcbffesvugIPebQCtVJ：5 findings（P2×1/P3×4），E1-E5 全部显式结论，C1b 静态一致性 + 双零介入 PASS，路由项 1 论证材料产出）
- [x] **Proof（D6 可信设备）**：指纹不可逆哈希（算法与字节编码）与 (userId,deviceHash) 复合唯一；豁免固定窗口语义（lastUsedAt 不续 expireAt——"豁免不续期"逐字节断言）；登记（同 hash 复活刷新/满员 false/并发唯一约束归一 update）；撤销矩阵四触发（列表移除/解绑因子/管理员重置/换绑）；rememberDevice 显式 true/false 与 trustedDeviceRegistered attr 回填；headers 穿线仅经 verifySecondFactorAndComplete（禁放 completeMfaLogin——恢复码结构性排除的负例）；`policy.allowTrustedDevice` 复合结果消费点；**OAuth 副本"不加豁免分支"专项回归断言仍在且语义成立（路由项 2 关联面）**；对抗探查：伪造/重放 deviceHash、豁免窗口无限续期攻击、跨用户指纹挪用、撤销后残留豁免。（`D6-trusted-device.md`，task ses_fecbf2987ffePqJpmpQjtPRrUP：3 findings（P2×1/P3 watch×2），F1-F7 全 PASS，OAuth 副本专项锚点 PASS）
- [x] **Proof（D7 登录级链路一期零回归）**：设计 §二 矩阵六行逐行核验（含 store 装配 collect-beans + 条件激活 + lessons 15 类加载安全不变式在 EmailCodeStore 三实现上的复刻完整性）；既有 MFA 套件断言零修改（git 层面核对 W12-W15 未改动一期断言）；一期交付面回归（两阶段登录 E2E、loginType=5 短信登录、恢复码登录、因子等同分支 PHONE_SMS + mfaType==SMS 不重复验证、失败计数分界、未启用 MFA 用户零感知、扫码 MFA 链路 ScanLoginResult 可选字段向后兼容、W6 closure 补的 SMS 全链用例仍在）；`ERR_AUTH_MFA_REQUIRED` 异常表达与 errorParams 契约。（`D7-phase1-zero-regression.md`，task ses_fecbefb3affe1YkimpQ1X5C7u3：矩阵六行全 PASS，G2 断言零修改 HOLDS（14 文件 git 核对），G3 8/8 PASS，G4 测试树与 plan 基线一致）
- [x] **Fix（仅当确认 live defect）**：探查过程中确认的 P0/P1 现场修复 + focused 回归测试；修复遵循生成文件纪律与 owner-doc 同步。（**P1×2 修复**：D2-F1——RedisSmsCodeStore/RedisEmailCodeStore VALID 裁决以 CAS 胜出为前提 + `TestRedisCodeStoreCasRace` 4 用例；D3-F1——unbindMfa/resetUserMfa/LoginServiceImpl 恢复码分支三边界 bulk 物理 DELETE credential 行 + `testUnbindAndAdminResetDeleteCredentialRows` + 3 个既有测试重排（断言面保持）。附带 P3 修复：D1-4/V-F1 removeWebauthnCredential 物理删除、D5-F2/V-F2 javadoc 批量语义更正。修复后 fresh 复核子 agent `fix-verification.md`（task ses_feca0ee11ffeoWYwYJ8WuqTXUL）六项全 PASS：边界穷举无遗漏/跨实现语义一致/无断言弱化/能力变更无生产消费方依赖/无静默 no-op/39 测试绿）
- [x] **Proof**：summary.md 汇总（findings 总表：编号/维度/严重度/锚点/建议方向 + 回归锚点结论表 + 子 agent 执行证据）。（`summary.md`：39 findings 总表（closure audit 复核口径）+ 七回归锚点结论表 + 8 个子 agent task 标识）

Exit Criteria:

- [x] 七维度 detail 报告 + summary.md 落盘，每条 finding 有 P0-P3 分级与 live 锚点。
- [x] 对抗探查中新增的探查测试（若有）已在 nop-auth（及涉及时 nop-credential）测试树落盘并通过，且标注审计来源。（`TestRedisCodeStoreCasRace`（标注 A2-audit D2-F1 来源）+ `testUnbindAndAdminResetDeleteCredentialRows`（标注 A2-audit D3-F1）；nop-credential 侧无需新增（C1b 断言面静态一致 + 全量重跑绿））
- [x] **无静默跳过**：每个维度的对抗用例结论是显式的（探查到/未探查到 + 证据），不允许"未覆盖且未声明"。（七报告各含探针裁决节 + 覆盖率声明）
- [x] 独立子 agent 执行证据（task/session 标识）记录在 summary.md。（8 个 task 标识：七维度 + 修复复核）
- [x] No owner-doc update required（本 Phase 的 owner-doc/design drift 修复统一归属 Phase 3 Fix 项收口；本 Phase 自身不改文档）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - finding 裁决收口 + routed deferred 再裁定 + 文档同步

Status: completed
Targets: 审计目录（裁决表）、`ai-dev/design/nop-auth/02-mfa-phase2-design.md`、`docs-for-ai/03-modules/nop-auth.md`、roadmap

- Item Types: `Decision | Fix | Follow-up`

- [x] **Decision**：finding 裁决表落盘（adjudication.md）：每条 finding → `fixed`/`successor`/`deferred-with-reason` 三态之一；P0/P1 全部 fixed（本 plan Phase 2）或有 successor 所有权路径；零悬挂（无"待定"状态残留）。（`adjudication.md`：fixed×4 / successor-A·B×2 / deferred+watch 逐条含 Why Not Blocking；P0×0、P1×2 均 fixed；零悬挂核对段在案）
- [x] **Decision**：3 项路由 deferred/follow-up 逐项再裁定并写入裁决表：① webauthn 管理动作 @MfaRequired 标注（应标注→successor 路径或不标注→理由，对照 C1b 缩窄先例与破坏性评估材料）；② `MfaLoginPolicyServiceImpl` ↔ `LoginServiceImpl.checkMfaRequired` 同构不变式复核（当前是否仍同步 + watch-only 维持或升级处置）；③ 联系方式修改通用 CRUD 敏感化（平台治理 successor 登记或显式不做 + 理由）。（①rename 不标注（终局，C1b 先例适用）+remove 应标注→successor-B；②当前同步成立（D4 逐块比对表）+watch-only 维持 + D4-4 新登记；③并入 successor-A）
- [x] **Fix**：审计确认的 owner-doc/design drift 修复（docs-for-ai/03-modules/nop-auth.md 与设计文档 vs live 行为不一致处；含 nop-credential.md 交叉引用若触碰 C1b 面）。（nop-auth.md 五处更新：credential 实体失效边界语义/unbindMfa/removeWebauthnCredential 动作行/解绑 ceremony 流程/恢复码分支；设计文档新增 §十 A2-audit 落地裁定（六条，含 §5.3.2 偏离记录 #7 修正）；nop-credential.md 无触碰（C1b 面无 drift）；nop-auth.md 批量语义原本正确无需改）
- [x] **Proof**：收口验证——`./mvnw test -pl nop-auth -am` 绿 + `./mvnw test -pl nop-credential -am` 绿（后者为 D5 C1b 跨模块标注面的无条件必核项，不以"是否有修复"为前提；missing-tenant-id flake 已修复退役，任何测试失败按实际结果处置、不得沿用旧豁免口径）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；若 Phase 2 落有修复代码，`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 0 NEW（修复若落在 nop-credential 等其他模块，加跑对应模块）。（nop-auth 家族 `:nop-auth-service,:nop-auth-sso -am` **360 tests 0 failures**；`:nop-credential-service -am` **209 tests 0 failures**；`:nop-graphql-core -am` **80 tests 0 failures**（javadoc 触点）；check-doc-links **0 errors**；hollow scan nop-auth Total=1 但为 nop-auth-sso `OAuthLoginServiceImpl:231` **pre-existing**（git diff 证明本次未触碰该文件）= **0 NEW**，graphql-core Total=0；一次中间 run 命中 workspace 已知 plexus javac 编译 flake（零测试失败、复跑全绿）——plan Current Baseline 预登记的基建 flake，非产品缺陷）
- [x] **Follow-up**：roadmap A2-audit 条目收口更新（`done` 判定交由本 plan closure audit）；裁决产生的 successor 登记到 roadmap 相应位置。（A2-audit `done` + A2-followup-1/A2-followup-2 两个 successor 工作项登记（均标注不阻塞 A3-audit）+ Last updated 头部）

Exit Criteria:

- [x] 裁决表零悬挂且与 summary.md findings 一一对应。
- [x] 3 项 deferred 再裁定全部有结论与理由（审计报告可查）。
- [x] P0/P1（若存在）已修复且有 focused 测试证明，或 successor 所有权已登记。（P1×2 已修复：`TestRedisCodeStoreCasRace` 4 用例 + `testUnbindAndAdminResetDeleteCredentialRows` + 3 个重排测试全绿；独立复核 PASS）
- [x] owner-doc/design drift（若存在）已修复，或明确记录 No owner-doc update required。（drift 五处已修复，见 Phase 3 Fix 项）
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 七维度审计全部执行且证据可复核（fresh 子 agent 执行 + file:line 锚点 + 探查输出）。（8 个 task 标识见 summary.md §一；closure audit 抽查 12+ 锚点对号成立——NopAuthUserBizModel 标注行因本 plan 内 D3-F1 修复发生行号平移（:676→:678 等，同五方法），post-fix 锚点已在 fix-verification.md 记录）
- [x] finding 裁决表零悬挂；P0/P1 无静默降级（fixed 或 successor 所有权）。（closure audit 逐 ID 核对 39/39 唯一归属；P0×0；P1×2 均 fixed）
- [x] 3 项路由 deferred/follow-up 再裁定完成并记录。（adjudication.md §二）
- [x] 一期契约锚点回归结论显式记录（PASS/FAIL + 证据）：设计 §二 矩阵六行 + 三个附加回归项（既有断言零修改 / 双零介入 / C1b 跨模块标注面），每个锚点有归属维度的探查结论。（summary.md §三：矩阵六行 + 三附加项 + OAuth 副本专项全 PASS）
- [x] 探查/修复引入的测试全部通过；`./mvnw test -pl nop-auth -am` 绿 + `./mvnw test -pl nop-credential -am` 绿（C1b 标注面无条件重跑；missing-tenant-id flake 已修复退役，任何失败按实际结果处置）。（`:nop-auth-service,:nop-auth-sso -am` 360/0（`nop-auth` 为聚合器 pom，有效选择子模块——W16 review 轮 2 同裁定先例）；`:nop-credential-service -am` 209/0；一次中间 run 命中已知 plexus javac 编译 flake（零测试失败、复跑全绿））
- [x] 审计记录符合 `ai-dev/audits/README.md` 规范（目录/summary/分级）。（`2026-08/2026-08-18-1244-deep-audit-nop-auth/{audit-charter,D1-D7,fix-verification,summary,adjudication}.md`——A1-audit 先例同构）
- [x] 独立子 agent closure-audit 已完成并记录证据（含：审计记录与 live code 一致性抽查、裁决表无悬挂复核、Anti-Hollow——若落有修复，验证调用链运行时连通且无空方法体/静默跳过作为正常实现）。（task ses_fec87a092ffemPHOF1ytvHVQ1N，证据见 Closure 段）
- [x] 若本 plan 落有修复代码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth --severity high` 0 NEW（无修复代码则显式记录 N/A）。（Total=1 为 pre-existing `OAuthLoginServiceImpl:231`（git status 证明本 plan 未触碰该文件）= 0 NEW；graphql-core Total=0；closure audit 复跑同结果）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。（closure audit 复跑：0 errors / 25894 refs）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。（closure audit 期间 1/1 passed；终态复跑见 Closure 段）
- [x] checkstyle / 代码规范检查通过（受影响模块，若落有修复代码）。（全部 mvn run BUILD SUCCESS；导入分组规范遵循 io.nop.* → jakarta → third-party → java*）

## Deferred But Adjudicated

（与 `adjudication.md` 同步；confirmed live defect 不在此处——P1×2（D2-F1/D3-F1）已 fixed。完整清单见裁决表 deferred 段（20 条 watch-only/optimization），此处登记 plan 级要点。）

### A2-followup-1（successor-A：MFA 敏感数据治理族）

- Classification: `out-of-scope improvement`（行为变更需独立 plan，plan-first——权限/认证模型面）
- Why Not Blocking Closure: 覆盖的 D5-F1/D6-1/D3-F3/D1-7/D1-1/D3-F2/D1-3/路由项 3 均为 P2/P3——通用 CRUD 通道有 mutation 权限门禁（默认未授普通角色）兜底、TOTP 无 cap 有 bindToken TTL + 6 位码空间限制、恢复码并发双花窗口极窄且每次使用即 disabled；均非可稳定利用的认证破坏。
- Successor Required: yes
- Successor Path: `ai-dev/backlog/nop-credential-mfa-roadmap.md` A2-followup-1 工作项（已登记，不阻塞 A3-audit）

### A2-followup-2（successor-B：操作级 MFA 补全 + webauthn 管理面裁定落地）

- Classification: `out-of-scope improvement`（行为变更 + 设计 §3.5 再裁定）
- Why Not Blocking Closure: 路由项 1 的 remove 标注为一致性增强（operation-mfa 缺省 false + 自身多重防护在位）；D2-F2 的 scene 校验缺口被一期设计裁定（§3.5 零改动）+ 探查证据（挑战 token 仅返回持会话者 + 仍需第二因子）双重缓解；add-key 端点是能力恢复（UX）非安全缺口；D5-F3 是构建期加固（误标当前无实例）。
- Successor Required: yes
- Successor Path: `ai-dev/backlog/nop-credential-mfa-roadmap.md` A2-followup-2 工作项（已登记，不阻塞 A3-audit）

### watch-only residual 群（详见 adjudication.md deferred 段）

- Classification: `watch-only residual`（16 条）+ `optimization deferred`（4 条）
- Why Not Blocking Closure: 逐条附 Why Not Blocking（裁决表）；共性为 fail-closed 方向偏差、快照语义可辩护、或可利用性≈0（需先攻破前置因子）。
- Successor Required: no（演进义务型登记：D1-5 level-4 因子引入时补防降级 / D4-5 auth 层 fragment-tolerant 演进时复核 / D4-4 两副本同步义务）

## Non-Blocking Follow-ups

（Phase 3 裁决表产出后汇总登记；confirmed live defect 不得出现在这里。）

- pre-existing 测试基建 flake 仅 plexus javac 进程内 CME（`-Dmaven.compiler.fork=true` 绕过；本 plan 收口验证期间命中一次、复跑全绿）——missing-tenant-id 顺序污染已于 2026-08-17 根因修复退役。
- 工作区遗留 `nop-format/nop-ooxml/nop-ooxml-xlsx/samples/generated-pie-chart/_rels/.rels` 无关变更（xlsx 样本 CRLF，非本 plan 产物）——提交时排除（fix-verification V-F4）。

## Closure

Status Note: 三 Phase 全部执行完毕且独立 closure audit PASS——七维度对抗审计证据可复核、39 findings 裁决零悬挂、P1×2 已修复并有 focused 测试与独立复核、3 项路由 deferred 全部终局裁定、owner docs/设计/roadmap 同步完成、全部验证门禁绿。successor（A2-followup-1/-2）已登记 roadmap 且不阻塞 A3-audit。
Completed: 2026-08-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit fresh subagent（task ses_fec87a092ffemPHOF1ytvHVQ1N；与七维度探查/修复复核子 agent 均不同 session）
- Evidence:
  - **每条 Exit Criterion**：P1-EC1~3 / P2-EC1~6 / P3-EC1~5 全 PASS（closure audit verdict 表：charter 12+ 锚点抽查对号；七报告探针结论显式无静默跳过；裁决表 39/39 唯一归属；3 路由项终局；doc 同步五处 + 设计 §十；log 三条目）。
  - **每条 Closure Gate**：G1-G11 全 PASS（见上方勾选，含 closure audit 现场复跑工具：check-doc-links 0 errors/25894 refs、scan-hollow 0 NEW（1 条 pre-existing 非本 plan 触碰）、CasRace 4/4 green、D3-F1 测试 1/1 green）。
  - **Anti-Hollow**：D2-F1 修复在 RedisSmsCodeStore.java:91-96 与 RedisEmailCodeStore.java:90-95 双双在位（`if (!nosql.removeIfMatch(...)) return EXPIRED`）；D3-F1 三边界（unbindMfa :710 / resetUserMfa :976 / verifyRecoveryCodeAndComplete :673）均调 deleteWebauthnCredentials（deleteByQuery 物理 DELETE）；git diff 全量复核——每 hunk 均为功能性变更或 javadoc 修正，无空方法体/静默 no-op。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`**：closure audit 期间 1/1 passed（终态见下方复跑记录）。
  - **Deferred 分类检查**：Deferred But Adjudicated / Non-Blocking Follow-ups 无 P0/P1（P1×2 在 fixed）；successor 两项有 roadmap 路径且标注不阻塞 A3。
  - **诚实性记录**（closure audit 发现、已回修）：findings 计数 40→39（P3×31→30，含 D1-2 并入 D2-F2 合并行口径）；NopAuthUserBizModel 标注锚点因本 plan 修复行号平移（post-fix 锚点已在 fix-verification.md 记录）。

Follow-up:

- A2-followup-1（MFA 敏感数据治理族）+ A2-followup-2（操作级 MFA 补全 + webauthn 管理面裁定落地）——roadmap 已登记，不阻塞 A3-audit。
- pre-existing 基建项：plexus javac 进程内 CME flake（`-Dmaven.compiler.fork=true` 绕过）；`nop-auth-sso OAuthLoginServiceImpl:231` UnsupportedOperationException（hollow scan pre-existing 记录，非本 plan 引入）。
- 无其他 plan-owned 遗留工作。
