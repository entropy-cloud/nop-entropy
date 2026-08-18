# A3-audit 二期收口全量验证 summary（收口审计报告）

> Date: 2026-08-19 | Plan: `ai-dev/plans/2026-08-19-0515-1-phase2-closure-full-verification-a3-audit.md`
> Charter: 本目录 `audit-charter.md`（验证矩阵 V1-V5 / 排除项 E1-E5 / 命令口径 §六）
> 基线：HEAD b7c81136c，git status clean（除本审计目录与 plan 文件 untracked）
> 执行分工：V1/V4/V5 = 编排 session；V2/V3 = fresh 独立子 agent（沿 A1/A2 "探查子 agent 只探查报告" 分工）

## §V1 全量 build/test 门（主门 + 等价证据链）

### 主门（全仓 `./mvnw clean install -T 1C`，含测试）

- 结果：**BUILD FAILURE**（EXIT=1，06:25-06:30，Wall 04:42）——失败点 `nop-stream-runtime`（842 tests：Failures 1 + Errors 1），其后 reactor 模块 SKIPPED。
- **失败分诊（逐条）**：

| 失败测试 | 失败信息 | 分诊 | 证据 |
|---|---|---|---|
| `TestClusterRegistryConsistencyInvariant.testInMemoryRenewLeaseHonorsPerRenewalTimeout:148` | expected 1 but was 0 | **负载诱发 flake，非代码缺陷**：clean HEAD 隔离复跑 **10/10 全绿**（surefire 报告 mtime 06:45，晚于主门失败 06:30——closure audit 核正）；测试属 nop-stream mission 领域（fcc71fc05 lease semantics RL-*），非二期受影响模块族 |
| `TestSupervisionLoopCheckpointReconnectE2E.testRegionRestartRewiresCheckpointPipeline_checkpointsCompleteAfterRestart:138` | checkpoint-aborted（timeout or explicit abort） | **负载诱发 flake，非代码缺陷**：clean HEAD 隔离复跑 **1/1 绿**（同上）；测试属 nop-stream mission 领域（512c4c868 checkpoint/恢复路径），非二期受影响模块族 |

- **排除项对照**：两失败均不在 charter E1-E5 清单；分诊结论 = 隔离复跑绿（HEAD 对照即本 worktree——源码 clean HEAD，仅 untracked 文档），非 defect 故无需豁免口径新增（与 A2-followup-2 "-T 1C displayName 竞态 flake 隔离复跑绿" 同族处置，登记见 §Findings F-2）。
- **E1 排除项当日观测**：`TestFeatureConditionEvaluator`（nop-xlang）当日主门 **3/3 PASS**（E1 为间歇性 pre-existing flake，当日未复现——无需豁免）；rocksdb 基准测试未达（主门在其前失败；等价链 -DskipTests 不跑测试，chain-b 覆盖面不含 nop-stream 族 = 与二期无关）。
- 据此按 charter §六.2 启动**等价证据链**。

### 等价证据链 (a)：全仓 `./mvnw clean install -DskipTests -T 1C`

- 结果：**BUILD SUCCESS（EXIT=0）**（`_tmp/a3-build/eq-chain-a.log`）。全仓编译+安装通过，含全部二期受影响模块族。

### 等价证据链 (b)：受影响模块族逐模块 `./mvnw test -pl <modules> --fail-at-end`（不带 `-T 1C`）

模块清单（charter §六.2.b）：nop-credential-service / kms-vault / web、nop-auth-service / sso / meta、nop-ai-api / core / service / gateway、nop-nosql-core / lettuce、nop-graphql-core、nop-integration 全部 10 子模块、nop-metadata。

- 结果：**BUILD SUCCESS（EXIT=0，06:38-06:41，32 reactor 项目全 SUCCESS；nop-metadata 展开为 8 子模块）**。逐模块计数（`_tmp/a3-build/eq-chain-b.log`）：

| 模块 | Tests | Failures | Errors | 结论 |
|---|---|---|---|---|
| nop-credential-service | 215 | 0 | 0 | ✅（与 A2-followup-2 基线 215/0 一致） |
| nop-credential-kms-vault | 40 | 0 | 0 | ✅ |
| nop-credential-web | 2 | 0 | 0 | ✅ |
| nop-auth-service | 389 | 0 | 0 | ✅（基线 389/0 一致；3 skipped 既有） |
| nop-auth-sso | 5 | 0 | 0 | ✅（1 skipped 既有） |
| nop-auth-meta | 3 | 0 | 0 | ✅（TestNopAuthMfaSettingXmeta 绿） |
| nop-ai-api | 46 | 0 | 0 | ✅ |
| nop-ai-core | 218 | 0 | 0 | ✅（3 skipped 既有） |
| nop-ai-service | 36 | 0 | 0 | ✅（resolver wiring 测试绿） |
| nop-ai-gateway | 85 | 0 | 0 | ✅ |
| nop-nosql-core / nop-nosql-lettuce | 0 / 52（skip 52，env 条件跳过既有形态） | 0 | 0 | ✅ |
| nop-graphql-core | 82 | 0 | 0 | ✅（80 基线 + 2 fail-fast 新增 = 82，A2-followup-2 口径一致） |
| nop-integration-api | 11 | 0 | 0 | ✅ |
| nop-integration-email-java / email-tencent | 16 / 11 | 0 | 0 | ✅（W16-ext 基线一致） |
| nop-integration-feishu | 44 | 0 | 0 | ✅（**飞书 watch 锚点测试绿**——TestFeishuCredentialResolution 含 L239/L249 两用例） |
| nop-integration-file-local / oss / sftp | 无测试 / 15 / 12 | 0 | 0 | ✅（file-local 模块仅构建，无测试套件） |
| nop-integration-sms-tencent / sms-yunpian | 11 / 12 | 0 | 0 | ✅ |
| nop-integration-zxing | 2 | 0 | 0 | ✅ |
| nop-metadata（8 子模块，service 全量） | 1077（service）+ web 1 | 0 | 0 | ✅（W16 基线 1077 一致） |

