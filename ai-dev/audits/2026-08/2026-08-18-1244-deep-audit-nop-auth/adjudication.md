# A2-audit finding 裁决表（adjudication）

> Audit Status: closed
> 依据：plan Goals「finding 裁决表零悬挂——每条 finding 落到且只落到一种处置」。
> 三态：`fixed`（本 plan 内修复 + focused 测试）/ `successor`（所有权路径，roadmap 登记）/ `deferred`（watch-only / optimization deferred，含 Why Not Blocking）。
> P0/P1 不静默降级：本审计 P0×0；P1×2（D2-F1/D3-F1）均 `fixed`。

## 一、逐条裁决

### fixed（本 plan 内修复）

| ID | 严重度 | 修复内容 | 验证 |
|---|---|---|---|
| D2-F1 | P1 | Redis 双码 store VALID 裁决改以 CAS 胜出为前提（败者 EXPIRED，与 Db 实现对齐） | `TestRedisCodeStoreCasRace`（4 用例）；独立复核 PASS |
| D3-F1 | P1 | webauthn credential 三失效边界（unbindMfa/resetUserMfa/恢复码使用）bulk 物理 DELETE——旧（被窃）钥匙不再随重绑复活 | `testUnbindAndAdminResetDeleteCredentialRows` + 3 个既有测试重排（断言面保持）；独立复核 PASS（边界穷举 + 无弱化确认） |
| D1-4 / V-F1 | P3 | removeWebauthnCredential 改 bulk 物理 DELETE（credentialId 唯一键释放，守卫/约束判定一致） | `testCredentialManagementApis` 既有断言仍绿 |
| D5-F2 / V-F2 | P3 | GraphQLExecutor + 接线测试 javadoc 批量语义更正（整批预执行中止） | 注释级，无行为变更 |

### successor（所有权路径）

**successor-A：MFA 敏感数据治理族（generic CRUD 通道收紧 + 敏感化）**

覆盖 findings：D5-F1（4 MFA 表裸 CrudBizModel）+ D6-1（TrustedDevice 豁免行伪造）+ D3-F3（恢复码 CRUD 植入）+ D1-7（setting.phone 通用面脱敏）+ D1-1（TOTP confirm/unbind 无尝试上限）+ D3-F2（恢复码 used 并发双花）+ 路由项 3（联系方式修改通用 CRUD 敏感化）+ D1-3（verifyChannelProof 失败审计事件，低优先）。
所有权路径：登记于 `ai-dev/backlog/nop-credential-mfa-roadmap.md`（A2 产出 successor 工作项）。实施形态建议：override 拒绝或敏感列不可写 + TOTP 分支失败计数 + used 条件写；属行为变更，需独立 plan（plan-first：触及权限/认证模型面）。
Why successor not in-plan：均为 P2/P3；修复面横跨 4+ BizModel/xmeta/权限面与行为语义（含路由项 3 的平台级机制裁定），超出审计 plan 的"P0/P1 现场修复"授权面。

**successor-B：操作级 MFA 补全 + webauthn 管理面裁定落地**

覆盖：路由项 1 的 remove 裁定落地（removeWebauthnCredential 加 @MfaRequired + 容器级元数据断言同步）+ D2-F2（mfaVerifyAsync scene 校验，需设计 §3.5 再裁定——一期零改动裁定 vs 场景隔离加固的证据重估）+ D2-F1 能力恢复候选（add-key-while-enabled 端点，多设备 UX）+ D5-F3（@MfaRequired 误标构建期 fail-fast）。
所有权路径：roadmap successor 登记（A3-audit 前置输入候选）。

### deferred（watch-only / 优化延后，含 Why Not Blocking）

