# 2568 CRA Phase 8.73 / R4-33 — DaoUserContextCache 条件 UPDATE + 刷新单飞（2.3-C1-010 移交接收轨）

> Plan Status: completed
> Last Reviewed: 2026-11-13
> Draft Review: R1 adversarial review 2026-11-13（agent_051e7b46 fresh session）：1 Blocker（AT 基线取值自洽性）+5 Major（退化路径 logout 守卫/EQL 属性名/callWithLock 签名与测试接线/红① 独立会话结构/affected=0 行为变更裁定）+4 Minor 全部修订并入本文件；修订后按加速指导不再追加 R2 轮（单工单、单文件、修订全部机械性落点）
> Source: `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/C1-round2.md`（finding 2.3-C1-010，P2）；`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md:148`（R4-33 移交接收轨批，1 项）
> Related: roadmap `ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` Work Item 8.73；plan 2502（trusted-device 第七面另归 8.31，不在本 plan）

## Protected Area 授权

- 触碰 `nop-auth/nop-auth-service`（PA=nop-auth，ask-first）。
- **授权来源**：用户 2026-11-13 goal 指令「执行这个分支下的 concurrency roadmap 直到彻底完成」——在 2026-11-01 blocked 登记（AskUserQuestion 未获回复）之后下达，构成对本批 ask-first 事项的显式完成指令。本 plan 不改认证语义/权限模型，仅并发正确性；不触碰 `nop-auth/model/nop-auth.orm.xml`（无 ORM plan-first 面）。
- 2026-11-13 AskUserQuestion 复询未获回复后按 goal 指令执行，此决定已在此留痕。

## Purpose

收口 finding 2.3-C1-010：DaoUserContextCache 并发 flush 对 NopAuthSession 行的 DB 层丢失更新（过期副本回写旧 refresh_token，令牌旋转被回滚 → SSO 部署下刷新链断裂 → 再登录风暴）。修复形态 = 条件 UPDATE（乐观条件）+ per-session 刷新单飞 + clearDirty 移至落库成功后 + flush 失败补日志。

## Current Baseline

- live 核查（2026-11-13）：`DaoUserContextCache.saveUserContextAsync`（`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:111-146`）仍为「getEntityById → 实体字段改写 → `clearDirty()`@:144」的读-改-写形态，NopAuthSession 实体无 version 列 → 缺陷原样在位。
- `getUserContextAsync`（:48-85）每请求新建 `UserContextImpl` 副本（:66），副本 :78-79 携带读时 refreshToken/accessToken；但 :81 `setLastAccessTime(now)` 是本请求时刻、**不是** DB 行读时值 → 乐观基线必须显式从 :52 加载实体另行捕获（RT 取 :79 读时值，AT 取 `session.getLastAccessTime()` DB 行值），当前未记录。
- `checkExpired`（:87-107）读路径副作用写（:101-102 setLogoutType/setLogoutTime）为本 finding F3②，修复随本批条件 UPDATE 语义一并收敛（logout 写改为显式持久化路径）。
- 平台先例：`DbMfaChallengeStore.markVerified`（同模块，`mfa/store/DbMfaChallengeStore.java:153-164`）条件 UPDATE + affected-row 判定；`IResourceLockManager.callWithLock`（nop-commons io.nop.commons.concurrent.lock.IResourceLockManager）为平台并发设施。
- 测试基建：`TestDaoSessionStoreAndUserContextCache`（同模块 test）已具备 H2 + 真实 orm 栈（`buildH2Stack`/`wireRealBeans`），可直接扩展红绿钉。
- `AuthHttpServerFilter.handleUserContext`（nop-biz-auth-core，`loginService.flushUserContextAsync(userContext)` 返回值被丢弃，约 :432）→ flush 失败必须日志自证，不可依赖调用方。
- cra-index P2-G41 行：本项 修复归属 nop-auth-service 接收单元，CRA 台账保留发现行（防双计注记维持）。

## Goals

- 过期副本 flush 不再回滚已旋转的 refresh_token/accessToken（丢失更新根面闭合）。
- flush 与 logout/删除竞态时安全放弃（affected-row=0 → WARN 日志 + 放弃写入）。
- 同 session flush 串行化（平台 `IResourceLockManager` per-session 单飞），同 JVM 并发 flush 有全序。
- `clearDirty()` 仅在落库成功后执行；flush 异常有 ERROR 日志（`AuthHttpServerFilter` 丢弃返回值下失败可观测）。
- F3②（checkExpired 读路径副作用写）随批收敛：过期判定不再隐式依赖实体改写落库。

## Non-Goals

