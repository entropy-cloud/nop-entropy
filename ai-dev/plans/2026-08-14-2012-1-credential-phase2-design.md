# 凭证库二期设计（W9-design：OAuth 流程引擎 + 外部 KMS/HSM + 归属统一 + RBAC 授权）

> Plan Status: completed
> Completed: 2026-08-14
> Last Reviewed: 2026-08-14（两轮独立子 agent 对抗性审查达成共识：round1 ses_fffc99294ffezw7TyDU6SftoHI / round2 ses_fffbf9581ffex7h5XF1STVxB0v，发现项已全部处置；执行期四小节独立 review + 独立 closure audit 见 Closure 段）
> Mission: nop-credential-mfa
> Work Item: W9-design
> Source: `ai-dev/backlog/nop-credential-mfa-roadmap.md`（二期工作项 W9-design / Stage 8）；`ai-dev/design/nop-credential/00-vision.md` §三/§四；`ai-dev/design/nop-credential/01-architecture-baseline.md`
> Related: 一期 W1-W3（`2026-08-12-0615-1/2/3`，均 completed）；W7-successor（`2026-08-13-1118-3`，completed）；消费方 W9-impl/W10-impl/W11-impl（依赖本 plan 产出，后续起草）

## Purpose

把 `ai-dev/design/nop-credential/02-phase2-design.md` 从 13 行占位文档产出为凭证库二期四主题的正式设计（OAuth 流程引擎 / 外部 KMS/HSM 集成 / 凭证归属统一（系统级+用户级）/ RBAC 细粒度授权），每主题小节独立 review，供 W9-impl/W10-impl/W11-impl 三个后续 impl plan 直接消费。

本 plan 是**纯文档计划**：只产出/修改 `ai-dev/design/` 与 roadmap 状态，不改任何产品代码。

## Current Baseline

一期 live 产物（W1-W3 + W7/W7-successor 已 closure audit 通过，2026-08-14 一期 milestone done）：

- 模块结构：`nop-credential/`（api/dao/meta/service/web/app/codegen + `nop-credential/model/nop-credential.orm.xml`），根 pom 已注册
- 密码学层：`ICredentialKeyProvider`（`nop-credential-api/.../crypto/ICredentialKeyProvider.java`，active key + 多 key 并存）+ `CredentialCipher`（`cv1:{keyId}:{v1密文}` 包装，`nop-credential-service/.../crypto/CredentialCipher.java`）+ `DefaultCredentialKeyProvider`（本地环境变量/配置文件主密钥来源）
- 消费 SPI：`ICredentialProvider`（`nop-credential-api/.../ICredentialProvider.java`：getCredential/getCredentialData/testCredential/mask/registerUsage/unregisterUsage；fail-closed，唯一解密点在 service 层实现）
- 类型注册：`ICredentialTypeRegistry`（api/registry）+ `credential-type.xdef` + `_vfs/nop/credential/types/` 实例文件
- 管理 API：`NopCredentialBizModel`（save/saveCredential/get/findPage/maskList/typeList/test/reencryptAll/delete；data 恒脱敏，xmeta `published=false` 明文边界）
- ORM：`NopCredential`（`USAGE_SCOPE` 列 propId=7 precision=50，**一期仅声明占位，语义未定稿**）+ `NopCredentialUsage`（credentialId+consumerRef 唯一约束，引用计数删除拦截）
- 集成先例：W7-successor `IAiModelCredentialResolver`（nop-ai-api SPI + nop-ai-service impl，钩点 `ChatServiceImpl.buildHttpRequest`，优先级链 `accountKey > credentialId > resolveApiKey`，强 fail-closed，consumerRef `ai:NopAiModel:<id>`）——二期归属/授权校验的消费侧先例链
- OAuth 复用锚点：`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/login/OAuthLoginServiceImpl.java`（extends AbstractLoginService，授权码换取/Token 解析既有实现）
- 占位文档：`ai-dev/design/nop-credential/02-phase2-design.md`（13 行，2026-08-14 创建，仅规划范围声明）
- **用户裁决（2026-08-14）**：凭证模块**不做租户隔离**（租户为平台全局可开启能力）；归属统一只覆盖"系统级（共享/管理员管理）+ 用户级（个人私有）"两种场景，服务"多个外部系统共用同一密钥管理服务"

真正剩余的 gap：四主题设计内容全部为空（占位文档无任何架构决策）。

## Goals

