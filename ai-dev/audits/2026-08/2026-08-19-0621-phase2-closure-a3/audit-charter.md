# A3-audit 二期收口全量验证审计章程（audit-charter）

> Date: 2026-08-19 06:21
> Plan: `ai-dev/plans/2026-08-19-0515-1-phase2-closure-full-verification-a3-audit.md`
> Baseline: HEAD = b7c81136c（git status clean，除本 plan 文件为 untracked 新文件）
> 性质：验证型收口审计——不引入新功能；验证暴露的回归类 confirmed defect 在 plan 内修复或建立 successor 所有权，禁止静默降级、禁止执行期新增豁免。

## 一、验证矩阵（验证域 × 命令 × 预期结果 × 证据落盘位置）

| # | 验证域 | 命令/方法 | 预期结果 | 证据落盘 |
|---|---|---|---|---|
| V1 | 全量 build/test 门 | 主门：全仓 `./mvnw clean install -T 1C`（含测试）；若因 pre-existing 排除项失败 → 等价证据链：全仓 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS + 受影响模块族逐模块 `./mvnw test -pl <module>`（不带 `-T 1C`）全绿 | 主门 BUILD SUCCESS，或等价证据链成立且失败集合与排除项清单精确一致（逐条 HEAD 对照） | 本目录 `summary.md` §V1 + `build-log/`（主门/等价链关键输出） |
| V2 | 零明文回归断言复核 | fresh 独立子 agent：① 零明文锚点族测试全绿核对（依赖 V1 测试结果）；② live 锚点 file:line 对号抽查（见 §三 锚点清单 B 组） | 每锚点 PASS/FAIL 显式结论；测试族全绿 | 本目录 `plaintext-boundary-review.md`（子 agent 报告） |
| V3 | 一期功能零回归复核 | fresh 独立子 agent：两设计 §二 矩阵逐行 PASS/FAIL（凭证五行 + MFA 六行）+ 一期锚点族测试全绿 + "既有断言零修改" git 抽查 + W7-successor 消费链 + `@sec:` 共存锚点 | 矩阵 11 行逐行显式 PASS（或 FAIL→confirmed defect 处置）；git 抽查证明一期核心测试文件在二期全程未被修改断言 | 本目录 `phase1-regression-review.md`（子 agent 报告） |
| V4 | deferred/watch 登记一致性核对 | 编排 session：A1/A2 adjudication + C1a/C1b + A2-followup-1/-2 + W16 族 Deferred/Follow-up 段逐条核对（见 §四 条目级清单）；successor 所有权落地核对；watch 锚点测试存在且绿；D4-4 两副本同步不变式 live 复核 | 零悬挂；无 P0/P1 降级残留；successor（C1-hardening / A2-followup 族）全部 done 落地；watch 锚点测试存在且绿 | 本目录 `summary.md` §V4（含发现漂移的前后对照） |
| V5 | milestone（★★）派生判定前置核对 | 编排 session：W9-W16 + A1-A3 逐项 done 核对（见 §五 派生判定标准） | 全部 done（A3 由本 plan closure audit 判定） | 本目录 `summary.md` §V5 + roadmap 落定（Phase 3） |

**执行约束（roadmap 二期审计门禁）**：V2/V3 由 fresh 独立子 agent 执行（非本 plan 编排 session 自查）；编排 session 负责 V1 命令执行、dispatch 与证据收集。发现者不复核自己的修复：若落有 Fix，由另一 fresh 复核子 agent 验证。

## 二、pre-existing 排除项清单（仅此五条，禁止执行期新增）

来源：plan Current Baseline（2026-08-19 live 核对登记）。