- 不改认证/授权语义（登录、登出、token 旋转流程的对外行为不变）。
- 不触碰 `nop-auth/model/nop-auth.orm.xml`、不加 version 列、不加新表。
- 不修 F3③（`AbstractUserContextCache.saveUserContextAsync` Local 部署 clearDirty 先于 put —— Local 无 DB 落库失败窗，2.3-C1-001 同批已裁定观察面，维持 watch-only）。
- 不做跨 JVM 分布式互斥（条件 UPDATE 本身即为跨副本乐观护栏）。
- F3⑤（getUserContextAsync 每请求新建副本的内存面）与 F3⑥（LocalUserContextCache 死重内存）维持 C5 观察面不立项。

## Scope

### In Scope

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java`（单文件主体）
- 新增/扩展测试：`TestDaoSessionStoreAndUserContextCache`（红绿钉）或同类内新用例
- owner doc：`docs-for-ai/03-modules/nop-auth.md` flush 契约节落账
- roadmap 8.73 状态行 + cra-index P2-G41 行状态更新

### Out Of Scope

- nop-biz-auth-core（AuthHttpServerFilter / AbstractUserContextCache / UserContextImpl）零改动
- nop-auth-api / nop-auth-rest / nop-auth-web 零改动

## Execution Plan

### Phase 1 - 红证测试（先红后绿）

Status: completed
Targets: `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestDaoSessionStoreAndUserContextCache.java`

- Item Types: `Fix`（回归钉）

- [x] 红① 「过期副本回滚旋转 token」：三段**独立 orm 会话**（`ormTemplate.runInSession` 各自包裹；@SingleSession 在最小 H2 栈不自建会话）——段1 load 副本 A（基线 RT0/AT0）；段2 独立会话直写旋转 RT0→RT1；段3 A 再次 save → 断言 DB 行 refreshToken 仍为 RT1（修复前红：A 无条件覆盖回 RT0）。旋转与 A 的 save 严禁共享会话（否则一级缓存致红静默消失）。确定性红，H2 栈。
- [x] 绿① 「logout 后 save 放弃」：会话 logout 后 save → DB 行不被复活/改写（现行为即绿，钉为契约）。
- [x] 绿② 「正常 save 全字段落库」：accessToken/refreshToken/cacheData/lastAccessTime 全部落库 + clearDirty 生效（防条件 UPDATE 改形引入回归；AT 条件必须用 DB 行读时值——见 Phase 2 基线定义）。
- [x] 白名单钉「子类基线字段不进 cacheData」：serialize cacheData 不含基线字段键（防基线泄漏进 DB 行）。
- [x] 运行确认红①在未修复代码上 RED、绿①② 与白名单钉 GREEN，留证（`_tmp/red-green-ledger-2568.md`：红①红④ expected:<rt-one-rotated> but was:<rt-zero-baseline>；绿①②+白名单钉 GREEN）。

Exit Criteria:

- [x] 红① 在修复前 RED（丢失更新可复现），输出留证
- [x] 绿①② + 白名单钉 GREEN 确认既有契约（R2-Minor-2）
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 3 收口统一落）

### Phase 2 - 实施（条件 UPDATE + 刷新单飞 + clearDirty 后置）

Status: completed
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java`

- Item Types: `Fix`

