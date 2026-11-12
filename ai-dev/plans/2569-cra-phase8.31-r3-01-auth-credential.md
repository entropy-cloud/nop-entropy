# 2569 CRA Phase 8.31 / R3-01 — 5.1 auth 凭据批（MA-02/MA-03/MA-04/I-01/I-02/I-06 + trusted-device 第七面）

> Plan Status: completed
> Last Reviewed: 2026-11-13
> Draft Review R2: agent_334804ac fresh session——可进入实施（0B/0M/4m 文字收紧随实施落）；R1 1B+5M+6m 全部 live 核验到位
> Draft Review R1: agent_d7d95f65 fresh session——1 Blocker（W7 Timestamp/long 谓词失配）+5 Major（W6 红证②不可红/W4 归一层歧义/W6 红证①确定性/W4 补偿 seam/W3 挂起形态）+6 Minor 全部修订并入；12 项 baseline 引用核对全部成立
> Source: `ai-dev/audits/2026-10/2026-10-21-1925-deep-audit-nop-auth-credential-concurrency/`（5.1 维度报告 + merge-adjudication §六 工单台账）；`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md:101`（R3-01 批定义）；plan 2502（trusted-device 第七面 Deferred 移交本批）
> Related: roadmap Work Item 8.31；plan 2568（8.73 同域先行，IResourceLockManager 注入先例）

## Protected Area 授权

- 触碰 `nop-auth/nop-auth-service`、`nop-auth/nop-auth-sso`（PA=nop-auth，ask-first）+ `nop-credential/nop-credential-service`（非 PA）。
- **授权来源**：同 plan 2568——用户 2026-11-13 goal「执行…直到彻底完成」（2026-11-01 blocked 登记后下达，构成 ask-first 显式完成指令）；留痕于 8.73 先例与 daily log。
- 不触认证语义/权限模型（并发纪律 + 请求超时 + 交付语义，登录/授权判定流程不变）；不触 `nop-auth/model/nop-auth.orm.xml`（无 version 列新增面）；`nop-auth-api`/`nop-credential-api` 零触碰（错误码常量已有，无新增 SPI 面）。

## Purpose

收口 R3-01 六工单 + trusted-device 第七面：①Redis TTL 附着族（锁号计数键无 TTL 永久自锁）；②JWKS 锁内 HTTP（复核确认已随 plan 2551 落地，台账 cross-ref 关闭）；③外呼无请求级超时；④OAuth 刷新半个成功（invalid_grant 归一 + 补偿持久化）；⑤SSO 登出半成功（IdP 外呼纳入 waitAll）；⑥可信设备 max-count check-then-insert 越界 + update-on-deleted 500；⑦trusted-device 过期行清扫第七面（LedgerCleaner 六族→七族）。

## Current Baseline（live 核查 2026-11-13）

