# A1-audit 凭证库二期安全审计（W9/W10/W11 独立对抗审计 + finding 裁决收口）

> Plan Status: completed
> Mission: nop-credential-mfa
> Work Item: A1-audit（凭证库二期安全审计）——凭证库二期组审计工作项，依赖 W9-impl/W10-impl/W11-impl（均已 done）
> Last Reviewed: 2026-08-17（执行收口：三 Phase 全部 completed + Closure Gates 全勾 + 独立 closure audit Can Close（0 Blocking）；起草期 draft review 两轮记录：首轮 1 Blocker/1 Major/6 Minor 全部处置，复审 READY）
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md` A1-audit 条目 + stage 12；`ai-dev/design/nop-credential/02-phase2-design.md`（§二 兼容性矩阵 = 回归基准）；W9/W10/W11 Part A/B 四份凭证 impl plan + W12-impl plan 的 Closure 证据与 Deferred/Follow-up 登记
> Related: W9-impl `2026-08-14-2342-1-credential-oauth-flow-engine.md`；W10-impl `2026-08-14-2342-2-credential-external-kms-key-provider.md`；W11-impl Part A `2026-08-14-2342-3-credential-scope-ownership.md`、Part B `2026-08-16-2321-1-credential-rbac-grant-authorization.md`；W12-impl `2026-08-16-2321-2-mfa-operation-level-stepup.md`（凭证库敏感动作标注 deferral 的裁定输入）

## Purpose

对凭证库二期三个实现（W9-impl OAuth 流程引擎 / W10-impl 外部 KMS 密钥来源 / W11-impl 归属统一 + RBAC 授权）执行独立安全审计：以设计文档（含 impl 裁定回写标注）与一期契约锚点为基准，对 live code 做对抗性探查，产出带 P0-P3 分级的 findings + 零悬挂的裁决表；同时收口四份凭证 impl plan 与 W12-impl plan 显式路由到 A1-audit 的 8 项 deferred/follow-up 再裁定。审计本身不引入新功能；confirmed live defect（P0/P1）在本 plan 内修复或建立 successor 所有权，不允许静默降级。

## Current Baseline

（2026-08-17 live repo 核对）

- **凭证库二期交付面已全部落地并通过独立 closure audit**：W9-impl（authType=oauth2 类型声明 + `nop_credential_oauth_state` 实体 + 授权码闭环 + 惰性刷新 DB 行级锁互斥 + disabled 全路径拒绝）、W10-impl（`nop-credential/nop-credential-kms-vault` 参照实现 + 同名 bean `nopCredentialKeyProvider` 门控覆盖 + default-bean 缺失守卫 + 启动期 fail-closed 校验组 + reencryptAll keyset 翻页修复）、W11-impl Part A（scope/ownerId 归属 + provider 归属矩阵 + BizModel 两层防御 + 六旁路动作收口）、Part B（`NopCredentialAuth` RBAC 授权实体 + §6.3 判定矩阵串联 + `NopCredentialAuthBizModel` 七旁路动作禁用 + Web 授权编辑）。
- **模块与测试基线**：`nop-credential/`（api/dao/meta/service/web/app + `nop-credential-kms-vault` 子模块）；service 侧既有测试类含 `TestCredentialCipher`/`TestCredentialOAuthRefresh`/`TestCredentialProviderImpl`/`TestCredentialProviderOwnership`/`TestCredentialProviderRbacAuth`/`TestDefaultCredentialKeyProvider`/`TestDefaultCredentialTypeRegistry`/`TestKeyProviderGuardMasterKeysResidual`/`TestKeyProviderModuleMissingGuard`/`TestLocalKeyProviderWithKmsModulePresent`/`TestNopCredentialAuthBizModel`/`TestNopCredentialBizModel`/`TestNopCredentialOwnershipBizModel`/`TestNopCredentialReencryptPagination`/`TestOAuthFlowService`/`TestVaultCredentialKeyProvider`/`TestVaultKeyProviderWiring`（审计回归断言的运行基线）。
- **设计文档为回归基准**：`ai-dev/design/nop-credential/02-phase2-design.md`——§二 一期契约兼容性影响矩阵（五行矩阵：四契约锚点 + 一期消费链零回归行）+ 各主题 x.5 兼容小节 + §八 impl 映射；W9/W10/W11 的 impl 裁定偏离已回写标注（OAuth §3.3/§八、KMS §4.1/§4.3、RBAC §6.3、归属 §5.x）。
- **消费链**：`IAiModelCredentialResolver`（`nop-ai-api`）+ `AiModelCredentialResolverImpl`（`nop-ai-service`，优先级链 accountKey > credentialId > resolveApiKey，fail-closed，引用计数 `ai:NopAiModel:<id>`）——W7-successor 交付，属回归面。
- **四份凭证 impl plan + W12-impl plan 显式路由到 A1-audit 的 deferred/follow-up 项**（共 8 项，本 plan Phase 3 必须逐项再裁定，禁止悬空）：
  1. PKCE（OAuth 2.1 纵深防御）——W9 Deferred「A1-audit 评估后决定并入」（optimization candidate）。
  2. 消费链上下文丢失告警审计——W11 Part A `§七#8` watch-only residual + Part B 同项「A1-audit 评估」。
  3. user 级凭证共享（n8n sharing 模式）/ `status=disabled` 全局收紧——W11 Part A Deferred「需求实证或 A1-audit 发现后重开」。
  4. 凭证库模块敏感动作标注（`reencryptAll`/`saveCredential`/`delete` 加 `@MfaRequired`）——W12-impl Deferred「A1-audit 裁定或后续小 plan」（依赖边 nop-credential-service → nop-biz-auth-api 评估 + owner 裁定链）。
  5. oauth2 凭证 `testCredential` 真实连通性——W9 Non-Blocking Follow-up（consumer-side concern）。
  6. `nop_credential_oauth_state` 过期行批量清理任务——W9 Non-Blocking Follow-up（live 仅惰性清理）。
  7. KMS 迁移关窗自动化（残余列表清空与 reencryptAll 联动）+ `reencryptAll` 进度上报——W10/W3 Non-Blocking Follow-ups。
  8. `reencryptAll` 直接持 `CredentialCipher` 的一期既存事实（进程内重加密不出明文边界）与 owner doc「唯一解密点」表述的措辞治理——W9 Non-Blocking Follow-up（本审计为凭证组最后聚合点，须给终局处置：措辞修复或维持裁定）。
