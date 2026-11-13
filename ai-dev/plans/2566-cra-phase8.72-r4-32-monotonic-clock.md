# 2566 CRA Phase 8.72 — R4-32 R28 墙钟敏感跨单元批（7 站点）

> Plan Status: completed
> Last Reviewed: 2026-11-12
> Source: roadmap 工作项 8.72；批次定义 = batch-proposal（cra-phase7）R4-32 行（七站点成员一字不差成立）；设计文档 = `../design/concurrency/monotonic-clock-batch.md`（裁定 A-G + 设施确认 + 勘正 3）
> Related: plan 2547（2.1-C7-004 拆出来源+nop-kernel plan-first 先例）、plan 2543（红⑧ stub 时钟钉教训）、plan 2500（cra-index :381 三条换源面义务）、plan 2555（3.1-C7-004 来源批）
> **PA**：框架核心 plan-first（沿 plan 2547 先例）——设施确认触及 nop-api-core IClock 既有面（零新 API、零签名变更，勘正 1 收窄）+ 2.1 站点在 nop-core + **TimeOut 属 nop-api-core 内部行为变更面[R1-M7 补声明]**。授权来源 = roadmap 8.72 行 + batch-proposal R4-32 行；owner doc = source-anchors（实施回填锚点）+ 各单元 owner doc；回归证据 = 七模块测试+变异红证
> **特殊约束**：① 既有设施确认制（IClock default nanoTime + CoreMetrics.nanoTime/registerClock + TestClock）零新增产品设施；② 测试缝纪律吸收 2543 红⑧（钉不覆写 seam stub、站点级 stub 优先、全局 registerClock 仅兜底+finally 恢复、真实 sleep 钉退役）；③ cra-index :381 三条（isChangeRecord/verdict TTL/maxAge）随批并入 2.1-C7-004 追加面（防扩边界=仅三条本体）；④ 2.5-C7-013 Lua 脚本 Redis TIME 优先裁决，Redis 版本兼容不可行时回退形态需重过 PA；⑤ 2.5-C7-013 与 2.3-C8-005 同批设计接缝（§1.2 口径准则）；⑥ 3.3-C7-003 第 8 面不入批（注记预留）

## Purpose

关闭 R4-32 的 **7 个墙钟敏感工单**：相对计时站点从墙钟（System.currentTimeMillis/CoreMetrics.currentTimeMillis）换源单调时钟（nanoTime），消除时钟回拨/前跳类缺陷族（租约误判、令牌桶回填、熔断窗漂移、预算失真）。既有 IClock 设施一处确认 + 七站点换源落地。

## Current Baseline（live 亲读 @ 2026-11-12，取证 agent fresh session；勘正 3 已登记）

（逐站点锚点见设计文档锚点表[R1-m1 断引用勘正：设计无 §〇 节]；设施面：nop-api-core IClock default nanoTime + CoreMetrics.nanoTime/registerClock + nop-autotest TestClock 全部已在位——勘正 1「一处设计」实为既有设施确认）

## Goals

- 7 站点全部换源 nanoTime（或站点内合适单调形态），载体先红后绿/stub 注入红证（吸收红⑧纪律）。
- cra-index :381 三条追加面覆盖 + 两处台账扩面注记（kernel 台账+batch-proposal 行）。
- 契约落 source-anchors 单调时钟锚点行 + 各单元 owner doc。

## Non-Goals

- 3.3-C7-003 第 8 面（P3 条化回升面随 B1-11，不入批）；FeishuConnector:465（C7-003 入站滚动窗面——勘正 2 防扩）；mtime/C7-007 判据结构改写；绝对时间戳写面（lease/dueTime epoch 语义保留经缝）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（裁定 A-G + 设施确认 + 勘正 3）

