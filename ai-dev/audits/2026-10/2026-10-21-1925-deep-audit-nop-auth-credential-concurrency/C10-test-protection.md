# C10 并发正确性的测试保护力 — WI 5.1 nop-auth-credential

- 审计维度：C10（测试保护力矩阵 + 变异推演 + 反向钉死）
- 日期：2026-10-21
- 审计员：C10 审计员（复核阶段口径：可读本目录全部发现与 cra-index；其余盲审纪律同前）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文（变异推演：对每个已登记 P0/P1——本轮扩展至全部 P1/P2——回答"补上正确实现后，现有哪个测试会红？"）

## 一、既有并发/原子性相关测试清单（保护资产盘点）

| 测试 | 并发形态 | 保护范围 |
|---|---|---|
| `TestLoginFailCountAtomicity`（2 tests） | 32 线程 CyclicBarrier 对齐真实并发 + H2 | 登录失败计数丢失更新（check2 P1 回归钉）——`attemptStore().incrementLoginFailCount` 原子性、锁号阈值不被并发绕过 |
| `TestDbCodeStoreSendRace`（2 tests） | JDK 动态代理确定性复现 read-then-insert 窗口（免真并发） | Db 短信/邮件码 store 首发 duplicate-key 归一重试 |
| `TestRedisCodeStoreCasRace`（4 tests） | CasLoserNosql 替身确定性复现 CAS 败者 | Redis 码 store `removeIfMatch` 败者 EXPIRED（双花防御） |
| `TestRecoveryCodeConditionalWrite`（3 tests） | 真实多线程 + H2 | 恢复码 used 条件写：并发双 verify 恰一次 VALID、条件 UPDATE 一次性行 affected=1,0 |
| `TestLocalSendCodeRateLimiter`（9 tests，含 `testConcurrentBurstAllowsExactlyOne`） | 32 线程 barrier | 发码限流"并发突发同目标恰好放行 1 个"契约（C2 核清清单引用先例） |
| `TestDbSendCodeRateLimiter`（6 tests）/`TestDbLoginAttemptStore`（4 tests）/`TestDbMfaChallengeStore`（16 tests） | 含 CountDownLatch 并发段（DbMfaChallengeStore）+ 确定性过期（直接改列，非 sleep） | 三族 Db store 条件 SQL/affected-row 语义 |
| `TestMfaLoginE2E` / `TestOperationMfaE2E` / `TestTrustedDeviceE2E`（11 tests）/ `TestMfaVerificationHardeningE2E` / `TestWebAuthnMfaE2E` 等 E2E 族 | **全单线程顺序语义** | challenge 一次性消费断言（"must be consumed after successful mfaVerify"）、票绑定、豁免/满员/固定窗口/复活刷新顺序语义 |
| `TestDaoSessionStoreAndUserContextCache`（5 tests） | 单线程 | session 定位/登出过滤/缺 lastAccessTime fail-closed（无清理断言面） |
| `TestRevokeUserSessions` | 单线程 | revokeUserSessionsAsync KILL 语义 + exceptSessionId 保留（顺序面） |
| `TestCredentialOAuthRefresh` | 单线程 + stub endpoint 调用计数 | 惰性刷新锁下先行者收敛（"已被并发先行者刷新"分支经 stub 计数确定性验证）、fail-closed、D2-04 锁内复查 |
| `TestNopCredentialReencryptPagination` | 单线程 | reencryptAll 分页 + `lastNonCv1SkippedCount` 计数（:215/:222——C1-001 字段的唯一读点） |
| `TestJwkKeyParsing`/`TestJwkErrorNormalization`/`TestJwtHelper`/`TestOAuthLoginServiceImplContract` | 单线程 | JWKS 解析/错误归一/JWT issuer 校验——**JWKPublicKeyLocator 的锁/节流/TTL 行为零测试** |

形态学结论（供 merge-adjudication §七 结构性发现引用）：真线程并发测试 3 个（FailCountAtomicity/RecoveryCodeConditionalWrite/LocalRateLimiter），确定性竞态替身测试 3 个（DbCodeStoreSendRace/RedisCodeStoreCasRace/部分 Db store），其余全顺序语义。并发测试栈**零 Thread.sleep 同步**（唯一 grep 命中为 TestDbMfaChallengeStore 注释文本），无平台性脆弱时序测试。

