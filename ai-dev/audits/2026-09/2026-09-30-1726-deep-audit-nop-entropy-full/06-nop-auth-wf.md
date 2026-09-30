# G6: nop-auth + nop-wf 深度审计（首轮）

- 审计日期：2026-09-30
- 审计人：G6 首轮审计子代理（维度 04 / 07 / 09 / 13 / 22）
- 审计基线：live code（HEAD 含已提交的 plan 2275 限流/登录失败计数产物，commit d746ce4aeb）；主 agent 提示中所指"NopAuthLoginAttempt/NopAuthRateLimitCounter 未提交新文件"实际已提交，一并纳入审计

## 审计范围

**模块**：`nop-auth/`（api/dao/meta/service/sso/web/oauth/app）+ `nop-wf/`（api/core/dao/meta/service/web/scheduler/ai/app），排除 `target/`、`_` 前缀生成产物、`_dump/`、`.m2-repo-2275/`。

**深读文件（main 代码）**：
- 登录认证链：`LoginServiceImpl`、`LoginApiBizModel`、`DaoLoginSessionStore`、`DaoUserContextCache`、`DbLoginAttemptStore`、`RedisLoginAttemptStore`、`LoginAttemptStoreProvider`
- MFA/验证码/限流：`LoginMfaFlow`、`MfaFactorVerifier`、`MfaTrustedDeviceManager`、`MfaCodeSender`、`MfaLoginPolicyServiceImpl`、`DbSmsCodeStore`、`DbEmailCodeStore`（对照）、`DbMfaChallengeStore`、`DbSendCodeRateLimiter`、`LocalSendCodeRateLimiter`、`RedisSendCodeRateLimiter`（对照）
- 权限本体：`NopAuthUserBizModel`、`NopAuthConfigs`、`NopAuthErrors`、`DefaultActionAuthChecker`、`MfaSensitiveTableBizModel` 及 8 个 MFA 敏感表 BizModel、`NopAuthTrustedDeviceBizModel`、`OAuthLoginServiceImpl`（sso）
- ORM 源模型：`nop-auth/model/nop-auth.orm.xml`（全文）、`nop-wf/model/nop-wf.orm.xml`（实体/索引结构）
- 工作流引擎：`WorkflowEngineImpl`（全文 1941 行）、`WfRuntime`、`WorkflowStepImpl`（invokeAction 段）、`ApprovalFlowHelper`、`WfListenerModel`、`WfTaskScanner`（scheduler）、`WfAiHelper`（nop-wf-ai）、`approval-support.xbiz`、`oa.xwf`、`oa.xlib`、`examples/*.xwf`（15 个 main 资源 xwf）、`approval-form/v1.xwf`（test 资源，作为 listener 范式核对）、`NopWfApprovableForm.xmeta/.xbiz`

**机械基线核实（主 agent 给的数字）**：
- 裸异常（nop-auth 54 处 / nop-wf 3 处）：grep 复核 `throw new RuntimeException|IllegalArgumentException|IllegalStateException`，main 代码 **0 处**，全部位于 `src/test/`（nop-auth test 32 处、nop-wf test 3 处）。按口径 test 不报。
- System.out/printStackTrace（nop-auth 30 处）：main 代码 **0 处**，全部在 test。不产生 main 代码发现。
- `@Inject private`：0 处，与基线一致。

**各维度覆盖说明**：
- 维度 04（ORM）：重点核对 auth 的 MFA 新增 8 表（Challenge/SmsCode/EmailCode/RateLimitCounter/LoginAttempt/RoleMfaPolicy/MfaCredential/MfaTrustedDevice）与 wf 全部 14 实体的主键/域/tagSet/索引/唯一约束。发现见 [G6-04-01]；其余实体（审计字段、not-pub/masked 标记、i18n-en displayName、级联删除）规范。
- 维度 07（BizModel 规范）：全部 44 个 auth entity BizModel + 15 个 wf entity BizModel + LoginApiBizModel/SiteMapApiBizModel/WorkflowServiceImpl 核对继承模式（setEntityName + CrudBizModel/实现 I*Biz）。整体高度规范；发现见 [G6-07-01]；MfaSensitiveTableBizModel 写路径收口为亮点（非发现）。
- 维度 09（错误处理）：全模块 main 代码无裸异常、无吞异常、无 System.out；ErrorCode 定义/`.param()` 使用规范。发现见 [G6-09-01]、[G6-09-02]。
- 维度 13（安全与权限）：登录链/MFA/限流逐文件深读（见上）。发现见 [G6-13-01]、[G6-13-02]。
- 维度 22（工作流语义）：引擎状态机 + listener + xwf 全链核对（见发现 5 条）。教训 12/MA7.6-01 模式在本模块**仍然存在**（[G6-22-01]）。

## 发现

### [G6-22-01] 审批流 `*end` listener 范式不判定结束原因——disagree 结束同样触发业务 approve（驳回即通过）