- [x] 设计文档（`ai-dev/design/concurrency/monotonic-clock-batch.md`）
- [x] 成员取证（七站点锚点 + 设施已在位确认 + :381 三条边界判定 + 勘正 3）
- [x] 独立 draft review R2（fresh session 换 task_id：1B+2M+6m 全修——B2-R2-1 M3 落错方向产生功能自毁形态（读侧换源 lastLoadTime 保留墙钟=终态即混源、与变异②判死形态相同、闭环不可能）→恢复 :514+:145 成对换源+cra-index 原文勘正注记/M-R2-1 变异③删除未传导 §四-A→两条各一钉/M-R2-2 1.6 分支叉漏第三分支（诊断展示→保留墙钟）/m-R2-1~6 文件归属/toString 连带/:81 javadoc/TimeOut getter javadoc/ThresholdBreaker 无构造缝点明/ms→ns 换算；R1 15 项落位核验 14 项 PASS+五项想象性分析推演全可执行）
- [x] 独立 draft review（R1 must-fix-first：1B+7M+7m 全修——B1 :381 第(1)条 :56 now 与 mtime 同域耦合换源=静默杀死写入年龄锚→改绝对域豁免注记+变异③删除/M1 VirtualResourceDependencySet:28 mtime 域剔除/M2 expireAt 泄漏 tryLockAndExecute 对外回调契约全调用方回归面/M3 :381 三条映射对齐原文/M4 WfTaskScanner 双缝/M5 exit 红证口径对齐/M6 Lua 三裁决（秒针+replicate_commands+testcontainers）/M7 TimeOut 锚点勘正+nop-api-core PA 补声明/m1-m7 随批）

### Phase 2 - 7 站点实现 + 回归测试

Status: completed
Targets: nop-kernel(nop-core/nop-commons) + nop-wf[R1-m2 勘正：WfTaskScanner ∈ nop-wf-scheduler 非 nop-task] + nop-retry + nop-gateway[R1-m2：C8-005 在 nop-gateway 非 nop-graphql] + nop-persistence(nop-nosql-lettuce) + nop-ai + nop-ai-gateway
Item Types: `Fix`（7）

**实施顺序**（设施确认先行 → 2.1 P0 先行 → 其余站点）

1. 2.1-C7-004+CL-034+:381 三条追加面：**C7-004 台账站点+三条**[R1-m6 范围勘正：非全量扫尾义务]（LocalResourceLockManager×7/IResourceLock:82/ResourceMultiLock/DefaultTaskExecutionQueue/ResourceCacheEntry :145/:247/:372/:392 判定侧/TimeOut[R1-M7 锚点 getExpireTime/getRemainingTime]）原子换 CoreMetrics.nanoTime()；VirtualResourceDependencySet:28 剔除[R1-M1 mtime 域]；:56 R1-B1 绝对域豁免不改码；getExpireTime+tryLockAndExecute expireAt 回调契约 javadoc 修订[R1-M2 全调用方回归面]；mtime/C7-007 零改写+checkTimeout 日志行连带[R1-m4]；变异红证两条各一钉（租约回拨/maxAge 混源行为断言[R1-m3 符号预设不可靠]；变异③已删[R2-M1]）
2. 1.5-C7-006：WfTaskScanner **双缝**[R1-M4]（nowMillis wall=lease/dueTime 绝对域 + nowNanos=预算 4 站点）；红证注入点=预算缝；回拨绝对域自愈型注记
3. 1.6-C7-011：[R1-m5 措辞如实勘正：RetryEngineImpl 首提/续租/elapsed 已全在 getCurrentTime 缝上] 本站=首提归一核验钉+RetryScannerImpl lastScanFailureTimeMillis=[R2-M2 裁定] 诊断展示消费（getter+测试 >0 断言）→保留墙钟+注记；红⑧ 形态钉
4. 2.3-C8-005：TokenBucket :158/:171 换 nanoTime（回拨冻结/前跳集中回填消除）；stub 回拨注入红证
5. 2.5-C7-013：LettuceRateLimiter :56 禁本地钟——Lua 脚本 Redis TIME[R1-M6 三裁决：秒针钉死+存量键注记/replicate_commands 脚本首行/testcontainers 行为钉]（客户端 now 参数移除；回退形态重过 PA）
6. 3.1-C7-004：ThresholdBreaker 4 站点换 nanoTime；ProviderFailoverQueue 构造缝默认 CoreMetrics::nanoTime；回拨腿红证
7. 3.3-C7-002：FailoverStreamFlow :181/:608/:703 换 nanoTime（仅 metrics 时长维度自标）；C7-003 预留注记

Exit Criteria:

