# 2395 full-code-review R1.29 — nop-cluster 修复批（P1-4.4-C1..C4）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.29；findings `P1-4.4-C1..C4`（check/msg-cluster-cred.md P1-1..P1-4，4.4 §2.3 标定维持），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.4-format-search-message-cluster-audit.md` §2.3
> Related: P1-4.2-004（sentinel 零测试——本批 sentinel 判别测试即首建测试树，补测义务部分履行，完整补测归 R1.41）；P2-4.2-001（nacos 零测试——同上，互补关系维持）；P1-4.4-C3 与 R1.41 同批互引
> Owner Doc: `docs-for-ai/01-repo-map/module-groups.md`（cluster 面无独立 owner doc——语义注记落 network.md 与既有先例一致）

## Purpose

收口 nop-cluster 四条 P1：①AbstractLeaderElector 选主注解 `@cfg;` 笔误——装配即 IOC 解析失败启动崩溃；②LeastActiveLoadBalance 窗口回绕未取模——startIndex>0 时 IndexOutOfBoundsException；③SentinelFlowControlRunner.runAsync 成功路径 Context 泄漏（finally 有条件 exit 永不执行）；④NacosNamingService.copyFromInstance `@@` 无 pos 守卫——无组名 serviceName 串崩溃。

## Current Baseline

- **C1**：`AbstractLeaderElector.java:72` `@InjectValue("@cfg;nop.cluster.leader.lease-safe-gap|4000")`——全仓 `@cfg;` 仅此 1 处（同类注解 :45/:54/:63/:89/:94 均 `@cfg:`）；失败链：IocConstants.PREFIX_CFG="@cfg:" → parsePrefixExpr 不匹配 → ERR_IOC_INVALID_BIND_EXPR；装配点 app-dao.beans.xml nopSysDaoLeaderElector（显式置 true 即装配=启动失败）。
- **C2**：`LeastActiveLoadBalance.choose:64` `int i = startIndex + currentCursor` 未取模（startIndex=random.nextInt(n)，max=min(n,10) 回绕产生 i≥n）→ :73/:78 存原始 i → :90/:99/:107 items.get(i) 越界；放大器：ServiceLoadBalanceAdapter.getActiveCount 恒 0 → 全体并列 → 必走随机分支；nopLoadBalance_leastActive 已注册；TestLoadBalance 无 LeastActive 用例（nop-cluster-core/lb）。
- **C3**：`SentinelFlowControlRunner.runAsync:47` ContextUtil.enter；成功路径 proceedCalled=true → finally :88 `if(!proceedCalled)` 为假 → ContextUtil.exit() 永不执行（Context 泄漏——发起线程 context 栈持续增长）；同步版 run():166-173 finally 无条件 exit 差异确凿；whenComplete 仅 serviceEntryArg.exit（entry 与 context 配对独立）。sentinel 子模块零测试。
- **C4**：`NacosNamingService.copyFromInstance:290-292` indexOf("@@") 无 pos 防护直接 substring——对照 ServiceInstance.getNormalizedServiceName:251-256 `pos > 0` 防护（防御不一致）；nacos 子模块零测试。
- **测试基建**：nop-cluster-core 测试树成熟（lb/elector/discovery/naming 子包）；sentinel/nacos 零测试（新建树 + junit test 依赖——R1.16/2391 先例）。

## Goals

- C1：`@cfg;` → `@cfg:`（装配冒烟测试）。
- C2：窗口下标取模 `(startIndex + currentCursor) % n`（Dubbo 原版形态——leastIndexs 存取模后下标，:90/:99/:107 直接 get）；TestLoadBalance 补 LeastActive 用例（回绕场景）。
- C3：finally 无条件 `ContextUtil.exit()`（对齐同步版；serviceEntry.exit 的 !proceedCalled 分支保留）；sentinel 测试树首建 + Context 泄漏判别。
- C4：`pos > 0` 守卫对齐 getNormalizedServiceName（无 "@@" 时 groupName 不设、serviceName 原样）；nacos 测试树首建 + 判别。
- 判别测试四腿 stash 实证；nop-cluster 各模块测试全绿。

## Non-Goals

- sentinel/nacos 完整补测（R1.41）；ConsistentHash 环缓存等 P2 台账行（P2-4.4 系）；roundRobin 装配错配（标定 #8 为 P2 台账）。

## Scope

### In Scope

- `AbstractLeaderElector:72` 注解笔误 + elector 装配冒烟
- `LeastActiveLoadBalance.choose` 取模 + TestLoadBalance LeastActive 用例
- `SentinelFlowControlRunner.runAsync` finally 无条件 exit + sentinel 测试树首建（FlowControlEntry 构造 + Context 判别）
- `NacosNamingService.copyFromInstance` pos 守卫 + nacos 测试树首建（Instance 构造）
- owner doc：network.md cluster 语义注记

### Out Of Scope

- Non-Goals 全部；R1.41 补测义务的完整履行（本批仅判别级测试）

## Execution Plan