- **MA-03 已随 plan 2551 落地（复核事实）**：`JWKPublicKeyLocator` 现形态=单飞共享（:61/:97-104）+ 显式超时（:55/:109-112/:173-174 `request.setTimeout`）+ 失败 grace 且 `lastRequestTime` 仅成功后推进（:100-115，C2-002 修复面同批在场）。本批不重复修复，台账 cross-ref 关闭（沿 8.53 C3-002 already-landed 先例）。
- **MA-02 在位**：`RedisLoginAttemptStore` :59-63（set 对写）/:71-79（increment `v==1` 才 setTtl）；`RedisSendCodeRateLimiter` :87-89（counterKey）/:105-106（ipKey）；`RedisMfaChallengeStore` :149-153；`RedisSmsCodeStore` :104-107；`RedisEmailCodeStore` :103-106。INCRBY 成功 + PEXPIRE 抛错（syncGet 上抛）→ 键无 TTL 永不清理（锁号计数键=人工 DEL 才能解锁）。测试基建 `FakeNosqlService`（`counterTtl` map 已追踪 TTL，可扩展注入 setTimeoutAsync 故障）。
- **I-01 在位**：`OAuthTokenClient.fetchToken`（nop-credential-service :81-91 区域）`HttpRequest` 未设 setTimeout；JWKS 站已随 MA-03 落地（:174）。`VaultCredentialKeyProvider.fetchMaterial` D3-05 显式 10s 先例。
- **I-02 在位**：`CredentialProviderImpl.refreshIfNearingExpiry`（:400-475 区域）锁内 HTTP 刷新 + merged 回写同事务；`invalid_grant` 现归一为 `ERR_CREDENTIAL_OAUTH_REFRESH_FAILED`（:447-450），未映射 `ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED`（错误码已存在于 CredentialErrors.java:176）。`TestCredentialOAuthRefresh.refreshFailureFailsClosed`（:254-268）钉死旧契约（REFRESH_FAILED + data 不变），stub HTTP server 基建完备（refreshResponseBody/refreshResponseStatus 可编程）。
- **I-06 在位**：`OAuthLoginServiceImpl.doLogout`（nop-auth-sso :200-214）：thenCompose 派生的 IdP 登出外呼 future 被丢弃，`waitAll(future, promise)` 只等 promise。
- **MA-04 在位**：`MfaTrustedDeviceManager.register` :151-167 max-count check-then-insert 无锁（并发不同 hash 双双越过 → max+1 行）；`isExempted` :112-123 `updateEntityDirectly` update-on-deleted → `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` 500；`removeAllForUser` :241-250 同构 TOCTOU。
- **第七面在位**：`NopAuthLedgerCleaner.purgeExpiredLedgerRows`（nop-auth-service cleanup :187-210）仅六族；`NopAuthMfaTrustedDevice` 无清扫通道（仅人工 removeBySid）。测试基建 `TestAuthLedgerRetention_MA_01` 在位。
- 平台设施：`IResourceLockManager.callWithLock`（plan 2568 先例：实例级 holderId + beans ioc:default 缺省实现）。

## Goals

- W1：Redis TTL 附着族 7 站点收敛——setTtl 失败补偿删除（key 不得残留无 TTL 形态）+ ERROR 日志；瞬时故障恢复后下次递增重设 TTL（无永久毒键）。
- W2：MA-03 台账 cross-ref 关闭（Proof，零代码）。
- W3：`OAuthTokenClient` 请求级超时（`nop.credential.oauth.request-timeout-millis`，缺省 10s，≤0=客户端缺省）。
- W4：invalid_grant → `ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED` 归一；刷新成功但持久化失败 → 单次补偿持久化（`engineUpdateTokenFields` 通道）+ 结果留痕；javadoc 声明轮换窗口；C10-007 反向钉同 diff 改写。
- W5：SSO 登出 IdP 外呼纳入 waitAll 聚合 + 失败 ERROR 日志（半成功可观测）。
- W6：trusted-device register 新行路径 per-user 串行化（`IResourceLockManager`，复用 2568 缺省 bean 形态）；`isExempted` lastUsedAt 改条件 UPDATE（update-on-deleted 500 消除）；removeAllForUser/豁免时点毫秒窗语义 owner doc 声明。
- W7：`NopAuthLedgerCleaner` 增第七族通道（NopAuthMfaTrustedDevice 过期行，expireAt 锚，复用保留期/批量配置）。

## Non-Goals

- 不触 nop-nosql 平台设施（INosqlService/INosqlCounter 原语零改动——audit 声明约束）。
- 不做nop-auth-sso OAuthLoginServiceImpl 三处 fetchAsync 超时（audit F3 已核清：不在行锁内，核清级）。
- 不加 `NopAuthMfaTrustedDevice` version 列（C2-005 以条件 UPDATE 收口，无 ORM 模型变更）。
- 不改 `nop-auth-sso` 登录/授权判定语义；不动 `nop-auth-graphql`/`nop-auth-web`。
- C2-005 子面 (a)（因子撤销后毫秒~秒窗在途登录仍豁免）：owner doc 声明时点语义，不做豁免二次确认（audit 裁定可选，声明路径优先）。

## Scope

### In Scope

- `nop-auth/nop-auth-service`：login/RedisLoginAttemptStore、ratelimit/RedisSendCodeRateLimiter、mfa/store/RedisMfaChallengeStore、mfa/store/RedisSmsCodeStore、mfa/store/RedisEmailCodeStore、mfa/MfaTrustedDeviceManager、cleanup/NopAuthLedgerCleaner
- `nop-auth/nop-auth-sso`：login/OAuthLoginServiceImpl
- `nop-credential/nop-credential-service`：oauth/OAuthTokenClient、CredentialProviderImpl
- 测试：FakeNosqlService 扩展、TestCredentialOAuthRefresh 改写、新增 trusted-device 并发钉、LedgerCleaner 第七族用例、SSO 登出聚合钉
- owner doc：`docs-for-ai/03-modules/nop-credential.md`、`docs-for-ai/03-modules/nop-auth.md`、`docs-for-ai/03-modules/nop-sys.md`（若 cleaner 契约变化——预计仅注释级，零契约变更则显式声明）