- **watch 锚点测试绿证据**：飞书（feishu 44/0）；A1 fixed 锚点（TestNopCredentialOauthStateBizModel ⊂ credential-service 215/0；TestAiModelCredentialResolverWiring ⊂ nop-ai-service 36/0）；A2 fixed 锚点（TestRedisCodeStoreCasRace ⊂ nop-auth-service 389/0）。

### mission 门（Phase 3 测试门，一并执行）

- 命令：`./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C`
- 结果：**BUILD SUCCESS（EXIT=0，Wall 03:20）**——全 reactor 零失败（含 nop-auth-service 389/0/0（3 skipped 既有）；日志 `_tmp/a3-build/mission-gate.log`）。flake 未复现。

## §V2 零明文回归断言复核（fresh 子 agent）

- 报告：本目录 `plaintext-boundary-review.md`（task `ses_fe9038efbffeknl1ka5rSH1OLf`，read-only，HEAD b7c81136c）。
- 结论：**ALL-PASS 9/9 锚点**。凭证侧三通道（xmeta published=false L6 / BizModel 五查询面置空 + maskList / provider 唯一明文出口——归属 owner-only + RBAC 前置于解密、delFlag 先序）+ MFA 侧（secret 密文 ORM:1072 + 写入侧加密:322-328 / 恢复码 salt+hash / webauthn 列表面无 credentialId/publicKey + ORM not-pub 双保险 / phone not-pub 结构性排除 + E2E schema 拒绝 / getMfaStatus 无 secret + maskPhone）。
- 漂移 sweep：phase-2 新增面（OAuth 双端点 / webauthnBeginAddKey / confirmWebauthnAddKey）无明文/脱敏违规；生产 `credentialCipher.decrypt` 仍仅 2 处（唯一出口 + reencryptAll 进程内）。
- 发现：**F-1（P3，测试锚点描述漂移）**——`TestNopAuthMfaSettingXmeta` 实际不含 phone 断言（有效覆盖由 `TestMfaSensitiveSurfaceE2E#testMfaSettingGenericQueryStructurallyExcludesPhone` 真实 merged-schema 路径承担）；安全属性无缺口。处置：非阻断登记（Non-Blocking Follow-up），理由见 §Findings。

## §V3 一期功能零回归复核（fresh 子 agent）

- 报告：本目录 `phase1-regression-review.md`（task `ses_fe9033b7fffeFFvQX7NAj3Ev0l`，read-only + git history）。
- 结论：**ALL-PASS——凭证五行矩阵（R1-R5）逐行 PASS + 两附加锚点（`ICredentialProvider` 窗口零提交；`CredentialData.typeName` 为 03-design §4.1/§4.3 裁定内 +18/-0 纯 additive；`@sec:` enhancer 零触碰零耦合）+ MFA 六行矩阵（M1-M6）逐行 PASS**。
- 既有断言零修改 git 抽查：6 个一期测试文件 = 1 untouched + 4 additions-only + 1 裁定内 arrangement 修改（TestMfaUserSelfService flake 根治 8f7bc2373，断言面保持，plan 2026-08-17-0447-1 / 2026-08-18-0904-1 可溯）。**未裁定断言弱化：0 项**。
- 主代码两处一期可观测面触碰均裁定在案（D5-05 内层 v1: 强制收紧 / checkMfaRequired protected 单模块签名增量 W15）。

## §V4 deferred/watch 登记一致性核对

- 记录：本目录 `deferred-watch-consistency.md`。
- 结论：**零悬挂；无 P0/P1 降级残留；successor 所有权全部落地（C1-hardening done + A2-followup-1/-2 done）；A1/A2 P1 修复 live 抽查在位（D2-01/D6-01/D2-F1/D3-F1）；watch 锚点测试存在且绿（绿证据 = §V1 chain-b）；D4-4 两副本同步不变式 A3 复核成立（8 共享块等价，仅 3+1 处已登记差异，无新增）**。

## §V5 milestone（★★）派生判定前置核对