| ID | 分类 | Why Not Blocking |
|---|---|---|
| D1-5 | watch-only residual | webauthn=3 恒为 factorLevel 表上限，confirm 防降级当前结构性成立；仅当引入 level-4 因子时成缺口（登记为扩展义务） |
| D1-6 | optimization deferred | maskPhone ≤4 位原样返回：极短号码本身信息量低，且 getMfaStatus 面向本人；非跨用户泄漏 |
| D2-F3 | watch-only residual | Redis 票过期后 challenge 可再验证：三实现契约分歧已被 test 钉定为已知差异；再验证产出新票仍需通过因子验证，无免费旁路 |
| D2-F4 | optimization deferred | Redis store bean 配置未接线：缺省值即设计值，仅运营调参场景失效；非缺省行为缺陷 |
| D2-F5 | watch-only residual | channel-proof ticket 不绑会话：设计 §4.6 裁定 3 既有（一次性 + userId 绑定成立），翻案需新证据 |
| D3-F4 | watch-only residual | USED/INVALID 计数差异 oracle：仅泄漏"码曾有效"，需先持有有效码；max-attempts 已限穷举 |
| D3-F5 | optimization deferred | 恢复码 10 位数字熵：安全依赖 max-attempts=5 + 密码门禁（两层已足）；扩位数为兼容破坏性变更 |
| D3-F6 | optimization deferred | EXPIRE_AT 死列：无功能影响（恢复码生命周期由作废矩阵管理）；清理属 ORM 治理 |
| D3-F7 | watch-only residual | 登录级缺 challenge.mfaType 比对：challenge 本身服务端签发且短 TTL，跨类型挪用需先通过因子验证；可利用性≈0 |
| D4-1 | optimization deferred | 受限会话 sms/email unbind 码无送达端点：fail-safe 方向（收紧非放宽）；D4-1 属升级路径可用性缺口 |
| D4-2 | watch-only residual | OAuth userName≠userId fail-closed：越权面闭合，仅影响非常规部署的引导流可用性 |
| D4-3 | watch-only residual | 受限拦截不受 mfa.enabled 门控：快照语义（登录期策略结果）可辩护；行为是收紧方向 |
| D4-4 | watch-only residual | 两副本 evaluator null 防御不对称：A 侧 bean 必注入（sso 装配保证），防御差异无行为后果；登记进路由项 2 watch 列表 |
| D4-5 | watch-only residual | executor fragment continue：当前 fail-closed（auth 层强转崩溃=拒绝）；仅当未来 auth 层变 fragment-tolerant 才成绕过——演进义务登记 |
| D4-6 | optimization deferred | saveMfaPolicy 不校验 roleId：孤儿策略行无 FK 属设计选择，登录评估对无用户角色自然不命中 |
| D5-F4 | optimization deferred | 票头大小写三形态匹配：RFC 完全大小写不敏感未实现，但方向 fail-closed（不匹配=拒绝） |
| D5-F5 | watch-only residual | 批量中止烧票：fail-closed UX 成本（客户端重验证）；安全方向正确 |
| D6-2 | watch-only residual | isExempted 无版本列：陈旧写仅可能缩短豁免（fail-safe 方向） |
| D6-3 | watch-only residual | 满员 TOCTOU +1：上界近似成立（+1 行），豁免语义不受破坏 |
| D7-F1..F4 | 记录（设计内偏差/测试基建/信息项） | D7 报告裁定为 phase-1 契约内的已裁定偏差或测试基建，无行为缺陷 |

零悬挂核对：summary.md findings 总表每条在本表或 successor/deferred 段有唯一归属。

## 二、3 项路由 deferred/follow-up 再裁定

### 路由项 1：webauthn 管理动作是否加 @MfaRequired（W14 留 A2 评估）

**裁定：rename 不标注（终局）；remove 应标注（successor-B 落地）。**

- **renameWebauthnCredential 不标注**：纯展示元数据变更（设备名），不触碰认证因子集合、凭证状态或任何安全语义；C1b 缩窄先例（管理面非破坏性动作不标注——A1-audit §二#4）直接适用。标注反而稀释 @MfaRequired 的"敏感操作"信号密度。
- **removeWebauthnCredential 应标注**：修改认证因子集合（删除一把钥匙）命中 §3.3 首批判定标准（"修改认证因子类"——unbindMfa 同族已标注）；last-credential 守卫防的是自锁死而非劫持面（攻击者删受害者钥匙的 DoS 面在 operation-mfa 开启时应有二次验证门槛）。反方材料（探查报告 D5 §路由项 1）：自身登录态 + 本人数据限定 + 审计已具备；删除不直接提升攻击者权限。裁定权衡：与 unbindMfa 语义一致性优先——unbind（删全部）已标注，remove（删一把）不标注形成强度倒挂。
- **不在本 plan 实施的原因**：plan Scope 明确路由项"只裁定 + 登记，除非裁定为 P0/P1 defect"——本项非缺陷（operation-mfa 缺省 false + 自身已有多重防护），为一致性增强 → successor-B 登记。

### 路由项 2：MfaLoginPolicyServiceImpl ↔ LoginServiceImpl.checkMfaRequired 同构不变式复核（W13 watch-only residual）

**裁定：当前同步成立；watch-only 维持 + 1 处新差异登记（D4-4）。**

- D4 逐块比对（isomorphism 表）：8 个共享逻辑块逐字等价（开关/store/setting/第三态/分支 2/mfaType/因子等同/challenge helper）；3 处差异均为登记在案的裁定漂移（userName 解析 substrate、豁免分支显式不同步 §6.6 裁定 4、异常抛出位置 SPI 契约）。
- 新发现未登记差异 D4-4（evaluator null 防御不对称）：无行为后果（装配保证非 null），登记进本表 watch-only + 设计 watch 列表回写。
- 维持 W13 登记的 watch 义务：任何一方变更必须同步另一方（A3-audit 复核点）。

### 路由项 3：联系方式修改（通用 CRUD 路径）敏感化（W12 Deferred）

**裁定：并入 successor-A（MFA 敏感数据治理族）；不在本 plan 实施。**

- live 复核确认 W12 判断仍成立：无专用 changePhone/changeEmail mutation，EMAIL/PHONE 修改走继承 CrudBizModel 通用 save/update，方法级 @MfaRequired 注解无法覆盖共享基类动作。
- 与 D5-F1/D6-1/D3-F3 同族（通用 CRUD 通道对敏感数据的无差别可写性），治理形态需平台级裁定（override 拒绝 / 敏感列不可写 / xmeta 限制）→ successor-A 统一登记，roadmap 落 successor 工作项。

## 三、裁决与 roadmap 的衔接

- successor-A / successor-B 已登记到 `ai-dev/backlog/nop-credential-mfa-roadmap.md`（A2-audit 条目收口段）。
- 按 plan guide 规则 20，W12/W13/W14/W15 四份历史 plan 不回改；本终局结论以本表 + daily log 为准。