### Out Of Scope

- `nop-auth-api`/`nop-credential-api`/`nop-auth-core` 零触碰
- nop-nosql / nop-http-api 零触碰

## Execution Plan

> 串行实施顺序：W1 → W2 → W3 → W4 → W5 → W6 → W7（W2 零代码）。

### Phase 1 - W1 Redis TTL 附着族（5.1-MA-02，P1）

Status: completed
Targets: 上述 5 个 Redis store/limiter + `FakeNosqlService` + 新增 `TestRedisTtlAttachment`

- Item Types: `Fix`

- [x] 红证（R1-Minor-8/9 形态钉）：`TestRedisTtlAttachment` 置于 `io.nop.auth.service.mfa.store` 包（同包访问 fake 的 package-private `counterTtl`）；`FakeNosqlService` 增故障注入 flag（setTimeoutAsync 首呼抛错）；旧代码行为=increment 首呼 setTtl 抛错**从 incrementLoginFailCount 异常传播**（测试 catch 后断言）+ 键残留无 TTL → 第二次 increment v=2 不再 setTtl（毒键）→ 新契约「恢复后键必须带 TTL」断言 RED。补偿删除后计数丢失可接受（调用方 LoginServiceImpl 登录失败路径，登录本已失败，下次重试 v=1 重设 TTL）。
- [x] 绿证：同场景修复后——setTtl 失败 → 补偿删除键 + ERROR 日志 → 下次 increment 重设 TTL 成功（`counterTtl` 在位）；正常路径（无故障）行为零变化。
- [x] 7 站点统一形态落地（每站 `setTtl` 调用点失败补偿：`nosql.remove(key)` + ERROR 日志后原样上抛）：RedisLoginAttemptStore×2、RedisSendCodeRateLimiter×2、RedisMfaChallengeStore、RedisSmsCodeStore、RedisEmailCodeStore。
- [x] 变异探针：摘除补偿删除 → 红 tests 必回红；恢复 grep 验证。

Exit Criteria:

- [x] 红→绿留证 + 变异探针回红留证
- [x] 7 站点 grep 全量核清（`setTimeoutAsync` 调用点清单 = 修复点清单，F3 失效面零遗漏）
- [x] 既有 Redis store 测试（TestRedisStoreWiring/TestRedisCodeStoreCasRace/TestRedisEmailCodeStore/TestRedisStorePeekRealCodecRoundTrip）零回退
- [x] owner doc：`nop-auth.md` 相应节补一句补偿删除语义（失败面声明）；`ai-dev/logs/` 条目更新

### Phase 2 - W2 MA-03 cross-ref 关闭 + W3 OAuthTokenClient 超时（5.1-I-01）

Status: completed
Targets: `OAuthTokenClient.java` + 台账

- Item Types: `Proof`（W2）/ `Fix`（W3）

- [x] W2：MA-03/I-01 JWKS 站复核结论写入 plan + cra-index cross-ref（修复计数归 R4-17/plan 2551，发现行保留防双计）。
- [x] W3 红证（R1-Major-6 形态钉）：stub server 挂起响应；测试以 deadline 守护驱动（`Future.get(timeout)` / `assertTimeoutPreemptively`，防挂死构建）+ 实测旧代码实际失败形态留证（平台 client 若有部署级缺省读超时则旧代码 10-30s 后抛错——红证改为断言「新配置生效时失败时延 ≤ 配置值且错误可归因」与旧代码时延基线对比）；测试配置键取小值（如 500ms）驱动新路径。
- [x] W3 实施：`fetchToken` 设 `request.setTimeout(timeoutMillis)`；配置键 `nop.credential.oauth.request-timeout-millis`（缺省 10_000；≤0 = 客户端缺省不设）；错误信息英文；javadoc。
- [x] 绿证：挂起场景在超时配置下按时抛 `ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED`（deadline 断言）；正常路径回归零变化（既有 TestCredentialOAuthRefresh 全绿）。