| 工作项 | roadmap 状态 | 核对 |
|---|---|---|
| W9-design / W9-impl / W10-impl / W11-impl（A+B） | done done done done | ✅（roadmap Work Items 块 + 各 plan Closure 段在案） |
| A1-audit / C1-hardening（C1a+C1b） | done done | ✅ |
| W12-design / W12-impl / W13-impl / W14-impl / W15-impl | done ×5 | ✅ |
| A2-audit / A2-followup-1 / A2-followup-2 | done ×3 | ✅ |
| W16-design / W16-impl / W16-impl-ext | done ×3 | ✅（W16-impl-ext roadmap 登记前提满足：登记在案且本体 done） |
| A3-audit | 本 plan | closure audit 通过后置 done（Phase 3） |

派生条件 W9-W16 + A1-A3 逐项满足（A3 = 本 plan closure 前置），★★ 可置 done（Phase 3 落定）。

## Findings（全部非阻断，无 confirmed defect）

- **F-1（P3，测试锚点描述漂移）**：`TestNopAuthMfaSettingXmeta` 不含 phone published 断言；有效覆盖在 `TestMfaSensitiveSurfaceE2E`（真实 merged-schema 路径，更强覆盖面）。处置：Non-Blocking Follow-up 登记（xmeta 单元层对称断言补齐 = out-of-scope improvement；安全属性零缺口——V2 子 agent 结论）。**Why Not Blocking**：安全不变式"phone 不发布"由 ORM not-pub（结构性）+ xmeta 兜底 + E2E 真实路径三重成立，缺的只是单元层对称断言，非缺陷。
- **F-2（环境观测，非缺陷）**：主门 `-T 1C` 高负载下 nop-stream-runtime 两测试 flake（隔离复跑绿，见 §V1 分诊）；建议 nop-stream mission 侧关注负载敏感测试的鲁棒性（与既有 rocksdb 基准 flake 同族）。**Why Not Blocking**：非二期模块、非代码缺陷、隔离全绿；登记为三期候选池观察项（successor-candidates）。

## successor-candidates（三期候选池，只登记不立项）

1. F-1 xmeta 单元层 phone 对称断言（out-of-scope improvement）。
2. F-2 nop-stream 负载敏感测试鲁棒性（观察项，归 nop-stream mission 领域）。
3. （无其他新候选——既有 deferred 项不重复登记：PKCE / testCredential 探测 / state 清理 / KMS 关窗自动化 / cv2 / 飞书再解析 / `@sec:` 强制化等维持原裁定。）

## 分诊记录汇总

| # | 现象 | 分诊 | 处置 |
|---|---|---|---|
| 1 | 主门 nop-stream-runtime 2 例失败 | 负载 flake（隔离复跑绿 @ clean HEAD） | 等价链接管 + F-2 登记 |
| 2 | E1 `TestFeatureConditionEvaluator` 当日未复现 | pre-existing flake 间歇性 | 无需处置（当日 PASS） |
| 3 | E1 rocksdb 基准未达 | 主门早停 + 等价链 skipTests | 不适用（非二期族；rocksdb 基准 flake 既有登记在案） |
| 4 | F-1 测试锚点描述漂移 | 非缺陷（覆盖面在 E2E） | Non-Blocking Follow-up + 候选池 |

## 结论

**二期收口全量验证通过（等价证据链成立）**：

1. **V1 全量门**：主门失败 = 2 例 nop-stream-runtime 负载 flake（clean HEAD 隔离复跑全绿，分诊在案、非二期模块、非代码缺陷）+ E1 间歇项当日未复现/未达；等价证据链成立——全仓 `clean install -DskipTests -T 1C` BUILD SUCCESS + 受影响模块族 32 reactor 项目逐模块 test 全绿（332 条 Tests run 行零失败零错误），计数与 A2-followup-2 收口基线全面一致（auth-service 389 / credential-service 215 / metadata 1077 / graphql-core 82 / integration 十子模块 / ai 族 / nosql 族）。**零未分诊失败。**
2. **V2 零明文**：ALL-PASS 9/9 锚点（fresh 子 agent `ses_fe9038efbffeknl1ka5rSH1OLf`），A1/A2 D1 结论最终态成立，phase-2 新增面无违规。
3. **V3 一期零回归**：ALL-PASS 11/11 矩阵行 + 附加锚点 + 断言零修改抽查零违例（fresh 子 agent `ses_fe9033b7fffeFFvQX7NAj3Ev0l`）。
4. **V4 deferred/watch**：零悬挂、无 P0/P1 降级残留、successor 所有权全部落地、watch 锚点存在且绿、D4-4 两副本同构成立。
5. **V5 派生判定前置**：W9-W16 + A1-A2 全 done（A3 = 本 plan closure 前置），★★ 派生条件满足。
6. **Fix 裁定：no-fix-required**——验证未暴露任何 confirmed defect（主门 2 失败为负载 flake 隔离绿；F-1 为测试锚点描述漂移非缺陷；F-2 为环境观测）。因此无修复代码 → hollow-scan / checkstyle 修复门 = N/A。
7. **owner-doc 裁定：No owner-doc update required**——无行为变更、无 owner 文档漂移（V2/V3/V4 均未发现 owner-doc 与 live 冲突；F-1 漂移在历史 plan 措辞层非 owner doc）。