### Phase 1 - 四修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] C1：AbstractLeaderElector:72 `@cfg;` → `@cfg:`
- [x] C2：LeastActiveLoadBalance:64 `int i = (startIndex + currentCursor) % n;`（items.get(i) 直接用；javadoc 记录回绕语义）
- [x] C3：runAsync finally 改无条件 `ContextUtil.exit()`（保留 serviceEntry 的 !proceedCalled exit 分支；javadoc 记录 entry/context 配对差异）
- [x] C4：copyFromInstance `pos > 0` 守卫（对齐 getNormalizedServiceName；无 "@@" 时 groupName 保持 null、serviceName 原样）
- [x] 测试：①C1 装配冒烟——elector 测试包新增（临时 beans 定义 elector 子类 bean 或直接容器加载验证注解解析通过；实施时核 elector 测试现有形态）；②C2——TestLoadBalance 补 testLeastActiveWrapsWithoutOverflow：n=3 实例、差异化 active（adapter stub）+回绕起步 → 不抛 IOOBE 且选中活跃最小者；③C3——sentinel 测试树首建：runAsync 正常完成后 `ContextUtil.getCurEntry()==null`（修复前泄漏非 null）+BlockException 路径 exit 配对；④C4——nacos 测试树首建：copyFromInstance 无 "@@" serviceName 不抛且原样；带 "@@" 正常拆组
- [x] **判别验证（四腿 stash）**：各修 stash 后对应测试 FAIL（C1 装配异常/C2 IOOBE/C3 context 残留/C4 StringIndexOutOfBounds），恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 四修全落地
- [x] 判别测试四腿绿 + 既有测试零回归
- [x] nop-cluster-core/sentinel/nacos 测试全绿（sentinel/nacos 测试树首建）
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] network.md：cluster 语义注记（LeastActive 回绕取模；Sentinel async exit 配对；Nacos groupName 守卫；leader 注解修正）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.29 翻 done；index P1-4.4-C1..C4 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-4.4-C1..C4）
- [x] 行为/契约结果已达成（判别四腿 + 零回归）
- [x] 必要 focused verification 已完成（cluster 各模块测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-cluster/nop-cluster-core,nop-cluster/nop-cluster-sentinel,nop-cluster/nop-cluster-nacos` 全绿（core 60/sentinel 2/nacos 2，audit 复跑 _tmp/audit-2395-gate.log）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- sentinel/nacos 完整补测（R1.41——本批判别级测试为首个覆盖）
- ServiceLoadBalanceAdapter.getActiveCount 恒 0 的语义（C2 放大器——最小活跃度实际退化为随机，属独立设计决策）

## Closure

Status Note: 四修 live 亲核在位且失败链与 baseline 逐字吻合；判别四腿签名精确（@cfg; 逐字/IOOBE Index 3 of 3/context 残留 2F/Range [0,-1) 逐字）；core 60/sentinel 2/nacos 2 审计复跑全绿；收口四方一致；Anti-Hollow 三项过；follow-ups 三方一致（R1.41 补测/getActiveCount 设计决策）。audit Minor 5 项处置：归档日志缺陷（cluster-tests.log 非终态绿——以 audit-2395-gate.log 终态为准，已记录）/真实代码 commit 注明（C2-C4 diff 实际落 22de5578d7，1882d28cf1 为 sentinel 测试迭代——git index 冲突事故副产品，本段即为溯源声明）/C1 反射形态裁定（index.md 已记，plan 补记）/C2 成员断言替代最小活跃断言（恒 0 语义下无意义，判别力无损）/javadoc 行内注释化（语义满足）。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_fb2b2a0b-3ab4-419c-8c6a-74997fe53021
- Evidence:
  - C1 PASS：AbstractLeaderElector:72 @cfg:（ConfigExpressionProcessor:139/:155 失败链亲读）；TestLeaderElectorConfigBinding 1/1
  - C2 PASS：LeastActiveLoadBalance:66 取模（Dubbo 形态）；回绕用例 5/5（含新用例）
  - C3 PASS：SentinelFlowControlRunner:98 无条件 exit（:88-93 entry 分支保留；:169-176 同步版对照）；2/2
  - C4 PASS：NacosNamingService:292-296 pos>0（static 化）；2/2
  - 判别证据：disc-c1b（got @cfg; 逐字）/disc-c2（IOOBE 逐字）/disc-c3（恰 2F）/disc-c4（Range [0,-1) 逐字）
  - 收口完整性：roadmap :160 done；index :182-185 fixed；daily log 10-10.md:3-14（环境事件如实记录）；network.md :62-64
  - 工具门禁：doc-links/checklist/hollow 三模块均 0（audit 亲跑）
  - Follow-ups 诚实性：R1.41 补测/getActiveCount 恒 0 设计决策三方一致，non-blocking 成立
  - Minor 处置：归档日志缺陷以 audit-2395-gate.log 为终态证据；真实 commit 注明（22de5578d7+1882d28cf1）；C1/C2 形态裁定补记本段

Follow-up:

- sentinel/nacos 完整补测（R1.41——本批判别级测试为首个覆盖）；getActiveCount 恒 0 语义（独立设计决策 follow-up）；ConsistentHash 环缓存等 P2 台账行
