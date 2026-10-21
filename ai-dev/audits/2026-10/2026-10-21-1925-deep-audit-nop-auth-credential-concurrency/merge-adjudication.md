# 合并裁定 — WI 5.1 nop-auth-credential 并发审计（C1/C2/C6 + C10）

- 裁定日期：2026-10-21
- 裁定输入：本目录三维度主文件（含复核后「最终保留项」）+ 三深挖轮文件（含复核结论）+ `C10-test-protection.md` + `ai-dev/audits/cra-index.md`（157 条口径查重）
- 裁定口径：同族相邻按「根因 + 失效层」合并，防止同一缺陷被多维度重复计数；判级取证据最全的主登记；条件化（休眠面）成员显式标注激活回升条款

---

## §一 裁定输入与范围

| 输入 | 条数 |
|---|---|
| C1 初审 | 5（C1-001…005） |
| C2 初审 | 6（C2-001…006） |
| C6 初审 | 4（C6-001…004） |
| 深挖第 2 轮新增 | 4（C1-006、C2-007、C2-008、C6-005） |
| **发现小计** | **19**（复核：保留 19 / 降级 0 / 驳回 0；C1-005 与 C2-004 跨维度同缺陷待归一 → 唯一缺陷 18） |
| CL 线索 | 17（CL-001…017） |
| C10 缺保护登记 | 7（C10-001…007，单列不入工单计数） |

## §二 计数口径对账（输入 → 输出）

- **19 条发现 → 14 张工单**（8 缺陷族 MA-01~08 + 6 独立 I-01~06）+ watch 台账 4 条（CL-001/002/009/010）。
- 族吸收：MA-01 吸收 2（C6-002/003）、MA-02 吸收 1（C2-006）、MA-03 吸收 1（C2-002）、MA-04 吸收 2（C2-005 + 跨维度 C1-005）、MA-06 吸收 1（C2-007）、MA-07 吸收 1（C1-004）。
- 发现覆盖对账：MA 族承载 14 条发现（MA-01×3、MA-02×1、MA-03×2、MA-04×3、MA-06×2、MA-07×2、MA-08×1）+ 独立工单承载 5 条发现（I-01~I-05 各 1）= **19 = 19 闭合**（MA-05/I-06 为 CL 主登记工单，不占发现席位）。
- 判级分布（工单口径）：**P0×0（显式空执行）/ P1×2 / P2×5 / P3×7**。
- P0 即时通道：显式空执行（本单元无 P0）。
- CL 17 条全部落位（§八权威表）：并入工单 10 / 重复合并 2 / 升格立案 1 / 独立线索台账 4。
- C10 缺保护 7 条单列（会红 0 / 反向钉死 1 / 无测试 13）。

## §三 缺陷族合并（MA-01~08）