- [x] 副本基线捕获：`getUserContextAsync` 建副本时（:66 处改为 `new DaoUserContext(...)` 子类）显式捕获**读时实体 DB 行值**基线：refreshToken 取 :79 读时值、lastAccessTime 取 `session.getLastAccessTime()`（:52 实体的 DB 行值——**严禁**取 :81 之后的副本 lastAccessTime，那是本请求时刻，用作条件会导致全量 affected=0 持久化瘫痪，R1 Blocker-P1）。子类字段为普通 Java 字段，`serializeToJson` 白名单不含 → 不进 cacheData；:66 接线改造由红①端到端覆盖 + 白名单钉防回归。基线缺失（登录路径等非本 cache 创建的基类 `UserContextImpl`）时退化：UPDATE 仍带 `sessionId=?`（主键）与 `logoutType=LOGOUT_TYPE_NONE` 条件（R1-P2：**不得**写成无条件 UPDATE，否则 logout 后迟到 flush 复活已登出行，违反绿①契约），无基线乐观条件。
- [x] `saveUserContextAsync` 改造：per-session 单飞——注入 `IResourceLockManager`，`callWithLock("auth:session-flush:" + sessionId, holderId, lockReason, task)`（四参签名：holderId 用实例级标识（构造时生成一次的 UUID/实例哈希——平台先例 `AlertEvaluator` 实例级 `lockHolderId` 形态，同 JVM lock/unlock 配对语义，R2-Minor-3）、lockReason 固定字符串 `"dao-user-context-flush"`；`lock()` 在 futureRun 异步线程内阻塞获取，单 session 串行化即本批裁定语义）内执行 EQL 条件 UPDATE（属性名形态，`DbMfaChallengeStore` 先例）：`update NopAuthSession o set o.accessToken=?, o.refreshToken=?, o.cacheData=?, o.lastAccessTime=? where o.sessionId=? and o.refreshToken=? and o.lastAccessTime=? and o.logoutType=?`（条件值=读时实体 DB 行基线；常量参数绑定 `AuthApiConstants.LOGOUT_TYPE_NONE`，不硬编码字面量）。
- [x] affected-row==0 → 放弃写入 + WARN 日志（含 sessionId；行不存在/已登出/基线失配三可能）。**行为变更裁定（R1-P6，显式接受）**：良性并发下后到 flush（同基线但首 flush 已推进 DB）整体放弃 = 该请求内非 token 字段修改（nickName/roles 等）本次不落库；因副本 request-scoped、下请求重读 DB，丢失窗口为单请求内变更，fail-safe 取向（DB 中 token 权威）优先于 last-writer-wins，在 owner doc 契约节声明。affected>0 → 落库成功后才 `clearDirty()`。
- [x] flush 异常路径：try/catch → ERROR 日志（sessionId + 异常摘要）后原样上抛（`AuthHttpServerFilter` 丢弃返回值，日志为唯一观测面）。
- [x] `checkExpired` F3② 收敛（R2-Minor-1 钉死形态）：过期分支显式 EQL 条件更新 `update NopAuthSession o set o.logoutType=?, o.logoutTime=? where o.sessionId=? and o.logoutType=?`（LOGOUT_TYPE_NONE 条件；`DbMfaChallengeStore` 同构先例）；`logoutType` 判定与 `saveUserContextAsync` 条件语义对齐。
- [x] 测试接线：`wireRealBeans` 反射注入 `IResourceLockManager`（`LocalResourceLockManager` 实例；`@Inject` 字段缺注入即 NPE，禁止可空绕过，R1-P4）。
- [x] 基线捕获无跨请求泄漏（子类字段 per-副本生命周期，无静态/长驻 map——无新增内存泄漏面）。
- [x] 红① 复绿 + 绿①② + 白名单钉零回退；变异探针已执行：摘除基线条件 WHERE 子句 → 红① 回红（expected:<rt-one-rotated> but was:<rt-zero-baseline>），恢复后 grep 确认条件行真实存在（:225）+ 全模块复绿。
- [x] 并发钉：多线程并发 save（≥8 线程同一会话副本族）不抛异常且最终 RT 不回滚（deadline 断言，非 sleep）。

Exit Criteria:

- [x] 红① GREEN、绿①② + 白名单钉零回退、变异探针回红留证
- [x] 并发钉 GREEN（deadline 轮询，无 sleep）
- [x] `clearDirty` 后置与失败日志在 live code 中逐点可指（executeConditionalFlush :affected>0 分支 / catch Throwable ERROR 分支）
- [x] 无静默跳过：affected==0（WARN）/ 异常路径（ERROR + rethrow）均有日志，无空 catch
- [x] `docs-for-ai/03-modules/nop-auth.md` flush 契约节已落账（含 R1-P6 行为变更声明；实施期发现：登录路径同会话 insert 待 flush → affected=0 + 行不存在回退实体改写，已随契约节声明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 验证与收口

Status: completed
Targets: 本 plan + roadmap + cra-index + owner doc

- Item Types: `Proof` / `Follow-up`