Exit Criteria:

- [x] W2 cross-ref 落 cra-index（P2-G73 成员注记）
- [x] 红→绿留证；既有 credential 测试零回退
- [x] owner doc：`nop-credential.md` 新配置键 `nop.credential.oauth.request-timeout-millis` 落账；`ai-dev/logs/` 条目更新

### Phase 3 - W4 OAuth 刷新半个成功（5.1-I-02）+ C10-007 前置改写

Status: completed
Targets: `CredentialProviderImpl.java` + `TestCredentialOAuthRefresh.java`

- Item Types: `Fix`

- [x] **C10-007 同 diff 前置（先改测试）**：`refreshFailureFailsClosed` 拆两用例——①invalid_grant（stub 返回 `{"error":"invalid_grant"}`）→ 新契约 `ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED`；②暂态失败（5xx）→ `ERR_CREDENTIAL_OAUTH_REFRESH_FAILED` 且 DB data 不变（旧 fail-closed 契约保留面）。①在旧代码上 RED。
- [x] invalid_grant 归一（R1-Major-3 层面钉）：分型点在 `OAuthTokenClient.fetchToken` error 分支（:117-124，类型化 `token.getError()`）——新增模块内常量 `ARG_OAUTH_ERROR`（CredentialErrors，非 api 模块零 API 触碰）携带原始 error 码；`CredentialProviderImpl` catch 点按该 param 精确匹配 `invalid_grant` → 映射 `ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED`（保留 ARG_CREDENTIAL_ID）；传输异常/其他错误维持 REFRESH_FAILED。禁止在 ProviderImpl 层做合并字符串 contains 匹配（errorDescription 巧合误判面）。
- [x] 补偿通道：refreshIfNearingExpiry 内 capture holder——updater 成功合并后记录 token 字段子集；`engineUpdateInLock` 抛错且 holder 在位 → 单次 `engineUpdateTokenFields` 补偿持久化 + WARN（成功）/ERROR（补偿也失败）；补偿后原异常仍上抛（fail-closed 语义不放宽）。补偿通道 focused 用例（直接驱动补偿路径，断言 DB data 更新）。
- [x] javadoc：轮换窗口声明（刷新成功与持久化提交之间的窗口语义 + 补偿边界）。
- [x] 变异探针：摘除 invalid_grant 映射 → ①回红。

Exit Criteria:

- [x] ①RED→GREEN、②绿证保持、补偿用例 GREEN、变异探针回红留证
- [x] 既有 TestCredentialOAuthRefresh 其余用例 + TestCredentialProviderImpl 零回退
- [x] owner doc `nop-credential.md` 刷新契约节落账（含轮换窗口 + 错误码语义）
- [x] `ai-dev/logs/` 条目更新

### Phase 4 - W5 SSO 登出半成功（5.1-I-06）

Status: completed
Targets: `OAuthLoginServiceImpl.java` + sso 测试

- Item Types: `Fix`

- [x] 红证（R1-Minor-11 harness 钉）：纯 JVM 直构 `OAuthLoginServiceImpl`——stub httpClient（fetchAsync 返回失败 future）+ `userContextCache` stub（doGetUserContext 返回测试上下文，防 NPE）+ loginSessionStore stub；旧代码 doLogout 对 IdP 外呼失败无感知（正常完成）→ 新契约「IdP 外呼纳入聚合（失败传播，`FutureHelper.waitAll`=allOf 任一失败整体失败）」断言 RED。先例 `TestSsoRefreshAsyncChain`（protected 字段同包替换）。
- [x] 实施：派生 IdP 登出 stage 纳入 `waitAll` 聚合 + `whenComplete` ERROR 日志（`nop.err.auth.sso-logout-idp-fail`）；SSO_CALLBACK 分支不变。
- [x] 绿证：IdP 成功路径登出行为零变化（既有 sso 测试零回退）；失败路径可观测。

Exit Criteria:

- [x] 红→绿留证；既有 sso 测试零回退
- [x] owner doc：`nop-auth.md` SSO 登出节补聚合失败语义（对外行为变化声明 + **R2-m4 重试幂等论据**：客户端重试登出时 parseAuthToken 对已吊销 token 返回 null → success，幂等无害）；`ai-dev/logs/` 条目更新