| 族 | 判级 | 主登记 | 吸收成员 | 根因 + 失效层 | 修复形态（同 diff 批） |
|---|---|---|---|---|---|
| **MA-01** 六族 DB 状态存储清理配对缺失族 | **P1** | C6-001 | C6-002、C6-003 | 根因：状态行只创建无回收（F4）；失效层：六族 DB 存储的「创建/过期/清理」配对（session 族连惰性删除都没有、五族仅同键惰性、trusted-device 仅人工） | `NopCredentialOauthStateStore.cleanupExpired()` 语句形态统一批量清扫 + job 低频接入 + `NopAuthConfigs` 保留期配置；C10-001/004/005 回归钉随批 |
| **MA-02** Redis 计数键 TTL 附着非原子族 | **P1** | CL-006（复核确认，判级裁定见 §四#8） | C2-006 | 根因：INCRBY 与 PEXPIRE 两步非原子（运行期网络失败 + 崩溃窗口两面同根）；失效层：Redis 三 store 6+1 递增点 | INCR+PEXPIRE 收敛单 Lua 原子（FakeNosqlService 随批扩展，正向修复约束）或 PTTL=-1 兜底补设；失败 ERROR + 补偿删除 |
| **MA-03** JWKS 刷新链并发治理族 | P2 | C2-001 | C2-002；CL-008/012/013 并入 | 根因：synchronized(this) 复合操作把同步 HTTP、失败推进、节流/TTL 锚耦合于同一临界区；失效层：JWKPublicKeyLocator 单站点 | fetch 移出临界区 + volatile 整表替换（:122 已就位）；成功/尝试时间分离；显式 per-request timeout；失败可观测 |
| **MA-04** 可信设备并发纪律族 | P2 | C2-004 | C2-005；C1-005（跨维度吸收）；CL-017 搭车 watch | 根因：max-count 检查/豁免判定/撤销矩阵间无锁无条件写（用户维度无串行化点）；失效层：MfaTrustedDeviceManager 全部 DB check-then-write 面 | 用户维度 `IResourceLockManager` 锁或条件写；豁免二次确认或文档声明判定时点语义；过期清扫搭车 MA-01 |
| **MA-05** check-then-insert 输家无归一族 | P3 | CL-003 | CL-007；CL-016（**条件化休眠**：channel providers 零装配，W5-2 到货即活——回升条款）；CL-015 重复关闭不入本族 | 根因：exists 预检→insert 无 duplicate-key 归一，输家收裸约束异常（数据一致性由唯一约束兜底不失）；失效层：registerUsage/grant/upsertPending/confirmWebauthn/completeBinding 五站 | 统一捕获 `ERR_SQL_DUPLICATE_KEY` 转幂等成功（`DbSmsCodeStore.send:86-101` 先例） |
| **MA-06** NopAuthSession 写语义族 | P3 | C2-003 | C2-007；CL-011 并入 | 根因：无 version 列多写者 LWW + 快照式挤下线无串行化点（同表两个失效面）；失效层：NopAuthSession 表 | version 列（**orm 模型 plan-first 变更**）+ userName 粒度串行化/条件 UPDATE（`WHERE logout_type=0`）；logoutType 写入口收敛 |
| **MA-07** 缓存对象 getter 逃逸防御收口族 | P3 | C1-002 | C1-004 | 根因：init-once 缓存的 value 层活引用经 getter 逃逸（当前无发布后写者，防御性收口）；失效层：registry/sitemap 两缓存组件 | 构建完成后深度不可变化（unmodifiable 包装，写侧一次收口），对齐 oauth2 分支先例 |
| **MA-08** TOTP 窗口推进败者归一 | P3 | C2-008 | CL-005（时序修正后残余面：consume 返回值判定分离） | 根因：窗口推进并发语义依赖乐观锁偶然行为——好处（双花拦截）与代价（败者 500）均非设计；失效层：MfaFactorVerifier TOTP 分支 | 条件 UPDATE（`WHERE lastVerifiedWindow < ?`，affected=0 归一 false）对齐 webauthn 先例；consume 返回 null 归一 CHALLENGE_EXPIRED 联动 |

## §四 冲突与勘误裁定（7 项）

1. **C1-005（P3）↔ C2-004（P2）跨维度同缺陷**：裁定 C2-004 为主登记、判级 **P2**（触发链完整具体 + 配额契约并发违反，证据更全）；C1-005 标注「重复并入」保留原文不删。降档口径核对：两维后果论述（有界、非 MFA 绕过）一致，P2 取「可具体化竞态」与「有界后果」的中间档成立。
2. **C2-002 ↔ CL-012 同站点双登记**：吞异常与无条件推进为一次交错的两面——归一 MA-03 单案，不重复计数。
3. **CL-004（C1 文件）↔ CL-014（C6 文件）跨文件双登记**：同一缺陷（SSO doLogout 派生链丢弃）——归一升格 **I-06（P2）**（SSO 单点登出半成功，安全相关；最终判级与「登出必须成功吊销 IdP 会话」契约对照留 7.1，暂计 P2）。
4. **CL-013 表述证伪勘误**：`lastRequestTime` 实为 volatile（`JWKPublicKeyLocator.java:45`），「非 volatile 锁外读」不成立；持锁 I/O 面由 C2-001 承载——CL-013 降为并入 MA-03 的勘误行。
5. **C6-001 绝对化表述勘误（不降级）**：通用 CRUD delete 经 biz 代理对 session 行权限门控可达（`_service.beans.xml:141-145`）——「不存在任何 DELETE」证伪；「无自动/周期回收 + 令牌载荷无限期驻留」风险主体成立，**维持 P1**。
6. **CL-015 遗留问题裁决**：`UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER (credentialId, consumerRef)` 实证存在（`nop-credential.orm.xml:118`）——无双行面；输家裸异常面即 CL-003 已登记站点——**重复关闭并入 CL-003**。
7. **CL-005 时序修正（复核联动 C2-008）**：TOTP 双签发主时序被乐观锁窄带拦截——残余面收窄为「consume 判定分离」语义缺口，随 MA-08 联动修复，不单独立案。
8. **MA-02 判级裁定**：CL-006 原为线索未判级、C2-006 原判 P3（理论风险）——合并后裁定 **P1**：运行期触发链具体（PEXPIRE 瞬时故障 → 键永不过期 → 永久锁号自锁死循环需人工 DEL），命中校准「罕见但可具体化的竞态 + 长时运行必然触发的无界增长」；「已声明边界」仅覆盖不刷新语义、不覆盖 TTL 附着失败残余（C2-006 原文自证）。