- `02-phase2-design.md`（或按粒度裁决拆分后的多文件）产出四主题完整设计：每个主题小节含设计结论、使用契约、拒绝了什么、与一期契约的兼容性确认
- OAuth 流程引擎主题：授权码换取/刷新闭环/自动续期设计，复用 `OAuthLoginServiceImpl` 既有能力（能力盘点 + 复用边界），Token 加密存储语义不变（`cv1:` 兼容）
- 外部 KMS/HSM 主题：`ICredentialKeyProvider` 扩展 SPI 设计（Vault/云 KMS 主密钥来源；本地实现保留为默认），轮换与 KMS 故障 fail-closed 语义
- 归属统一主题：`scope=system|user` + `ownerId` 归属字段语义、`ICredentialProvider` 消费侧归属校验、BizModel 按归属过滤 CRUD、`usageScope` 一期占位语义定稿、多系统共用密钥服务的归属语义
- RBAC 主题：凭证级授权模型（谁可以用哪个凭证）、消费侧校验点、与归属模型的组合规则、与 nop-auth RBAC 的复用边界
- 产出 W9-impl/W10-impl/W11-impl 的设计→实施映射（每个 impl 消费哪些小节），供后续 impl plan 引用

## Non-Goals

- 任何实现代码（impl 归 W9-impl/W10-impl/W11-impl 各自的 plan）
- 租户隔离设计（用户已裁决不做）
- nop-integration / nop-metadata 深度迁移设计（W16-design 范围）
- 一期已落地契约的重开（`cv1:` 密文格式、明文边界、软删除语义、引用计数机制均保持不变，只做增量扩展）
- `@credential:` 配置 resolver（一期 baseline §4 已拒绝，除非二期设计显式翻案并写明理由）

## Scope

### In Scope

- `ai-dev/design/nop-credential/02-phase2-design.md`（或裁决后的拆分文件集）
- `ai-dev/design/nop-credential/README.md`（阅读顺序/索引同步）
- `ai-dev/design/README.md`（子系统索引行状态描述：一期"不做 RBAC/OAuth/KMS"表述更新为二期设计已定稿）
- `ai-dev/design/nop-credential/00-vision.md` / `01-architecture-baseline.md`（仅限 Phase 1 基线复核发现 drift 时的事实性修正，修正项在本 plan 内登记）
- `ai-dev/backlog/nop-credential-mfa-roadmap.md`（W9-design 状态：`planned` → closure audit 通过后 `done`；Stage 8 中"`usageScope` 一期占位（`instance`）"措辞与 live 列不符的 drift 一并修正）
- `ai-dev/logs/{year}/{month}-{day}.md`（执行日志）

### Out Of Scope

- `docs-for-ai/`（设计未实施前不更新使用文档；impl 落地后由各 impl plan 负责）
- 任何 `nop-credential/` 代码与 ORM 变更（归属字段 ORM 变更是 W11-impl 的事）
- A1-audit（凭证库二期安全审计，依赖三个 impl 全部 done）

## Execution Plan

### Phase 1 - 基线复核 + 粒度裁决 + 文档骨架

Status: completed
Targets: `ai-dev/design/nop-credential/02-phase2-design.md`、`ai-dev/design/nop-credential/README.md`

- Item Types: `Fix（仅限基线 drift 事实性修正） | Decision | Proof | Follow-up`

- [x] **Proof**：复核一期 live 锚点与设计文档（`00-vision.md`/`01-architecture-baseline.md`）一致——`ICredentialProvider` SPI 面、`NopCredential`/`NopCredentialUsage` ORM 列、`NopCredentialBizModel` 方法面、W7-successor 集成链；发现 drift 则登记为 Fix 项并做事实性修正（draft review 已实证一处：baseline 文档写 `deleted=true` 而 live ORM 列为 `DEL_FLAG/delFlag`）
- [x] **Fix**：draft review 已实证的基线 drift 修正——`01-architecture-baseline.md` 写 `deleted=true` 而 live ORM 列为 `DEL_FLAG/delFlag`（复核确认后修正文档措辞；若复核推翻实证则记录裁定）——复核成立，已修正；另修正复核中发现的 2 处同类事实性 drift（`id`→`credentialId`、管理面 `save`→`saveCredential`）与主密钥配置项名对齐
- [x] **Proof**：盘点 nop-auth RBAC 能力面（角色/用户-角色实体、权限校验机制的 live 锚点与作用层级），**落盘于 `02-phase2-design.md` RBAC 小节的暂存小节**（骨架阶段先建，Phase 2 在其上做复用边界裁定）——设计文档须自包含，W11-impl 只读设计文档即可拿到输入基线（落盘于 §6.0）
- [x] **Decision**：粒度裁决——四主题合入单一 `02-phase2-design.md` 还是拆分为多文件（评估依据：roadmap 粒度约束"预估超出单 plan 规模先行拆分裁定，不硬撑单文件"；若拆分，列出文件清单并在 README 建立索引；裁决结论写入文档头部与 daily log）——裁决：单文件（结论写入文档头部与 daily log）
- [x] **Follow-up**：建立文档骨架——四主题小节框架 + 每小节"设计结论/背景与动机/核心设计/拒绝了什么/与一期契约兼容性"五段结构 + 跨主题 out-of-scope 裁定区 + 设计→impl 映射区

