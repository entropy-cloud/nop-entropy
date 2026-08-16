# A1-audit 凭证库二期安全审计 — 汇总报告（summary）

> Audit Type: deep-audit（六维度对抗探查 D1-D6）
> Date: 2026-08-17
> Plan: `ai-dev/plans/2026-08-17-0447-1-credential-phase2-security-audit.md`（Phase 2）
> Charter: `audit-charter.md`（本目录）
> Regression Baseline: `ai-dev/design/nop-credential/02-phase2-design.md` §二 影响矩阵（五行）+ 两附加锚点

## 一、执行方式与独立子 agent 证据

按 roadmap 二期审计门禁，六个维度均由 **fresh 独立子 agent**（互不相同、非本 plan 编排 session、非原 impl session）执行探查；探查子 agent 只探查与报告不改产品代码。P0/P1 修复由编排 session 执行，并由**另一个 fresh 复核子 agent** 验证（发现者不复核自己发现项的修复）。

| 维度 | 报告 | 执行 subagent task id | Findings |
|---|---|---|---|
| D1 明文边界回归 | `D1-plaintext-boundary.md` | `ses_ff355739cffejYDSKcjfOZHVAt` | 4（P2×2 / P3×2） |
| D2 OAuth 引擎对抗 | `D2-oauth-engine.md` | `ses_ff3553f0affeStG83kx06phcYn` | 5（**P1×1** / P3×4） |
| D3 KMS 故障路径 fail-closed | `D3-kms-fail-closed.md` | `ses_ff3551808ffew4J3TicHWKrNRd` | 5（P2×1 / P3×4） |
| D4 归属与授权绕过对抗 | `D4-ownership-authz-bypass.md` | `ses_ff354e0f3ffeHSigGpuFaZFNyr` | 7（**P1×1** / P2×2 / P3×4） |
| D5 密钥轮换/多 key 并存/密文格式兼容 | `D5-key-rotation-cipher-compat.md` | `ses_ff354b526ffeO0Etpi4Sp4qBNx` | 7（P2×1 / P3×6） |
| D6 nop-ai 消费链回归 + SPI 零变更 | `D6-consumer-chain-spi.md` | `ses_ff3548cc4ffejoRDWba75m02KI` | 4（**P1×1** / P2×1 / P3×2） |
| P1 修复独立复核 | （复核报告见本文件 §四） | `ses_ff327c737ffe88XV1kLSy175gu` | VERIFIED |

合计 **32 条 findings**（P0×0 / P1×3（两个同根因）/ P2×7 / P3×22）。每维度对抗用例逐条显式结论（探查到/未探查到 + file:line 证据），无静默跳过——见各 detail 报告结论表。

## 二、Findings 总表（编号 / 维度 / 严重度 / 锚点 / 建议方向）