- **审计基建**：审计目录惯例 `ai-dev/audits/YYYY-MM/YYYY-MM-DD-HHMM-{type}-{module}/summary.md`（`ai-dev/audits/README.md`）；审计 prompt 模板 `ai-dev/skills/deep-audit-prompts.md`（多维度深审）与 `ai-dev/skills/open-ended-adversarial-review-prompt.md`（开放式对抗审查）；roadmap 门禁「A1/A2/A3 审计工作项走独立 fresh session；finding 裁决表零悬挂；P0/P1 不静默降级」。
- **A1-audit 依赖已满足**：W9-impl/W10-impl/W11-impl 均 `done`；本工作项为凭证库二期组当前第一个 `todo`。

## Goals

- 六个审计维度全部执行并留下可复核的 live 证据（file:line 锚点 + 探查测试/脚本输出）：明文边界回归、OAuth 引擎对抗、KMS 故障路径 fail-closed、归属与授权绕过对抗、密钥轮换/多 key 并存/密文格式兼容、nop-ai 消费链回归。
- 审计记录落盘 `ai-dev/audits/2026-08/`：summary.md + 分维度 detail 报告，每条 finding 标注 P0-P3 与建议修复方向。
- **finding 裁决表零悬挂**：每条 finding 落到且只落到一种处置——`fixed in this plan`（含 focused 测试）/ `successor plan 所有权`（给出路径）/ `watch-only/optimization deferred`（含 Why Not Blocking）；P0/P1 confirmed live defect 不得进入 deferred。
- 8 项路由 deferred/follow-up 全部完成再裁定（终局结论只记录在审计报告裁决表与 daily log——按 guide 规则 20，来源历史 plan 不回改）。
- 一期契约锚点回归确认，且**每个锚点有归属审计维度**：设计 §二 影响矩阵五行（`cv1:` 密文格式 → D5；明文边界（`published=false` + BizModel 置空 + 唯一解密点）→ D1；软删除 fail-closed（delFlag 解密前先序）→ D1/D4；引用计数（registerUsage/consumerRef）→ D4/D6；一期消费链（W7-successor 等）零回归 → D6）+ 两个显式归因的附加回归项：`ICredentialProvider` SPI 契约零变更（归属 §4.1/§5.1 结论，探查归 D6——接口签名 diff 为空）/ `@sec:` 配置加密共存不受影响（归属 roadmap 横切关注表，探查归 D5——`DefaultConfigValueEnhancer` + `AESTextCipher` 复用路径无交叉污染）。

