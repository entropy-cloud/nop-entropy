# A1-audit Finding 裁决表 + 8 项路由 deferred/follow-up 再裁定（adjudication）

> Date: 2026-08-17
> Plan: `ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md`（Phase 3）
> Inputs: `summary.md`（32 findings 总表 + 回归锚点结论）+ 各维度 detail 报告 + W9/W10/W11（A/B）/W12 impl plan 显式路由到本审计的 8 项 deferred/follow-up
> 处置三态：`fixed`（本 plan 修复）/ `successor`（后继所有权，路径见 §三）/ `deferred-with-reason`（watch-only / optimization candidate / out-of-scope improvement，含 Why Not Blocking）
> **零悬挂声明**：本表覆盖 summary.md 全部 32 条 findings（29 个独立根因，3 组跨维度同根因合并）+ 复核观察项 2 条 + 8 项路由 deferred 再裁定，无"待定"状态残留。P0/P1 全部 `fixed`，无静默降级。

## 一、Finding 裁决表（32 条 + 复核观察 2 条）

### 1.1 fixed（本 plan 修复，含 focused 测试与独立复核）

| 编号 | 严重度 | 处置 | 证据 |
|---|---|---|---|
| D2-01 / D4-01（同根因） | P1 | **fixed** | `NopCredentialOauthStateBizModel` admin-only 查询面 + 7 mutation 禁用 + action-auth delta `NopCredentialOauthState-main` roles="admin"；`TestNopCredentialOauthStateBizModel` 5/5 绿；独立复核 `ses_ff327c737ffe88XV1kLSy175gu` VERIFIED |
| D6-01 | P1 | **fixed** | `app-service.beans.xml` 注册 `nopAiModelCredentialResolver`（ioc:default + `_module` 自动装载）；`TestAiModelCredentialResolverWiring` 3/3 绿（容器级：bean 解析 + ChatServiceImpl 注入 + 可选依赖）；owner doc `nop-ai.md` 装配语义同步；独立复核 VERIFIED |
| D4-03 | P2 | **fixed**（doc drift） | delta 头注释措辞修正（"所有登录用户开放"→"按角色授予语义 + BizModel 运行时分级"）；owner doc `nop-credential.md` 同步 |
| D5-01 | P2 | **fixed**（doc drift） | owner doc + 设计 §4.3 A1-audit 标注：live 为单事务整体执行（幂等可重跑保持），大表分批建议；非代码缺陷（正确性无损） |
| 路由 deferred 项 8（"唯一解密点"措辞） | — | **fixed**（doc drift） | owner doc 三处 + source-anchors CRED-001 统一为"唯一明文出口"；设计 §二 A1-audit 术语裁定标注（reencryptAll 进程内重加密无出口，不变式成立） |
| 复核观察项 1（Usage orderNo） | P3 | **fixed** | delta `FNPT:NopCredentialUsage:query/mutation` orderNo 对齐基件实际值 10011/10012 |

### 1.2 successor（后继所有权：roadmap C1-hardening，见 §三）