### Phase 5 - W6 trusted-device 并发纪律（5.1-MA-04）+ W7 第七面清扫

Status: completed
Targets: `MfaTrustedDeviceManager.java`、`NopAuthLedgerCleaner.java` + trusted-device/cleanup 测试

- Item Types: `Fix`

- [x] W6 红证①（R1-Major-4 确定性钉）：max-count=1 + 已有 1 未过期行，双线程 start-barrier 并发 register 不同 deviceHash 新行；**deadline 内循环多批次**，断言「出现 max+1 行」即红成立（单批次红为概率性，循环收敛）；变异探针同口径。绿证=修复后全部批次行数 ≤ max-count。并发钉无 sleep（start barrier + deadline await）。
- [x] W6 红证②（R1-Major-2 形态钉）：单线程「删除后再调」在旧代码不红（findByHash null 早退）——红证改双线程竞态形态：deadline 内循环并发「isExempted 命中 vs 并发 removeBySid 删除」批次，断言旧代码出现 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`（概率性红，出现即证）；绿证=新条件 UPDATE 形态同并发批次零异常 + affected=0 静默 false（确定性）。
- [x] W6 实施：register 新行路径 per-user 串行化（`IResourceLockManager.callWithLock("auth:mfa-trusted-device:" + userId, 实例级 holderId, reason, ...)`，2568 bean 形态复用——`nopAuthSessionFlushLockManager` 同类型可共用或新增 ioc:default bean）；isExempted lastUsedAt 改 EQL 条件 UPDATE（`set lastUsedAt where sid=? and expireAt > now`，affected=0 即过期/已删，静默返回 false 语义保持）。**既有测试接线面**：手工构造点补注入（`TestTrustedDeviceE2E` setField 形态）；容器注入点依赖 ioc:default bean 自动解析。
- [x] W6 绿证：①串行化后行数 ≤ max-count（同 hash upsert 与新行两形态均钉）；②deleted 行返回 false；既有 TestTrustedDeviceE2E/TestMfaRestrictedLoginE2E 零回退。
- [x] W7：`purgeExpiredLedgerRows` 增第七族通道——**R1 Blocker-1 类型钉**：`NopAuthMfaTrustedDevice.expireAt` 为 `java.sql.Timestamp` 列（`_NopAuthMfaTrustedDevice` DATETIME 语义），与五族 epoch-millis-BIGINT 不同 → 新增 Timestamp 变体通道（谓词绑定 `new Timestamp(cutoffMillis)`，同 `purgeExpiredSessions` :218-219 先例），不按字面复用 long 绑定的 `purgeExpiredByExpireAt`；类注释六族→七族；用例（TestAuthLedgerRetention_MA_01 扩展：插入过期 trusted-device 行 → purge → 行删除 + 未过期行保留）。
- [x] 变异探针（抽一链）：摘除 per-user 锁 → 红证①回红。

Exit Criteria:

- [x] 红①②→绿 + 变异探针回红留证；W7 用例 GREEN
- [x] 既有 trusted-device/ledger 测试零回退
- [x] owner doc `nop-auth.md` trusted-device 节补并发纪律 + 清扫通道 + 时点毫秒窗语义声明
- [x] `ai-dev/logs/` 条目更新

### Phase 6 - 验证与收口

Status: completed
Targets: 本 plan + roadmap + cra-index + owner docs

- Item Types: `Proof` / `Follow-up`

- [x] `./mvnw test -pl nop-auth/nop-auth-service -am` + `./mvnw test -pl nop-auth/nop-auth-sso -am` + `./mvnw test -pl nop-credential/nop-credential-service -am`（上游链已知面 solo 归因口径沿 2568 先例；记录测试签名）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth/nop-auth-service --severity high` EXIT=0（同 `--module nop-credential/nop-credential-service`）
- [x] owner docs 落账 + `node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0
- [x] roadmap 8.31 → done；cra-index：5.1-MA-02 → fixed、MA-03/I-01(JWKS 站) → cross-ref(2551)、I-01(credential 站)/I-02/I-06/MA-04 → fixed、G73 组 → 全 fixed；trusted-device 第七面 → fixed（9.1 豁免登记解除）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` EXIT=0
- [x] 独立 fresh-session closure audit（evidence 写入 Closure 段）
- [x] git 提交（单 commit）