## 二、保护力矩阵（缺陷 × 既有测试 × 变异推演结论）

> 变异推演口径：假设缺陷被正确修复（按各发现建议），现有哪个测试会红？

| 缺陷（判级） | 最接近的既有测试 | 变异推演（修复后该测试红否） | 结论 |
|---|---|---|---|
| **C6-001**（P1）session 行零自动回收 | TestDaoSessionStoreAndUserContextCache（断言查询/登出过滤，不涉清理） | 不红——清理为增量路径，无任何测试断言行驻留或回收 | **无测试**（缺保护） |
| **C2-001**（P2）JWKS 锁内同步 HTTP | TestJwkKeyParsing/TestJwkErrorNormalization（纯解析） | 不红——锁行为零测试覆盖；把 fetch 移出锁、改异步化均不可观测 | **无测试**（缺保护） |
| **C2-002**（P3，含 CL-012）刷新失败推进 lastRequestTime | 同上（零覆盖） | 不红 | 无测试（P3 不单独立项，随主案修复自带测试义务） |
| **C2-003**（P3）session 无 version 多写者 | TestDaoSessionStoreAndUserContextCache | 不红（无并发写交错测试） | 无测试（同上） |
| **C2-004**（P2，吸收 C1-005）register max-count 并发超额 | TestTrustedDeviceE2E max-count 满员用例（:245-264，单线程 max-count=1 满员拒绝语义） | 不红——修复（用户级锁/条件写）对顺序语义无影响；并发超额路径无测试 | **无测试**（缺保护；E2E 只钉顺序正确的一半——F3 在测试层同型：并发维度未钉） |
| **C2-005**（P3）豁免/撤销 TOCTOU | TestTrustedDeviceE2E 豁免/过期用例（顺序） | 不红 | 无测试（同上） |
| **C2-006**（P3）Redis TTL 崩溃窗口孤儿键 | TestRedisStoreWiring/FakeNosqlService 委托契约 | 不红（崩溃窗口不可表达） | 无测试（同上） |
| **C2-007**（P3）autoLogout 快照漏杀 | TestRevokeUserSessions（顺序 KILL 语义） | 不红——并发双登录场景无测试 | 无测试（同上） |
| **C2-008**（P3）TOTP 窗口推进败者 500 | TestMfaLoginE2E（顺序 verify+consume 断言） | 不红（单线程不败者路径）；条件写改造对顺序语义无影响 | 无测试（同上） |
| **C6-002**（P2）五族仅惰性删除、无批量清扫 | TestDbMfaChallengeStore 等（同键惰性删除语义已钉） | 不红——批量清扫为增量；**正向修复约束**：新增批量清扫不得破坏既有惰性删除断言（无冲突） | **无测试**（缺保护） |
| **C6-003**（P2）trusted-device 过期行无回收 | TestTrustedDeviceE2E :228（"expired trusted row must not exempt"——惰性失效已钉） | 不红——自动回收为增量 | **无测试**（缺保护） |
| **C6-004**（P2）外呼无调用点超时 | TestCredentialOAuthRefresh（stub 即回，无超时面）；TestJwk* | 不红——超时行为不可观测（stub 无延迟注入） | **无测试**（缺保护） |
| **C6-005**（P2）刷新半个成功丢轮换 token | **TestCredentialOAuthRefresh.refreshFailureFailsClosed（:255-270）** | **部分红**——若修复 (a) 将 invalid_grant 归一为 reauth-required 错误码，该测试 `assertEquals("nop.err.credential.oauth-refresh-failed", ...)` 必红 | **反向钉死**（见 §三） |

## 三、反向钉死清单

| # | 测试 | 钉死内容 | 阻碍的修复 |
|---|---|---|---|
| 反向钉死 1 | `TestCredentialOAuthRefresh.refreshFailureFailsClosed`（:259-264） | `assertEquals("nop.err.credential.oauth-refresh-failed", ex.getErrorCode())`——把"invalid_grant 一律报 REFRESH_FAILED"钉为契约 | C6-005 建议 (a)（invalid_grant → reauth-required 状态归一）。修复必须同 diff 改写该测试（区分"暂态刷新失败"与"终态需重授权"两个用例），否则修复即红 |
| 正向修复约束（非钉死） | `TestRedisStoreWiring`/`TestRedisCodeStoreCasRace` 等 FakeNosqlService 系 | 委托原语逐条记录断言 | CL-006 修复若收敛为 Lua 原子脚本（INCR+PEXPIRE），FakeNosqlService 需同步扩展新原语，否则测试先红（属修复配套义务，非缺陷行为钉死） |