- [x] 7 站点全部换源实施（S1 2.1+CL-034 / S2 1.5 双缝 / S3 1.6 首提归一[R1-m5 live 勘正：newRecord 原为缝外残留] / S4 2.3 / S5 2.5 / S6 3.1 / S7 3.3；证据=_tmp/red-green-ledger-2566.md）
- [x] 载体测试存在且断言目标行为（stub 回拨/前跳注入红证，红⑧纪律遵守——钉不覆写 seam stub；S3 钉 stub getDbEstimatedClock 下游、S5 行为钉 docker 门控诚实登记）
- [x] 变异红证逐站点 ≥1 链或结构钉诚实登记[R1-M5 口径与设计 §四 对齐]（七站点：S1×3 链/S2/S3/S4 双腿/S5 结构钉变异/S6/S7 各 1 链；S2 绝对域墙钟缝为结构钉登记）
- [x] :381 三条追加面覆盖（(1)豁免/(2)(3)换源+lastLoadTime 成对勘正注记）+ 两处台账扩面注记（cra-index :383 勘正注记 + G27 行 + batch-proposal R4-32 行）
- [x] E3：七模块逐模块绿（-am 全量形态被上游预存缺陷/并行抖动阻断→按 2565 先例 solo 逐模块口径：nop-wf/nop-retry/nop-service-framework/nop-persistence/nop-ai-gateway EXIT=0；nop-kernel 562 tests 3 fail=已知 flake 单跑 4/4 绿归因；nop-ai 1 err=nop-ai-shell 预存[批外零依赖]；详见 ledger 门禁节）
- [x] E1 全量编译门禁 BUILD SUCCESS（`-pl '!nop-task/nop-task-core'` 排除形态 EXIT=0；全量形态 FAILURE@nop-task-core=工作区预存未解决 merge[UU 冲突标记，本批零触碰，登记交接阻断非本批引入]）
- [x] source-anchors 单调时钟锚点行（R4-32 行）+ 各单元 owner doc 落账（nop-wf/nop-retry/nop-ai/nop-ai-gateway/model-first-development 五处新节）
- [x] 工具门禁：check-doc-links --strict EXIT=0（0 errors）；scan-hollow `--severity high` 七触达模块 Critical=0/High=10 全部批外预存位逐条核对
- [x] `ai-dev/logs/` 已更新（2026/11-12.md plan 2566 实施条目）

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（7/7）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（触达模块）
- [x] `./mvnw test`（逐模块）
- [x] 代码规范自查 + scan-hollow 通过

## Deferred But Adjudicated

### 3.3-C7-003 第 8 面 / FeishuConnector:465 / Redis 版本兼容回退形态（如触发）
- Classification: `watch-only residual`（裁定边界明示）
- Why Not Blocking Closure: C7-003 随 B1-11 条化激活；Feishu 属 C7-003 面；Redis 回退形态若触发需重过 PA（登记后另批）。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-12，must-fix-first）**：1B+7M+7m 全修落 plan/design。B1 :381 追加面① DefaultResourceChangeChecker:56 `now` 与 mtime epoch 同表达式耦合（writeAge=now-current），换 nanoTime 恒巨大负值→宽限双条件永久退化=静默杀死 plan 2500 写入年龄锚（测试绿但行为回归）→改判绝对域豁免注记（保留 currentTimeMillis 落账豁免+理由）、变异③删除；M1 VirtualResourceDependencySet:28 为 mtime 域替身误入换源清单→剔除；M2 expireAt 经 tryLockAndExecute action.apply 泄漏为对外回调契约（:124/:242/:268 也消费，「仅 :174 内部比较」事实错误）→javadoc 修订覆盖回调参数语义+全调用方回归面；M3 :381 三条映射错位（(2)=verdictCheckTime 非 isRefreshEnabled，后者原文零改写）→落账口径对齐原文；M4 WfTaskScanner 单缝串双时钟域→双缝 nowMillis/nowNanos、红证注入点=预算缝；M5 exit「≥2 链」与设计每站 1 链不符→口径对齐；M6 Lua 改造三裁决（TIME 秒针钉死+存量键注记/replicate_commands 首行/testcontainers 先例构造）；M7 TimeOut 无 getEndTimeMillis（实为 getExpireTime/getRemainingTime）+TimeOut 属 nop-api-core 需补 PA 声明；m1 §〇 断引用/m2 Targets 模块名勘正（nop-wf/nop-gateway）/m3 混源红证改行为断言/m4 checkTimeout debug 日志连带/m5 1.6 站点措辞如实（缝已在位）/m6 「全部站点」改「台账站点+三条」防全量扫尾误读/m7 getLastLoadTime 零消费者注记。设施面六站点锚点全部核实吻合。修正后待复审。

