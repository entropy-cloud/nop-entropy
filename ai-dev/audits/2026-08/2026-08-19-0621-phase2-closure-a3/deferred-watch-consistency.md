# deferred/watch 登记一致性核对记录（V4，编排 session 执行）

> Date: 2026-08-19 | Plan: A3-audit Phase 2 | 基线 HEAD b7c81136c | 核对清单 = audit-charter §四

## 一、A1 adjudication（`ai-dev/audits/2026-08/2026-08-17-0518-deep-audit-nop-credential/adjudication.md`）

| 核对项 | live 证据 | 结论 |
|---|---|---|
| fixed 5 组仍在 live（抽 D2-01 + D6-01） | D2-01：`NopCredentialOauthStateBizModel.java` L36-37/L79-92（查询面 admin-only 运行时判定 + ERR_CREDENTIAL_ADMIN_REQUIRED + 全部标准 mutation 旁路禁用注释段 L92）+ `TestNopCredentialOauthStateBizModel.java` 存在；D6-01：`nop-ai-service` `app-service.beans.xml:11-18` `nopAiModelCredentialResolver` 注册（V3 子 agent 复核）+ `TestAiModelCredentialResolverWiring.java` 存在 | ✅ 一致 |
| successor 25 条 → C1-hardening 落地 | roadmap：C1-hardening `done`（C1a 24 finding ID + C1b 四动作，2026-08-17 收口） | ✅ 所有权落地 |
| deferred 2 条（D5-02 / D5-04，均 P3） | A1 §1.3 登记 + C1a Deferred 段引用维持（Successor Required: no）；无翻案证据 | ✅ 无 P0/P1 降级残留 |
| 8 项路由终局裁定 | #1/#5/#6/#7 → C1a 可选项登记（C1a Deferred 段 4 条逐一在案）；#2 watch-only（C1a follow-up 引用）；#3 维持 deferred；#4 → C1b 落地（roadmap done + live `@MfaRequired` 四动作 = reencryptAll/delete/grant/revoke，V2 报告侧证 reencryptAll 存在、C1b 收口记录）；#8 fixed（owner doc/source-anchors CRED-001 措辞） | ✅ 零悬挂 |

## 二、A2 adjudication（`ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/adjudication.md`，Audit Status: closed）

| 核对项 | live 证据 | 结论 |
|---|---|---|
| fixed 4 仍在 live（抽 D2-F1 + D3-F1 两 P1） | D2-F1：`RedisMfaChallengeStore.java:158` / `RedisSmsCodeStore.java:94` / `RedisEmailCodeStore.java:93` 均为 `nosql.removeIfMatch(...)`（Lua CAS，败者 EXPIRED 语义注释在案）+ `TestRedisCodeStoreCasRace.java` 存在；D3-F1：`NopAuthUserBizModel.java` unbindMfa 路径（L749-754 注释段）→ `deleteWebauthnCredentials` L1763 `dao.deleteByQuery(query)`（bulk 物理 DELETE）+ L894 removeWebauthnCredential 同口径 | ✅ 一致 |
| successor 2 → followup 族落地 | roadmap：A2-followup-1（successor-A 八表 CRUD 收口 + 验证加固 + 敏感面收敛）`done`；A2-followup-2（successor-B 路由项 1 + D5-F3 + D2-F2 + add-key）`done`（2026-08-19 收口） | ✅ 所有权落地 |
| deferred 12 watch + 4 optimization + D7 记录 | adjudication 表逐条有 Why Not Blocking；演进义务型登记：D1-5（level-4 扩展义务）/ D4-5（fragment-tolerant 演进义务）/ D4-4（两副本同步义务）——见下条 live 复核；followup-1/-2 plan Non-Blocking Follow-ups 段引用"维持原裁定不重开" | ✅ 无 P0/P1、零悬挂 |
| D4-4 两副本同步不变式（A3 复核点，A2 路由项 2） | live 逐块比对 `LoginServiceImpl.checkMfaRequired`（L1090-1133）↔ `MfaLoginPolicyServiceImpl.checkMfaForUserName`（L68-114）：8 共享逻辑块（开关/store/setting 装载/第三态判定/分支 2/mfaType 空/因子等同/challenge helper 共用 `MfaChallengeHelper.createLoginChallenge`）全部等价；仅 3 处已登记裁定漂移（userName 解析 substrate、豁免分支显式不同步 §6.6 裁定 4、异常抛出位置）+ 第 4 处已登记 D4-4（evaluator null 防御不对称：LoginServiceImpl 侧 @Nullable + NONE 回退，SPI 侧必注入）——与设计 §十#4（L736）登记一致，**无新增未登记差异** | ✅ 同构成立 |
| 3 项路由终局裁定 | #1 remove 标注（→followup-2 落地，live `removeWebauthnCredential` @MfaRequired L872 注释族）/ rename 不标注（L868 注释）；#2 见上；#3 联系方式敏感化（→followup-1 落地，`TestMfaSensitiveSurfaceE2E` ② ③ ④ 三个测试锚定） | ✅ 零悬挂 |