| # | 排除项 | 位置 | 登记来源 |
|---|---|---|---|
| E1 | rocksdb 基准测试 + `TestFeatureConditionEvaluator`——本 worktree clean HEAD 即失败 | `nop-stream/nop-stream-rocksdb`（基准测试族）、`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/feature/TestFeatureConditionEvaluator.java` | A2-followup-2 实证登记（文档化 pre-existing，HEAD 对照） |
| E2 | plexus javac 进程内 CME 编译 flake | 全仓编译期偶发 | A2-audit 登记；`-Dmaven.compiler.fork=true` 绕过 |
| E3 | missing-tenant-id 顺序污染 flake——**已于 2026-08-17 根因修复退役** | —（若再现按新缺陷处置，不得引用本条豁免） | 根因修复记录 |
| E4 | nop-auth-sso `OAuthLoginServiceImpl:231` hollow-scan 单条 high | `nop-auth-sso` | pre-existing（多轮 HEAD 对照证实） |
| E5 | `scan-hollow-implementations.mjs --module` 多标志互相覆盖 | 工具行为 | 已知工具治理项；规避 = 单标志逐模块扫描 |

分诊规则：V1 失败若不在上表 → confirmed defect → 本 plan 修复（Fix）或 successor 所有权。E1 两模块（nop-stream-rocksdb / nop-xlang）均不在二期受影响模块族内。

## 三、一期契约锚点清单（live 已核对）

### A 组：两设计 §二 兼容性矩阵（回归基准）

1. **凭证五行**（`ai-dev/design/nop-credential/02-phase2-design.md` §二，L20-L32）：
   ① `cv1:` 密文格式（四主题均"不变"）；② 明文边界（`published=false` + BizModel 置空 + 唯一明文出口——A1 术语裁定：`CredentialProviderImpl` 为唯一明文出口，reencryptAll 进程内重加密不违不变式）；③ 软删除 fail-closed（delFlag）；④ 引用计数（registerUsage/consumerRef）；⑤ 一期消费链（W7-successor 等）零回归（A1 保留注记：resolver 装配缺位为一期自带缺口，A1 已修复）。
   两附加锚点：`ICredentialProvider` SPI 零变更、`@sec:` 配置加密共存。
2. **MFA 六行**（`ai-dev/design/nop-auth/02-mfa-phase2-design.md` §二，L20-L31）：
   ① 两阶段登录 challenge（scene=login 缺省）；② `ERR_AUTH_MFA_REQUIRED` 异常表达；③ `completeLogin` 分界裁决；④ store 装配（collect-beans + 条件激活 + lessons 15）；⑤ 明文边界（secret 加密/恢复码 BCrypt/masked 不暴露）；⑥ 一期零回归（缺省 false 零介入 / 无策略行 = 一期行为 / 不绑定新因子无感知 / 不勾选无记录逐字节一致）。

### B 组：live 锚点（file:line 已核对，2026-08-19 06:2x）

**零明文锚点（V2 抽查对象）**：

| 锚点 | live 位置（已核对） |
|---|---|
| 凭证 data 列 published=false | `nop-credential/nop-credential-meta/src/main/resources/_vfs/nop/credential/model/NopCredential/NopCredential.xmeta` L6 |
| data 置空/maskList | `nop-credential/nop-credential-service/src/test/java/io/nop/credential/service/entity/TestNopCredentialBizModel.java` |
| provider 明文出口判定矩阵 | `nop-credential/nop-credential-service/src/test/java/io/nop/credential/service/TestCredentialProviderImpl.java` / `TestCredentialProviderOwnership.java` / `TestCredentialProviderRbacAuth.java` |
| MFA setting secret 加密/恢复码 BCrypt/masked 不暴露（A2 D1 锚点） | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/`（V2 子 agent 对号） |
| phone 结构性排除 not-pub | `nop-auth/nop-auth-meta/src/test/java/io/nop/auth/meta/TestNopAuthMfaSettingXmeta.java` |
| 敏感面收敛 E2E | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMfaSensitiveSurfaceE2E.java` |
| webauthn credentialId+publicKey masked | `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/`（V2 子 agent 对号） |

**一期零回归锚点（V3 抽查对象）**：