## Non-Goals

- MFA 二期组审计（A2-audit 范围：因子强度/防重放/恢复通道/策略一致性/操作级钩子覆盖）。
- nop-integration / nop-metadata 深度迁移（W16-design/impl 范围；审计中发现的迁移相关观测项只登记不实施）。
- 性能、可用性、容量审计（安全属性之外的面）。
- 已裁定 deferred 的功能实现（PKCE 等若裁定"并入"，产出的是 successor 登记与规模建议，不是本 plan 内实现）。
- 前端 UX、管理面增强类 follow-up。

## Scope

### In Scope

- 审计执行：fresh 独立子 agent 对 live code 的对抗探查（可新增探查性质的安全测试落盘到 nop-credential 测试树，作为可回归的对抗断言；探查测试不算新功能，须标注审计来源）。
- finding 裁决与处置：P0/P1 现场修复（含 focused 测试 + 回归）；P2/P3 裁定处置路径。
- 8 项路由 deferred/follow-up 的再裁定（只裁定 + 登记，除非裁定为 P0/P1 defect）。
- 文档同步：审计报告落盘；发现 owner-doc/design drift 时修复 `docs-for-ai/03-modules/nop-credential.md` 与 `ai-dev/design/nop-credential/02-phase2-design.md` 的不一致；roadmap A1-audit 状态更新。
- `ai-dev/logs/` 收口记录。

### Out Of Scope

- 任何新功能交付（除 P0/P1 修复与对抗探查测试）。
- A3-audit（二期收口全量验证）的工作前置或替代。
- 凭证库标注 @MfaRequired 的实施（若裁定"应标注"，实施归 successor 小 plan 或并入 W13 后续轮次——本 plan 只出裁定与依赖边结论）。

## Execution Plan

### Phase 1 - 审计章程锚定与探查清单

Status: completed
Targets: `ai-dev/audits/2026-08/`（新建审计目录）、本 plan

- Item Types: `Decision | Proof`

- [x] **Decision**：建立审计目录 `ai-dev/audits/2026-08/{YYYY-MM-DD-HHMM}-deep-audit-nop-credential/`，落盘 audit-charter.md：审计范围（模块/实体/端点全清单）、六个维度的探查清单（每维度列出目标文件锚点、威胁假设、对抗用例）、回归基准声明（设计 §二 矩阵 + 四份 impl plan Closure 段引用）。（落盘 `ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/audit-charter.md`）
- [x] **Decision**：引用点/攻击面枚举核对：以 live code 复核 credential 管理面全部 BizModel action（含被禁用的旁路动作清单）、provider 全部明文出口方法、OAuth 回调 publicAccess 面、KMS 装配/校验链、Web 页面数据通道——与 charter 中的面清单一致。（29 action 注解对号；publicAccess 唯一面 oauthCallback:58；探查线索登记：NopCredentialOauthStateBizModel 裸 codegen CRUD → D2/D4 用例）
- [x] **Proof**：charter 中每个攻击面给出 file:line 锚点（live 核对，非引用旧 plan 结论）。（charter S1-S8 全部锚点 2026-08-17 live grep/read 核对）

Exit Criteria:

- [x] 审计目录与 charter 落盘，攻击面清单与 live code 一致（抽查锚点可对号）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 对抗审计执行（独立 fresh 子 agent）

Status: completed
Targets: `nop-credential/`（service/web/kms-vault）、`nop-ai/`（消费链）、`ai-dev/audits/2026-08/.../`

- Item Types: `Proof | Fix`