## §五 跨单元关联（cra-index 157 条口径查重）

**同站点重复立项：0。** 家族/同型关联 5 处单列（零重复，修复批协同）：

| # | 本单元 | 关联单元/条目 | 关系 |
|---|---|---|---|
| 1 | MA-03 / I-01（IdP 外呼阻塞 + 无调用点超时） | WI 2.3 `C4-007`（SSO token 刷新无超时阻塞 IdP HTTP） | 同族不同站点（其站 nop-biz-auth-core 过滤器链、本站 nop-auth-sso/nop-credential-service）——超时治理统一方案协同 |
| 2 | MA-01（状态表零保留族） | WI 1.6 `C7-001`、WI 1.7 `C7-003`（F4 账本/账本保留族） | 跨单元同族第 4/5/6 例——Phase 8 批次对齐，保留期/清理设施统一 |
| 3 | MA-06（session 无 version 多写者） | WI 2.4 `C2-001`（ORM session 零互斥，平台层） | 两层互补：平台设施 vs 业务实体模型缺列——非同站点 |
| 4 | I-05（请求线程改单例可变字段） | WI 4.2 `C1-001`（Nacos 事件线程原地改写共享实例） | 同型不同站点/字段——家族关联单列 |
| 5 | I-02（刷新半个成功） | WI 1.6 `C3-006`（簿记写覆盖业务结果） | 半个成功族家族关联，判级按各自后果独立 |

其余 18 个唯一缺陷 × cra-index P0/P1/P2 台账逐条比对零命中。

## §六 工单台账（权威表）

| 工单 | 判级 | 成员（含吸收） | 一句话描述 | Protected Area / 修复约束 |
|---|---|---|---|---|
| MA-01 | **P1** | C6-001 主 + C6-002 + C6-003 | 六族 DB 状态存储零/弱清理配对：session 行含令牌载荷无限期驻留（F4 无界） | orm 不变（纯清理）；C10-001/004/005 回归钉同批 |
| MA-02 | **P1** | CL-006 主 + C2-006 | Redis 计数键 INCR/EXPIRE 两步非原子：TTL 附着失败键永不过期 → 永久锁号 | nop-nosql 平台设施不触碰；FakeNosqlService 随批扩展 |
| MA-03 | P2 | C2-001 主 + C2-002 + CL-008/012/013 | JWKS 刷新链：锁内同步 HTTP + 失败推进节流/TTL 锚 + 无告警 | 与 I-01 同 PR 批（同站点超时面） |
| MA-04 | P2 | C2-004 主 + C2-005 + C1-005 + CL-017(watch) | 可信设备配额/豁免/撤销全链无串行化点 | 用户级锁方案与 MA-01 清扫搭车 |
| MA-05 | P3 | CL-003 主 + CL-007 + CL-016（条件化） | 五站 check-then-insert 输家裸约束异常，无幂等归一 | CL-016 激活回升条款：channel providers 装配即回升 |
| MA-06 | P3 | C2-003 主 + C2-007 + CL-011 | session 表多写者 LWW + 挤下线快照漏杀 + 写入口分散 | **orm 模型 version 列 = plan-first** |
| MA-07 | P3 | C1-002 主 + C1-004 | 缓存对象 getter 逃逸（无写者，防御性收口） | 写侧 unmodifiable 一次收口 |
| MA-08 | P3 | C2-008 主 + CL-005（修正面） | TOTP 窗口推进败者 500 + consume 判定分离 | 对齐 webauthn 条件写先例 |
| I-01 | P2 | C6-004 | OAuthTokenClient/JWKPublicKeyLocator 外呼无调用点请求超时（默认 30s 为部署配置非代码保证） | JWK 站随 MA-03 同批 |
| I-02 | P2 | C6-005（+C10-007 反向钉死随批） | OAuth 刷新半个成功：IdP 已轮换、本地回滚丢新 refresh token → 凭证永久失效待重授权 | invalid_grant 归一 + 补偿持久化；**修复须先改 TestCredentialOAuthRefresh.refreshFailureFailsClosed** |
| I-03 | P3 | C1-001 | lastNonCv1SkippedCount 非 volatile 运行期写（生产零读点） | volatile 一行 |
| I-04 | P3 | C1-003 | 两个单例 5 个 check-then-act 惰性初始化字段 | eager-init 或 volatile |
| I-05 | P3 | C1-006 | 请求线程运行期写单例 TOTPAuthenticator.skew | skew 参数直传（verifyRaw 已参数化） |
| I-06 | P2 | CL-004（=CL-014） | SSO doLogout IdP 反向登出逃逸 waitAll + 异常静默——单点登出半成功 | thenCompose 纳入 waitAll + ERROR 日志；契约对照留 7.1 |
| watch | — | CL-001、CL-002、CL-009、CL-010 | Vault 吞异常丢 cause（可观测性）/ reencryptAll javadoc-事务语义 / setTtl syncGet 阻塞 / 每请求反序列化 | 独立线索台账，不占工单 |