| 锚点 | live 位置（已核对） |
|---|---|
| `cv1:` round-trip | `nop-credential/nop-credential-service/src/test/java/io/nop/credential/crypto/TestCredentialCipher.java` |
| 一期 MFA E2E 家族 | `TestMfaLoginE2E.java` / `TestMfaUserSelfService.java` / `TestScanLoginMfa.java`（`nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/`） |
| W7-successor 消费链 | `AiModelCredentialResolverImpl` 优先级链（accountKey > credentialId > resolveApiKey）+ 引用计数（V3 子 agent 对号） |
| `@sec:` 共存 | `DefaultConfigValueEnhancer` 路径不因凭证库改变（V3 子 agent 对号） |

## 四、deferred/watch 登记核对清单（条目级）

### A1 adjudication（`ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/adjudication.md`）

- fixed 5 组（D2-01/D4-01、D6-01、D4-03、D5-01、路由项 8 措辞、复核观察项 1）——核对：修复仍在 live（抽 D6-01 resolver beans 注册 + D2-01 OauthState BizModel 锁定）。
- successor 25 条 → C1-hardening——核对：C1a（24 finding ID）/C1b（@MfaRequired 四动作）roadmap `done`。
- deferred 2 条：D5-02（软删行不重加密，设计取舍）、D5-04（cv2 演进）——核对：登记仍在案、无翻案证据、非 P0/P1。
- 8 项路由终局裁定：#1 PKCE/#5 testCredential/#6 state 清理/#7 关窗自动化（→C1a 可选项登记，Successor Required: no）；#2 无上下文 WARN（watch-only）；#3 user 级共享/disabled 收紧（维持 deferred）；#4 @MfaRequired 标注（→C1b 落地）；#8 措辞治理（fixed）。

### A2 adjudication（`ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/adjudication.md`，Audit Status: closed）

- fixed 4（D2-F1/D3-F1 P1×2 + D1-4/V-F1 + D5-F2/V-F2）——核对：修复仍在 live（抽 D2-F1 CAS 语义 + D3-F1 bulk DELETE）。
- successor 2 → A2-followup-1（successor-A）/A2-followup-2（successor-B）——核对：均 roadmap `done`。
- watch-only 12 + optimization 4（D1-5/D1-6/D2-F3/D2-F4/D2-F5/D3-F4/D3-F5/D3-F6/D3-F7/D4-1/D4-2/D4-3/D4-5/D4-6/D5-F4/D5-F5/D6-2/D6-3/D7-F1..F4）——核对：登记仍在案、无 P0/P1、演进义务型登记（D1-5 level-4 扩展义务 / D4-4 两副本同步义务 / D4-5 fragment-tolerant 演进义务）在案。
- 3 项路由终局裁定：#1 remove 标注/rename 不标注（→followup-2 落地）；#2 两副本同构不变式当前同步成立 + D4-4 watch 登记（A3 复核点 = V4 live 复核）；#3 联系方式敏感化（→followup-1 落地）。

### 各 plan Deferred/Follow-up 段（条目级，已提取核对）

- **C1a** Deferred 5 条：PKCE（opt）/ testCredential 连通性（oos）/ state 批量清理（opt）/ KMS 关窗自动化+进度上报（opt）/ D5-02+D5-04（watch）。Follow-up 2 条：无上下文 WARN watch、W16-impl 起草（已完成）。
- **C1b** Deferred 0；Follow-up 2 条：前端引导体验（W12 错误码通道）、nop-ai/integration 标注扩展（需求另起）。
- **A2-followup-1** Deferred 0；Follow-up 2 条：A2 deferred 表无 ownership 项维持原裁定、changePhone/Email 正门端点（立项才登记）。
- **A2-followup-2** Deferred 0；Follow-up 3 条：A2 deferred 表维持、add-key 前端页面、sendMfaCode scene 收紧（info 级）。
- **W16-impl** Deferred 0（预登记倾向由 ext 批次承接）；Follow-up 1 条：过渡并存行 JSON 明文死值清理提示（runbook 运维治理）。
- **W16-impl-ext** Deferred 3 条：飞书刷新期再解析（opt，watch 锚点测试 `TestFeishuCredentialResolution.tokenRefreshUsesCapturedResolvedValues`/`reconnectUsesCapturedResolvedValues`——live：`nop-integration/nop-integration-feishu/src/test/java/io/nop/integration/feishu/client/TestFeishuCredentialResolution.java`）/ 新类型 testCredential 连通性（oos）/ feishu/oss 静态值强制 `@sec:`（watch-only，前提变化已回写设计 §七#7）。Follow-up 3 条：并存窗口运维复核 runbook、hollow-scan 多标志工具治理项（=E5）、checkstyle 非门禁口径。