| 编号 | 严重度 | 处置 | 建议方向（C1 内排期裁定） |
|---|---|---|---|
| D1-01 | P2 | **successor → C1** | batchGet/findList/findFirst 继承查询动作补 data 置空（第二层防御完备性；第一层 published=false 结构性已阻断） |
| D1-03 / D4-07（同根因） | P2 | **successor → C1** | saveCredential/beginOAuthFlow 越权错误码归一（防 credentialId 枚举探测存在性与 scope 类别；读路径已归一的对称收口） |
| D3-01 | P2 | **successor → C1** | Vault active-key-id 配置归一（读共享 `nop.credential.active-key-id` 兜底或启动期差异校验；owner doc 已加运维注意） |
| D4-02 | P2 | **successor → C1** | action-auth 静态 roles 与可配置 admin-roles 双源对齐（候选：delta roles 对齐缺省 `admin,nop-admin` + 文档声明静态层局限；更严方向非安全弱化） |
| D6-02 | P2 | **successor → C1** | `NopAiModelBizModel` 覆盖 delete（含 batchDelete/deleteByQuery 评估）接线 unregisterUsage（解运维死锁：模型删除后凭证永久不可删） |
| D1-02 / D4-05（同根因） | P3 | **successor → C1** | saveCredential 非 oauth2 更新路径补 delFlag fail-closed（墓碑防篡改，与 oauth2 分支对齐） |
| D1-04 | P3 | **successor → C1** | decrypt 异常 ARG_CIPHERTEXT 截断（密文串不进日志） |
| D2-02 | P3 | **successor → C1** | beginOAuthFlow 补 clientId 非空校验（对齐回调侧字段集） |
| D2-03 | P3 | **successor → C1** | engineUpdateInLock 无变化分支跳过回写（写放大 + version 漂移） |
| D2-04 | P3 | **successor → C1** | 锁内 probe 补 assertOauth2NotDisabled（TOCTOU 闭合，一行） |
| D2-05 | P3 | **successor → C1** | 结果页 JS 上下文改 JSON 编码（转义语境规范化） |
| D3-02 | P3 | **successor → C1** | reencrypt-page-size 下限校验（≥1，防空页死循环） |
| D3-03 | P3 | **successor → C1** | key-provider 取值 trim/大小写归一（错误归因准确化） |
| D3-04 | P3 | **successor → C1** | keyId 正则下沉 api 模块单源（消除双源维护） |
| D3-05 | P3 | **successor → C1** | Vault 启动期材料读取显式请求超时 |
| D4-04 | P3 | **successor → C1** | batchGet 存在性 oracle：行为归一或文档澄清（缺 id 抛错 vs 不可见剔除） |
| D4-06 | P3 | **successor → C1** | usage mutation 面收口（对齐 D2-01 同型：运行时 admin + 旁路禁用；防删除 usage 行间接解锁凭证删除） |
| D5-03 | P3 | **successor → C1** | reencryptAll 非 cv1 前缀行计数上报（关窗完备性信号） |
| D5-05 | P3 | **successor → C1** | decrypt 强制内层 `v1:` 前缀（堵 cv1 包装 legacy 弱路径 + 补测试空档） |
| D5-06 | P3 | **successor → C1** | passphrase isBlank 校验（length-1 → isBlank） |
| D5-07 | P3 | **successor → C1** | getMasterKeys() 可见性收窄或返回脱敏结构（无生产调用点，反模式清理） |
| D6-03 | P3 | **successor → C1** | registerUsage 前置校验凭证存在/未软删（行为收紧，owner doc 声明） |
| D6-04 | P3 | **successor → C1** | isEmpty → isBlank（accountKey/apiKey 空白判定） |
| 复核观察项 2（wiring 测试第 3 用例断言偏弱） | P3 | **successor → C1**（测试增强） | 断言改写为显式 bean 构造不依赖 provider/dao（当前隐式证明可接受） |

### 1.3 deferred-with-reason（无 successor 所有权要求）

| 编号 | 严重度 | 分类 | Why Not Blocking |
|---|---|---|---|
| D5-02 | P3 | **deferred（设计取舍已声明）** | 软删除行不重加密属运维语义边界（软删行本应物理清理后退役旧 key）；本 plan 已在 owner doc + 设计 §4.3 显式声明该取舍与前置动作（物理清理软删行再关窗）。若 C1 需要可选 `includeDeleted` 模式，属 enhancement 非缺陷 |
| D5-04 | P3 | **deferred（cv2 演进方向）** | cv1 密文无 keyId AEAD 绑定的完整性缺口由派生密钥分离间接防护（不同 keyId 材料不同 → 篡改 keyId 段导致解密失败 fail-closed，D5 用例 3 验证成立）；同材料异 keyId 配置属部署失误范畴（D5-06 收紧后进一步压缩）；cv2 格式演进（AAD 绑定 keyId+credentialId）登记为远期方向，不构成当前 supported baseline 缺陷 |