> 执行约束（roadmap 二期审计门禁）：本 Phase 的探查执行必须由 fresh 独立子 agent（非本 plan 编排 session 自查、非原 impl session 复用）分维度执行；编排 session 负责 dispatch 与证据收集。每个维度产出 detail 报告（findings + P0-P3 + 锚点 + 探查证据）。**职责分工**：探查子 agent 只探查与报告（不改产品代码）；P0/P1 修复由编排 session（或其指定的独立 fixer 子 agent）执行，修复后由**另一个 fresh 复核子 agent** 验证修复有效且未引入回归——保持"发现者不复核自己发现项的修复"的独立性。

- [x] **Proof（D1 明文边界回归）**：GraphQL/REST/Web 三通道不可达明文——xmeta `published=false` 结构性 + BizModel 层置空 + maskList 输出 + provider 明文出口（getCredentialData 的 owner/admin 判定、解密前 fail-closed 先序 delFlag）；对抗探查：越权身份/伪造 consumerRef/字段投影/旁路 action 复活路径。（D1-plaintext-boundary.md：8 用例显式结论，4 findings，明文边界+软删除锚点 PASS）
- [x] **Proof（D2 OAuth 引擎对抗）**：state 防重放与单次消费、回调 publicAccess 面最小化、token 加密回写（保留字段）、惰性刷新跨副本互斥（DB 行级锁恰一次）、disabled 凭证全路径拒绝、saveCredential 分组写/保留字段拒绝、beginOAuthFlow 归属校验（W11 回补项）；对抗探查：state 篡改/重放、回调参数注入、过期 state 复用、并发刷新竞态。（D2-oauth-engine.md：11 用例显式结论，5 findings 含 P1×1，引擎侧防护全部成立）
- [x] **Proof（D3 KMS 故障路径 fail-closed）**：启动期校验组全路径（不可达/401/403/404/材料非法/来源混合/残余含 active）、无本地降级、运行期零托管端调用、同名 bean 门控覆盖优先级、default-bean 缺失守卫、迁移残余列表只解不加密 + WARN 审计、reencryptAll keyset 翻页完备性（无漏行/无死循环）。（D3-kms-fail-closed.md：7 用例全成立，5 findings 全 P2/P3，零 P0/P1）
- [x] **Proof（D4 归属与授权绕过对抗）**：provider per-method 归属矩阵（明文出口 owner 唯一/管理面 owner+admin）、BizModel 两层防御（defaultPrepareQuery 结构性读过滤 + 写分级）、六旁路动作禁用不可复活、batchGet 过滤语义、NopCredentialAuth §6.3 判定矩阵三态（无记录/有记录无上下文/有记录有上下文角色求交 + admin 不豁免 + 引擎内部通道豁免的可达性）、grant/revoke 幂等、凭证删除物理级联清理、action-auth delta 面（saveCredential/mutation 对登录用户开放）、`nop.credential.admin-roles` 配置绕过探查。（D4-ownership-authz-bypass.md：10 用例显式结论，7 findings 含 P1×1，引用计数+软删除锚点 PASS）
- [x] **Proof（D5 密钥轮换/多 key 并存/密文格式兼容）**：cv1:{keyId} 解析与多 key 解密、keyId 篡改/伪造格式/跨 key 重放攻击、reencryptAll 全量迁移语义、`@sec:` 配置加密共存不受影响（`DefaultConfigValueEnhancer` + `AESTextCipher` 复用路径无交叉污染——附加锚点，归属 roadmap 横切关注表）。（D5-key-rotation-cipher-compat.md：7 用例显式结论，7 findings，cv1 格式 + @sec: 共存锚点 PASS）
- [x] **Proof（D6 nop-ai 消费链回归 + SPI 零变更）**：AiModelCredentialResolverImpl 优先级链（accountKey > credentialId > resolveApiKey）+ fail-closed（configured-but-broken 拒绝）+ 引用计数 registerUsage/unregisterUsage 生命周期；`ICredentialProvider` SPI 契约零变更核对（接口签名 diff 为空——附加锚点，归属设计 §4.1/§5.1 结论）。（D6-consumer-chain-spi.md：6 用例显式结论，4 findings 含 P1×1，三锚点 PASS 带保留）
- [x] **Fix（仅当确认 live defect）**：探查过程中确认的 P0/P1 现场修复 + focused 回归测试；修复遵循生成文件纪律与 owner-doc 同步。（P1×2 根因修复：D2-01/D4-01 OauthState 面双层收口 + D6-01 resolver bean 注册；聚焦测试 5+3 用例；独立复核 subagent VERIFIED——summary.md §四）
- [x] **Proof**：summary.md 汇总（findings 总表：编号/维度/严重度/锚点/建议方向）。（32 findings 总表 + 回归锚点结论表 + 子 agent 执行证据 + P1 修复复核证据 + 可达性口径更正注）