## 三、各 plan Deferred/Follow-up 段（charter §四 条目级清单逐条）

| plan | Deferred | Non-Blocking Follow-ups | 结论 |
|---|---|---|---|
| C1a | 5 条（PKCE opt / testCredential oos / state 清理 opt / KMS 关窗自动化 opt / D5-02+D5-04 watch），各带 Why-Not-Blocking + Successor no | 2 条（无上下文 WARN watch 引 A1 §二#2；W16 起草——已完成） | ✅ |
| C1b | 0（起草时空缺，执行期无新增） | 2 条（前端引导体验；nop-ai/integration 标注扩展需求另起） | ✅ |
| A2-followup-1 | 0（显式声明规则） | 2 条（A2 deferred 表无 ownership 项维持原裁定；changePhone/Email 正门端点立项才登记） | ✅ |
| A2-followup-2 | 0 | 3 条（A2 deferred 表维持；add-key 前端页面；sendMfaCode scene 收紧 info 级） | ✅ |
| W16-impl | 0（预登记倾向由 ext 承接） | 1 条（过渡并存行 JSON 明文死值清理 runbook 运维治理） | ✅ |
| W16-impl-ext | 3 条（飞书刷新期再解析 opt——watch 锚点测试 live `TestFeishuCredentialResolution.java:239/249` 两用例存在；新类型 testCredential oos；feishu/oss 静态值强制 `@sec:` watch-only 前提变化已回写设计 §七#7），各带 Why-Not-Blocking + Successor no | 3 条（并存窗口 runbook；hollow-scan 多标志工具治理项 = charter E5；checkstyle 非门禁口径） | ✅ |

## 四、watch 锚点测试存在性汇总

| 锚点 | live 位置 | 存在 | 绿（chain-b） |
|---|---|---|---|
| 飞书捕获值语义（W16-ext §七#1） | `nop-integration-feishu/src/test/java/io/nop/integration/feishu/client/TestFeishuCredentialResolution.java:239,249` | ✅ | 见 summary §V1 |
| Redis 码 store CAS 竞态（A2 fixed 证据） | `nop-auth-service/.../mfa/store/TestRedisCodeStoreCasRace.java` | ✅ | 见 summary §V1 |
| OauthState 锁定（A1 fixed 证据） | `nop-credential-service/.../entity/TestNopCredentialOauthStateBizModel.java` | ✅ | 见 summary §V1 |
| resolver 容器接线（A1 fixed 证据） | `nop-ai-service/.../credential/TestAiModelCredentialResolverWiring.java` | ✅ | 见 summary §V1 |
| 两副本同构（A2 演进义务 D4-4） | 非测试锚点 = 代码同构复核（本文件 §二） | ✅ | N/A（结构性复核） |

## 五、总结论

**零悬挂；无 P0/P1 降级残留；successor 所有权全部落地（C1-hardening done + A2-followup 族 done）；watch/optimization 登记与 live 一致；watch 锚点测试存在（绿证据 = chain-b 测试结果，见 summary.md §V1）；D4-4 两副本同步不变式 A3 复核成立（无新增未登记差异）。发现登记与 live 无漂移。**