| 编号 | 维度 | 严重度 | 锚点 | 一句话 | 建议方向 |
|---|---|---|---|---|---|
| D1-01 | D1 | P2 | `NopCredentialBizModel.java`（batchGet:784/findList/findFirst 继承面） | batchGet/findList/findFirst 返回实体未置空 data（get/findPage/saveCredential 均有置空）——第二层防御在继承查询动作面不完整；当前无直接泄漏路径（published=false 结构性排除） | 继承动作补置空（successor 小修） |
| D1-02 | D1 | P3 | `NopCredentialBizModel.java`（saveCredential 非 oauth2 更新路径） | 已软删凭证无 delFlag 拒绝：墓碑行密文/元数据可被改写（oauth2 分支有检查，两分支不一致） | 与 D4-05 合并处置 |
| D1-03 | D1 | P2 | `NopCredentialBizModel.saveCredential`/`OAuthFlowService.beginOAuthFlow` | 越权错误码三态可区分（NOT_FOUND/ADMIN_REQUIRED/OWNER_OR_ADMIN）——credentialId 枚举可探测存在性与 scope 类别；ownerId 不经错误面外发 | 错误码归一（successor，与 D4-07 同根） |
| D1-04 | D1 | P3 | `CredentialCipher.decrypt`（ARG_CIPHERTEXT param） | 三处把完整密文串放异常 param——密文（非明文）进日志，卫生级 | param 截断（successor 小修） |
| D2-01 | D2 | **P1** | `NopCredentialOauthStateBizModel.java:9-13` + `_nop-credential.action-auth.xml:44-57` | state 表裸 codegen CRUD 面未收口：持有 mutation 权限的普通登录用户可伪造/篡改（token 落点劫持）/删除（DoS）/侦查 state 行（可达性两条路径见注） | **fixed in this plan**（双层收口 + 测试） |
| D2-02 | D2 | P3 | `OAuthFlowService.java:176-199` | beginOAuthFlow 只校验 clientSecret 非空、clientId 空则放空串（与回调侧不对称） | 发起侧补 clientId 校验（successor 小修） |
| D2-03 | D2 | P3 | `CredentialProviderImpl.java:318-327` | 惰性刷新锁内"无需刷新"分支仍整行重加密回写（写放大 + version 漂移） | 无变化跳过回写（successor 小修） |
| D2-04 | D2 | P3 | `CredentialProviderImpl.java:304-316` | engineUpdateInLock 锁内只复查 delFlag 不复查 disabled（TOCTOU 窗口，当前可触发性低） | 锁内 probe 补 disabled 检查（successor 小修） |
| D2-05 | D2 | P3 | `OAuthFlowService.java:274-281` | resultUrl 以 escapeHtml 注入 JS 字符串上下文（转义语境混用，配置信任边界内） | JS 上下文改 JSON 编码（successor 小修） |
| D3-01 | D3 | P2 | `VaultCredentialKeyProvider.java`（active-key-id 读取） | Vault provider 静默忽略共享配置 `nop.credential.active-key-id`（只读 vault.active-key-id），local→vault 迁移可无声改变 active key | 归一读取或启动校验（successor） |
| D3-02 | D3 | P3 | `NopCredentialBizModel.reencryptAll` + `CredentialConfigs` | reencrypt-page-size 可配 0 → 空页死循环（`page.size()==pageSize` 恒真） | 配置下限校验（successor 小修） |
| D3-03 | D3 | P3 | `DefaultCredentialKeyProvider.init` | key-provider 值大小写/空白不匹配时报 "module missing"（fail-closed 保持，错误归因误导） | trim/大小写归一（successor 小修） |
| D3-04 | P3→登记 | P3 | `VaultCredentialKeyProvider`（keyId 模式复制） | kms-vault 硬编码复制 keyId 正则，与 CredentialCipher 双源维护 | 下沉 api 模块（successor 小修） |
| D3-05 | D3 | P3 | `VaultCredentialKeyProvider.init`（fetchMaterial） | 启动期材料读取无请求级超时（依赖全局 read-timeout，显式置空则启动无限阻塞） | 显式超时（successor 小修） |
| D4-01 | D4 | **P1** | 同 D2-01（charter S4 线索确认） | 同根因：OauthState 裸 CRUD 零鉴权 + delta 未收紧（与 D2-01 合并处置） | **fixed in this plan**（同 D2-01） |
| D4-02 | D4 | P2 | `nop-credential.action-auth.xml` + `CredentialConfigs.admin-roles` | action-auth 静态 roles="admin" 与可配置 admin-roles（缺省 admin,nop-admin）双源真相漂移：nop-admin 在 GraphQL 层被字面 admin 拒绝（更严方向） | 双源对齐（successor，W13 后续或凭证小 plan） |
| D4-03 | D4 | P2 | `nop-credential.action-auth.xml` 头注释 | delta 注释宣称 query/mutation「对所有登录用户开放」，但平台语义为 deny-by-default（未绑定角色的 permission 拒绝，`SiteCacheData.isPermitted:80-82`）——注释措辞与实际生效机制不符（实际=部署方按角色管理授予） | 注释措辞修正（Phase 3 doc-sync 处置） |
| D4-04 | D4 | P3 | `NopCredentialBizModel.batchGet` | 缺 id 抛错 vs 不可见 id 静默剔除——存在性 oracle 与自称"与 get 同口径归一"不符 | 文档澄清或行为归一（successor 小修） |
| D4-05 | D4 | P3 | 同 D1-02 | 同根因：saveCredential 非 oauth2 更新路径缺 delFlag fail-closed | 合并处置 |
| D4-06 | D4 | P3 | `NopCredentialUsageBizModel`（mutation 面） | usage mutation 面单层防护（无运行时判定/旁路未收口），删除 usage 行可间接解锁被拦截的凭证删除 | 对齐收口（successor 小修，与 D2-01 同型） |
| D4-07 | D4 | P3 | 同 D1-03 | 同根因：saveCredential 错误码存在性+scope oracle | 合并处置 |
| D5-01 | D5 | P2 | `NopCredentialBizModel.reencryptAll`（事务边界） | 「逐条提交可重跑」声明与生产路径不符：GraphQL mutation 整体单事务，大表迁移长事务（幂等兜底，正确性无损） | owner doc 措辞修正或分批实现（Phase 3 裁定） |
| D5-02 | D5 | P3 | `NopCredentialBizModel.reencryptAll`（delFlag=0 过滤） | 软删除行旧密文永不重加密，旧 key 退役后明文永久不可恢复（关窗完备性盲区，设计未声明） | 设计声明或带 delFlag 选项（Phase 3 裁定） |
| D5-03 | D5 | P3 | `NopCredentialBizModel.reencryptAll`（非 cv1 行 continue） | 非 cv1 前缀行静默跳过，与解密失败 fail-closed 双标，关窗完备性无信号 | 计数上报（successor 小修） |
| D5-04 | D5 | P3 | `CredentialCipher`（无 AEAD 绑定） | cv1 密文无 keyId 绑定的完整性保护，同材料异 keyId 改写不可检测（keyId 篡改防护依赖派生密钥分离的间接保证） | cv2 演进方向登记（deferred） |
| D5-05 | D5 | P3 | `CredentialCipher.decrypt`（内层 v1: 前缀） | cv1: 包装的 legacy 载荷可落入 MD5+静态 IV 弱路径（fail-closed 保持，测试有空档） | 内层前缀强制（successor 小修） |
| D5-06 | D5 | P3 | `DefaultCredentialKeyProvider`（length-1 检查） | master-keys/残余条目空白 passphrase 可通过校验（弱密钥配置缺口） | isBlank 校验（successor 小修） |
| D5-07 | D5 | P3 | `DefaultCredentialKeyProvider.getMasterKeys()` | public 返回 keyId:passphrase 明文列表（主密钥泄露面反模式，无生产调用点） | 收窄可见性（successor 小修） |
| D6-01 | D6 | **P1** | `AiModelCredentialResolverImpl`（无 beans 注册）+ `ai-defaults.beans.xml` | resolver 从未注册（NopIoC 无扫描）→ ChatServiceImpl.credentialResolver 恒 null，credentialId 运行时消费链全部署形态静默失效（configured-but-broken）；owner doc "resolver bean 仍创建" 与 live 矛盾 | **fixed in this plan**（bean 注册 + wiring 测试 + owner doc 同步） |
| D6-02 | D6 | P2 | `NopAiModelBizModel.java:72-78`（仅覆盖 save） | delete 模型行不 unregisterUsage → 引用计数泄漏使凭证删除被永久拦截（运维死锁，fail-closed 方向） | delete 接线 unregisterUsage（successor 小修） |
| D6-03 | D6 | P3 | `CredentialProviderImpl.registerUsage` | 不校验凭证存在/未软删——配错 credentialId 管理面静默成功，错误延迟到运行时 | 前置校验（successor 小修，行为收紧需 owner doc） |
| D6-04 | D6 | P3 | `AiModelCredentialResolverImpl`（isEmpty 判定） | 优先级链与 FIELD_EMPTY 校验用 isEmpty 不捕获纯空白 | isBlank（successor 小修） |