Exit Criteria:

- [x] 六维度 detail 报告 + summary.md 落盘，每条 finding 有 P0-P3 分级与 live 锚点。
- [x] 对抗探查中新增的探查测试（若有）已在 nop-credential/nop-ai 测试树落盘并通过（`./mvnw test -pl :nop-credential,:nop-credential-kms-vault,:nop-ai -am` 绿——不带 `-T 1C`，规避 W11 登记的 reactor 顺序 flake；探查测试标注审计来源）。（`-pl nop-credential -am` 全绿 171+32+1；`-pl nop-ai -am` nop-ai 全模块 SUCCESS，唯一 FAILURE 为 nop-auth-service pre-existing missing-tenant-id 顺序 flake——单独运行 5/5、2/2 全绿且该模块先于 nop-ai-service 执行，污染不可能来自本 plan；两新测试类 javadoc 标注 A1-audit 来源）
- [x] **无静默跳过**：每个维度的对抗用例结论是显式的（探查到/未探查到 + 证据），不允许"未覆盖且未声明"。
- [x] 独立子 agent 执行证据（task/session 标识）记录在 summary.md。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - finding 裁决收口 + deferred 再裁定 + 文档同步

Status: completed
Targets: 审计目录（裁决表）、`ai-dev/design/nop-credential/02-phase2-design.md`、`docs-for-ai/03-modules/nop-credential.md`、roadmap

- Item Types: `Decision | Fix | Follow-up`