Exit Criteria:

- [x] 粒度裁决结论已记录（单文件或拆分文件清单），`ai-dev/design/nop-credential/README.md` 阅读顺序已含二期设计文档条目
- [x] nop-auth RBAC 能力盘点已落盘于设计文档 RBAC 小节暂存小节（live 锚点可观察）
- [x] 文档骨架落盘（四主题小节标题 + 五段结构标题可被 grep 观察）
- [x] roadmap W9-design 状态为 `planned` 且 Work Items 行引用本 plan 路径（plan 转 active 时由起草者更新；执行时核对，若失真即修正）——核对一致，无需修正
- [x] No new test required: documentation-only change（Minimum Rules #25）
- [x] No owner-doc update required beyond README（本 phase 产物即 owner doc 本身）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 四主题小节产出（每小节独立 review 门槛）

Status: completed
Targets: `ai-dev/design/nop-credential/02-phase2-design.md`（或拆分文件集）

- Item Types: `Decision | Proof`

- [x] **Decision**：OAuth 流程引擎小节——授权码换取/刷新闭环/Token 自动续期入库的设计；`OAuthLoginServiceImpl` 能力盘点与复用边界（注意其为**入站 SSO 登录**服务、单一 `SsoConfig`，凭证库需要的是**出站**第三方 OAuth 客户端——按凭证类型多套 clientId/clientSecret 的配置模型、回调/redirect 端点（新增公开 REST 面）、state/CSRF 防护均为本小节须裁定的差异点）；凭证类型声明 OAuth 认证方式（`authType=oauth2` 既有声明位如何消费）；Token 加密存储语义与 `cv1:` 兼容；拒绝了什么（§三 落盘）
- [x] **Decision**：外部 KMS/HSM 小节——`ICredentialKeyProvider` 扩展 SPI 设计（Vault/云 KMS 作为主密钥来源的接口契约）；本地环境变量/配置文件实现保留为默认；多 key 并存与轮换语义在 KMS 场景的映射；KMS 不可用时的 fail-closed 语义；拒绝了什么（§四 落盘）
- [x] **Decision**：归属统一小节——`scope=system|user` + `ownerId` 字段语义与默认值策略；`ICredentialProvider` 消费侧归属校验规则（用户级凭证谁能取）与**调用方身份如何传播到校验点**（SPI 签名扩展 vs 上下文捕获的裁决，roadmap 原文"用户上下文与消费侧归属校验"）；`NopCredentialBizModel` 按归属过滤 CRUD 规则；`usageScope` 一期占位语义定稿（live 列无默认值、全仓库无 `instance` 值消费，仅声明未使用——roadmap Stage 8 "`usageScope` 一期占位（`instance`）"措辞与 live 不符，以本裁定为准并修正 roadmap）；多系统共用同一密钥管理服务的归属语义（系统级=共享/管理员管理，用户级=个人私有）；"不做租户隔离"裁决的落点说明；拒绝了什么（§五 落盘：usageScope 废弃、上下文捕获裁决）
- [x] **Decision**：RBAC 细粒度授权小节——凭证级授权模型（谁可以使用哪个凭证：授权主体/客体/动作建模，是否引入新 ORM 实体或复用 nop-auth 角色/权限机制；以 Phase 1 的 nop-auth RBAC 能力盘点为输入）；消费侧校验点（`ICredentialProvider` 取用时如何校验）；与归属模型的组合规则（系统级/用户级 × 授权矩阵）；与 nop-auth RBAC 的复用边界（nop-credential 对 nop-auth 的依赖方向裁定，须与 vision §五.1 依赖约束一致）；拒绝了什么（§六 落盘：新实体 NopCredentialAuth、不依赖 nop-auth）
- [x] **Proof**：每小节完成后立即由独立子 agent（fresh session）review 该小节（想象性分析：W9/W10/W11-impl 能否仅凭该小节起草 impl plan），review 发现回修后才进入下一小节（review task id / log 引用记录在 daily log）——四轮 review：§三 `ses_fffaf6db7ffeTtLxogNyflaBLb`、§四 `ses_fffa87a70ffeM5yFYR6vQPMQvT`、§五 `ses_fffa02adbffeFAosgW1P35lMTU`、§六 `ses_fff97ac5affeeWq59U1zWNaSfJ`，发现（含 6 个 Blocker）全部回修，见 daily log 08-14 条目