- [x] `-am` 链实跑：`./mvnw test -pl nop-auth/nop-auth-service -am` 上游链唯一红面=nop-commons TestPeriodicTaskInsulation（已知面①，campaign 先例 2539/2541 登记；solo 9/9+全模块 460 绿归因，零 diff 面——nop-commons 本批未触碰）+ 叶模块 `./mvnw test -pl nop-auth/nop-auth-service` 全绿 476（两轮）；`./mvnw test -pl nop-service-framework/nop-biz-auth-core` 136 全绿（零触碰模块确认）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-auth/nop-auth-service --severity high` EXIT=0（0 findings；勘误：正确调用形态为嵌套路径，`--module nop-auth-service` 裸模块名 EXIT=2 不成立）
- [x] owner doc `docs-for-ai/03-modules/nop-auth.md` 落账 + `node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0（0 errors；plan 内 1 处路径警告顺手改写）
- [x] roadmap Work Item 8.73 → done（状态行引用本 plan）；cra-index P2-G41 行 → fixed（保留发现行，双登记注记维持；9.1 豁免登记解除）。勘误（closure audit Major-2）：roadmap 闭包声明曾先于审计事实预写 APPROVED，已订正为 APPROVED-with-corrections 实际结论
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` EXIT=0
- [x] 独立 fresh-session closure audit（agent_0b16a91c，不复用实现 session），evidence 写入本文件 Closure 段
- [x] git 提交（单 commit 含代码 + 测试 + 文档 + plan）

Exit Criteria:

- [x] 全部验证命令留证（`_tmp/test-auth-service-2568-am.log`/`_tmp/red-green-ledger-2568.md`；已知情 _tmp 不入库，签名记录于本文件与本 plan）
- [x] closure audit APPROVED-with-corrections（2M+2m 全闭环）
- [x] roadmap / cra-index / logs 一致

## Closure Gates

- [x] 2.3-C1-010 缺陷本体（丢失更新根面）已修复且有确定性红绿钉 + 变异探针留证
- [x] F3 失效面逐条处置登记（①本批修 / ②本批收敛 / ③watch-only 维持裁定 / ④⑤⑥核清或维持观察）
- [x] 无 in-scope live defect 被降级 deferred
- [x] owner doc 与 live baseline 一致
- [x] 独立 closure audit 完成并写入 evidence
- [x] Anti-Hollow：flush 链路（getUserContextAsync 基线捕获 → saveUserContextAsync 条件 UPDATE → clearDirty）live 连通，无空方法体/静默吞异常
- [x] `./mvnw test -pl nop-auth/nop-auth-service -am`：上游链绿[已知面① solo 归因] + 叶模块 476 全绿（两轮）——按 campaign E3 先例口径验收
- [x] 代码规范：imports 分组、4 空格缩进、错误信息英文

## Deferred But Adjudicated

### F3③ AbstractUserContextCache clearDirty-先于-put（Local 部署）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Local 部署 put 为内存操作，无 DB 落库失败窗；2.3-C1-001 同批裁定已登记
- Successor Required: no

### F3⑥ LocalUserContextCache.init() 死重内存面

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计原判归 C5 观察面不立案
- Successor Required: no

## Closure

Status Note: 8.73/R4-33 移交接收轨（2.3-C1-010）全修：条件 UPDATE + per-session 单飞 + clearDirty 后置 + 失败日志 + F3② 显式过期标记；红①红④ 先红后绿 + 变异探针回红留证；roadmap Phase 8 至此仅余 8.31（R3-01）。
Completed: 2026-11-13

Closure Audit Evidence:

- Reviewer / Agent: agent_0b16a91c（独立 fresh-session subagent）
- Evidence:
  - Phase 1 Exit Criteria：PASS（红①红④ 修复前 RED expected:<rt-one-rotated> but was:<rt-zero-baseline>，绿①②+白名单钉 GREEN，`_tmp/red-green-ledger-2568.md`）
  - Phase 2 Exit Criteria：PASS（live code 逐点核对——基线取读时 DB 行值 :125-126 于 :128 前捕获/callWithLock 四参实例级 holderId/clearDirty 仅成功路径/affected=0 两分支/异常 ERROR+rethrow/F3② 显式条件 UPDATE/无空壳）
  - Anti-Hollow：PASS（getUserContextAsync 建 DaoUserContext 捕获基线 → saveUserContextAsync instanceof 分支消费；beans.xml nopAuthSessionFlushLockManager 存在 + @Inject 可解析；focused 5/5 实跑绿）
  - Phase 3 Exit Criteria：PASS（-am 链实跑上游唯一红面=已知面① TestPeriodicTaskInsulation solo 归因零 diff；叶 476 两轮绿；biz-auth-core 136 绿；scan-hollow EXIT=0；doc-links EXIT=0）
  - Closure Gates：PASS（F3 逐条处置登记/无降级 deferred/owner doc 一致/无空 catch）
  - 文本一致性：PASS（勘误后 roadmap 8.73 行=APPROVED-with-corrections 与本文件一致；logs 11-13 条目一致）
  - Deferred 项分类检查：PASS（F3③ AbstractUserContextCache 现状零改动=watch-only 描述一致；F3⑥ 同）
  - 修正项闭环：Major-1 -am 实跑+归因留证 / Major-2 roadmap 闭包时序订正 / Minor-1 scan-hollow 命令形态勘误 / Minor-2 单 commit 提交（本 commit）

## Non-Blocking Follow-ups

- （无——如实施期发现新面，按 roadmap P0 即时通道或新 successor 登记）