- [x] **Decision**：finding 裁决表落盘（adjudication.md）：每条 finding → `fixed`/`successor`/`deferred-with-reason` 三态之一；P0/P1 全部 fixed（本 plan Phase 2）或有 successor plan 路径；零悬挂（无"待定"状态残留）。（`adjudication.md` §一：32 findings + 复核观察 2 条一一对应——fixed 5 组（P1×2 根因 + 3 doc-drift + orderNo）/successor 25 条（roadmap C1-hardening）/deferred-with-reason 2 条（D5-02 设计取舍已声明、D5-04 cv2 演进方向）；P0/P1 零降级）
- [x] **Decision**：8 项路由 deferred/follow-up 逐项再裁定并写入裁决表：PKCE（并入/再延 + 理由与规模建议）、消费链上下文丢失告警（做/不做 + 理由）、user 级共享与 disabled 全局收紧（重开/维持 + 理由）、凭证库 @MfaRequired 标注（依赖边结论 + 标注清单 + 实施归属：successor 路径）、oauth2 testCredential 连通性（归属裁定）、state 表批量清理（做/登记）、KMS 关窗自动化与进度上报（归属裁定）、reencryptAll 持 `CredentialCipher` 的「唯一解密点」措辞治理（修复措辞或维持裁定 + 理由）。（adjudication.md §二：1 PKCE 再延 optimization candidate（机密客户端 + state 闭环已验，规模建议 1 Phase）/2 上下文告警 不做（第 5 行设计语义，WARN 噪音）/3 user 共享与 disabled 收紧 维持 deferred（无重开证据；saveCredential 覆盖路径为设计声明边界）/4 @MfaRequired 应标注 → C1-hardening（依赖边 API-only 干净结论 + 缩窄清单：reencryptAll/delete/grant/revoke 标注，saveCredential/beginOAuthFlow 不标注）/5 testCredential 连通性 再延 out-of-scope（探测语义需设计）/6 state 批量清理 再延 optimization candidate（惰性清理+TTL 已覆盖）/7 关窗自动化与进度上报 再延 optimization candidate（runbook 可用 + WARN 信号已验）/8 措辞治理 **修复措辞**（owner doc 三处 + source-anchors 统一"唯一明文出口" + 设计 §二 术语裁定标注））
- [x] **Fix**：审计确认的 owner-doc/design drift 修复（docs-for-ai/03-modules/nop-credential.md 与设计文档 vs live 行为不一致处）。（`nop-credential.md`：功能概览"唯一明文出口"措辞（含 reencryptAll 进程内事实）/实体表 OauthState 收口标注/管理面权限节 OauthState 行 + action-auth 授予语义措辞修正（D4-03）/KMS 迁移路径 vault active-key-id 注意（D3-01）/轮换节 reencryptAll 执行语义标注（D5-01/D5-02）/子模块表与源码锚点措辞；`nop-ai.md`：D6-01 装配缺位修复标注 + 运行时消费装配语义重写（bean 注册事实 + 装配回归测试守护）；`source-anchors.md` CRED-001 措辞；设计 `02-phase2-design.md`：§二 A1-audit 术语裁定 + 矩阵复核结论标注、§4.3 reencryptAll 事务边界/软删行标注；action-auth delta：头注释措辞 + Usage orderNo 对齐基件（复核观察项））
- [x] **Follow-up**：roadmap A1-audit 条目收口更新（`done` 判定交由本 plan closure audit）；裁决产生的 successor 登记到 roadmap 相应位置（如凭证标注小 plan）。（roadmap 凭证库二期组新增 **C1-hardening** `todo` 工作项——§1.2 全部 successor findings + 可选项（PKCE/testCredential/state 清理/关窗自动化）+ @MfaRequired 标注小计划（C1a/C1b 拆分建议），依赖 A1-audit done；A1-audit 条目已附执行收口记录，`done` 状态随 closure audit 通过落定）
- [x] **Proof**：收口验证——`./mvnw clean install -pl :nop-credential -am -DskipTests -T 1C`（含 kms-vault 子模块聚合）+ `./mvnw test -pl :nop-credential,:nop-ai -am` 绿（不带 `-T 1C`，规避 W11 登记的 reactor 顺序 flake；若 Phase 2 有修复，回归面覆盖）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；若 Phase 2 落有修复代码，`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 0 NEW。（clean install BUILD SUCCESS；tests：nop-credential -am 全绿 171/32/1 + nop-ai -am 全模块 SUCCESS（唯一 FAILURE = nop-auth-service pre-existing missing-tenant-id 顺序 flake，单独运行 5/5、2/2 绿且 reactor 先序不可能被本 plan 污染）；doc-links exit 0（0 errors，5 warnings 均为 W16-design plan 引用其未产出交付物的既有提示，非本 plan 引入）；hollow scan 20 条 = W11 基线 13 + 本 plan 7 条预裁定禁用动作（OauthState 收口，与 Part A/B 先例同模式：显式消息 + javadoc + 收口测试断言），**0 条非预裁定 NEW**；`checkstyle:check -Pqa` 两受影响模块 BUILD SUCCESS）

Exit Criteria:

- [x] 裁决表零悬挂且与 summary.md findings 一一对应。（adjudication.md §四核对声明）
- [x] 8 项 deferred 再裁定全部有结论与理由（审计报告可查）。
- [x] P0/P1（若存在）已修复且有 focused 测试证明，或 successor plan 文件已存在于 `ai-dev/plans/`。（P1×2 根因 fixed + focused 测试 5/5、3/3 + 独立复核 VERIFIED；successor 走 roadmap C1-hardening 登记路径（roadmap 为本 mission 的 successor 注册面，plan 文件待 DRAFT 轮起草——裁决表 §三显式声明所有权路径））
- [x] owner-doc/design drift（若存在）已修复，或明确记录 No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 六维度审计全部执行且证据可复核（fresh 子 agent 执行 + file:line 锚点 + 探查输出）。（6 个互异 fresh session id + 独立复核/闭合审计各 1，记录于 summary §一；closure auditor 抽查锚点与 live code 一致）
- [x] finding 裁决表零悬挂；P0/P1 无静默降级（fixed 或 successor 所有权）。（adjudication.md：32+2 一一对应；P1×2 根因 fixed + 独立复核 VERIFIED；closure auditor 复核通过）
- [x] 8 项路由 deferred/follow-up 再裁定完成并记录。（adjudication.md §二，全部含理由/规模/归属）
- [x] 一期契约锚点回归结论显式记录（PASS/FAIL + 证据）：设计 §二 影响矩阵五行（cv1 密文格式/明文边界/软删除 fail-closed/引用计数/一期消费链零回归）+ 两附加锚点（`ICredentialProvider` SPI 零变更、`@sec:` 配置加密共存），每个锚点有归属维度的探查结论。（summary §三：全 PASS，两处"带保留"诚实标注（D6-01 一期自带缺口已修复、D6-02 消费方侧缺口归 C1））
- [x] 探查/修复引入的测试全部通过；受影响模块 build/test 绿。（nop-credential -am 171/32/1 全绿；nop-ai -am 全模块 SUCCESS——**保留**：唯一 FAILURE 为 nop-auth-service pre-existing missing-tenant-id 顺序 flake（单独运行 5/5、2/2 绿 + reactor 先序不可能被本 plan 污染 + clean baseline 复现登记）；closure auditor 独立重跑两个新测试类 5/5、3/3 绿）
- [x] 审计记录符合 `ai-dev/audits/README.md` 规范（目录/summary/分级）。（2026-08/ 月度目录 + summary.md + D1-D6 detail + adjudication + P0-P3 分级）
- [x] 独立子 agent closure-audit 已完成并记录证据（含：审计记录与 live code 一致性抽查、裁决表无悬挂复核）。（task `ses_ff319db3bffepeG7Qmh46DkoZO`：Verdict **Can Close**、0 Blocking、4 Advisory——已吸收进本段与 Closure Evidence）
- [x] `./mvnw test -pl nop-credential -am`（含 kms-vault）绿；`./mvnw test -pl nop-ai -am` 绿（均不带 `-T 1C`，规避 W11 登记的 reactor 顺序 flake）。（同上，带保留声明：nop-auth-service pre-existing flake 单独运行全绿）
- [x] 若本 plan 落有修复代码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high` 0 NEW（无修复代码则显式记录 N/A）。（20 条 = W11 基线 13 + 本 plan 7 条预裁定禁用动作（OauthState 收口，Part A/B 同型先例），0 条非预裁定 NEW；closure auditor 独立重跑 exit 0）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。（证据回写后运行通过，见 Closure Evidence）
- [x] checkstyle / 代码规范检查通过（受影响模块）。（`checkstyle:check -Pqa -pl nop-credential/nop-credential-service,nop-ai/nop-ai-service` BUILD SUCCESS）