Exit Criteria:

- [x] 四主题小节全部产出，每小节含"设计结论 + 拒绝了什么 + 与一期契约兼容性"三要素（grep 小节标题与要素标题可观察）
- [x] 每小节的独立 review 已完成且发现的问题已回修（review task id / log 引用记录在 daily log）
- [x] 设计遵守 `ai-dev/design/00-design-writing-guide.md`：接口名+职责可以出现，**字段语义/取值域等使用契约可写**（如 `scope=system|user` 的语义与默认值策略），但不写 Java/ORM 列级定义、类实现、实现步骤；不引用 `ai-dev/discussions/`、`ai-dev/analysis/`；无 "Proposed vs Current" 演进叙事（四轮 review 均核查规范合规）
- [x] 一期契约兼容性确认显式成立：`cv1:` 密文格式、明文边界（`published=false` + BizModel 置空）、软删除 fail-closed、引用计数机制在四主题下均不变或显式声明增量（§二 矩阵）
- [x] No new test required: documentation-only change（Minimum Rules #25）
- [x] No owner-doc update required beyond design docs（未实施不写 docs-for-ai）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 跨主题收口 + 设计→impl 映射 + 索引同步

Status: completed
Targets: `ai-dev/design/nop-credential/02-phase2-design.md`、`ai-dev/design/nop-credential/README.md`、`ai-dev/design/README.md`、`ai-dev/backlog/nop-credential-mfa-roadmap.md`

- Item Types: `Decision | Proof | Follow-up`

- [x] **Decision**：各主题 out-of-scope / deferred 裁定汇总——每个延期项落到 `Deferred But Adjudicated` 语义（classification + Why Not Blocking + Successor Required），不允许无理由 deferred（§七 落盘：9 项 + `@credential:` 显式维持拒绝）
- [x] **Follow-up**：设计→impl 映射——W9-impl（OAuth）/W10-impl（KMS）/W11-impl（归属+RBAC）各自消费哪些小节、预计触碰的 Protected Area（跨模块公共 API / ORM 模型结构变更需 impl plan 走 plan-first 的清单）（§八 落盘）
- [x] **Follow-up**：索引同步——`ai-dev/design/nop-credential/README.md` 阅读顺序/职责边界更新；`ai-dev/design/README.md` 子系统索引行状态描述更新（"一期不做 RBAC/OAuth/KMS" → 二期设计已定稿待实施）
- [x] **Proof**：roadmap 同步——W9-design 行/Stage 8 措辞与本 plan 裁决结果一致（含三处已登记 drift：Stage 8 "`usageScope` 一期占位（`instance`）"措辞、若粒度裁决为拆分则"落 `02-phase2-design.md`"表述、W9-impl 行"复用 OAuthLoginServiceImpl 既有 OAuth 客户端能力"的入站/出站误解框架）——①已修正（裁定废弃）；②粒度裁决为单文件，表述一致无需变更；③已修正（含 Framework reuse 表与 Stages 表 8/9 行同框架修正）
- [x] **Proof**：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（该检查为 repo 级：若出现与本设计无关文件的预存 broken link，先做最小修复并在 daily log 登记，不允许绕过门禁）——首跑 1 error 为本设计文件自身 VFS 路径写法（非预存无关错误），改为完整 repo 资源路径后复跑退出码 0
- [x] **Follow-up**：roadmap W9-design 状态在 closure audit 通过后标 `done`（本 plan Closure 完成后执行）——closure 段执行并回填

Exit Criteria:

- [x] deferred 裁定区完整（每项有 classification 与理由，无悬挂）
- [x] 设计→impl 映射区存在且 W9/W10/W11-impl 三个工作项各有明确消费小节清单与 Protected Area 提示
- [x] 两个 README 索引与文档实态一致（引用路径全部存在）；roadmap W9-design 行/Stage 8 措辞与本 plan 裁决结果一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] No new test required: documentation-only change（Minimum Rules #25）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：不涉及代码变更，`./mvnw compile`/`./mvnw test`/checkstyle 条目按 guide 模板说明删除；`scan-hollow-implementations.mjs` 同理 N/A（documentation-only，无 affected-module）。