**核对目标**：以上全部条目——零悬挂（每条有终局归属）、无 P0/P1 残留在 deferred 区、successor 所有权全部落地（C1-hardening done、A2-followup 族 done）、watch 锚点测试存在且绿、登记与 live 无漂移（发现漂移 → Fix 或按 finding 处置）。

## 五、milestone（★★）派生判定标准（W9-W16 + A1-A3 逐项）

| 工作项 | roadmap 状态要求 | plan closure 证据 |
|---|---|---|
| W9-design | done | `2026-08-14-2012-1` Closure 段 |
| W9-impl | done | `2026-08-14-2342-1` Closure 段 |
| W10-impl | done | `2026-08-14-2342-2` Closure 段 |
| W11-impl（Part A + Part B） | done | `2026-08-14-2342-3` + `2026-08-16-2321-1` Closure 段 |
| A1-audit | done | `2026-08-17-0447-1` Closure 段 |
| C1-hardening（C1a + C1b） | done | `2026-08-17-1345-1` + `-2` Closure 段 |
| W12-design / W12-impl | done | `2026-08-14-2012-2` / `2026-08-16-2321-2` |
| W13-impl | done | `2026-08-17-0447-2` |
| W14-impl | done | `2026-08-17-2212-1` |
| W15-impl | done | `2026-08-17-2212-2` |
| A2-audit | done | `2026-08-18-0904-1` |
| A2-followup-1 / A2-followup-2 | done | `2026-08-18-1924-1` / `-2` |
| W16-design / W16-impl / W16-impl-ext | done | `2026-08-17-0447-3` / `2026-08-17-2212-3` / `2026-08-18-0904-2` |
| A3-audit | 本 plan closure audit 通过后置 done | 本 plan Closure 段 |

★★ 置 done 前置：上表全部成立 + 本 plan（A3）Closure Gates 全勾 + 独立 closure audit 证据写入。

## 六、全量验证命令口径（定稿）

1. **主门**：全仓 `./mvnw clean install -T 1C`（含测试）。预期 BUILD SUCCESS。
2. **主门失败时的等价证据链**（仅当失败集合 ⊆ §二排除项，逐条 HEAD 对照）：
   a. 全仓 `./mvnw clean install -DskipTests -T 1C` 必须BUILD SUCCESS；
   b. 受影响模块族逐模块 `./mvnw test -pl <module-path>`（不带 `-T 1C`，规避 reactor 顺序 flake，沿 A1/W16 先例）全绿，覆盖：`nop-credential/nop-credential-service`、`nop-credential/nop-credential-kms-vault`、`nop-credential/nop-credential-web`、`nop-auth/nop-auth-service`、`nop-auth/nop-auth-sso`、`nop-auth/nop-auth-meta`、`nop-ai/nop-ai-api`、`nop-ai/nop-ai-core`、`nop-ai/nop-ai-service`、`nop-ai/nop-ai-gateway`、`nop-nosql`、`nop-service-framework/nop-graphql/nop-graphql-core`、`nop-integration` 全部子模块、`nop-metadata`；
   c. mission 门 `./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C`（Phase 3 测试门）。
3. 编译 flake（E2）处置：重跑或 `-Dmaven.compiler.fork=true`。
4. 测试计数以各模块 surefire 汇总为准，落盘 summary.md。

## 七、证据归档约定

- 主门/等价链关键控制台输出：`_tmp/a3-build/`（工作区）+ 关键摘录归档本目录 `summary.md`（日志文件路径 `_tmp/` 外的审计目录归档 = summary 内嵌关键行 + 计数表）。
- 子 agent 报告：`plaintext-boundary-review.md` / `phase1-regression-review.md`（V2/V3）。
- findings（若有）：summary.md findings 节 + 修复证据。