## Deferred But Adjudicated

（Phase 3 裁决后填充，与 `adjudication.md` 同步；confirmed live defect 不在此处——P1 全部 fixed。）

### D5-02 reencryptAll 软删除行不迁移

- Classification: `out-of-scope improvement`（设计取舍已声明）
- Why Not Blocking Closure: 软删行本应物理清理后退役旧 key；本 plan 已在 owner doc + 设计 §4.3 显式声明该取舍与关窗前置动作（物理清理软删行），正确性无损。
- Successor Required: no（C1-hardening 可选增强 `includeDeleted` 模式）

### D5-04 cv1 密文无 keyId AEAD 绑定

- Classification: `watch-only residual`
- Why Not Blocking Closure: keyId 篡改防护由派生密钥分离间接保证（D5 用例 3 攻击验证 fail-closed 成立）；同材料异 keyId 属部署失误范畴（D5-06 收紧后进一步压缩）；cv2 格式演进登记为远期方向。
- Successor Required: no（cv2 演进时处理）

### 路由 deferred 项的终局裁定（8 项）

见 `adjudication.md` §二（PKCE 再延 optimization candidate / 上下文告警不做（第 5 行设计语义）/ user 共享与 disabled 收紧维持 deferred（无重开证据）/ @MfaRequired 应标注 → C1-hardening / testCredential 连通性再延 out-of-scope / state 批量清理再延 / 关窗自动化再延 / 措辞治理 fixed）。全部含 Why Not Blocking 或 successor 路径。

## Non-Blocking Follow-ups

（Phase 3 裁决表产出后汇总登记；confirmed live defect 不得出现在这里。）

- roadmap C1-hardening（`todo`）承接全部 successor 处置 findings（P2×5 + P3 打包）与可选项——所有权路径见 `adjudication.md` §三。
- pre-existing 测试基建 flake（非本 plan 引入，08-16/08-17 日志已登记 clean baseline 复现）：nop-auth-service 全模块套件顺序运行时 `TestMdxQuery`/`TestNopAuthUserBizModel` 的 missing-tenant-id 跨测试类污染（单独运行全绿）；workspace 全量 test-compile 偶发 plexus javac 进程内 CME（`-Dmaven.compiler.fork=true` 绕过）。