> 编号去重：D2-01 与 D4-01 同根因（同一修复）；D1-02=D4-05、D1-03=D4-07 为跨维度同根因交叉确认。独立根因数 = 32 − 3 = 29。
>
> **可达性口径更正（编排 session live 复核）**：D2-01 原文"生成基线无 roles = 全体登录用户可用"引用的平台代码有误——live `SiteCacheData.isPermitted`（nop-auth-service `SiteCacheData.java:79-84`）对空 roles 集 **return false（deny-by-default）**，与 D4-01 的条件可达性分析一致。修正后的可达性为两条真实路径：(a) 部署将 `FNPT:NopCredentialOauthState:query/mutation` 资源绑定到任意非 admin 角色（基线把资源+管理页注册进 admin sitemap，绑定是 UI 引导的正常操作）；(b) 无 `IActionAuthChecker` 的部署（`GraphQLActionAuthChecker.isAllowAccess`:118-124 checker==null → 恒放行；`nop-credential-app` standalone 不含 nop-auth-service，真实存在）。P1 定级与修复决策不变（BizModel 运行时收口对全部部署形态生效，含无 checker 场景）。

## 三、回归锚点结论（设计 §二 五行矩阵 + 两附加锚点）

| 锚点 | 归属维度 | 结论 | 证据 |
|---|---|---|---|
| cv1:{keyId} 密文格式兼容 | D5 | **PASS** | CredentialCipher 自一期 commit `dfb4f7ed7` 零修改 + TestCredentialCipher 16/16 + KMS 仅换材料来源（D5 报告） |
| 明文边界（published=false + BizModel 置空 + 唯一解密点） | D1 | **PASS** | schema 结构性排除 + 解析期拒绝 + 测试固化；batchGet/findList/findFirst 置空缺失为一期遗留纵深完备性项（D1-01，P2，非回归） |
| 软删除 fail-closed（delFlag 解密前先序） | D1/D4 | **PASS**（双维独立复核一致） | 全部解密出口先序拒绝且先于归属判定；saveCredential 写路径缺口非解密出口（D1-02/D4-05，P3，登记） |
| 引用计数（registerUsage/consumerRef） | D4/D6 | **PASS（带保留）** | 删除拦截 SPI 语义零变更（D4）；消费方侧生命周期缺口 D6-02（P2）为消费方缺陷非契约回归 |
| 一期消费链（W7-successor 等）零回归 | D6 | **PASS（带保留）** | git 证实二期零触碰 nop-ai 消费链；D6-01 证明一期功能本身从未接通（一期自带缺口，非二期回归，本 plan 已修复） |
| 附加：`ICredentialProvider` SPI 契约零变更 | D6 | **PASS** | `git log --follow` 单 commit（一期 `1d12af9dc`）此后 diff 为空，6 方法与设计 §4.1/§5.1 一致；`ICredentialKeyProvider` 同样零变更（实为一期接口，任务书"二期新增"表述与 git/live 不符——已在本报告更正） |
| 附加：`@sec:` 配置加密共存不受影响 | D5 | **PASS** | 前缀识别互斥、AESTextCipher 实例独立、双向误用均显式 fail-closed（D5 报告） |