**P1×2（MA-01/MA-02）→ 进 Phase 8 批量修复；P2×5；P3×7。C10 缺保护 7 条（C10-001…007）单列随对应工单落地。**

## §七 结构性发现

1. **「会红=0」campaign 同型第 N 次确认**：3 个真线程并发测试全部服务于**已修复缺陷**的回归钉（check2/A2 系列），对**现存** 13 条并发/资源缺陷保护力为零；E2E 族单线程测试构成「顺序正确的一半」钉死面（与 4.3「行为等价矩阵只钉死正确的一半」同型）——并发维度的缺陷在本单元测试栈中**结构性不可表达**（替身/确定性竞态形态已具备，未投向现存缺陷面）。
2. **并发防护双层架构的接缝缺陷**：请求路径实质防护依赖 DB 条件写/唯一约束/Lua CAS（逐点核验成立的合格面占多数），缺陷集中于**条件写缺席的站点**（六族清理、配额检查、快照挤下线、输家归一）——修复方向全部收敛为「补条件写/唯一键/归一」，无一处需要引入 JVM 锁进热路径。
3. **已声明边界 vs 残余面的三例精确切分**：C2-006（声明覆盖不刷新语义、不覆盖 TTL 失败）、C6-003（声明只约束 max-count 语义）、CL-006（无声明）——本单元「已声明边界」抗辩全部通过复核校验，无一例以声明掩盖缺陷。
4. **范围外承载模块残余**（移交记录）：`LocalMfaChallengeStore.peek` 活引用逃逸（调用面全只读）、`TOTPAuthenticator` 本体（I-05 修复即消解写点）——归 nop-biz-auth-core 所在单元后续审计输入。

## §八 CL 线索落位权威表

| CL | 落位 | 去向 |
|---|---|---|
| CL-001 | 独立线索台账（watch） | 可观测性，非并发缺陷 |
| CL-002 | 独立线索台账（watch） | 文档-实现不符 + 长事务面，随 MA-01 修复批评估拆事务 |
| CL-003 | **并入 MA-05（主登记）** | — |
| CL-004 | **升格 I-06（P2）** | 与 CL-014 归一 |
| CL-005 | 并入 MA-08（时序修正面） | §四#7 |
| CL-006 | **并入 MA-02（主登记，升 P1）** | §四#8 |
| CL-007 | 并入 MA-05 | — |
| CL-008 | 并入 MA-03 | — |
| CL-009 | 独立线索台账（watch） | C9 面 |
| CL-010 | 独立线索台账（watch） | C7/C9 面 |
| CL-011 | 并入 MA-06 | — |
| CL-012 | 并入 MA-03 | §四#2 |
| CL-013 | 并入 MA-03（表述证伪勘误） | §四#4 |
| CL-014 | 重复合并 → CL-004（I-06） | §四#3 |
| CL-015 | 重复关闭 → CL-003（MA-05） | §四#6 |
| CL-016 | 并入 MA-05（条件化休眠，回升条款） | — |
| CL-017 | 并入 MA-04（watch 搭车） | — |

## §九 修复批次与后续

- **Phase 8 批量修复入口**：P1×2（MA-01 六族清理统一立项、MA-02 Lua 原子化）优先；P2×5 随后（MA-03/MA-04/I-01 同批外呼治理、I-02 携反向钉死改写、I-06）；P3×7 逐项 7.1 裁决（in-scope / deferred + Why Not Blocking）。
- **Protected Area**：MA-06 触碰 `nop-auth/model/nop-auth.orm.xml`（ORM 模型结构，**plan-first**）与 nop-auth 认证模型（ask-first）——该工单实施前须走 plan audit；其余 13 张工单零生成文件/零 `_` 前缀触碰。本审计全程只读，未触发 ask-first。
- **C10 义务**：C10-001…007 建议测试形态随对应工单落地；C10-007 的反向钉死测试改写是 I-02 的同 diff 前置。
- **cra-index 回填**：本报告目录以 `2026-10/2026-10-21-1925-deep-audit-nop-auth-credential-concurrency` 登记 WI 5.1 行（P0×0/P1×2/P2×5+7（含 C10）/P3×7，CL 17 条全落位，计数口径见 §二）。