## Closure

Status Note: 六维度对抗审计全部执行（fresh 子 agent + 零静默跳过）且七个回归锚点全 PASS；32 findings 裁决零悬挂（P1×2 根因 fixed + 独立复核 VERIFIED，P2/P3 走 C1-hardening successor 或带理由 deferred）；8 项路由 deferred/follow-up 全部终局裁定；owner-doc/design drift 全部修复。唯一带保留项为 nop-auth-service pre-existing 顺序 flake（非本 plan 引入，单独运行全绿）。
Completed: 2026-08-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（fresh subagent，task `ses_ff319db3bffepeG7Qmh46DkoZO`）；修复复核 fresh subagent `ses_ff327c737ffe88XV1kLSy175gu`；六维度探查 fresh subagent 六个（ids 见 summary.md §一）
- Evidence:
  - Phase 1 Exit Criteria：PASS（charter 落盘 + 攻击面 live 对号 + 锚点核对；closure auditor 抽查一致）
  - Phase 2 Exit Criteria：PASS（D1-D6 detail + summary + 无静默跳过 + 子 agent 证据 + 测试落盘标注审计来源）；Anti-Hollow：修复 1 为真实行为变更（admin 判定 + 7 mutation 禁用，`NopCredentialOauthStateBizModel.java:52-165`）、修复 2 为真实 bean 注册（`app-service.beans.xml:17-18`）——closure auditor live 读码验证 + 调用链连通（`_service.beans.xml:16` 注册 / `_vfs/nop/ai/_module` 自动装载）+ 两个测试类独立重跑 5/5、3/3 绿
  - Phase 3 Exit Criteria：PASS（裁决表 32+2 一一对应零悬挂 / 8 项再裁定 / doc-sync live 核验 5 文件 / roadmap C1-hardening 登记）
  - Closure Gates：全 PASS（含保留声明）——`./mvnw test -pl nop-credential -am` 171/32/1 全绿；`./mvnw test -pl nop-ai -am` nop-ai 全模块 SUCCESS，**保留**：唯一 FAILURE = nop-auth-service pre-existing missing-tenant-id 顺序 flake（单独运行 TestMdxQuery 5/5、TestNopAuthUserBizModel 2/2 全绿；该模块 reactor 先于 nop-ai-service 执行不可能被本 plan 污染；clean baseline 复现登记于 08-16/08-17 日志）；checkstyle `-Pqa` 两受影响模块 BUILD SUCCESS（counts 来源：编排 session 执行记录于 daily log；closure auditor 独立重跑项 = 新测试类/hollow scan/doc-links 全过）
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 errors；5 warnings 为 W16-design plan 引用其未产出交付物的既有提示，非本 plan 引入；closure auditor 独立重跑 exit 0）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-credential --severity high`：20 条 = W11 基线 13 + 本 plan 7 条预裁定禁用动作（显式消息 + javadoc + 收口测试断言，Part A/B 同型先例），**0 条非预裁定 NEW**（closure auditor 独立重跑 exit 0）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md --strict` 退出码 0（证据回写后运行）
  - Deferred 项分类检查：Deferred But Adjudicated 仅含 D5-02（设计取舍已声明）/D5-04（watch-only）/8 项路由裁定指引，无 confirmed live defect 降级；Non-Blocking Follow-ups 仅含 C1-hardening 指针 + pre-existing 测试基建 flake 登记（closure auditor 复核确认）
  - Closure auditor 结论：**Can Close**（0 Blocking / 4 Advisory——保留措辞逐字复述、证据来源标注、wiring 测试断言增强（已裁 C1）、回写后跑 checklist——均已吸收进本段）

Follow-up:

- roadmap C1-hardening（`todo`）拥有全部 successor 处置 findings 的后继所有权（P2×5 + P3 打包 + 可选项 + @MfaRequired 标注小计划），见 `adjudication.md` §三
- pre-existing 测试基建 flake（nop-auth-service missing-tenant-id 顺序污染 + plexus javac CME）登记于 Non-Blocking Follow-ups，非本 plan 范围
- no remaining plan-owned work