- [x] 四主题小节全部产出且各自通过独立 review（evidence 在 daily log / 本 plan Closure 段）
- [x] 一期契约兼容性确认章节成立（`cv1:`/明文边界/软删除/引用计数无回退）（§二 矩阵 + 各主题 §x.5；closure audit 实读锚点验证）
- [x] 粒度裁决、`usageScope` 定稿、依赖方向裁定三项关键 Decision 均有明确结论 + 拒绝了什么（单文件裁决头部；usageScope 废弃 §5.1；依赖方向 §3.3/§6.1）
- [x] deferred 项全部带分类与 non-blocking 理由，无 in-scope 缺口被降级（§七 9 项全分类；reencryptAll 分页限制转为 W10-impl 显式交付物）
- [x] `ai-dev/design/nop-credential/README.md` 与 `ai-dev/design/README.md` 已同步
- [x] 独立子 agent closure-audit 已完成（抽查文档内容与一期 live 代码锚点一致性，不只是小节标题存在）并记录证据（task `ses_fff8aa362ffe1RlqC1HREJDJ4H`，READY_TO_CLOSE，见 Closure 段）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] roadmap W9-design 已标 `done` 并回填 plan 引用

## Deferred But Adjudicated

（起草时无；执行中若某主题小节裁定需要外部输入（如 Vault 环境）无法完成设计，按 classification + Why Not Blocking + Successor Required 格式在此登记，不允许静默留空。）

## Non-Blocking Follow-ups

- 设计实施后由各 impl plan 负责 `docs-for-ai/03-modules/nop-credential.md` 的二期章节补充（本 plan 显式不做）

## Closure

Status Note: 纯文档计划收口——`02-phase2-design.md` 从 13 行占位产出为四主题完整设计（OAuth 出站引擎 / KMS 材料交付 / 归属统一 / RBAC 凭证级授权），每小节经独立 fresh-session review 后回修（6 个 Blocker 全部处置），一期契约兼容性矩阵成立，deferred 全分类，索引/roadmap 同步，两项门禁退出码 0，独立 closure audit 判定 READY_TO_CLOSE。W9-impl/W10-impl/W11-impl 可直接以 §八 映射起草 impl plan。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session），task `ses_fff8aa362ffe1RlqC1HREJDJ4H`；执行期四小节 review：§三 `ses_fffaf6db7ffeTtLxogNyflaBLb`、§四 `ses_fffa87a70ffeM5yFYR6vQPMQvT`、§五 `ses_fffa02adbffeFAosgW1P35lMTU`、§六 `ses_fff97ac5affeeWq59U1zWNaSfJ`
- Evidence:
  - Phase 1/2/3 全部 `Status: completed`，checklist 18/18、Exit Criteria 20/20 全勾（audit 逐项核对 PASS）
  - 文档 vs live 代码锚点抽查 20+ 处全部属实：`OAuthLoginServiceImpl`（入站/单 SsoConfig）、`credential-type.xdef:14` authType 声明位、`ICredentialKeyProvider` 三方法、`DefaultCredentialKeyProvider` 配置项与启动期 fail-closed、`credential-defaults.beans.xml` ioc:default 覆盖可行、reencryptAll 分页上限限制属实（NopCredentialBizModel:295-296）、USAGE_SCOPE 无默认值且全仓库零消费、loadActiveCredential 仅查 delFlag、标准 save 抛异常、nop-auth.orm.xml 行锚 237/293/552/602 精确、NopAuthConstants admin/nop-admin 与 §5.1-8 缺省对齐
  - §七 deferred 诚实性 PASS：9 项全分类无悬挂；reencryptAll 分页限制为 W10-impl 显式交付物（§八）而非静默 deferred
  - 索引同步 PASS：两 README、roadmap 三处 drift（Stage 8 usageScope 措辞 / W9-impl 行入站出站 / Framework reuse + Stages 表）均修正
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2284 文件，25090 refs，0 errors）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - 纯文档计划：mvnw compile/test/checkstyle 门禁按本 plan Closure Gates 头部说明 N/A（零代码变更，git 工作区仅 ai-dev/ 文档改动）；信息性基线运行 `./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C` 在被 -am 连带构建的无关模块 nop-stream-runtime 存在 2 个预存失败（TestChannelMessageServiceIoC，channelMessageService bean 装配），与本 plan 的零代码改动无因果，登记见 daily log
- Deferred 检查：无 in-scope live defect / contract drift 被降级（audit 确认）

Follow-up:

- W9-impl / W10-impl / W11-impl 按 roadmap 依赖顺序起草（消费 §八 映射；各自 plan-first 事项已列）
- no remaining plan-owned work>