- **文件**: `nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf:5-21`（配合 `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf:25-33`）
- **证据片段**:
  ```xml
  <!-- approval-form/v1.xwf -->
  <listener id="onApproveEnd" eventPattern="*end">
      <source>
          <c:script><![CDATA[
              const entityBizObj = bizObjManager.getBizObject('NopWfApprovableForm');
              const entityId = wfRt.wf.bizEntityId;
              // 幂等：仅在 SUBMITTED 状态时调用 approve（避免 EVENT_BEFORE_END/AFTER_END 重复触发）
              const entity = entityBizObj.invoke('requireEntity', {id: entityId}, null, wfRt.svcCtx);
              if (entity.approveStatus === 'SUBMITTED') {
                  entityBizObj.invoke('approve', {id: entityId}, null, wfRt.svcCtx);
              }
          ]]></c:script>
  <!-- oa.xwf（被 x:extends 继承，disagree 为 common=true，对所有普通步骤可用） -->
  <action name="disagree" displayName="拒绝" common="true">
      <transition wfAppState="disagree" appState="disagree">
          <to-end/>
      </transition>
  </action>
  ```
- **严重程度**: P1
- **现状**: 仓库中唯一的 `*end` listener 范式（也是 `TestUseApprovalE2E.testWorkflowApproval_fullChain` 作为"WORKFLOW 模式全链"对外演示的集成模板）在流程结束时无条件调用业务 `approve`，只做了重复触发幂等（`approveStatus === 'SUBMITTED'`），**没有检查结束原因**（`wfRt.wf.record.appState` 是否为 `agree`）。已核实完整触发链：审批人在 `approve1` 执行 `disagree`（oa.xwf 的 `WhenAllowDisagree` 仅排除 `cc` 步骤，普通审批步骤可用）→ `transitionTo` TO_END 分支 `markEnd()`（WorkflowEngineImpl.java:1520-1529）→ 延迟 `checkEnd` → `doEndWorkflow(COMPLETED)` 依次触发 `EVENT_BEFORE_END`/`EVENT_AFTER_END`（WorkflowEngineImpl.java:1595/1611）→ `*end` 匹配（WfListenerModel.matchPattern）→ listener 执行，实体仍为 SUBMITTED → `approve()` 被调用 → **审批人明确拒绝，业务单据却被置为 APPROVED**。E2E 测试只覆盖 agree 路径（TestUseApprovalE2E.java:202-207），disagree 路径无回归。
- **风险**: 业务模块照抄此模板（它是平台演示的标准姿势）即得到"驳回即通过"的审批流；这正是维度 22 教训 12（MA7.6-01，P0）的同构缺陷。按本维度判级标准，若出现在业务模块生产流中即为 P0；此处定 P1 因文件位于 test 资源，但它是平台发布的 canonical 范式且 `NopWfApprovableForm` 实体/页面（nop-wf-web/pages/NopWfApprovableForm）在 main 源集中，构成生产可见样例。
- **建议**: listener 内显式判定结束原因：`if (wfRt.wf.record.appState === 'agree' && entity.approveStatus === 'SUBMITTED') approve; else if (wfRt.wf.record.appState === 'disagree') reject;`；同时补 disagree 路径的 E2E 回归测试；在 `docs-for-ai/02-core-guides/workflow-configuration.md` 增补"`*end` listener 必须检查 appState"的强制条款。
- **信心水平**: 确定（触发链逐行核实）
- **误报排除**: 不是"框架约定 listener 自带结束原因"——`WfRuntime.triggerEvent` 只传事件名，无 reason 参数；也不是"disagree 在该流不可达"——`common="true"` + `WhenAllowDisagree`（specialType != 'cc'）在 approve1 上可用。
- **复核状态**: 未复核