Exit Criteria:

- [x] 全部验证命令留证
- [x] closure audit APPROVED（或 APPROVED-with-corrections 全闭环）
- [x] roadmap / cra-index / logs 一致

## Closure Gates

- [x] 6 工单 + 第七面逐项落地且各有红绿或 Proof 留证（W2=Proof cross-ref）
- [x] F3 失效面处置：MA-02 七站点全修；I-01 两站（credential 站修 + JWKS 站 cross-ref）；I-02 同族清单核清无第二站点；I-06 单点；MA-04 register 三路径 + 同构兄弟核清；C2-005 两子面（②修 / ①声明）
- [x] C10-007 反向钉同 diff 前置履行（改写在先，修复在后，同 commit 可证）
- [x] 无 in-scope live defect 被降级 deferred
- [x] owner docs 与 live baseline 一致
- [x] 独立 closure audit 完成并写入 evidence
- [x] Anti-Hollow：补偿通道/串行化锁/清扫通道 live 连通（focused 用例 + 代码链核对），无空方法体/静默吞异常
- [x] 三模块 `-am` 链验证（已知面 solo 归因口径）
- [x] 代码规范：imports 分组、4 空格缩进、错误信息英文

## Deferred But Adjudicated

### C2-005 子面 (a)：因子撤销后在途登录豁免毫秒~秒窗

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit 裁定「豁免二次确认/文档声明时点语义」二选一；窗口为毫秒~秒级且撤销矩阵其余四面闭合；时点语义落 owner doc
- Successor Required: no

### OAuthLoginServiceImpl 三处 fetchAsync 无请求级超时

- Classification: `watch-only residual`
- Why Not Blocking Closure: audit F3 已核清（不在行锁/类锁内，客户端缺省超时兜底）；I-01 立案面仅为锁内 I/O
- Successor Required: no

## Closure

Status Note: R3-01 六工单 + trusted-device 第七面全收口：W1 Redis TTL 补偿删除（7 站点统一通道）/W2 MA-03 cross-ref（2551 already-landed）/W3 令牌端点请求级超时/W4 invalid_grant→REAUTH_REQUIRED 归一 + 轮换窗口补偿通道 + C10-007 反向钉同 diff 改写/W5 SSO 登出 IdP 聚合（实施期深化：原顺序 IdP 登出从未发出）/W6 trusted-device per-user 锁 + 条件 UPDATE/W7 账本清扫第七族。roadmap 至此全部批次 done，campaign 终态闭合。
Completed: 2026-11-13

Closure Audit Evidence:

- Reviewer / Agent: agent_b57cd035（独立 fresh-session subagent）
- Evidence:
  - W1–W7 live 行为级核对全 PASS（审计报告逐项：guard 7 站点 grep 零旁路/归一无 contains 残留/补偿链+锁链+清扫链连通/beans 显式 ref）
  - 实跑：TestCredentialOAuthRefresh 19 绿 + TestSsoLogoutIdpAggregation 2 绿（audit 现场）+ leaf 模块全量 auth-service 482（2 flake solo 归因）/sso 24 绿/credential 219 绿（_tmp/2569-leaf-*.log）
  - -am 链：三链上游唯一红面 = nop-core TestResourceCacheInvalidationChain（9.1 已登记并行负载敏感面，solo 4/4 绿归因，零 diff——nop-core 本批未触碰）
  - Anti-Hollow：双模块 scan-hollow EXIT=0（0 发现）；无空方法体/静默吞异常
  - Deferred 项分类检查：PASS（C2-005① 与 fetchAsync 超时两项 watch-only 理由成立）
  - 文本一致性：m-1 logs 条目/m-2 nop-auth.md:108 旧声明/m-3 plan 回填——三项随本收口全部闭环
  - red-green 留证：_tmp/red-green-ledger-2569.md（W1 mutation poison 2 红→绿 3/3；W4 归一 mutation 红→绿 19；W5 红→绿 2；W6 结构钉 mutation 红→绿 3；W7 契约改写用例绿）

## Non-Blocking Follow-ups

- （无——实施期新发现按 roadmap 通道登记）