**Draft review R2（fresh session，2026-11-12）**：1B+2M+6m——B2-R2-1 M3 修正落错方向（「:514 lastLoadTime 零改写」导致读侧换源后终态即混源：nanoTime 原点远小于 epoch→now-lastLoadTime 恒巨大负值→maxAge 永不触发/节流永假，与变异②判死形态相同、先红后绿闭环不可能，且与 §二-3 成对口径自相矛盾）→恢复 :514+:145 成对换源、cra-index :381 原文「零改写」系 plan 2500 写定时未虑混源危险的技术错误→落账勘正注记（零改写=判据结构零改写、时钟源同源切换）；M-R2-1 变异③删除未传导到 §四-A（豁免站点 revert 墙钟=无差异变异不可构造）→改两条各一钉；M-R2-2 1.6 二分支叉漏实际命中的第三分支（lastScanFailureTimeMillis=对外诊断时间戳→保留墙钟）；m-R2-1 :124/:242/:268 文件归属勘正（属 LocalResourceLockManager）/m-R2-2 ResourceLockState.toString 日志连带/m-R2-3 :81 javadoc 注记行同步豁免措辞/m-R2-4 TimeOut.getExpireTime getter 翻转补 javadoc/m-R2-5 ThresholdBreaker 无构造缝→载体走 registerClock 兜底+:526 注释行订正/m-R2-6 预算 ms→ns 换算登记。R1 15 项落位核验 14 项 PASS；五项想象性分析推演全可执行。两轮收敛，修正后可执行，进入实施。


## Closure

Status Note: 8.72/R4-32 全 7 站点落地（2.1 kernel/core 整域原子换源含 ResourceCacheEntry 成对同源[R2-B1]+TimeOut 内部化+CL-034+:381 三点[豁免/verdict TTL/maxAge]/1.5 双缝/1.6 newRecord 归一+诊断戳保留墙钟[R2-M2]/2.3 令牌桶/2.5 Redis TIME Lua 三裁决/3.1 熔断+队列缝/3.3 窗口成对），既有设施确认零新增产品设施，红绿+stub 注入红证逐链落 `_tmp/red-green-ledger-2566.md`，冻结面零回退，PA（nop-api-core TimeOut 内部行为变更）按 plan 2547 先例声明履行，五处 owner doc+source-anchors+cra-index/batch-proposal 扩面注记落账。closure audit APPROVED（0B/0M/3m）。
Completed: 2026-11-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（APPROVED，七站点 14 面全部 live 取证 PASS）
- Evidence:
  - 七站点换源 live 取证全 PASS（LRLM ctm=0/nano=11、RCE 成对同源 :153/:257+:530、双缝 ctm=0、lua replicate_commands+TIME、ThresholdBreaker nano=4 等）
  - 载体测试全在（sleep 钉改形+回拨腿/双缝钉/红⑧ 形态钉/回拨+前跳/Docker 门控行为钉/registerClock 兜底钉/挂门回拨）
  - 红绿账本抽验 4 链 grep 断言与 live 一致；冻结面零回退
  - 实跑：四模块 517 tests 0F/0E；nop-kernel 562 tests 3 fail=TestResourceCacheInvalidationChain 已知 flake（单跑 4/4 GREEN 归因坐实）
  - Anti-Hollow 三链连通（RCE 判定相减消费/TokenBucket 回填计算/LRLM 租约判定+回调契约）
  - 工作区事件留痕：实施期间 nop-task-core 5 文件 UU 冲突残留（stash apply 残留非本批内容）→coordinator 恢复 HEAD 态+编译验证，audit 复核 git diff HEAD 零输出
  - Minor-1 测试命令模块名→目录路径勘正；Minor-2 本收口段落账；Minor-3 ledger 未引用 testLock 用例（备注维持）
  - `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0

Follow-up:

- 2.5 Docker 行为钉（testcontainers）在 docker 不可用环境门控登记——docker 可用环境补跑留证
- 3.3-C7-003 第 8 面（P3 条化回升随 B1-11）
- nop-rpc TestDefaultFlowControlRunner 4 err=HEAD 预存确定性缺陷（=独立工单 2.3-C8-008 面，登记 9.1 议题）