OAuth 增量行（status=disabled 四路径拒绝）与 §3.5/§4.5/§5.5/§6.5 兼容小节的对抗复核结论见各维度报告（全部 PASS；唯 state 管理面 FAIL → D2-01 已修复）。

## 四、P1 修复与独立复核证据（Phase 2 Fix item）

### 修复 1（D2-01/D4-01）：NopCredentialOauthState 管理面双层收口

- `NopCredentialOauthStateBizModel.java` 重写：admin-only 查询面（defaultPrepareQuery/get/batchGet → requireCredentialAdmin，无登录态同样拒绝）+ 7 个标准 mutation 全部禁用（UnsupportedOperationException，身份无关）。
- `nop-credential.action-auth.xml` delta：新增 `NopCredentialOauthState-main` 小节（query/mutation roles="admin"，orderNo 10008/10009 与基件同构嵌套精确覆盖）。
- 聚焦测试 `TestNopCredentialOauthStateBizModel`（5 用例）：admin 三入口放行 / 非 admin+无登录态拒绝（错误码断言）/ 7 动作收口 / 引擎 dao 直写不受影响。

### 修复 2（D6-01）：AiModelCredentialResolver bean 注册

- `nop-ai-service/.../beans/app-service.beans.xml`：注册 `nopAiModelCredentialResolver`（ioc:type=IAiModelCredentialResolver, ioc:default=true；`_vfs/nop/ai/_module` 标记使模块 beans 自动装载）。
- `nop-ai-service/pom.xml`：test 依赖 nop-ai-core + nop-http-client-jdk（wiring 测试部署组合，kms-vault 先例）。
- 聚焦测试 `TestAiModelCredentialResolverWiring`（3 用例，完整 app 容器）：bean 按类型/id 解析 / ChatServiceImpl.credentialResolver 持同一实例（反射断言）/ 可选依赖启动安全（无 nop-credential 部署不失败，fail-closed 语义保持）。