### [G6-22-02] 引擎 `checkEnd` 一律以 WF_STATUS_COMPLETED 收尾——disagree/异常结束与正常通过在实例状态上不可区分，EXPIRED/FAILED 为死状态

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1570-1587`
- **证据片段**:
  ```java
  void checkEnd(WfRuntime wfRt) {
      IWorkflowImplementor wf = wfRt.getWf();
      if (!wf.isEnded() && wf.isStarted()) {
          IWorkflowRecord wfRecord = wf.getRecord();
          boolean bEnd = wfRt.willEnd();
          if (bEnd) {
              killSteps(wfRt);
          } else if (wf.getStore().isAllStepsHistory(wfRecord)) {
              bEnd = true;
          }
          if (bEnd) {
              this.doEndWorkflow(NopWfCoreConstants.WF_STATUS_COMPLETED, wfRt);  // 恒为 COMPLETED
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `doEndWorkflow` 全仓只有两个调用点：kill（WF_STATUS_KILLED，WorkflowEngineImpl.java:732）与 checkEnd（恒 WF_STATUS_COMPLETED）。`disagree`→`to-end`、`reject` 后全步骤历史自动结束、超时 dueAction 结束等全部落 COMPLETED(40)。wf 级 `WF_STATUS_EXPIRED(50)`/`WF_STATUS_FAILED(60)` 在 `_NopWfCoreConstants` 与 orm dict `wf/wf-status` 中定义但引擎从不写入（grep 全 main 代码零写入点）。结束原因的唯一载体是 `wfRecord.appState`（`changeWfAppState`），子流程结束时 `endSubFlow → notifySubFlowEnd(status)` 把 COMPLETED 传给父步骤的 `subWfResultStatus`——父流程对子流程 disagree 只能靠 output vars 区分，数值状态同样混淆。
- **风险**: 任何只看 `wf.status`/`subWfResultStatus` 的下游（报表、监听器、父流程条件、approvable 列表归档逻辑）都无法区分"通过结束"与"驳回结束"，是 [G6-22-01] 类缺陷的结构性根因；dict 中的 EXPIRED/FAILED 语义对使用者构成虚假承诺。
- **建议**: `checkEnd` 至少将 `wfAppState=disagree` 的结束映射为独立终态（或复用 FAILED/新增 REJECTED），或在 `doEndWorkflow` 前计算 end-reason 并作为 `EVENT_BEFORE_END/AFTER_END` 的 scope 变量暴露给 listener；子流程向父流程传递 appState。
- **信心水平**: 确定（写入点穷尽核实）
- **误报排除**: 不是"COMPLETED 只代表流程走完、语义自足"——同 dict 内定义了 FAILED/EXPIRED/REJECTED(step 级) 终态却永不出现，且维度 22 教训 12 已把"流程结束≠审批通过"定为强制判别项。
- **复核状态**: 未复核

### [G6-22-03] `*end` 事件模式同时命中 before-end 与 after-end——每个 listener 单次结束触发两遍，幂等负担转嫁给所有业务方

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/model/WfListenerModel.java:14-20`（配合 `WorkflowEngineImpl.java:1595,1611`、`NopWfCoreConstants.java:76-77`）
- **证据片段**:
  ```java
  // WfListenerModel
  public boolean matchPattern(String event) {
      return StringHelper.matchSimplePattern(event, getEventPattern());
  }
  // WorkflowEngineImpl.doEndWorkflow
  wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_END);   // "before-end"
  ...
  wfRt.triggerEvent(NopWfCoreConstants.EVENT_AFTER_END);    // "after-end"
  // approval-form/v1.xwf 的自证注释：
  // 幂等：仅在 SUBMITTED 状态时调用 approve（避免 EVENT_BEFORE_END/AFTER_END 重复触发）
  ```
- **严重程度**: P2
- **现状**: `eventPattern="*end"` 是模板示范的写法（approval-form/v1.xwf:6），`matchSimplePattern("*end")` 同时匹配 `before-end` 与 `after-end`，listener 在一次流程结束中执行两次。before-end 时机 `wfRecord` 尚未 saveWfRecord、endTime 未写、子流程未通知——若业务方在 listener 中读流程终态或让副作用依赖"已落库"，两次执行的可见状态不同。目前唯一示例靠 `approveStatus === 'SUBMITTED'` 事务性幂等兜底，`docs-for-ai/02-core-guides/workflow-configuration.md` 对双触发与两个事件的语义差异只字未提。
- **风险**: 业务 listener 若不具备幂等（例如发通知、写外部系统、累计计数），每次审批结束产生双份副作用；before-end 时机读取到未提交的流程状态还会造成时序 bug。
- **建议**: 文档明确"`*end` 会命中 before-end/after-end 两次，业务 listener 应使用 `after-end` 精确模式"；或引擎侧为 listener 提供 `event` 变量并在文档中示范 `event == 'after-end'` 判定。
- **信心水平**: 确定
- **误报排除**: 不是"triggerEvent 只触发一次"——doEndWorkflow 中两次 triggerEvent 逐行可见；示例文件注释本身承认双触发。
- **复核状态**: 未复核

### [G6-22-04] approval-support.xbiz：submitForApproval 启动工作流，withdrawApproval 只改业务状态不终止流程——单边联动造成流程/业务状态漂移

- **文件**: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz:30-39,46-69`
- **证据片段**:
  ```xml
  <!-- submitForApproval：启动 wf -->
  const wfName = thisObj.objMeta['wf:wfName'];
  if (wfName) {
      const wfManager = inject('nopWorkflowManager');
      const wf = wfManager.newWorkflow(wfName, null);
      ...
      ApprovalFlowHelper.start(wf, args, svcCtx);
  }
  <!-- withdrawApproval：仅改状态，无任何 wf 交互 -->
  if (status !== 'SUBMITTED') { throw ... }
  entity.approveStatus = 'UNSUBMITTED';
  return entity;
  ```
- **严重程度**: P2
- **现状**: 这是全平台可审批实体的审批 mixin 单一事实源（`IApprovableBiz` 的 5 个 action 全部由此 xbiz source 承载）。submit 启动流程并经 `bizEntityFlowIdProp` 反写 nopFlowId；withdraw 只把 approveStatus 置回 UNSUBMITTED，**既不 kill 也不撤回运行中的 wf 实例**。撤回后：审批人待办仍存在；审批人 agree → 流程 to-end 结束（wfAppState=agree、实例 COMPLETED）→ listener 幂等条件 `approveStatus === 'SUBMITTED'` 不成立而跳过 → 业务单据 UNSUBMITTED 但流程实例显示"审批通过已结束"；nopFlowId 残留指向已结束流程。
- **风险**: 上下游状态机漂移：审批待办列表仍向审批人展示已撤回单据并允许操作；以流程实例状态为准的对账/报表把已撤回单据计为"走完流程"；二次 submitForApproval 会再启一个新流程，旧 nopFlowId 被覆盖。
- **建议**: withdrawApproval 中按 nopFlowId 加载运行中实例并 kill/withdraw（`checkManageAuth` + `wf.kill`），或至少把 withdraw 语义路由到引擎的 forWithdraw action；补"withdraw 后 agree"的回归测试钉定期望行为。
- **信心水平**: 很可能（漂移路径由代码推导，未运行复现；listener 跳过 approve 的分支已逐行核实）
- **误报排除**: 不是"撤回必须由流程侧 withdraw action 发起所以 xbiz 不该做"——该 mixin 是业务侧撤回的唯一入口，若设计如此则应拒绝在 UNSUBMITTED 可达状态下结束流程或同步终止，当前两边都不做。
- **复核状态**: 未复核

### [G6-13-01] DB 限流/登录失败计数表过期行无批量清理——按日键永不再访问故永不删除，XFF 伪造 IP 可放大无界增长

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/ratelimit/DbSendCodeRateLimiter.java:116-137,176-181`（同型：`DbLoginAttemptStore.java:127-132`、`DbMfaChallengeStore.java`/`DbSmsCodeStore.java` 惰性清理）
- **证据片段**:
  ```java
  // 惰性清理只作用于"当前正在访问的 key"
  private long deleteExpired(String key, long now) {
      SQL del = SQL.begin().name("authRateLimitDeleteExpired")
              .sql("delete from NopAuthRateLimitCounter o where o.counterKey = ? and o.expireAt <= ?", key, now)
              .end();
      return ormTemplate.executeUpdate(del);
  }
  // 日计数键含当天 epochDay，次日生成全新 key：
  String dayKey = scope + ':' + channel + ':' + target + ":d" + CoreMetrics.today().toEpochDay();
  ```
- **严重程度**: P2
- **现状**: plan 2275 的三个 DB 后端（rate-limit counter / login attempt / mfa challenge / sms·email code）清理策略全部是"访问路径顺带按 key 惰性 DELETE"。日计数键（`...:d{epochDay}`）与 IP 键（`...:ip:{clientIp}:d{day}`）在其窗口过后**永不再被读取**，惰性删除永不触发，行永久驻留。`LoginApiBizModel.extractClientIp`（:574-592）直接信任 `X-Forwarded-For` 首段（代码注释自认"IP 限流为次要防线"），攻击者每次请求伪造新 XFF 值即可绕过 IP 日限并批量制造一次性 IP 键行（每行一次 INSERT，`tracker-expire` 只约束 Local Caffeine 不约束 DB 表）。代码注释自认"批量清理为 Follow-up"（DbMfaChallengeStore.java:37），`IX_*_EXPIRE` 索引已建但无任何 job 消费。全仓 grep 无针对这四张表的清理任务。
- **风险**: `nop_auth_rate_limit_counter` 等表单调无界增长（IP 键维度可被外部流量直接放大），长期运行拖慢 PK 索引、膨胀备份；XFF 伪造同时使 IP 维度限流形同虚设。
- **建议**: 增加 nop-job 定时任务按 `expireAt < now` 批量删除（索引已具备）；XFF 解析仅在可信代理后启用（配置开关 + 取右段策略），或对 IP 键设置独立更短 TTL。
- **信心水平**: 确定（清理调用点穷尽核实；XFF 信任逐行核实）
- **误报排除**: 不是"惰性清理已覆盖"——deleteExpired 带具体 key 参数，只删当前键；不是"tracker-expire 兜底"——该配置仅作用于 LocalSendCodeRateLimiter 的 Caffeine map（NopAuthConfigs.java:166-169 注释明说"计数本身按自然日重置，过期仅用于……本地"）。
- **复核状态**: 未复核

### [G6-13-02] 可信设备指纹为无密钥 SHA-256——device-id 非秘密且哈希不可加盐，构成可离线复算的 30 天 MFA 豁免凭证

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaTrustedDeviceManager.java:81-92`
- **证据片段**:
  ```java
  public static String fingerprint(Map<String, Object> requestHeaders) {
      String deviceId = header(requestHeaders, HEADER_DEVICE_ID);   // X-Nop-Mfa-Device-Id，前端生成 UUID，非秘密
      if (StringHelper.isEmpty(deviceId)) return null;
      String userAgent = header(requestHeaders, "User-Agent");
      String acceptLanguage = header(requestHeaders, "Accept-Language");
      String input = deviceId + "|" + ... + "|" + ...;
      return StringHelper.bytesToHex(HashHelper.sha256(input.getBytes(UTF_8), null));  // 无服务端密钥
  }
  ```
- **严重程度**: P3
- **现状**: "记住此设备"的豁免凭证 = SHA-256(deviceId|UA|Accept-Language)，三输入全部客户端可控且 deviceId 明确标注"非秘密"（HEADER 注释），无服务端 HMAC 密钥参与。任何获知这三元组的一方（XSS 读取 localStorage 的 deviceId +navigator.userAgent、日志泄露、本地盘读取）可离线复算 deviceHash，配合用户名密码在 `checkMfaRequired` 豁免判定（LoginMfaFlow.java:406-413）处直接跳过第二因子 30 天。角色策略 `allowTrustedDevice=false` 可整体关闭该面（默认 true，NopAuthConfigs CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT 同组）。
- **风险**: MFA 防线在有 XSS 或 deviceId 泄露场景下被降级为单因子；DB 泄露虽不直接反推输入（UA 截断存储），但哈希无盐可被字典式撞库。
- **建议**: 指纹计算改为 HMAC-SHA256(服务端配置密钥, input)，密钥经 `@InjectValue` 注入；保持存量行平滑（双读或强制重新登记）。
- **信心水平**: 很可能（作为加固项成立；利用前提是三元组泄露，非直接可利用漏洞）
- **误报排除**: 不是"平台标准做法"——session token 等价物通常以随机服务端生成值存哈希；此处输入全部客户端可算，安全属性弱于随机 token 模型。
- **复核状态**: 未复核

### [G6-04-01] NopAuthUser.pwdUpdateTime/changePwdAtLogin 字段语义未实现——改密/重置密码不写 pwdUpdateTime，定期换密依据缺失

- **文件**: `nop-auth/model/nop-auth.orm.xml:85-90`（字段定义）；`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java:541-583`（resetUserPassword/changeSelfPassword）
- **证据片段**:
  ```xml
  <column code="PWD_UPDATE_TIME" comment="要求用户定期更换密码" displayName="上次密码更新时间" name="pwdUpdateTime" .../>
  <column code="CHANGE_PWD_AT_LOGIN" defaultValue="0" displayName="登陆后立刻修改密码" name="changePwdAtLogin" .../>
  ```
  ```java
  // changeSelfPassword / resetUserPassword 的公共尾部（无 pwdUpdateTime / changePwdAtLogin 写入）：
  String salt = passwordEncoder.generateSalt();
  password = passwordEncoder.encodePassword(salt, newPassword);
  user.setSalt(salt);
  user.setPassword(password);
  revokeUserSessions(user, userContext.getSessionId());
  ```
- **严重程度**: P3
- **现状**: 两个字段的 comment 声明"要求用户定期更换密码/登陆后立刻修改密码"，但全仓 grep（nop-auth/nop-service-framework/nop-core-framework）显示除生成 Bean/view 外**零读写**：`changeSelfPassword`、`resetUserPassword` 均不回填 `pwdUpdateTime`，登录链（LoginServiceImpl.isAllowLogin）也不校验 `changePwdAtLogin`/密码时效。应用层若未来按字段语义启用定期换密，历史改密时间全部缺失（null），首启即全员"过期"或全员"从未改密"。
- **风险**: 声明的安全语义是空承诺；后续启用时数据不可回填，构成隐性迁移成本与误判风险。
- **建议**: 在两处密码写路径补 `user.setPwdUpdateTime(now)`（changePwdAtLogin 置回 0）；或删减字段避免误导。
- **信心水平**: 确定
- **误报排除**: 不是"预留字段允许暂不实现"——字段语义指向数据必须随写路径维护的审计值（同表 PWD 相关列），且改密不记录使字段永远无法积累有效数据。
- **复核状态**: 未复核

### [G6-09-01] `nop.err.wf.approve.invalid-status` 错误码无定义、无 i18n——审批 mixin 全部 5 个动作抛出的公共错误码是裸字符串

- **文件**: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz:20,58,84,112,140`
- **证据片段**:
  ```xml
  if (status !== 'UNSUBMITTED' && status !== null && status !== 'REJECTED') {
      throw new NopScriptError("nop.err.wf.approve.invalid-status")
          .param("bizObjName", thisObj.bizObjName)
          .param("action", "submitForApproval")
          .param("currentStatus", status)
          .param("expectedStatus", "UNSUBMITTED or REJECTED");
  }
  ```
- **严重程度**: P3
- **现状**: 该错误码在 5 个 mutation 中重复抛出（面向前端 GraphQL 的公共 API 面），但既未在 `NopWfCoreErrors`/`NopWfErrors` 中 `ErrorCode.define`（ARG_* 参数常量也散落为字面量），也未在 `nop-wf-meta/src/main/resources/_vfs/i18n/{zh-CN,en}/*.i18n.yaml` 注册（grep 零命中）。最终用户收到的是无描述、无翻译的错误码。
- **风险**: 违反两档策略中"公共 API 必须用 ErrorCode 模式"的口径（error-handling.md）；前端只能按裸码猜测；后续无法集中治理/迁移该码。
- **建议**: 在 NopWfCoreErrors 定义 `ERR_WF_APPROVE_INVALID_STATUS`（含 ARG_BIZ_OBJ_NAME/ARG_ACTION/ARG_CURRENT_STATUS/ARG_EXPECTED_STATUS 常量），i18n 两个 locale 各补一条，xbiz 改引用。
- **信心水平**: 确定
- **误报排除**: 不是"NopScriptError 的码不需要注册"——NopWfCoreErrors 中同性质的码（如 ERR_WF_UNKNOWN_STEP）均为 define + i18n 注册形态。
- **复核状态**: 未复核

### [G6-09-02] NopAuthErrors 同文件中英文描述混排——新增 MFA 治理码用英文、存量码用中文

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/NopAuthErrors.java:62-70 对比 19-20,212-214,220-231`
- **证据片段**:
  ```java
  // 存量（中文）：
  ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL = define("nop.err.auth.login-check-fail", "登陆失败，用户名或者密码不匹配");
  // 新增（英文，同文件）：
  ErrorCode ERR_AUTH_MFA_CRUD_DISABLED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-crud-disabled",
          "Generic CRUD mutation '{action}' is disabled on MFA data '{bizObjName}'; "
                  + "use the dedicated MFA management API instead", ARG_ACTION, ARG_BIZ_OBJ_NAME);
  ErrorCode ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED = define(..., "Changing phone/email via generic CRUD is not allowed; ...");
  ```
- **严重程度**: P3
- **现状**: error-handling.md 明确规则"存量中文模块的新增业务错误码仍延续中文描述……不要在同文件内中英混排"。本文件属存量中文模块，但 A2-followup-1 新增的 4 个码（malformed-user-handle、mfa-crud-disabled、contact-change-not-allowed、mfa-cooldown）用了英文描述，其余 40+ 码为中文。
- **风险**: 纯文档口径漂移；前端按 locale 取描述时同一模块行为不一致，后续维护者无所适从。
- **建议**: 统一为中文（存量模块口径）或整文件英文化并登记为例外模块族；二选一后消除混排。
- **信心水平**: 确定
- **误报排除**: 不是"英文即合规"——规范对存量模块的增量要求是延续中文且禁止同文件混排，两条都不满足。
- **复核状态**: 未复核

### [G6-07-01] LoginApiBizModel.generateVerifyCode 缺少 IServiceContext 末参——偏离 BizModel 方法契约且无法参与上下文审计

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/biz/LoginApiBizModel.java:159-163`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(publicAccess = true)
  public String generateVerifyCode(@Name("verifySecret") String verifySecret) {
      return loginService.generateVerifyCode(verifySecret);
  }
  // 同类其余方法均带末参：loginAsync(@RequestBean LoginRequest request, IServiceContext context) 等
  ```
- **严重程度**: P3
- **现状**: service-layer.md 规定 BizModel 自定义方法以 `IServiceContext context` 作为最后一个参数（对齐 ICrudBiz 契约、承载 IUserContext/审计上下文）。该类 15 个 action 中唯此方法缺末参（连 @BizAudit 也无法附会话信息）。缓存侧为有界 LocalCache（maxLoginUserCount×2，expireAfterWrite），无 DoS 面。
- **风险**: 契约漂移样板（该类是平台登录入口样板）；审计/追踪拿不到该公开端点的会话上下文。
- **建议**: 增加 `IServiceContext context` 末参并在实现侧透传。
- **信心水平**: 确定
- **误报排除**: 不是"无参方法平台不允许"——允许，但规范明确要求末参；也不是接口契约锁死——ILoginSpi 由本模块自有，可同步演进。
- **复核状态**: 未复核

### [G6-22-05] NopWfApprovableForm（生产实体+页面）的 xmeta 绑定 `wf:wfName="test/approval-form"`，而该流程定义只存在于 src/test/resources

- **文件**: `nop-wf/nop-wf-meta/src/main/resources/_vfs/nop/wf/model/NopWfApprovableForm/NopWfApprovableForm.xmeta:3`（配合 `nop-wf/nop-wf-service/src/main/resources/_vfs/nop/wf/examples/` 15 个 main 资源流程）
- **证据片段**:
  ```xml
  <meta x:extends="_NopWfApprovableForm.xmeta" wf:wfName="test/approval-form"/>
  ```
- **严重程度**: P3
- **现状**: `NopWfApprovableForm` 实体（nop-wf-dao main）、BizModel（main）、管理页面（`nop-wf-web/src/main/resources/_vfs/nop/wf/pages/NopWfApprovableForm/`）均在生产源集，其审批绑定指向 `test/approval-form`——全仓该 xwf 仅存在于 `nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf`（与 `_dump` 快照）。main 资源只有 `examples/*` 系列。生产部署（未向 NopWfDefinition 表手工导入该定义）中页面上 submitForApproval → `newWorkflow("test/approval-form")` → 定义解析失败（ResourceWorkflowModelStore / DaoWorkflowModelLoader 均无此定义）→ 提交动作报错。
- **风险**: 平台样板模块自带一个生产可见但主流程不可用的演示页；也放大 [G6-22-01]（该定义即含缺陷 listener）。
- **建议**: 将 approval-form 定义移入 main `examples/`（修复 listener 后）或从 xmeta 解除 wfName 绑定、页面标注 demo-only。
- **信心水平**: 很可能（解析链已核对，未实际部署验证；若部署侧有种子数据导入流程则不成立）
- **误报排除**: 不是"test 资源也会打包"——`src/test/resources` 不进 main jar；examples 目录与 test/approval-form 路径不重叠。
- **复核状态**: 未复核

## 零发现维度说明（覆盖核对）

- **维度 04 其余项**：auth/wf 实体主键（varchar sid/业务键）、域复用（userId/roleId/phone/email/boolFlag…）、审计字段四件套、逻辑删除+version、cascadeDelete 仅限强归属子表（roleMappings/substitutionMappings/groupMappings）、敏感列 tagSet（PASSWORD/SALT/CODE/token 均 masked+not-pub，NopAuthSession.accessToken/refreshToken/cacheData not-pub）、i18n-en displayName 全覆盖——规范，无发现。NopAuthRateLimitCounter/NopAuthLoginAttempt 的 propId 存在 7/8 空号（直跳 9），属 xlsx 残留，Nop ORM 允许 propId 空洞，不构成缺陷。
- **维度 07 其余项**：MfaSensitiveTableBizModel 对 8 张 MFA 敏感表的全量 mutation 收口（含 recoverDeleted）+ TrustedDevice delete 的 admin carve-out 是高质量防护，非发现；NopAuthRateLimitCounter/NopAuthLoginAttempt 保持裸 CrudBizModel 与代码注释中的边界裁定一致（计数表无凭证植入面），不另立项。
- **维度 09 其余项**：main 代码 SLF4J 使用规范（throwable 均作末参）、异常链保留（DbSmsCodeStore/DbSendCodeRateLimiter 的 dup-key 分支均 rethrow 非 dup 异常）、WfTaskScanner/WfAiHelper 的 per-element 隔离符合 error-handling.md 三类允许位置。
- **维度 13 其余项**：登录失败计数锁号与凭证校验解耦（maxFailCount<=0 仅关锁号）、SMS 失败不污染账号锁、MFA 场景隔离（scene/verifiedAt 纪律、同会话校验、票不续命）、防枚举（未注册手机号统一响应）、TOTP 防重放窗口推进、WebAuthn signCount 单调写、恢复码 BCrypt+条件置位、sendSmsCode/sendMfaCode 限流与 fail-closed——逐项核对无缺陷；`WfRuntime` 构造对 null ctx 会 NPE（serviceContext.getEvalScope()）但 nop-wf 范围内未发现传 null 的调用方（scheduler 自建 context、xbiz 传 svcCtx），不立项。
- **维度 22 其余项**：`notifySubFlowEndAsync` 有来源校验（subWfId 存在 + 已结束 + 状态一致，防伪造推进父流程）；`initArgs` 过滤 wf/wfRt/wfVars 保留名（防 args 污染求值作用域）；步骤状态机有 history 不可回退守卫（WorkflowStepRecordBean.transitToStatus）；`doReject` 显式驳回目标按模型名 + DAG 祖先校验；WfAiHelper 低置信度缺省转人工（fail-safe）。

## 最终保留项（待复核）

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| G6-22-01 | P1 | nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf | `*end` listener 不判定结束原因，disagree 结束同样 approve（驳回即通过范式） |
| G6-22-02 | P2 | nop-wf-core/.../WorkflowEngineImpl.java:1570-1587 | checkEnd 恒 COMPLETED，EXPIRED/FAILED 死状态，结束原因仅存 appState |
| G6-22-03 | P2 | nop-wf-core/.../WfListenerModel.java + doEndWorkflow | `*end` 模式双触发 before-end/after-end，幂等负担转嫁业务方且无文档 |
| G6-22-04 | P2 | nop-wf-core/.../approval-support.xbiz | withdrawApproval 不终止运行中流程，submit/withdraw 单边联动状态漂移 |
| G6-13-01 | P2 | nop-auth-service/.../DbSendCodeRateLimiter.java 等 | DB 限流/计数表日键永不惰性删除、无批量清理 job，XFF 伪造放大无界增长 |
| G6-13-02 | P3 | nop-auth-service/.../MfaTrustedDeviceManager.java:81-92 | 可信设备指纹无密钥 SHA-256，输入全客户端可控，30 天 MFA 豁免可离线复算 |
| G6-04-01 | P3 | nop-auth/model/nop-auth.orm.xml:85-90 + NopAuthUserBizModel | pwdUpdateTime/changePwdAtLogin 声明语义零实现，改密不回填 |
| G6-09-01 | P3 | nop-wf-core/.../approval-support.xbiz | 公共错误码 nop.err.wf.approve.invalid-status 无 define 无 i18n |
| G6-09-02 | P3 | nop-auth-service/.../NopAuthErrors.java | 同文件中英文错误描述混排，违反存量模块延续中文口径 |
| G6-07-01 | P3 | nop-auth-service/.../LoginApiBizModel.java:159-163 | generateVerifyCode 缺 IServiceContext 末参，偏离 BizModel 方法契约 |
| G6-22-05 | P3 | nop-wf-meta/.../NopWfApprovableForm.xmeta:3 | 生产实体/页面绑定的 wf 定义仅存在于 test 资源，生产提交审批不可用 |

统计：P0 × 0，P1 × 1，P2 × 4，P3 × 6，共 11 条。

## 子项复核结论

复核人：独立复核代理 R1（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G6-22-01] | 保留（维持 P1）| 逐件打开核对：`nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf` 全文——`*end` listener（6-20 行）仅检查 `entity.approveStatus === 'SUBMITTED'` 即调 `approve`，无任何结束原因判定，与引文逐字一致；`nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf` 25-33 行确认 `disagree` 为 `common="true"` 且迁移 `wfAppState="disagree"` → `<to-end/>`；`oa.xlib:99-109` 确认 `WhenAllowDisagree` 仅排除 `specialType == 'cc'`，普通步骤 approve1 上 disagree 可达。引擎触发链逐行核实：`WorkflowEngineImpl.transitionTo` TO_END 分支（~1520-1529）`markEnd()` + `delayExecute(checkEnd)`，`checkEnd`（1570-1587）→ `doEndWorkflow(WF_STATUS_COMPLETED)`（1584），其中 1595 行 `EVENT_BEFORE_END`、1611 行 `EVENT_AFTER_END`；`WfRuntime.triggerEvent`（302-315 行）只按事件名 matchPattern 分派，无 reason 参数。`TestUseApprovalE2E.java` 实读确认只有 `invokeAction("agree", ...)` 的 wf 路径（文件中的 reject 是 BizModel mutation 测试，非 wf disagree）。`NopWfApprovableForm` 的 dao entity/BizModel/xmeta/web pages 均在 main 源集（find 列举核实）。一处事实修正：报告称其为「仓库中唯一的 *end listener 范式」不准确——nop-metadata main 资源另有 3 个 `*end` listener（metaDataContractApproval/qualityBreachApproval/tagLabelConfirmApproval），但实测三者均已按 MA7.6-01 修复（先判 `wfRt.wf.record.appState !== 'disagree'` 再 approve，注释明示），这反而印证平台标准要求该判定、而 nop-wf 的 canonical 模板未回改，风险方向不变。P1（test 资源中的 canonical 范式缺陷，生产流中即 P0）成立。 |
| [G6-22-02] | 保留（维持 P2）| 打开 `WorkflowEngineImpl.java` 核实：`doEndWorkflow` 全文件仅 2 个调用点（grep 实测 732 行 KILLED、1584 行 COMPLETED）；`WF_STATUS_EXPIRED`/`WF_STATUS_FAILED` 在 `_NopWfCoreConstants.java:104,109` 定义后 main 代码零写入点（grep 全 nop-wf 仅命中常量定义文件）；`notifySubFlowEnd`（1127 行）把 COMPLETED 状态透传父步骤。证据与结论均属实，P2（结构性根因，可排期）合理。 |
| [G6-22-03] | 保留（维持 P2）| `WfListenerModel.java` 全文实读：`matchPattern` 用 `StringHelper.matchSimplePattern(event, "*end")`，`before-end`/`after-end` 均命中；`WorkflowEngineImpl.doEndWorkflow` 中 1595/1611 行两次 `triggerEvent` 逐行可见，且 `saveWfRecord(status)`（ endTime/lastOperator 落库）位于两次触发之间——before-end 时机读到未提交状态的时序差属实；v1.xwf 13 行自证注释「避免 EVENT_BEFORE_END/AFTER_END 重复触发」存在；`docs-for-ai/02-core-guides/workflow-configuration.md` grep `before-end|after-end|*end` 零命中，确证文档只字未提。P2 成立。 |
| [G6-22-04] | 保留（维持 P2）| 打开 `nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz` 全文核对：`submitForApproval`（6-44 行）启动 wf 并反写 nopFlowId；`withdrawApproval`（46-70 行）仅校验状态后置 `approveStatus = 'UNSUBMITTED'` 即 return，全文无任何 `wfManager`/wf 交互——与引文一致。漂移路径重推成立：withdraw 后审批人 agree → 流程结束 → listener 幂等条件 `approveStatus === 'SUBMITTED'` 不成立跳过 approve → 业务 UNSUBMITTED 而流程 COMPLETED/agree。P2 成立。 |
| [G6-13-01] | 保留（维持 P2）| 打开 `DbSendCodeRateLimiter.java`：`deleteExpired(String key, ...)`（176-181 行）带具体 key 参数、只删当前键属实；日计数键 `:d" + CoreMetrics.today().toEpochDay()`（88 行）次日永不再访问属实。`DbLoginAttemptStore.java`（实际路径 io/nop/auth/service/login/，报告以短名引用）`deleteExpired` 位于 127-129 行，同型属实。`DbMfaChallengeStore.java` 头注释「批量清理为 Follow-up」实读确认（约 36 行，报告写 37，偏差 1 行不影响结论）。nop-job 目录 grep 四张表名零命中，确证无清理 job。`LoginApiBizModel.extractClientIp`（~574-592 行）取 XFF 首段且注释自认「IP 限流为次要防线」属实；`NopAuthConfigs` 中 `CFG_AUTH_RATE_TRACKER_EXPIRE` 注释明说 tracker 过期仅兜底本地 Map、不约束 DB 表。P2 成立。 |
