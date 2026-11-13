# 触发条款登记册（R-G 治理批产出物①）

> Plan：`ai-dev/plans/2567-cra-phase8.74-r-g-governance.md`（roadmap 8.74 / batch R-G）
> 批次定义：`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §五
> 权威台账：`ai-dev/audits/cra-index.md`（P2 裁决台账行 = 各条款组唯一状态位；本登记册为汇总+指针，不复制条款全文，不重写裁定）
> 落档日期：2026-11-12

## 1. 登记口径

- **每条登记项**：条款组 ID / 来源批次与 plan / 条款文本指针（cra-index 行或 plan/design 路径）/ 当前状态。
- **状态取值**：`active-监控中`（条款未触发，持续监控接线信号）/ `已激活已承接`（触发条件成立，修复已随激活批承接）/ `已关闭`（前提证伪或裁定关闭）。
- **激活监控义务**：任何涉及对应模块「接线/装配/部署形态」的后续变更（新 beans.xml 装配、新生产调用方、部署包络变化），须先对照本登记册复核激活条款（评审通道见产出物② checklist）。
- 本登记册落档时点（2026-11-12）**无一例激活**：全部触发条款组维持 `active-监控中`。

## 2. 触发条款组登记（20 组）

| 组 | 成员 | 来源批次→plan | 条款文本指针 | 当前状态 |
|---|---|---|---|---|
| G06 | 1.2-MA-12（reader 并发入口条化） | R-G（deferred）→ 本批登记 | cra-index P2-G06 行；1.2 merge-adjudication | active-监控中（触发=reader 并发接线/DISTRIBUTED 部署→回升 P1） |
| G19 | 1.6-C3-004/C6-005/C7-006/C8-004/C8-007（dormant/条化束） | R-G → 本批登记 | cra-index P2-G19 行；条件面捆绑 CL-004 | active-监控中（触发=补生产驱动/非默认形态确认→同批复核判级） |
| G28 | 2.1-C5-001/C5-008/C7-001/C7-003（条化束） | R-G → 本批登记 | cra-index P2-G28 行 | active-监控中（触发=运行期编译后端落地/fixedRate 条件化/ghost-chain 条化→回升/复核） |
| G30 | 2.1-C1-006/C1-007/C2-010/C6-010/C6-011/C7-005（零调用/休眠潜伏束） | R-G → 本批登记（拆解承接：C1-006→产出物④死代码登记；C7-005→产出物④注记；C1-007/C2-010→CL/域留档；C6-010/C6-011→触发条款） | cra-index P2-G30 行 | active-监控中（C6-010/C6-011 dormant 休眠面维持条款监控） |
| G33 | 2.2-C6-011（IPluginManager 聚合关闭条化） | R-G → 本批登记 | cra-index P2-G33 行 | active-监控中（触发=装配点出现→复核） |
| G42 | 2.3-C1-001/C1-003/C1-009/C2-005（条化/dormant 束） | R-G → 本批登记 | cra-index P2-G42 行 | active-监控中（触发=第三种装配形态出现/UserContext 混合同步装配/ws 调用方假设降档→回升 P1） |
| G46 | 2.4-C1-021（租户派生标志条化） | R-G → 本批登记 | cra-index P2-G46 行 | active-监控中（触发=运行期租户模型切换启用→升 P1） |
| G49 | 2.5-C7-005/C7-015/C8-005/C8-007（零调用方降档束） | R-G → 本批登记 | cra-index P2-G49 行 | active-监控中（触发=调用方接线→复核） |
| G53 | 3.1-C1-005/C4-006（条化对） | R-G → 本批登记 | cra-index P2-G53 行 | active-监控中（触发=多会话共享单例装配/有界池注入→回升 P1） |
| G56 | 3.2 shell 条化束（S6 七张：MA-05/MA-08/C1-006/C1-007/C4-006/C8-004/C8-005） | R-G → 本批登记；B1-03 族主条款锚点=cra-index P1 台账 3.2-C4-003 行（「本行注记即 B1-03 条款锚点」） | cra-index P2-G56 行 + P1 台账 3.2-C4-003 行；反向钉-1（ShellIOTest:294-300）随条款激活同 diff 改写；C10-002/007/012/015/016/019/023 随条款 | active-监控中（触发=投产接线即回 P1） |
| G57 | 3.3 渠道栈条化潮（**B1-11 六张回升券**：MA-02/MA-03/MA-04/C4-004/C4-005/C8-002） | R-G → 本批登记；豁免清单 batch-proposal §六 | cra-index P2-G57 行；batch-proposal §三#2/§六；护栏=产出物② `channel-wiring-review-checklist.md` | active-监控中（触发=startAll/stopAll/dispatchInbound 裁定后新增的非测试调用点或 beans.xml 加 init/destroy→P1×6 直接入 P1 批次） |
| G59 | 3.3-C1-010（装配守卫条件面） | R-G → 本批登记 | cra-index P2-G59 行 | active-监控中（触发=多 adapter 装配漂移实证→复核） |
| G60 | 3.4 条化束（**B1-12 回升券**：IND-05/06/07/08/09） | R-G → 本批登记；IND-06 搭载 O-2 SPI 移交条款（见 §4） | cra-index P2-G60 行；3.4 merge-adjudication | active-监控中（触发=接线/接管线出现 main 消费者→升 P1；IND-07/08 接线即升、同批修不合并） |
| G61 | 3.4-IND-04（ExtensionLanguageMapper dormant） | R-G → 本批登记 | cra-index P2-G61 行 | active-监控中（触发=调用者接线→复核） |
| G64 | 4.2-MA-02/MA-04（条化束） | R-G → 本批登记 | cra-index P2-G64 行 | active-监控中（触发=cleanupTasks/BroadcastRpc 接线→回升 P1） |
| G69 | 4.3-C3-022/C6-011（条化束；C3-022=§三.5#9 部署包络确认项） | R-G → 本批登记 | cra-index P2-G69 行 | active-监控中（触发=vertx-mqtt 包络声明变化/消费方接线→复核） |
| G72 | 4.5-I-C6-001（watch-zip 双重 init 条化，§三.5#10） | R-G → 本批登记 | cra-index P2-G72 行 | active-监控中（触发=CDI-only 形态激活→回升/降格） |
| G77 | 5.3-CL-007（Semaphore 占池 watch-only） | R-G → 本批登记 | cra-index P2-G77 行 | active-监控中（触发=负载画像量化后立项；跨单元关联 1.2-C4-004） |
| G87 | 6.2-W-06（TscTypeResolver dormant，CL-502） | R-G → 本批登记 | cra-index P2-G87 行 | active-监控中（触发=接线→复核） |
| G89 | 6.3-W-05/W-10（dormant 激活计级束；TP-005/010 随条款） | R-G → 本批登记 | cra-index P2-G89 行 | active-监控中（触发=半克隆/全量 clone 功能激活→复核计级） |

## 3. 三张条件化潮（与 §2 组行同源，单列潮口径）

| 潮 | 券数 | 对应组 | 条款指针 | 当前状态 |
|---|---|---|---|---|
| B1-11（3.3 渠道栈休眠潮） | 六张 P1 回升券 | G57 | family-clustering §三.2 终裁；cra-index 3.3 计数口径块 + P2-G57 行；产出物②护栏 | active-监控中 |
| B1-12（3.4 条化潮） | 回升券（batch-proposal §五 记「四张」为券口径；组行成员 5 条 IND-05~09） | G60 | family-clustering §三 B1-12 终裁；cra-index P2-G60 行 | active-监控中 |
| S6（3.2 shell 条化潮） | 七张 | G56 | 3.2 计数口径块（shell 零生产接线族条件化六降档+MA-06 分叉）；cra-index P2-G56 行 + P1 台账 B1-03 锚点行 | active-监控中 |

## 4. SPI 移交轨

| 项 | 指针 | 当前状态 |
|---|---|---|
| 3.4-O-2（IEmbeddingModel SPI 无 ICancelToken——设计层缺失） | batch-proposal §四.3 移交轨行；family-clustering §三.5#17；cra-index P2-G60 行 IND-06 条目 | 登记在册——移交归属单元（WI 3.1）plan-first 立项轨，不在本 campaign 立项 |

## 5. 附录：缺口声明维持登记（产出物⑤ 联动）

以下模块经产出物⑤逐一裁定「确无实质并发契约可落 owner doc」，**显式维持缺口声明**（依据=本批 plan Baseline + roadmap 各行 E5 缺口声明）：

| 模块 | 裁定 | 依据 |
|---|---|---|
| `nop-demo/`、`demo/` | 维持缺口声明（示例/模板，无并发契约落账） | 6.5 计数口径 demo 降口径 P3（W-09/W-11 治理声明） |
| `nop-frontend-support/` | 维持缺口声明（无 campaign 并发契约落账） | 6.5 四模块 drift 登记；R3-10 修复面限于 nop-dev-tools/xlang-debugger/js |
| `nop-entropy-e2e/` | 维持缺口声明（测试基建，非产品并发契约承载面） | 6.5 四模块 drift 登记 |
| `nop-search/`（4.5/6.1 涉及）、`nop-host`、`nop-cluster` 等既有「缺口登记，owner doc 建设归 R-G」行 | 维持既有登记行，不在本批扩面（不在 8.74 批成员清单内） | module-groups.md 既有行注记；本批范围=batch-proposal §五 明列模块 |

## 6. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-12 | 初版落档（plan 2567 Phase 1；20 组 + 三潮 + O-2 移交轨全部登记，状态=active-监控中） |
