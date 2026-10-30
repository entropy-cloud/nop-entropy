# 2515 CRA Phase 8.21 — R2-12 1.6 tcc 批（13 项：CAS 失败阻断补偿/回写内存/续租族 + 簿记改判/future 拓扑断裂 + OVERWRITE 原子化/幽灵链 + RUNNING attempt 砖化 + 僵尸记录 + tcc 清理接线/索引 + namespace 扫描 + C8-017 搭车）

> Design Review: R2 agent_bb236b91 @ 2026-10-30（修复清单机械核验 6 点全部闭合[勘误 6 OVERWRITE :287-292/成员表三格/C10 前置集行内接缝[本轮归位]/C6-012×2/:213-215/裁定⑥两层位点]——PASS）
> Design Review: R2 agent_1c1e1312 @ 2026-10-30（第 1 轮 FAIL[3 Major/3 Minor]：Major-1 「首跳失败聚合不可达」断言在 live whenComplete 拓扑不成立→err==null 显式门控+三前提显式检查；Major-2 DB 权威重载提交可见性竞态→store CAS future 改 afterCommit 完成[评审新-1：须配对 afterCompletion 回滚分支 completeExceptionally——协调方修入]；Major-3 续租 attempt 内残留窗→(a) 轮间续租+attempt 级超时上界<租约装配期 fail-fast；Minor×3 闭合[TccEngine LifeCycleSupport 生命周期挂接+beans 装配行/拒绝理由改写/replaceRecord dao 原语]——第 2 轮 PASS[probe (b) err 门控半边正确无新问题]）
> Plan Status: active
> Draft Review: R1 agent_bb236b91 @ 2026-10-30（FAIL[2 Major/4 Minor]：Major-1 OVERWRITE 漂移台账错误映射[:257-261 实为 resolveInsertRaceWinner 体]→勘误 6 纠正为 :287-292[handleBlockStrategy :275-298]；Major-2 C10-003 覆盖面缺 C3-010/C6-015 批外接缝→补登记；Minor×4[cra-index 行号 live 校正/C6-012 批外补登/TccStatus 模块标注+重抛点 :213-215/裁定⑥两层簿记位点枚举]——协调方修入后转 active）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.21（R2-12，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L163，Status=todo[本 plan 立项时回写为「todo（plan 2515 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-12 行（L86 全文：finding ID 清单 13 项[1.6-C1-001；1.6-C3-002；1.6-C3-003；1.6-C3-012；1.6-C6-002；1.6-C3-005；1.6-C3-006；1.6-C6-003；1.6-C7-003；1.6-C7-005；1.6-C7-012；1.6-C8-012；1.6-C8-017] / 模块与验证命令 nop-retry `./mvnw test -pl nop-retry -am`；nop-tcc `./mvnw test -pl nop-tcc -am` / Owner Doc=`docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md` / PA=无 / 依赖「无前置（可并行）；C7-001/C8-001 平台方案在 R1-07/R1-01；C8-012 R21 纪律（C8-017 同 diff 搭车）」/ exit criteria 增补：**CAS 失败阻断补偿/回写内存/续租族闭合；簿记异常不覆盖业务结果；OVERWRITE 原子化+死信（R10）；僵尸记录终态化；清理 API 生产接线+索引；namespace 扫描修复；C8-012 返回 future=回调挂接 future（C10-001 覆盖）**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.6 审计目录 `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/`（C1-shared-mutable-state.md C1-001 :64 起；C3-async-chains.md C3-002 :70 / C3-003 :95 / C3-005 :149 / C3-006 :175；C6-resource-lifecycle.md C6-002 :82 / C6-003 :119；C7-long-running.md C7-003 :107 / C7-005 :169 / C7-012 :349；C8-backpressure.md C8-012 证据链 :397 / C8-017 = C8-round3.md :145；merge-adjudication.md 裁定 1–8 必裁 + §二 M2/M3/M4/M6/M7/M8 并组行 :108-114 + §3.3 C10 映射 :233-246；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 1.6 节 :154-184 + 小结行 :775[**1.6（10）** 全映射]）；执行基线 @ `2cafbc3996`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.20 已 done，plan 2495–2514 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2502（R1-07，completed——**R01 族级 SSOT `ai-dev/design/concurrency/ledger-retention-policy.md` 出处**：1.6-C7-001 retry 三表保留清理已修（提交 873365710c）；其收口注记显式声明「nop-tcc 两表清理接线=C7-005 归 R2-12（落地时对齐本批族级 SSOT）」——本批裁定⑧为该声明的承接执行，引用 SSOT 不重建）；plan 2496（R1-01，completed——**1.6-C8-001 扫描链绝缘已修**：RetryScanner 绝缘驱动/链存活指标/积压兜底语义为 owner doc `nop-retry.md` §扫描链成文契约，本批全部扫描面修复不得回归链存活/失败可见契约）；plan 2514（R2-11，completed——PA 批内申报先例与「三联环境裁定」模式引用）；plan 2386/2388（TCC 执行表权限/崩溃恢复对称续跑前案——`TccRunner` TIMEOUT_SUCCESS 聚合与恢复路由为本批零回退钉）；**下游**：R4-10（8.50，1.6 补偿/护栏束 G17 14 项）**硬依赖本批**（batch-proposal L125「依赖 R2-12/R1-07」；roadmap 8.50 行同）——批末须回写就绪注记；R4-32（C7-011 拆出）与本批无依赖；**并行协同**：无同门禁在飞批，本批可独立开跑；G18（P2-G17 行外搭车组，batch-proposal L173「R2-12 提前（C8-012 同 diff）」）随本批闭合
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-12 行授权列）。本批十三成员站点分别位于 `nop-retry/nop-retry-engine`（C3-003/C3-005/C3-006/C6-002/C6-003/C7-003/C7-012/C8-012/C8-017）、`nop-tcc/nop-tcc-core`（C1-001/C3-002/C3-012）、`nop-tcc/nop-tcc-dao`（C1-001/C3-002/C7-005）、旁证 `nop-job/nop-job-retry-adapter`（C7-012 提交侧证据，只读引用不改码）、`nop-kernel/nop-api-core`（C8-017 IRpcCall javadoc 契约句，见下）——**核心站点均非 Protected Area**（业务模块 impl/store 层）。**批内 PA 申报一项：裁定⑧ C7-005 索引补齐须在 `nop-tcc/model/nop-tcc.orm.xml` 新增 `<indexes>`（expireTime+status+retryTimes 复合索引 + beginTime 排序索引）→ 触发 AGENTS.md「ORM 模型结构 plan-first」——申报载体=本 PA 行+设计文档裁定⑧+独立评审闭环，沿 2510/2514 先例「模型结构变更须先裁定方案并在 plan 登记后方可采」口径；配套 DDL 迁移脚本为非 `_` 前缀新建（落 `nop-tcc/deploy/`，载体随裁定⑧成文）；其余站点不触 `model/*.orm.xml`**（C6-002 仅消费既有 `UK_RETRY_ATTEMPT_RECORD_NO` 约束，零模型变更）。**C8-017 若 Phase 1 裁定须改 `IRpcCall.java` javadoc（nop-api-core 契约文本）→ 触发 nop-api-core PA**：预设默认分支 = **零平台层触碰**（契约句落 `nop-retry.md` owner doc + `RetryTaskImpl.callAsync` 实现侧收敛，javadoc 修订仅作为备选分支呈 Phase 1，若采须在该裁定项内批内申报后方可实施）。本批不触碰 R4-10/R4-32 批外成员本体。

## Purpose

关闭 R2-12 批的 13 项已确认缺陷（12 项 P1 全部 open + 1 项 P2 搭车 C8-017，cra-index 台账行 L168–L180 中批次列=R2-12 的行（live 行号，勘误 6 校正））。核心结果面有七：①**CAS 失败传播族闭合**（C1-001←C3-001、C3-002←C3-016、C3-012）——tcc 全局/分支状态 CAS 失败（0 行/异常）当前被吞为 WARN 后正常返回，守卫基于各链私有过期内存副本，补偿/确认 RPC 无条件照发（cancel/confirm 对撞打破参与方互斥）；分支 CAS 失败不回写内存使聚合按陈旧状态折叠出误报终态（全局 CANCEL_SUCCESS × 分支 CANCEL_FAILED → 预留资源永久悬挂）；全局首跳 CAS 异常致分支零驱动仍折叠 SUCCESS 直写终态。修复=CAS 结果显式传播+门控短路+聚合读 DB 权威状态。②**续租族闭合**（C3-003←C1-009）——retry 即时重试链全程不续租 nextTriggerTime，租约（默认 600s）到期扫描器接管在途任务双执行业务副作用；修复=调用链参与租约协议（续租+被抢中止）。③**簿记不覆盖业务结果**（C3-006←C6-016 + C8-012←C6-001/C1-008/C1-003A 完成期记账失败族）——attempt 簿记写内联结果回调，簿记异常改判业务结论（成功→重试）；`executeImmediateRetry` 手工 promise 拓扑断裂（返回 future ≠ 回调挂接 future，无 Throwable 兜底），同步 DB 异常下扫描器 `allOf().join()` 永久冻结；修复=记账/业务分层 + future 拓扑单一化（返回=挂接）+ 兜底，**C8-017 同 diff 搭车**（提交面失败交付三通道不对称契约缺口，R21 纪律）。④**OVERWRITE 原子化+死信（R10）**（C6-003←C8-006 + C3-005）——delete+insert 非事务包络，半个成功任务蒸发无死信；删行不取消在途旧链（幽灵任务双执行）；修复=原子替换包络+失败死信快照+代际取消在途链。⑤**终态化两处**（C6-002 + C7-003）——崩溃/挂死后 RUNNING attempt 永久残留并经 UK 碰撞把记录永久砖化；解析/同步异常路径产生永不终止僵尸记录（每租约周期 144 条 ERROR/天）；修复=attempt 对账/UK 容错 + 异常路径死信化既有兜底补齐。⑥**账本卫生**（C7-005）——tcc 清理 API 零生产调用方 + 耗尽行双重永久排除 + `nop-tcc.orm.xml` 全表零索引；修复=清理任务生产接线（对齐 R01 族级 SSOT）+ 索引（orm PA 批内申报）。⑦**namespace 扫描修复**（C7-012）——扫描器单值 `namespaceId="default"` 硬过滤，非 default 记录失败后重试链静默断裂；修复=覆盖面三分支一次定夺。批末 roadmap 8.21 → done，cra-index 台账 13 行（含搭车 G18 行注记）收敛，R4-10 硬依赖就绪。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-12 = 13 项成员（P1×12 + P2 搭车×1）；cra-index 台账独立行（live 行号，勘误 6 校正）：L168 C8-012（←C6-001/C1-008/C1-003A）、L169 C1-001（←C3-001）、L170 C3-002（←C3-016）、L171 C3-003（←C1-009）、L172 C3-005、L173 C3-006（←C6-016）、L174 C3-012、L175 C6-002、L176 C6-003（←C8-006）、L178 C7-003、L179 C7-005、L180 C7-012（以 cra-index live 为准逐行核对，均为 open、批次列均=R2-12）；P2-G18（L402）= C8-017 搭车行「批次随该 P1 批→实际批号 R2-12」；roadmap 8.21 行 Status=todo。前置：无前置（可并行），plan 2495–2514 前序批均收口——基线门禁已满足。
- **同单元已修/批外面登记**：1.6-C7-001（retry 三表零保留）已随 R1-07（plan 2502 分片 A）fixed（cra-index 台账行注记「nop-tcc 两表零保留=C7-005 归 R2-12」——**本批裁定⑧为该边界声明的承接**）；1.6-C8-001（扫描循环停摆）已随 R1-01（plan 2496）fixed（绝缘层+重臂+观测，live `RetryScannerImpl.java:100-130` 注释钉在案）——本批扫描面修复零回退其契约。1.6 单元合并后台账 50 条中：P1×14 → 12 项入本批（C7-001 已修 + C8-001 已修 = 余 12）+ P2 搭车 C8-017；其余 P2/P3 全部归 R4-10/R4-32/G19/G20/dormant（见 Non-Goals）——批次覆盖口径完整。
- **吸收成员（随主条目同 diff，不单列工单）**：C8-012←C6-001（同位点同拓扑）/C1-008（生产者同步抛位点 E-a/E-c）/C1-003 路径A（回调异常落点，裁定 13 解体）；C1-001←C3-001（同位点同机制，同值 CAS retryTimes 虚耗面随并）；C3-002←C3-016（同函数双失败模式 0 行/异常）；C3-003←C1-009（首执行窗=租约协议缺失的另一触发窗）；C3-006←C6-016（同位点 :519 簿记内联）；C6-003←C8-006（非原子 OVERWRITE 同位点）——六族吸收明细以 merge-adjudication §二 M2/M3/M4/M6/M7/M8 行 + 裁定 13 为准；cra-index 回填时以主 ID 收敛、吸收成员括注。
- **完成期记账失败族登记**（merge-adjudication 裁定 4）：C3-006（改写形态）+ C8-012（挂起形态）两形态均在本批同批统一设计（记账失败不得否决/改写/悬置业务结果）；CL-001（记录存活重拾形态，CL 单列）为同族相邻不并入，其处置边界在裁定⑥接缝声明（重拾面由⑤/⑦终态化面自然收窄）。**OVERWRITE 族登记**（裁定 5）：C6-003（非原子蒸发）+ C3-005（幽灵链）两 P1 在本批；C1-002（并发覆盖裸 UK 冲突暴露，P2，C10-005 覆盖面）归 R4-10 不代做——裁定⑤设计须不与其冲突（原子替换包络落地后 C1-002 暴露面自然收窄，接缝登记）。
- **C10 验收前置入批集（6 条本体收口门，随本体同 diff 落地[E2]；映射 SSOT = `inventory-p2-c10.md` 小结行 :775「1.6（10）」+ §3.3 映射表）**：C10-001（C8-012 + C3-006 收口门——假保护串行化测试：完成回调路径须真并发/真异常注入，不得仅串行调用覆盖）、C10-003（C1-001 + C3-002 + C3-012 收口门——CAS 冲突/双链对撞面真并发注入；覆盖面另含 C3-010/C6-015[批外，R4-10 随本体补测]——Phase 4 R4-10 就绪注记列名）、C10-005（C6-003 + C6-002 + C3-005 收口门——OVERWRITE 半个成功/幽灵链/UK 碰撞面；覆盖面另含 C1-002[归 R4-10，不代做]）、C10-006（C3-003 + C7-012 收口门——租约到期接管双执行面 + namespace 面断言；覆盖面另含 C3-013/C3-015/C7-011[批外]）、C10-007（C7-003 + C7-005 收口门——僵尸记录死信化 + 清理接线/索引面；覆盖面含 C7-001[已随 plan 2502 闭合]、C6-005/C8-003/C8-004[dormant/R4-10]）、C10-008（C8-017 收口门——提交面失败交付三通道断言；覆盖面另含 C7-009/C8-016/C8-008/C8-010/C8-013/C8-015[批外]；覆盖面另含 C6-012[P3，批外]）。**不在本批的 C10 处置**：C10-002（宿主 C3-004/C8-001 已修/C7-015/C7-014/C6-013，R4-10/R4-32）、C10-004（宿主 C3-008/C8-007/C7-006/C7-010/C8-004，dormant 捆绑 CL-004）、C10-009（宿主 C1-004/C1-005/C1-006/C3-009/C3-011/C3-014，R4-10；覆盖面另含 C6-012[P3，批外]）、C10-010（复核补充登记）；覆盖面另含 C3-010/C6-015[批外，R4-10 随本体补测]——Phase 4 R4-10 就绪注记列名）。
- **nop-retry/nop-tcc 域先序批状态（live 形态登记，2026-10-30 亲读）**：plan 2502（R1-07）已 landed——`RetryRecordStoreImpl.purgeExpiredLedgerRows` 三通道清理 + 引擎生命周期链接线（live `:320-345` 清理谓词 + owner doc `nop-retry.md:92-108` 配置键表）；plan 2496（R1-01）已 landed——`RetryScannerImpl.java:100-130` 绝缘驱动（running 真实化/链终止两路径/存活指标）+ `TestRetryScannerInsulation` 在案；plan 2388 恢复对称续跑（`TccTransaction.endAsync` CONFIRMING/CANCELLING 路由注释 :59-84 在案）。本批为上述已修面之上的残留缺陷修复，全部零回退。

### 对齐纪律显式条款

- **C7-005 × R01 对齐（plan 2502 承接执行：引用不重建）**：tcc 两表清理接线 = R1-07 收口注记显式留给本批的成员（cra-index 台账 C7-001 行「落地时对齐本批族级 SSOT」原文登记）。族级 SSOT = `ai-dev/design/concurrency/ledger-retention-policy.md`（保留期/TTL/行数上限 + fail-fast 包络声明，4.5 口径）——裁定⑧只做「按 SSOT 形态菜单落地 nop-tcc 两表实例」，不重开保留策略设计；配置键命名、缺省值口径、清理谓词形态与 `nop-retry.md` §账本保留策略表同构对齐（`nop.tcc.ledger.*` 键族）；**若裁定引入 nop-tcc 侧新保留/淘汰参数键，须回 R01 SSOT 修订登记（附理由），不得模块内发明**；耗尽行（retryTimes≥maxRetryCount）双重永久排除面 = 先终态化（KILLED 类终态标记）再入清理谓词（保留审计窗），与 C7-006 的「耗尽终态标记」诉求同向——但 C7-006 本体（观测/干预工具面）归 R4-10 不代做，接缝（终态标记字段由本批定、R4-10 消费同一字段）在设计文档一次写清。索引面见 PA 申报（orm plan-first）。
- **扫描面 × R1-01 绝缘层零回退**：C3-003/C7-003/C7-012/C8-012 全部触碰 `RetryScannerImpl`/`RetryEngineImpl` 扫描链——修复不得回归 plan 2496 绝缘契约（链存活/失败可见/running 真实化/存活指标，owner doc `nop-retry.md:83-91` 成文）；C8-012 修复后的 `allOf().join()` 拓扑（无论保留 join+兜底或改异步汇聚）不得重新引入「单点同步异常杀死扫描链」形态（绝缘层管 Throwable 重臂，管不了业务级永久冻结——本批修复面正在于此）。
- **C8-012 × C8-017 R21 纪律（同 diff 搭车，一次交付）**：G18 裁定（batch-proposal L173 + cra-index P2-G18 行）= C8-017 随 C8-012 同 diff 修复——裁定⑥统一呈现：future 拓扑单一化（返回=挂接+Throwable 兜底）必然收窄 C8-017 的「永不完成」通道，其契约半边（`RetryTaskImpl.callAsync` 同步抛转 `failedStage` + 提交面失败交付契约声明落 owner doc）随同一变更集落地，不另开工单、不拆批。
- **C3-003/C3-006 × A2 副作用重复族（设计共用不合并）**：merge-adjudication A2 族（租约接管/簿记改判/完成写失败重拾/排水跨窗）登记「修复可共用幂等执行令牌设计」——裁定④（续租协议）为该令牌语义的本批载体，裁定⑥/⑦消费同一语义不另发明；族内其余成员（C3-015/C7-015/CL-001）归 R4-10，接缝在裁定④声明。
- **C1-001/C3-002/C3-012 × 恢复对称续跑契约零回退**：plan 2388 裁定的 `TccTransaction.endAsync` CONFIRMING/CANCELLING 对称续跑路由（live :59-84 注释钉 + owner doc `nop-tcc.md:50-57`）零回退；修复 = CAS 失败传播与门控，不改变恢复路由本体；`TccRecordStore.fetchExpiredRecords` 版本 CAS 认领 fencing（审计覆盖度节核验为健壮）零回退；tcc 分支补偿计数耗尽后被扫描窗排除为 owner doc 已声明边界（`nop-tcc.md:55-56`）——本批不改其判级，只落裁定⑧终态化半边。
- **C8-017 × nop-api-core PA 边界**：预设默认分支 = 零平台层触碰（契约句落 `nop-retry.md` owner doc；`IRpcCall` javadoc 修订为备选分支，若采须批内申报 nop-api-core PA——见头部 PA 行）。

### 13 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `2cafbc3996` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.6-C1-001（P1；←C3-001；C10-003） | `TccRecordStore.java`（nop-tcc-dao `store`）；旁证 `TccTransaction.java`/`TccRunner.java`/`TccEngine.java`（nop-tcc-core `impl`） | 全局 CAS `updateTccStatusAsync` :162-197（0 行仅 warn 正常返回 :188-193）；分支 CAS `updateTccBranchStatusAsync` :222-283（0 行同构 :278-283）；`TccTransaction.doCancelAsync` :97-117（thenCompose 无条件补偿 :110-111）、`doConfirmAsync` :119-138（:132-133 同构）；`TccRunner.runBranchConfirmAsync` :72-96（isConfirmed 内存短路 :75、beginConfirmAsync 对 CAS 丢失正常返回后照发 RPC :79-82）、`runBranchCancelAsync` :98-122；恢复双驱动源 `TccEngine.checkExpiredTransactions` :315-343（`get(expireGap)` 超时不取消在途链 :331-334） | tcc 全局/分支状态 CAS 失败被吞为 WARN 后正常返回，互斥守卫基于各链私有过期内存副本且从不刷新——调用方 endAsync 与恢复扫描双驱动时 confirm/cancel RPC 对撞照发（TCC 参与方互斥被打破，预留资源不一致） | 裁定①（C10-003 收口门随同 diff）：CAS 失败显式传播（updated==0/异常→返回失败标记或异常完成），doCancelAsync/doConfirmAsync 与 runBranchConfirmAsync/runBranchCancelAsync 在 CAS 失败时短路不发 RPC（全模块内存守卫兄弟站点 F3 清单逐点覆盖：TccTransaction :100-108/:122-130、TccRunner :75/:101、TccBranchTransaction beginConfirmAsync/beginCancelAsync）；checkExpiredTransactions 超时分支补在飞去重或 future.cancel（幽灵双驱动源封堵，与 C3-008[R4-10] 接缝：本批只封本条双驱动源，超时弃链本体不代做）；DB 级补偿租约为备选分支；恢复对称续跑路由零回退 |
| 2 | 1.6-C3-002（P1；←C3-016；C10-003） | `TccRunner.java`（聚合）+ `TccRecordStore.java`（CAS 不回写）+ `TccTransaction.java`（聚合挂点）+ `TccStatus.java`（nop-tcc-api，排除谓词） | 聚合读内存 `aggregateCancelBranchStatus` :178-196、`aggregateConfirmBranchStatus` :198-217；`TccTransaction` whenCompleteAsync 聚合挂点 :113-116；分支 CAS 0 行内存保持旧值 `TccRecordStore.java:278-283`（`record.setStatus` 仅成功才回写 :282）；恢复排除谓词 status<CONFIRM_SUCCESS(11)（`TccStatus.java` finished 判定 :81/:108 语义） | 分支级 CAS 失败（0 行/异常）不回写内存，聚合按陈旧内存折叠 SUCCESS——全局误报终态（≥11 恢复扫描永久排除）而 DB 分支实为 CANCEL_FAILED，预留资源无机制再补偿 | 裁定②（C10-003 随同 diff）：聚合改为读 DB 权威状态（或 CAS 失败时重载分支状态后再聚合——两分支 Phase 1 一次定夺）；全局终态 CAS 之前按分支 DB 状态做最终裁决（误报 CANCEL_SUCCESS 不可达）；裁定①的 CAS 显式传播为前置（失败分支带权威状态进聚合） |
| 3 | 1.6-C3-012（P1；独立；C10-003） | `TccTransaction.java` + `TccRunner.java` + `TccRecordStore.java`（同 #1/#2 站点族） | 全局首跳 CAS 异常路径：`doCancelAsync`/`doConfirmAsync` CAS 异常（非 0 行）时 thenCompose 链断裂→分支零驱动→聚合折叠 SUCCESS→陈旧 from 直写终态（单链可达，`TccTransaction.java:110-111/:132-133` + `TccRunner` 聚合 + `TccRecordStore` CAS 异常完成单元格） | 全局首跳 CAS 抛异常→thenCompose 跳过→分支零驱动→聚合仍折叠 SUCCESS→以陈旧 from 状态直写全局终态（分支补偿从未发生但事务已「完结」） | 裁定③（C10-003 随同 diff）：CAS 异常完成单元格并入裁定①失败传播面（异常不再静默）；终态直写前置校验分支驱动确实完成（零驱动不可直写终态）；与裁定②共用「聚合/终态前 DB 权威裁决」机制，不建第二套 |
| 4 | 1.6-C3-003（P1；←C1-009；C10-006） | `RetryEngineImpl.java` + `RetryRecordStoreImpl.java`（nop-retry-engine） | 即时重试链无续租 `executeImmediateRetry` :361-424（链内只写 retryCount :377/:405，nextTriggerTime 仅 `handleExecutionFailure` :554-555 才设）；首执行一次性推延 `RetryRecordStoreImpl.newRecord` :116-121（推迟一个租约周期注释钉）；拾取条件 PENDING/RETRYING + nextTriggerTime<=now `RetryRecordStoreImpl:162-166`；tryLock 版本 CAS :184-206（`tryUpdateManyWithVersionCheck` :206）；租约缺省 `NopRetryApiConstants.DEFAULT_RETRYING_TIMEOUT_MS`（600s） | retry 调用链全程不续租 nextTriggerTime——长即时重试链或首 attempt RPC 挂起超过租约（600s）后扫描器接管在途任务双执行业务副作用（幂等键只挡新提交不挡接管） | 裁定④（C10-006 随同 diff；A2 族令牌语义载体）：即时重试每轮 attempt 前 CAS 续租 nextTriggerTime（租约被抢[版本/条件更新 0 行]则中止链——败方显式语义，不静默）；扫描器拾取/接管双条件校验（在途链不可见面封堵）；执行令牌语义一次定夺供裁定⑥/⑦消费；C1-009 首执行窗随同一协议闭合（newRecord 推延→续租协议覆盖） |
| 5 | 1.6-C3-005（P1；独立；C10-005） | `RetryEngineImpl.java` + `RetryRecordStoreImpl.java`（nop-retry-engine） | OVERWRITE 删行不取消旧链 `handleBlockStrategy` :275-298（OVERWRITE 分支 :287-292 delete→save→execute 新链）；旧链定时器持旧 record `scheduleImmediateRetry` :426-452（:439-449 到期照常执行）；旧链回写落空 `doExecute` whenComplete :495-520 + `RetryRecordStoreImpl.updateEntityDirectly` :284（行已删 0 行/异常）；全模块无 cancelToken 对续跑的取消管道 | OVERWRITE 删行后不取消在途异步链：旧链 RPC/已调度续跑继续执行业务副作用、终态回写落在已删行上（静默丢失）——幽灵任务与新旧双链同跑双执行 | 裁定⑤（C10-005 随同 diff；与 #8 同位点同族单设计）：记录级代际号/epoch（OVERWRITE 递增），旧链每次回调/续跑先校验代际再回写或短路（备选分支：canceled 标记短路——Phase 1 一次定夺）；触发窗口（调用方超时重提）为 retry 目标场景非低频 |
| 6 | 1.6-C6-003（P1；←C8-006；C10-005；R10） | `RetryEngineImpl.java` + `RetryRecordStoreImpl.java`（nop-retry-engine） | `handleBlockStrategy` OVERWRITE 分支 :287-292（先删后插无事务包络，勘误 6）；`RetryRecordStoreImpl.deleteRecord` :249-251（无 @Transactional 自动提交）；`saveRecord` :276-278（REQUIRES_NEW 独立事务，失败不回滚删除） | OVERWRITE delete+insert 非原子：删除已提交而新行插入失败（约束类失败确定性复现）→ 旧任务蒸发且无死信快照——幂等键任务整体不可恢复丢失 | 裁定⑤同族（C10-005 随同 diff）：delete+save 原子包络（store 层 `replaceRecord` @Transactional 单方法——备选分支：先插新行成功后再删旧/失败补偿恢复旧记录，Phase 1 一次定夺）；替换失败留死信快照（对齐 owner doc「死信保留全量快照」可恢复性设计）；C1-002（R4-10）暴露面收窄接缝登记 |
| 7 | 1.6-C3-006（P1；←C6-016；C10-001） | `RetryEngineImpl.java` + `RetryRecordStoreImpl.java`（nop-retry-engine） | 簿记写内联结果回调 `doExecute` whenComplete `saveAttempt` :519（抛异常则 stage 以簿记异常完成、response 丢失）；外层 ex!=null 即判业务失败 `executeSingleAttempt` :461-464、`executeImmediateRetry` 同型 :372-421；二次保存走 updateEntityDirectly `RetryRecordStoreImpl.saveAttempt` :144-150 | attempt 簿记写内联于结果回调，簿记瞬时 DB 异常覆盖业务结果——已成功的业务调用被改判失败并重试（重复副作用，重试系统最危险的错误方向） | 裁定⑥前半（C10-001 随同 diff；完成期记账失败族统一设计）：业务结论层与簿记层解耦（先捕获 response/ex 完成业务判定，簿记写移入 `whenCompleteAsync`+独立 try/catch 记日志）；`executeImmediateRetry` 同型同修；簿记失败显式可见（ERROR 日志+计数）不吞 |
| 8 | 1.6-C8-012（P1；←C6-001/C1-008/C1-003路径A；C10-001；**R21 搭车 #13**） | `RetryEngineImpl.java`（nop-retry-engine）；拓扑证据链 = C8-backpressure.md :397 起（E-a/E-b/E-c 逃逸点 + 25 站点家族全扫闭合「仅此一处现实断裂拓扑」） | 手工 promise 拓扑：`result` :368 创建、:423 返回（写方全在 cb1 闭包 :373-421 或其挂接的 `scheduleImmediateRetry().whenComplete` 内）；`scheduleImmediateRetry` future :437 创建、:451 返回（唯一写方 :441-447 delay-lambda whenComplete；`executor.schedule` 调度内部 future 被丢弃 :439）；同步 DB 异常逃逸点 = doExecute 前置持久化 :486-488（T1 延迟跳）与 cb1 内 updateRecord/handleExecutionSuccess（T2 回调体，零日志）；下游 join 冻结点 `RetryEngineImpl:680` `allOf(futures).join()` | `executeImmediateRetry` 返回 future ≠ 回调挂接 future 且无 Throwable 兜底——T1/T2 形态同步 DB 异常下 result 永不完成→扫描器 `allOf().join()` 永久冻结（部分形态零日志），租约重拾由同一冻结循环承载不可自愈 | 裁定⑥后半（C10-001 随同 diff）：future 拓扑单一化（返回 future = 回调挂接同一 promise；delay-lambda 异常显式传导至持有 future——E-a/E-b/E-c 三逃逸点逐一闭合）+ 每条链 Throwable 兜底（completeExceptionally 不悬置）；`allOf().join()` 面随裁定成文（保留 join+兜底或改异步汇聚，Phase 1 定夺；不得重新引入扫描链业务级永久冻结）；R1-01 绝缘契约零回退；**#13 C8-017 同 diff**（R21 纪律） |
| 9 | 1.6-C8-017（P2 搭车；C10-008；G18） | `RetryTaskImpl.java`/`RetryEngineImpl.java`（nop-retry-engine）；契约文本 `IRpcCall.java`（nop-api-core，只读旁证——修订为备选分支） | 同步抛注入点：`executeTask` :200-203（loadPolicyOrDefault/findRecordByIdempotentId DB 读同步抛）、:213-215（executeTask catch 内 throw e 重抛；resolveInsertRaceWinner 体 :254-268）、:287-292（OVERWRITE delete/save，勘误 6）、`handleDeadlineExceeded` :523-529、doExecute 前置持久化 :486-488；`RetryTaskImpl.callAsync` :100 直通 executeTask 无 try；triggerCallback executeTask 调用 :595 无 try | 提交面失败交付三通道不对称（同步抛/future 异常/永不完成）——IRpcCall 契约只声明 future 通道，第三方 fire-and-forget 调用方对 DB 类故障的失败感知静默失效（当下仓库内调用方均有外层 catch，缺口是契约性） | 裁定⑥契约半边（C10-008 随同 diff；R21 同 diff 搭车）：`RetryTaskImpl.callAsync` try-catch(Throwable) 将同步抛转 `CompletableFuture.failedStage`（消灭双后置条件，与裁定⑥同变更集）；提交面失败交付契约（等待非必需、fire-and-forget 受支持、失败保证经 future 交付）落 `nop-retry.md` owner doc（`IRpcCall` javadoc 修订为备选分支，若采批内申报 PA）；triggerCallback 调用点补 try（E-b 面收窄） |
| 10 | 1.6-C6-002（P1；独立；C10-005） | `RetryEngineImpl.java` + `RetryRecordStoreImpl.java`（nop-retry-engine）；约束载体 `nop-retry/model/nop-retry.orm.xml:365-368`（UK_RETRY_ATTEMPT_RECORD_NO，只读消费）；常量 `_NopRetryDaoConstants.java:34/:49/:54` | attempt 先落 RUNNING `doExecute` :486-488（崩溃/挂死/RPC 永不完成→RUNNING 永久）；终态写仅 whenComplete :519；attemptNo=retryCount+1 `RetryRecordStoreImpl`（retryCount 仅 handleExecutionFailure :544-545 递增）；`saveAttempt` :144-150 无 duplicate-key 容错（与 record 侧 `resolveInsertRaceWinner` :252-266 兜底不对称）；STOPPED/CANCELED 常量零使用 | 崩溃/挂死后 RUNNING attempt 永久残留且 retryCount 未递增→租约到期重拾产出同 attemptNo→UK 碰撞同步抛出→记录永久砖化（不可执行不可死信化，每租约周期循环） | 裁定⑦前半（C10-005 随同 diff）：saveAttempt 首插 duplicate-key 容错（resolveInsertRaceWinner 对称形态：查既有 RUNNING 行继承/推进状态）或重试前关闭遗留 RUNNING attempt（置 STOPPED）或 attemptNo 改 max+1——三分支 Phase 1 一次定夺；崩溃恢复语义测试（RUNNING attempt#1 在场时 scanner 重拾可继续推进）；「终态写失败不产生 UK 碰撞」复核勘误已登记（其后果归 C3-006 面） |
| 11 | 1.6-C7-003（P1；独立；C10-007） | `RetryEngineImpl.java` + `RetryScannerImpl.java`（nop-retry-engine） | 裸 parseMap `executeRetryFromScanner` :322（`ApiRequest.build(JsonTool.parseMap(requestPayload))`，守卫仅覆盖空字段 :305-319）；同步异常止于 scanner catch `RetryScannerImpl:214/:218`（catch Exception 后无状态推进）；attempt 先落 RUNNING :486-486（invokeAsync 同步抛时 whenComplete 未挂接）；tryLock 已先置 RETRYING+租约 `RetryRecordStoreImpl:184-206`；空字段兜底先例 :305-319（死信化，注释自认「此前僵尸问题」） | 解析/同步异常路径产生永不终止僵尸记录：损坏 JSON/invoker 同步抛→异常止于 scanner catch→记录状态/retryCount 零推进→每租约周期（144 条全栈 ERROR/天）无限循环重拾 | 裁定⑦后半（C10-007 随同 diff）：`executeRetryFromScanner` 内 try/catch 包裹反序列化与策略装载，异常直接走 `moveToDeadLetter` 既有兜底（:305-319 同族漏网路径补齐）；invokeAsync 同步抛补 attempt 失败落账（终态化）；死信化契约句落 owner doc |
| 12 | 1.6-C7-005（P1；独立；C10-007；R01 对齐 + orm PA 批内申报） | `TccRecordStore.java` + `TccEngine.java`（nop-tcc）；模型 `nop-tcc/model/nop-tcc.orm.xml`（live grep index 零命中） | 唯一清理入口 `TccEngine.cleanCompletedTransactions` :351-355（全仓库零生产调用方，仅接口/实现/测试）；`removeCompletedRecords` :334-355（onlyCompleted=true :338 与过期扫描 :303 耗尽行双重永久排除）；过期扫描查询 :291-305（三条件+beginTime 排序无索引可用，setLimit :304 有页但全表扫描）；`nop-tcc.orm.xml` 150 行 `<indexes>` 零命中 | tcc 账本清理 API 零生产接线 + 耗尽行既不可恢复也不可清理 + 全表零索引——nop_tcc_record/branch 随事务量线性无界增长，恢复扫描（唯一崩溃恢复通道）RTO 单调劣化 | 裁定⑧（C10-007 随同 diff；R01 对齐见纪律节；orm PA 申报见头部 PA 行）：清理任务生产接线（对齐 plan 2502 形态：与恢复扫描同构的绝缘载体周期链 + `nop.tcc.ledger.*` 配置键族 + 启动期 fail-fast，SSOT 修订登记义务）；耗尽行终态化后入清理谓词（先标 KILLED 类终态再清，保留审计窗；C7-006 本体归 R4-10 接缝登记）；`nop-tcc.orm.xml` 新增 expireTime+status+retryTimes 复合索引与 beginTime 排序索引 + 配套迁移脚本（非 `_` 前缀新建） |
| 13 | 1.6-C7-012（P1；独立；C10-006） | `RetryRecordStoreImpl.java`（nop-retry-engine）；旁证 `NopRetryJobRetryBridge.java:40`（nop-job-retry-adapter，提交侧透传证据，只读） | store 单值 namespace 绑定 :62（`namespaceId = DEFAULT_NAMESPACE_ID`，普通 setter 无分片装配）；fetch 硬过滤 :159-166（`eq(namespaceId, namespaceId)`）；提交侧落库优先 task 值 :96、job 桥接无条件透传 `NopRetryJobRetryBridge.java:40`（`withNamespaceId(event.getNamespaceId())`） | 扫描器只服务单一 namespace：非 default 记录首次执行照常、失败进入 PENDING 后扫描器永远看不到——重试承诺静默违约（半个成功：只增不减 PENDING 堆积、零日志零接管） | 裁定⑨（C10-006 随同 diff）：三分支 Phase 1 一次定夺——(a) fetch 过滤放宽为全部 namespace（隔离仅靠分区键/UK 已足够，预设默认分支）；(b) 按 namespace 多实例装配扫描器；(c) 最低限度提交时校验 namespace 与 store 匹配并 fail-fast/告警；default-only 部署不触发的条件面标注保留（判级不因此降级）；job 桥接联动注记（nop-job 侧零改码前提下行为闭合） |

### 平台设施与测试资产盘点（live 实读 @ `2cafbc3996`）

- **先例范式可引用不重开**：`RetryRecordStoreImpl.resolveInsertRaceWinner`（:252-266，UK 冲突兜底形态——裁定⑦三分支之对称先例）；plan 2502 `purgeExpiredLedgerRows` 三通道清理 + 引擎生命周期链接线 + `RetryEngineConfigs` 保留参数键（裁定⑧ nop-tcc 接线直接投影）；plan 2496 绝缘层契约（owner doc `nop-retry.md:83-91` 成文——裁定⑥扫描链面零回退基线）；`tryUpdateManyWithVersionCheck` 乐观锁 CAS（裁定④续租载体）；plan 2388 `endAsync` 恢复路由与 TIMEOUT_SUCCESS 聚合（裁定②终态裁决零回退基线）；merge-adjudication §五「先落 C10 缺保护测试（缺陷态红）→修复（转绿）」次序纪律；plan 2514 三联环境裁定模式。
- **既有测试资产（回归门禁与改写/补测对象）**：`TestRetryEngineImpl`（nop-retry-engine，引擎行为主体扩展面）；`TestRetryScannerImpl` + `TestRetryScannerInsulation`（**R1-01 绝缘零回退钉**）；`TestRetryLedgerRetention_C7_001` + `TestRetryRecordStoreImpl`（plan 2502 保留清理零回退钉 + 裁定⑤⑦补测落点）；nop-tcc 测试树（`nop-tcc-dao/src/test` TestTccRecordStore/TestTccEngine 载体 + `nop-tcc-core/src/test` TestTccHelperAndStatus 等，裁定①②③补测落点；tcc 双链对撞真并发注入形态随裁定①成文）。
- **owner doc 现状**（2026-10-30 亲读）：`docs-for-ai/03-modules/nop-retry.md`（134 行）：§阻塞策略 :28-33（OVERWRITE 声明 =「删除已有未完成记录并以新请求替换」——**未声明原子性/替换对在途链的语义**，裁定⑤契约句落点）；§执行语义 :74-81（尝试追踪/死信化/死信重放——裁定⑥⑦簿记分层与解析死信化契约句落点；**无提交面失败交付契约声明**——裁定⑥ C8-017 半边落点）；§扫描链 :83-91（R1-01 绝缘契约——零回退钉）；§账本保留策略 :92-108（R1-07——裁定⑧对齐锚）；§源码锚点 :123-129。`docs-for-ai/03-modules/nop-tcc.md`（77 行，薄）：§崩溃恢复语义 :50-57（对称续跑 + 耗尽排除声明——裁定①②零回退钉 + 裁定⑧终态化接缝）；§多跳传播 :59-67；**无 CAS 传播/清理接线/索引契约节**——裁定①⑧ owner doc 补节主落点；§源码锚点 :69-73。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `2cafbc3996`，漂移主因 = plan 2496/2502 批落地（绝缘驱动重写 RetryScannerImpl doScan/scanLoop、RetryEngineImpl 生命周期链接线与注释扩块、RetryRecordStoreImpl 清理面新增）致 RetryEngineImpl/RetryScannerImpl 区域统一 +21~+32 粒度漂移；tcc 侧站点机制面零实质漂移）**：RetryEngineImpl——executeImmediateRetry 审计 :340-392→live :361-424、result 创建审计 :336→live :368、返回审计 :391→live :423、scheduleImmediateRetry 审计 :394-420（future :405/lambda :407-417/return :419）→live :426-452（future :437/lambda :439-449/return :451）、doExecute 审计 :454-456/:463-488（:487 saveAttempt 内联）→live :486-488/:495-520（:519 saveAttempt 内联）、executeSingleAttempt 审计 :429-447→live :454-479、handleExecutionFailure 审计 :509-527（nextTriggerTime :522-523）→live :541-559（:554-555）、join 审计 :648→live :680、空字段兜底审计 :275-287→live :305-319、parseMap 审计 :290→live :322、executeTask 审计 :168-171→live :200-203、handleDeadlineExceeded 审计 :493→live :523-529、OVERWRITE 审计 :255-260→live :287-292（handleBlockStrategy :275-298；初稿误记 :257-261——该区间实为 resolveInsertRaceWinner :254-268 方法体，勘误 6 纠正）；RetryScannerImpl——审计 doScan :119-154（catch :146-150）→live :180-218（catch Exception :214/:218——**结构被 plan 2496 绝缘批重写**，Exception-only 无状态推进机制不变）；RetryRecordStoreImpl——newRecord 审计 :116-119→live :116-121、fetch 过滤审计 :159-163→live :159-166、tryLock 审计 :195-204→live :184-206、saveAttempt 审计 :141-149→live :144-150、deleteRecord 审计 :246-249→live :249-251、saveRecord REQUIRES_NEW 审计 :274-278→live :276-278、updateEntityDirectly 审计 :280-283→live :284、namespaceId 字段审计 :60→live :62、moveToDeadLetter :289 起（审计 :306-310 注释面 → live :289+，粒度漂移）；tcc 侧——TccRecordStore updateTccStatusAsync 审计 :160-197（0 行 :189-193）→live :162-197（:188-193）✓、branch :220-283→live :222-283（0 行 :278-283，审计 :274-281 ±粒度）✓、fetchExpiredRecords :291-326 ✓（:303/:304-305 ✓）、removeCompletedRecords 审计 :332-355→live :334-355 ✓、TccEngine checkExpiredTransactions :315-343（get(expireGap) :331 ✓、TimeoutException :332-334 ✓）、cleanCompletedTransactions :351-355 ✓、TccTransaction doCancelAsync :97-117（thenCompose :110-111 ✓）、doConfirmAsync :119-138 ✓、TccRunner runBranchConfirmAsync :72-96 ✓/runBranchCancelAsync :98-122 ✓/aggregateCancelBranchStatus :178-196 ✓/aggregateConfirmBranchStatus :198-217 ✓；`nop-tcc.orm.xml` index 零命中 live 复核成立；`nop-retry.orm.xml` UK :365-368（审计 :364-368 ±1）✓；`_NopRetryDaoConstants` :34/:49/:54 ✓；`NopRetryJobRetryBridge.java:40` ✓；`RetryTaskImpl.callAsync` :100 ✓。
2. **C6-002 子项勘误登记**（审计复核已修正，随并）：原文路径 (3)「:487 终态写失败」不产生 UK 碰撞（该异常路由 handleExecutionFailure 使 retryCount+1，下次 attemptNo=N+1），其后果归 C6-016（C3-006 面）；RUNNING 残留本体与砖化链路径 (1)(2) 成立。
3. **C8-017 判级前提勘误登记**：「当下无仓库内破绽」（两生产桥接方均有外层 catch）为 P2 判级前提——本批裁定⑥落地后随 owner doc 契约声明一并消除缺口本体；不因搭车改判 P1。
4. **C7-001/C8-001 已修勘误**：1.6 单元 P1 族 14 项中 C7-001（plan 2502 分片 A）与 C8-001（plan 2496）已 fixed；本批 Non-Goal 零触碰；C10-007 对 C7-001 的覆盖面已随 2502 闭合，本批仅承接 C7-003/C7-005 两成员的 C10-007 义务。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-retry -am` / `./mvnw test -pl nop-tcc -am` 触达上游模块（nop-kernel/nop-persistence/nop-orm/nop-auth 链等）——上游环境抖动（含 debezium fork timeout、nop-auth MFA、nop-ai-agent 负载时序类 flake、fork 启动崩溃类环境项，沿 plan 2512/2513/2514 已知面）按「stash 基线同红 / solo 绿 / 恢复复跑全绿」三联裁定处理并留痕，不得静默重跑或跳过。

## Goals

- Phase 1 设计裁定文档一次裁决九项裁定 + 测试缝（独立 draft review 通过），无临场发明空间：①C1-001 CAS 失败显式传播+补偿门控短路（全模块内存守卫兄弟站点覆盖 + 幽灵双驱动源封堵）；②C3-002 聚合/终态读 DB 权威裁决；③C3-012 CAS 异常单元格并入①传播面+零驱动不可直写终态；④C3-003 续租协议（续租 CAS+被抢中止+执行令牌语义[A2 族载体]）；⑤C3-005+C6-003 OVERWRITE 同族单设计（原子替换包络+失败死信快照+代际取消在途链）；⑥C3-006+C8-012 完成期记账失败族统一设计（记账/业务分层 + future 拓扑单一化 + Throwable 兜底 + join 面裁定）+ C8-017 契约半边（R21 同 diff）；⑦C6-002+C7-003 终态化（attempt UK 容错三分支 + 僵尸记录死信化既有兜底补齐）；⑧C7-005 清理接线（R01 SSOT 对齐投影 + 耗尽行终态化 + 索引 PA 申报落地方案）；⑨C7-012 namespace 覆盖三分支——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 13 项成员全部修复并各带回归测试（rule 25）；C10 六条收口门（C10-001/003/005/006/007/008）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① C1-001 对撞红（恢复链认领 × 调用方 endAsync 并发——缺陷态 confirm/cancel RPC 对撞照发红→修复后败方短路绿）；红② C3-002 聚合红（双链 cancel 结果分歧——缺陷态全局误报 CANCEL_SUCCESS 红→修复后终态与 DB 分支一致绿）；红③ C3-012 首跳红（全局 CAS 同步异常注入——缺陷态折叠 SUCCESS 直写终态红→修复后异常传播/终态不直写绿）；红④ C3-003 接管红（长链 > 租约——缺陷态扫描器接管双执行红→修复后被抢中止/不接管绿）；红⑤ C3-005 幽灵链红（OVERWRITE 重提 × 旧链在途——缺陷态旧链续跑双执行红→修复后代际短路绿）；红⑥ C6-003 蒸发红（超限 payload OVERWRITE——缺陷态任务蒸发无死信红→修复后原子替换或死信快照绿）；红⑦ C3-006 改判红（二次 saveAttempt 瞬时 DB 故障注入——缺陷态成功改判失败红→修复后业务结论保持/簿记失败可见绿）；红⑧ C8-012 冻结红（T1 延迟跳同步 DB 异常注入——缺陷态 join 永久冻结红→修复后 future 异常完成/扫描循环存活绿；C8-017 三通道断言随同 diff）；红⑨ C6-002 砖化红（RUNNING attempt#1 注入 + scanner 重拾——缺陷态 UK 抛出永久滞留红→修复后推进绿）；红⑩ C7-003 僵尸红（损坏 payload 注入——缺陷态每周期重拾零推进红→修复后死信化绿）；红⑪ C7-005 接线红（清理任务缺位/耗尽行注入——缺陷态零清理零终态红→修复后周期清理+终态化绿）；红⑫ C7-012 覆盖红（非 default namespace 失败注入——缺陷态永不接管红→修复后接管绿）。
- 联调纪律显式守约（本批四条）：**C7-005×R01 对齐**（SSOT 引用不重建 + 键族同构 + SSOT 修订登记义务）；**扫描面×R1-01 零回退**（绝缘契约不回归）；**C8-012×C8-017 R21 纪律**（同 diff 搭车）；**裁定④×A2 族令牌共用**（R4-10 接缝成文）。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-retry.md`（OVERWRITE 原子化/代际语义、续租契约、簿记-业务分层契约、提交面失败交付契约[C8-017]、僵尸死信化契约、namespace 扫描覆盖声明、attempt 终态化声明、源码锚点补行）+ `docs-for-ai/03-modules/nop-tcc.md`（CAS 传播契约、聚合权威裁决契约、清理接线/retention 声明[对齐 R01 SSOT]、索引声明、耗尽终态化接缝）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-10（8.50）回写硬依赖就绪注记（含裁定①⑤⑦与 R4-10 成员 C1-002/C3-008/C7-006 的接缝边界、C10-002/004/009 处置边界确认、A2 令牌语义引用指向）；向 R4-32（C7-011 拆出）按需注记时钟族边界（本批零触碰）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-10 成员**（G17 十四项：1.6-C1-002/C1-004/C1-007/C3-008/C3-009/C3-010/C6-015/C7-007/C7-008/C7-010/C7-014/C7-015/C8-003/C8-013）及其 C10-002/C10-004/C10-009 载体面——硬依赖本批，本批零承接零顺手修；特别地 **C1-002**（OVERWRITE 并发覆盖裸 UK 暴露）为裁定⑤同位点 P2 邻居——本批只落原子替换包络并登记暴露面收窄接缝，C1-002 本体归 R4-10；**C3-008**（超时弃链幽灵链本体）与裁定①幽灵双驱动源分立（本批只封 checkExpired 超时不取消的本条双驱动源，弃链追踪面归 R4-10）；**C7-006 本体**（耗尽观测/干预工具面）只随裁定⑧落终态标记半边。
- **不做 dormant/条件面成员**（P2-G19 五项：C3-004/C6-005/C7-006/C8-004/C8-007；CL-004 驱动装配缺位本体——裁定⑧清理接线不扩为恢复扫描默认驱动，该裁定权在 R4-10/协调方）与 **G20**（C8-008 过载指标伞形缺口，deferred 归 R-G）。
- **不做 P3 成员**（C7-009/C8-014/C8-016 死配置族、C3-013 pause/resume、C3-011/C3-014/C3-015/C3-017、C1-005/C1-006、C6-012/C6-013、C8-010/C8-011/C8-015）与 **R4-32**（C7-011 墙钟混用）。
- **不做已修面**：C7-001（plan 2502 保留清理，`purgeExpiredLedgerRows`/配置键零触碰零回退）、C8-001（plan 2496 绝缘层，`RetryScannerImpl` 绝缘驱动/存活指标零回退）、plan 2386/2388 已修面（执行表权限/恢复路由零回退）、CL-007（retryFromDeadLetter 装配缺位，单列线索不并入）。
- 不触碰 `nop-retry/model/nop-retry.orm.xml`（C6-002 仅消费既有 UK）、R2-13/R4-08/R4-09/R4-10/R4-32 批外成员本体、`_` 前缀生成物（裁定⑧迁移脚本为非 `_` 前缀新建）；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-retry/nop-retry-engine/src/main`：`impl/RetryEngineImpl`（C3-003/C3-005/C3-006/C6-002/C6-003/C7-003/C8-012）+ `impl/RetryTaskImpl`（C8-017）+ `store/RetryRecordStoreImpl`（C3-003 续租/C3-005 代际/C5-003→C6-002 attempt 容错/C6-003 原子替换）+ `scanner/RetryScannerImpl`（C7-003 catch 面/C7-012 fetch 载体按裁定⑨分支）+ `store/RetryEngineConfigs`（如裁定④⑧需新增参数键——回 SSOT/裁定成文）+ 测试（C10-001/005/006/008 补测 + 既有钉零回退）。
- `nop-tcc/nop-tcc-core/src/main`：`impl/TccTransaction`（C1-001/C3-002/C3-012 门控/聚合挂点）+ `impl/TccRunner`（聚合权威化）+ `impl/TccEngine`（C7-005 清理接线/超时分支在飞去重）+ 测试（C10-003）。
- `nop-tcc/nop-tcc-dao/src/main`：`store/TccRecordStore`（CAS 传播/聚合重载/耗尽终态化/清理谓词）+ `nop-tcc/model/nop-tcc.orm.xml`（**裁定⑧索引，PA 批内申报载体**）+ 配套迁移脚本（非 `_` 新建）+ beans 装配（清理周期链）+ 测试（C10-007 tcc 半）。
- 设计文档：`ai-dev/design/concurrency/retry-tcc-cas-compensation-and-ledger-hygiene.md`（新建；裁定①–⑨+测试缝+零回退钉，最终状态写法）。Owner doc：`docs-for-ai/03-modules/nop-retry.md`、`docs-for-ai/03-modules/nop-tcc.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-10/R4-32/dormant/P3 批次工单；nop-kernel/nop-api-core 平台设施本体（C8-017 javadoc 修订为备选分支，若裁定采须批内申报）；nop-job 侧改码（C7-012 job 桥接仅旁证引用，行为闭合落 retry 侧）；`nop-retry/model/nop-retry.orm.xml`；`_` 前缀生成物手改。

## Execution Plan

### Phase 1 - retry/tcc 并发与账本卫生设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/retry-tcc-cas-compensation-and-ledger-hygiene.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下九项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C1-001 CAS 传播与补偿门控**：CAS 失败（0 行/异常）显式失败传播形态（返回失败标记 vs 异常完成——一次定夺）；doCancelAsync/doConfirmAsync/runBranchConfirmAsync/runBranchCancelAsync 及 TccBranchTransaction begin* 内存守卫兄弟站点逐点处置表；checkExpiredTransactions 超时分支在飞去重/cancel 形态（C3-008 接缝：只封本条双驱动源）；DB 级补偿租约备选分支呈现；恢复对称续跑路由零回退声明
  - [x] **裁定② C3-002 聚合权威裁决**：聚合读 DB 权威状态 vs CAS 失败重载后再聚合（一次定夺）；全局终态 CAS 前按分支 DB 状态终裁；误报 CANCEL_SUCCESS 不可达断言口径
  - [x] **裁定③ C3-012 异常单元格**：CAS 异常并入①传播面；零驱动不可直写终态的前置校验形态；与②共用权威裁决机制声明
  - [x] **裁定④ C3-003 续租协议**：每轮 attempt 前 CAS 续租 nextTriggerTime（载体=`tryUpdateManyWithVersionCheck` 既有 CAS）；租约被抢中止链的败方显式语义；扫描器拾取/接管双条件；执行令牌语义（A2 族载体，供裁定⑥⑦与 R4-10 消费）；C1-009 首执行窗随协议闭合声明
  - [x] **裁定⑤ C3-005+C6-003 OVERWRITE 同族**：原子替换包络形态（`replaceRecord` @Transactional vs 先插后删 vs 失败补偿恢复——一次定夺）；替换失败死信快照语义；代际号/epoch 取消在途旧链（回调/续跑先校验代际）vs canceled 标记短路（一次定夺）；C1-002 暴露面收窄接缝登记（本体归 R4-10）
  - [x] **裁定⑥ C3-006+C8-012+C8-017 完成期记账失败族**：业务结论层/簿记层分层形态（whenCompleteAsync+独立 try/catch）；future 拓扑单一化（返回=挂接同一 promise；E-a/E-b/E-c 逃逸点逐一闭合清单）；`allOf().join()` 面（保留 join+兜底 vs 异步汇聚，一次定夺；扫描链业务级永久冻结不可再现声明 + R1-01 绝缘零回退）；`RetryTaskImpl.callAsync` 同步抛转 failedStage + 提交面失败交付契约声明落 owner doc（IRpcCall javadoc 备选分支与 nop-api-core PA 申报触发条件成文）；triggerCallback try 收窄
  - 〔draft review R1 Minor-6〕两层簿记位点逐一隔离：attempt 层 doExecute whenComplete 内联 saveAttempt（:519——该点抛出即以异常完成 stage、落入 cb1 的 ex 分支改判业务，正是逃逸入口）与 record 层 cb1 内 updateRecord/handleExecutionSuccess（:377-421），两处均移入独立 try/catch 隔离形态，禁只修 cb1 层残留同型缺陷
  - [x] **裁定⑦ C6-002+C7-003 终态化**：attempt UK 容错三分支（duplicate-key 容错[resolveInsertRaceWinner 对称形态] / 重试前关闭遗留 RUNNING / attemptNo max+1——一次定夺）；崩溃恢复语义测试形态；僵尸记录死信化（parseMap/策略装载异常走 moveToDeadLetter 既有兜底 + invokeAsync 同步抛 attempt 失败落账）；死信化契约句落 owner doc 口径
  - [x] **裁定⑧ C7-005 清理接线+索引**（R01 对齐 + PA 申报落地）：清理周期链形态（对齐 plan 2502 绝缘载体同构；`nop.tcc.ledger.*` 键族与缺省值；与 R01 SSOT 修订登记义务）；耗尽行终态化字段与语义（**C7-006 接缝：字段由本批定、本体归 R4-10**）；`nop-tcc.orm.xml` 索引方案（两索引列集）+ 迁移脚本载体；orm PA 批内申报闭环（本 plan PA 行+设计文档+独立评审）
  - [x] **裁定⑨ C7-012 namespace 覆盖**：三分支（fetch 放宽全 namespace[预设] / 多实例装配 / 提交时校验 fail-fast）一次定夺 + job 桥接零改码行为闭合声明 + 条件面标注保留
  - [x] **裁定⑩ 测试缝与零回退钉清单**：红①–红⑫ 测试形态逐条（含缺陷态红判据）；零回退钉 = `TestRetryScannerInsulation`（绝缘契约）、`TestRetryLedgerRetention_C7_001`（2502 保留清理）、owner doc §扫描链/§账本保留策略/§崩溃恢复语义 成文契约、`endAsync` 恢复路由注释钉、`fetchExpiredRecords` 版本 CAS 认领、`resolveInsertRaceWinner` 兜底、`newRecord` 一次性推延注释钉（被裁定④协议替代时须显式改写登记——唯一允许的钉改写，改写前后语义对照留痕）；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑩全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定⑥含 R21 搭车条款与 nop-api-core PA 备选分支触发条件；裁定⑧含 R01 SSOT 对齐投影与 orm PA 申报落地；裁定④含 A2 令牌接缝；裁定⑤含 C1-002 接缝；裁定⑦含崩溃恢复测试形态
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 13 成员实现 + 回归测试（按 retry/tcc 两组）

Status: done
Targets: `nop-retry/nop-retry-engine/src/`、`nop-tcc/nop-tcc-core/src/`、`nop-tcc/nop-tcc-dao/src/`——各自 main + test 树

- Item Types: `Fix`（12 项 P1 均为已确认 live defect，不得写成 Feature；C8-017 为 P2 搭车 `Fix`，R21 同 diff）、`Proof`（单元测试 + C10 收口门）

- [x] 组 retry（nop-retry-engine）：C3-003（裁定④续租）+ C10-006 retry 半；C3-005+C6-003（裁定⑤ OVERWRITE 同族）+ C10-005 retry 半；C3-006+C8-012+C8-017（裁定⑥记账族+拓扑+契约半边，同 diff）+ C10-001 + C10-008；C6-002+C7-003（裁定⑦终态化）+ C10-007 retry 半；C7-012（裁定⑨）+ C10-006 namespace 半
- [x] 组 tcc（nop-tcc-core/dao）：C1-001（裁定①）+ C3-002（裁定②）+ C3-012（裁定③）——三裁定同变更集（共享 CAS 传播与权威裁决机制）+ C10-003；C7-005（裁定⑧清理接线+终态化+索引[PA 申报已闭环]）+ C10-007 tcc 半
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：
  - [x] 红①–红③（tcc 组/C10-003）：双链对撞（恢复链认领×调用方 endAsync）RPC 门控 + 聚合权威裁决 + 首跳异常传播——缺陷态各自红 → 修复后绿
  - [x] 红④+红⑫（retry/C10-006）：长链超租约接管双执行 + 非 default namespace 接管——缺陷态红 → 修复后绿
  - [x] 红⑤+红⑥+红⑨（retry/C10-005）：幽灵链双执行 + OVERWRITE 蒸发死信 + RUNNING attempt 砖化——缺陷态红 → 修复后绿
  - [x] 红⑦+红⑧（retry/C10-001+C10-008）：簿记改判 + join 冻结（T1/T2 两形态）+ C8-017 三通道断言——缺陷态红 → 修复后绿（C10-001 真异常注入，非串行假保护）
  - [x] 红⑩+红⑪（retry/tcc/C10-007）：僵尸记录死信化 + 清理接线/耗尽终态化/索引生效——缺陷态红 → 修复后绿
- [x] **端到端验证**（rule 22）：≥1 条 retry 全链（提交→首执行→租约内即时重试→失败退避→扫描重拾→死信化→[接线面]清理，覆盖裁定④⑤⑥⑦⑨；DB 持久化形态）；≥1 条 tcc 全链（Try→分支 RPC→恢复扫描认领→CAS 门控补偿→聚合终态→[接线面]周期清理，覆盖裁定①②③⑧）
- [x] **接线验证**（rule 23）：裁定④续租 CAS 真实被即时重试链调用断言（非仅方法存在）；裁定⑤原子替换/代际校验真实被 handleBlockStrategy/回调路径消费断言；裁定⑥拓扑单一化断言（返回 future 完成态 == 挂接链完成态，含异常路径）；裁定⑧清理任务真实被 beans 装配/周期链调用 + 索引真实生效断言（EXPLAIN 或查询计划面，非仅 orm xml 存在）；裁定⑨fetch 过滤放宽后非 default 记录真实被拾取断言
- [x] **无静默跳过**（rule 24）：裁定①CAS 失败显式传播（非 warn 后吞）；裁定④被抢中止显式（非静默续跑）；裁定⑥E-a/E-b/E-c 逃逸点显式闭合（无永不完成形态残留）；裁定⑦⑧终态化路径显式可见（非空 catch）；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档先行单独提交；按组提交——retry / tcc，C10 测试与对应本体同一 commit（裁定⑥三成员+C8-017 同一变更集；裁定①②③同一变更集）；组间无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 13 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 六条收口门同 diff 落地
- [x] 必红证据（E2）：红①–红⑫均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = `newRecord` 一次性推延注释钉按裁定④协议替代，改写登记在案）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-retry -am` 与 `./mvnw test -pl nop-tcc -am` 均 BUILD SUCCESS（上游环境抖动按 Baseline 勘误 5 三联裁定处理并留痕）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [x] `ai-dev/logs/` 对应日期条目已更新


- 偏差登记（实施期）：① tcc：store CAS 事务形态以 ITransactionTemplate.runInTransactionAsync(REQUIRES_NEW) 实现 afterCommit 配对条款（方法内挂 listener 到自有事务会死锁），语义三条款等价达成，无事务模板时（单测直连）退化为立即结算；② tcc：ITccRecordStore 接口扩展（killExhaustedRecords/removeCompletedRecords）为跨模块公共 API 面加法（旧实现零破坏），批内登记；③ tcc：TestTccEngine 4 处既有测试改写（0 行吞为正常返回/RPC 异常仍折叠终态两缺陷契约 → 裁定①③契约断言），均非零回退钉清单成员，新旧语义对照入 javadoc；④ retry：renewLease 载体用单实体 tryUpdateWithVersionCheck（设计文本 tryUpdateManyWithVersionCheck 的同机制单实体形态，等价乐观锁 CAS）；⑤ retry：代际校验旧链 DB 回写半边=异常可见（ERR_ORM_UPDATE_ENTITY_NOT_FOUND 经裁定⑥簿记隔离 ERROR+计数）而非显式 0 行短路，语义达成（不静默落空）；⑥ retry：裁定⑦ saveAttempt 兜底重插前需 orm_state(TRANSIENT) 复位（ORM 状态机约束，设计未言）；⑦ retry：裁定⑦实现晚于主体（首两轮遗漏，后补红⑨⑩留痕齐全）。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-retry/nop-tcc 聚合全量测试；`docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2514 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-retry -am` 与 `./mvnw test -pl nop-tcc -am` 均 BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `nop-retry/model/nop-retry.orm.xml`、`_` 前缀生成物手改、R4-10/R4-32/dormant/P3 批外成员本体（C1-002/C3-008/C7-006 本体/C3-004/C7-001 保留清理/绝缘层等）、plan 2496/2502/2386/2388 已修面零 diff 核对登记；`nop-tcc.orm.xml` 变更限于裁定⑧申报索引
- [ ] Owner doc 更新：`nop-retry.md`——§阻塞策略补 OVERWRITE 原子化/代际语义、§执行语义补续租契约/簿记分层/僵尸死信化/attempt 终态化、新增提交面失败交付契约（C8-017）、扫描覆盖 namespace 声明（裁定⑨）、源码锚点补行；`nop-tcc.md`——CAS 传播/聚合权威裁决契约补节（裁定①②③）、清理接线与 retention 声明（裁定⑧，「以设计文档与 R01 SSOT 为准」归属声明）、耗尽终态化接缝、源码锚点补行；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-retry --severity high` 与 `--module nop-tcc --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 两条批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-retry.md/nop-tcc.md 落点 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（nop-retry.orm.xml/生成物/R4-10/R4-32/dormant/P3 批外成员本体/2496·2502·2386·2388 已修面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.21 行、cra-index 台账、R4-10 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（CAS 传播/门控短路/续租/原子替换/代际取消/拓扑单一化/终态化/清理接线/namespace 覆盖运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-12 十三行状态收敛（12 项 P1 open→fixed，注记 plan 2515；吸收成员随各主行括注；C8-017 随 G18 行注记收敛）；C10 六条收敛注记；roadmap 8.21 行 Status → done；R4-10（8.50）硬依赖就绪注记（沿 2514 R2-13/R4-08 先例——含裁定①⑤⑦接缝边界、A2 令牌语义引用指向、C10-002/004/009 处置边界确认）；R4-32 时钟族边界注记（按需）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 13 行收敛 + C10 注记、roadmap 8.21 = done、R4-10 就绪注记完成（R4-32 按需）
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 13 项成员全部修复：C1-001（CAS 失败阻断补偿）、C3-002（CAS 失败回写/聚合权威裁决）、C3-012（首跳异常传播/终态不直写）、C3-003（续租族闭合）、C3-005（幽灵链代际取消）、C6-003（OVERWRITE 原子化+死信，R10）、C3-006（簿记异常不覆盖业务结果）、C8-012（返回 future=回调挂接 future）、C8-017（提交面失败交付契约，R21 同 diff 搭车）、C6-002（RUNNING attempt 砖化消除）、C7-003（僵尸记录终态化）、C7-005（清理 API 生产接线+索引）、C7-012（namespace 扫描修复）
- [ ] batch-proposal R2-12 行 exit criteria 增补七段逐条达成并留证：①**CAS 失败阻断补偿/回写内存/续租族闭合**；②**簿记异常不覆盖业务结果**；③**OVERWRITE 原子化+死信（R10）**；④**僵尸记录终态化**；⑤**清理 API 生产接线+索引**；⑥**namespace 扫描修复**；⑦**C8-012 返回 future=回调挂接 future（C10-001 覆盖，C8-017 同 diff）**
- [ ] 对齐纪律守约：C7-005×R01 对齐（SSOT 引用不重建 + 键族同构 + 修订登记义务落地）；扫描面×R1-01 绝缘零回退；C8-012×C8-017 R21 同 diff；裁定④×A2 令牌接缝成文；nop-api-core PA 零触碰（或备选分支采纳时批内申报闭环）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑫均有先红后绿或变异/回退论证（E2）；C10 六条同 diff 落地
- [ ] 设计文档裁定①–⑩成文且经独立 draft review；orm PA 批内申报闭环（裁定⑧索引）；既有钉（零回退钉清单全列）零回退实证（唯一钉改写登记在案）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 两条批行验证命令 `./mvnw test -pl nop-retry -am`、`./mvnw test -pl nop-tcc -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-retry.md + nop-tcc.md（+source-anchors.md 按实际）（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）CAS 传播/门控短路/续租/原子替换/代际取消/拓扑单一化/终态化/清理接线/namespace 覆盖运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-retry/nop-tcc 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 13 行收敛 + roadmap 8.21 → done + R4-10 就绪注记（E6）

## Deferred But Adjudicated

### R4-10 成员（G17 十四项：1.6-C1-002/C1-004/C1-007/C3-008/C3-009/C3-010/C6-015/C7-007/C7-008/C7-010/C7-014/C7-015/C8-003/C8-013）及其 C10-002/C10-004/C10-009 载体面

- Classification: `out-of-scope improvement`（batch-proposal R4-10 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-10 批 plan（roadmap 8.50，硬依赖 8.21+8.8——本批 done 后就绪）
- Why Not Blocking Closure: 判级对齐零重复——cra-index 台账 G17 行批次列=R4-10；本批已收敛 1.6 单元全部 12 项 open P1 + P2 搭车 C8-017，R4-10 实施以本批后的 live 形态为基线，且可引用本批裁定①⑤⑦接缝与裁定④ A2 令牌语义为既有裁定不重开体。

### dormant/条件面成员（P2-G19 五项：C3-004/C6-005/C7-006/C8-004/C8-007；CL-004 驱动装配缺位本体）与 G20（C8-008）

- Classification: `out-of-scope improvement`（cra-index P2-G19/G20 行裁定 deferred，非本批降级）；Successor Required: `yes`；Successor Path: 触发条款（补生产驱动/非默认形态确认）→ 同批复核判级；C8-008 → 治理批 R-G
- Why Not Blocking Closure: 激活前提未满足（dormant 判级为批前裁定）；本批裁定⑧只承接 C7-005 清理接线（R1-07 收口注记显式留给本批），不扩为恢复扫描默认驱动裁定。

### P3 成员与 R4-32（C7-011 墙钟混用）

- Classification: `out-of-scope improvement`；Successor Required: `yes`；Successor Path: R4-32（C7-011）/R4-10（P3 死配置族等）
- Why Not Blocking Closure: 批次列均为批外批号；C7-011 与裁定④时钟面相邻但根因不同（首提租约本地钟 vs DB 钟混用），本批零触碰。

## Non-Blocking Follow-ups

- **R4-10/R4-32 对接注记**：本批落地后向 roadmap 8.50 行作就绪注记（硬依赖解除落点；含接缝边界与 A2 令牌引用指向）；R4-32 按需时钟族注记；两批实施引用本设计文档为既有裁定。
- **C8-017 的 IRpcCall javadoc 平台化声明**：若 owner doc 契约句运行稳定后需上收 `IRpcCall` javadoc（nop-api-core），属后续平台批独立申报事项，不阻塞本批 closure。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- （收口时填写）

## Risks And Rollback

- **tcc CAS 传播形态选择风险**：失败传播若采用异常完成形态，需核对恢复扫描链对异常完成的容忍度（不得把「败方短路」放大为扫描循环失败——与 R1-01 同构的 tcc 侧驱动面在 CL-004 边界内为宿主装配，裁定①须显式声明败方短路对扫描循环零影响）；回滚 = 单 revert 各裁定变更集（组间无耦合）。
- **裁定⑧索引上线风险**：大表在线加索引的部署面（迁移脚本形态随裁定成文；保留策略缺省值对存量数据的首轮清理冲击须在裁定⑧给出限流/分批口径，对齐 plan 2502 delete-batch-size 先例）。
- **裁定④续租频率风险**：每轮 attempt 前 CAS 续租的写放大（即时重试高频场景）——缺省租约/续租间隔随裁定成文并给包络声明。