## 二、8 项路由 deferred/follow-up 再裁定（终局结论）

> 来源：W9-impl / W10-impl / W11-impl Part A/B / W12-impl plan 显式路由到 A1-audit 的登记项。按 guide 规则 20，来源历史 plan 不回改；终局结论只记录于此与 daily log。

### 1. PKCE（OAuth 2.1 纵深防御）——W9 Deferred

- **裁定：再延（optimization candidate），登记 C1-hardening 可选项**
- Why Not Blocking：本引擎为**服务端机密客户端**（clientSecret 不出服务进程）——PKCE 主防的授权码截获面已被 state 一次性消费 + 128bit CSPRNG + TTL + 回调参数注入 fail-closed 闭环（D2 用例 1/2/3/11 防护全部验证成立）。PKCE 增益主要针对公开客户端与恶意授权码注入场景，属纵深加固非缺口修补。
- 规模建议：小 plan 1 Phase（credential-type 元数据扩展 code_challenge 相关字段不必需——PKCE 可引擎自持 per-flow 状态 + S256 生成/校验 + 测试）；按 IdP 要求启用。

### 2. 消费链上下文丢失告警审计——W11 Part A §七#8 + Part B

- **裁定：不做（维持 watch-only residual）**
- Why Not Blocking：无用户上下文的消费是判定矩阵第 5 行**设计语义**（服务级信任：SPI 是服务端边界，收紧针对"人"的冒用不针对服务代码）；LLM 运行时消费链多为后台机器调用，无上下文是正常态非异常态——WARN 将在合法热路径持续噪音并污染审计存储。人为调用链（GraphQL → ChatService）上下文由框架传播。若未来需要取用可观测性，应随 getCredential 审计日志增强统一设计（登记 C1 观察项，非独立工作）。

### 3. user 级凭证共享（n8n sharing 模式）/ status=disabled 全局收紧——W11 Part A Deferred

- **裁定：维持 deferred（无重开证据）**
- Why Not Blocking：(a) user 级共享无需求实证，且 system 级"受控共享"已由 RBAC grant 覆盖（角色 ↔ 凭证 use 授权），user 级叠加共享与"归属即授权"模型正交复杂；(b) disabled 全路径拒绝按设计 §3.5 声明四路径（发起/回调/刷新/取用）验收一致——saveCredential 覆盖路径不拒 disabled 属显式声明边界（D2 用例 7 复核确认，非违约）；当前管理面无独立 disable 动作，收紧无实际触发面。D2-04（锁内 TOCTOU）作为独立 P3 已入 C1。

### 4. 凭证库敏感动作标注 @MfaRequired（reencryptAll/saveCredential/delete）——W12-impl Deferred

- **裁定：应标注；实施归 successor（C1-hardening 内子项或独立小 plan，登记 roadmap）**
- **依赖边结论**：`nop-credential-service → nop-biz-auth-api` 为 **API-only 干净边**——`@MfaRequired`/`IOperationMfaChecker` SPI 所在模块仅依赖 `nop-api-core`（W12 交付），不引入 nop-auth-service 运行时耦合；凭证库现依赖面零 nop-auth 构件（W11 验证）新增该边不破坏依赖边界裁定。
- **标注清单裁定**：`reencryptAll`（全量敏感）、`delete`（数据级不可逆）、`NopCredentialAuth.grant/revoke`（权限变更）应标注；`saveCredential` **不标注**（高频用户操作，每次保存强制 MFA 损害 UX；明文写入口已有写分级 + 归属校验两层）——此为对 W12 原登记清单（含 saveCredential）的缩窄裁定；`beginOAuthFlow` 不标注（state 一次性绑定已防劫持，动作仅返回 URL 不泄密）。
- 前置条件：部署侧 `nop.auth.operation-mfa.enabled` + 用户 MFA 启用；未装 checker SPI bean 时操作级检查自然跳过（W12 可选语义，零破坏）。规模：~1 Phase（注解 + 容器级测试 + owner doc）。