## 四、时序敏感测试审查

- 全部并发测试（§一前 6 行）零 `Thread.sleep` 同步：barrier 对齐（3 个真线程测试）、动态代理/CAS 替身确定性复现（3 个）、直接改列确定性过期（TestDbMfaChallengeStore forceExpire 注释明示拒绝 sleep）——**未发现 sleep 同步的平台性脆弱测试**。
- E2E 族存在零星 `Thread.sleep`（TTL 等待/异步等待面），均非并发同步用途且不在本轮缺陷保护链上——watch 不立案。
- 未发现"测试自身 catch 掉竞争"（superficially-pass）形态：3 个真线程测试的断言均为精确计数断言（恰 N 次/恰 1 个放行），非宽松存在性断言。

## 五、缺保护登记（C10-001 起）

| 编号 | 对应缺陷 | 缺保护形态 | 建议测试形态（小规模参数化，不要求真实大规模压测） |
|---|---|---|---|
| C10-001 | C6-001（P1） | 无测试——修复后回归零保护 | 清理任务测试：登录 N 行→时间推进→触发清理→断言过期行删除、行内令牌载荷清除、未过期行保留（H2 + 可注入时钟） |
| C10-002 | C2-001（P2） | 无测试——JWK 锁行为零覆盖 | 两线程并发 kid-miss + 慢 stub 端点：断言仅一次外呼（计数）、等待方不被锁阻塞至 fetch 完成（时长上界断言） |
| C10-003 | C2-004（P2，吸收 C1-005） | 无测试——满员仅顺序钉定 | 双线程 barrier 并发 register 不同 hash（max-count=1）：断言至多 1 行新登记或一方显式满员拒绝（复刻 TestLoginFailCountAtomicity 形态） |
| C10-004 | C6-002（P2） | 无测试——批量清扫无回归保护 | 插入过期+未过期行→触发 create 路径顺带清扫→断言仅过期行删除、五族逐一参数化 |
| C10-005 | C6-003（P2） | 无测试——过期行回收无回归保护 | 过期行存在时 register/listForUser 顺带清扫断言 + countUnexpired 口径不变（仅计未过期） |
| C10-006 | C6-004（P2） | 无测试——超时行为 stub 不可观测 | stub 端点延迟 > 配置请求级 timeout：断言 fetch 在配置时长内抛错（OAuthTokenClient/JWKPublicKeyLocator 两站点参数化） |
| C10-007 | C6-005（P2） | 反向钉死——修复须先改 refreshFailureFailsClosed | 拆两用例：invalid_grant → reauth-required 状态归一断言 + 暂态失败（5xx）→ REFRESH_FAILED 且 data 不变断言；回滚窗口补偿用例（回写失败后新 token 可经补偿路径落库） |

## 六、矩阵统计与返回值

- 矩阵覆盖：P1×1 + P2×6 逐条推演 + P3×8 汇总行（P3 不单独立项）。
- 变异结论分布：**会红 0 / 反向钉死 1（C6-005）/ 无测试 13（P1+P2 计 7 条 + P3 汇总 6 条）**。
- 反向钉死：1 处（TestCredentialOAuthRefresh.refreshFailureFailsClosed 错误码钉死）；正向修复约束 1 处（FakeNosqlService 需随 Lua 化扩展）。
- 登记：**C10-001…007 共 7 条**（P2 口径，与全 campaign C10 缺保护单列惯例一致）；时序敏感缺陷测试：0；资源泄漏检测钩子：0（无关闭后断言池空/句柄数的钩子——本单元资源形态为 DB 行/Redis 键，建议形态见 C10-001/004/005）。
- 结构性结论：本单元"会红=0"为 campaign 同型确认的延续（4.2 全模块零真并发测试、4.3 测试栈结构性单线程化、4.5 第四次同型确认之后本单元为第 5 例）——既有 3 个真线程测试全部服务于**已修复缺陷的回归钉**，对**现存** 13 条并发/资源缺陷的保护力为零；E2E 族单线程语义测试构成"顺序正确的一半"钉死面，与 4.3"行为等价矩阵只钉死正确的一半"结构性同型。