### 独立复核（fresh subagent `ses_ff327c737ffe88XV1kLSy175gu`）：**VERIFIED**

- 修复有效性 4 项 PASS（攻击面闭合 / 引擎回归面 dao 直写零影响 / bean 定义语法与模块装载机制 / 副作用评估）；
- 无回归 3 项 PASS（动作签名 @Override 一致 / 聚焦测试 5/5+3/3 实测通过 / pom 仅 test scope）；
- 测试质量 PASS（真实断言非空转）；生成文件纪律 PASS（无 `_` 前缀文件手改）；
- 复核观察项（P3，非本修复引入）：delta 中 Usage 条目 orderNo 10008/10009 与基件实际 10011/10012 不符（菜单排序平局，合并不受影响）——登记 Phase 3 裁决表。

## 五、测试基线（Phase 2 Exit Criteria）

- `./mvnw test -pl nop-credential -am`：**全绿**（service 171（+5 新增）/ kms-vault 32 / web 1，BUILD SUCCESS，含 kms-vault 子模块聚合）。
- `./mvnw test -pl nop-ai -am`：**nop-ai 全模块 SUCCESS**（api/core/codegen/dao/gateway/meta/service/web/app/tools 等）；唯一 FAILURE 为 nop-auth-service 的 `TestMdxQuery`（5 error）+ `TestNopAuthUserBizModel`（2 error）——**pre-existing missing-tenant-id 顺序 flake**（08-16/08-17 日志已登记 clean baseline 复现；本 plan 证据：两类单独运行 5/5、2/2 全绿，且 nop-auth-service 在 reactor 中先于 nop-ai-service 执行，不可能被本 plan 新测试污染）。
- 探查测试标注审计来源：两个新测试类 javadoc 均标注「A1-audit」来源与对应 finding 编号。

## 六、结论

凭证库二期交付面的核心安全属性（明文边界/软删除先序/密文格式/引用计数/SPI 契约/@sec: 共存/OAuth 引擎闭环/KMS fail-closed/归属与 RBAC 判定矩阵）经对抗探查**全部成立**；三个 P1 根因（state 管理面裸 CRUD、resolver 装配缺位）已在本 plan 内修复并经独立复核 VERIFIED。剩余 P2/P3 为纵深完备性、运维体验与文档一致性项，逐条裁决见 `adjudication.md`（Phase 3，零悬挂）。