### 5. oauth2 凭证 testCredential 真实连通性——W9 Non-Blocking Follow-up

- **裁定：再延（out-of-scope improvement），登记 C1-hardening 可选项**
- Why Not Blocking：真实连通探测需先裁 IdP 侧探测语义（userinfo / introspection / 轻量 token），且可能消耗 refresh_token（rotation 副作用破坏刷新状态机）；当前 oauth2 `test` 动作语义 = 配置完备性校验，消费侧错误在运行时 fail-closed 暴露（链路无静默失效）。设计先行，非缺口。

### 6. nop_credential_oauth_state 过期行批量清理任务——W9 Non-Blocking Follow-up

- **裁定：再延（optimization candidate），登记 C1-hardening 可选项**
- Why Not Blocking：惰性清理（begin 时删过期行）+ TTL 600s + 行体量微小；无后台任务即无调度依赖。仅极端模式（长期零发起 + 海量历史行）有存储压力，属容量卫生非安全属性。C1 可选实现：nop-job 定时清理或启动期清理。

### 7. KMS 迁移关窗自动化（残余列表清空与 reencryptAll 联动）+ reencryptAll 进度上报——W10/W3 Follow-ups

- **裁定：再延（optimization candidate），登记 C1-hardening 可选项**
- Why Not Blocking：手动 runbook 可用且闭环有信号——reencryptAll 幂等可重跑（D5-01 标注后语义清晰）+ 残余列表每次启动 WARN 审计（D3 用例 6 验证）；进度上报为大表迁移 UX 增强。自动化与上报合并入 C1 可选子项设计。

### 8. reencryptAll 直接持 CredentialCipher 与"唯一解密点"措辞治理——W9 Non-Blocking Follow-up（终局处置）

- **裁定：措辞修复（本 plan Phase 3 已完成）**
- 处置：owner doc（`nop-credential.md` 功能概览/子模块表/源码锚点）+ `source-anchors.md` CRED-001 统一为**"唯一明文出口"**；设计 §二 落 A1-audit 术语裁定标注：`CredentialProviderImpl` 是明文离开模块边界的唯一通道，reencryptAll 进程内重加密（解密→重加密不出进程、无出口）不违反不变式。**终局结论：表述与实现一致，无代码动作。**

## 三、successor 登记（roadmap C1-hardening）

全部 successor 处置的 findings（§1.2 共 25 条）+ 路由裁定产生的可选项（PKCE / testCredential 连通性 / state 批量清理 / 关窗自动化与进度上报 / @MfaRequired 标注）登记为 roadmap 凭证库二期组新工作项 **C1-hardening**（`todo`，待 DRAFT 轮起草计划；建议拆分为 C1a 代码加固小 plan + C1b @MfaRequired 标注小 plan）。P2 项优先（D6-02 运维死锁 / D1-01 / D1-03 / D3-01 / D4-02），P3 打包。

## 四、零悬挂核对

- summary.md 32 findings ↔ 本表 32 行（1.1 fixed 5 组 + 1.2 successor 25 条（含同根因合并行）+ 1.3 deferred 2 条）+ 复核观察 2 条（1.1/1.2 各一）——一一对应，无未处置项。
- P0/P1（D2-01/D4-01、D6-01）全部 `fixed` 且有 focused 测试 + 独立复核 VERIFIED——无静默降级。
- 8 项路由 deferred 全部有终局结论与理由（§二）。
- 本 plan Deferred But Adjudicated / Non-Blocking Follow-ups 段落与本表同步（见 plan 文件）。
