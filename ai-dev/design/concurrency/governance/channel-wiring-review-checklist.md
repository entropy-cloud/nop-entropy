# 渠道栈接线评审 checklist（R-G 治理批产出物②）

> 定位：B1-11 护栏条款成文化（cra-index P2-G57 行「护栏=渠道栈接线变更评审 checklist（Phase 8 验收前置）」的兑现载体）。
> 适用范围：任何触及「渠道栈/生命周期组件接线形态」的变更——新增 beans.xml 装配、init/destroy 属性、生产代码中生命周期方法调用、部署包络变化。
> 触发条款联动：本 checklist 任一项命中即视为触发条款激活信号，须回查产出物① `trigger-clauses-register.md` 并按条款回升/复核判级。
> 落档日期：2026-11-12（plan 2567）

## A. 装配面扫描（beans.xml init/destroy）

- [ ] A1 扫描本次变更涉及的 `_vfs` beans.xml（含 Delta）：是否存在对渠道栈/生命周期组件的**新增 `<bean>` 装配**，或对既有 bean **新增 `init-method`/`destroy-method` 属性**。
- [ ] A2 命中即判定：B1-11 激活条款成立（「beans.xml 加 init/destroy」），P2 条件化工单回升 P1，直接入 P1 批次（不得再以「休眠」维持 deferred）。
- [ ] A3 反向核查：删除既有 init/destroy 接线也须登记（休眠化回流不影响已修复面，但需在 cra-index 对应行注记「装配回撤」以维持台账诚实）。

## B. 生命周期方法非测试调用扫描（startAll/stopAll/dispatchInbound）

- [ ] B1 对本次变更 diff 执行 grep：`startAll` / `stopAll` / `dispatchInbound`（渠道栈三方法）在 **src/main（非测试）** 是否出现新调用点。
- [ ] B2 命中任一即判定：B1-11 激活条款成立（「三方法非测试调用」），对应条件化工单（G57 六张：MA-02/MA-03/MA-04/C4-004/C4-005/C8-002）按券回升 P1。
- [ ] B3 扩面（S6/B1-12 同型）：shell 工具链投产接线（G56）、3.4 接管线/接 main 消费者（G60）同用本扫描形态——对 `ShellIO`/`ToolSession` 装配与 3.4 IND-05~09 消费面执行同款非测试调用 grep。
- [ ] B4 扫描留痕：评审记录须附 grep 命令与零命中/命中输出（不留痕视为未评审）。

## C. MA-05 反向钉条款（修复捆绑测试改写）

- [ ] C1 若触发条款激活并立项修复：3.2-MA-05（shell EOF 队列）修复**必须同 diff 改写反向钉-1**（`ShellIOTest:294-300` 队满 close 钉），不得绕过（cra-index P2-G56 行条款原文）。
- [ ] C2 同型义务核查：4.1 反向钉#2/#4（MA-08/C1-008，P3 本体未入批）按 4.1 裁定「与同文件 P2 工单同 PR 顺带」触及时同 diff 改写；评审时对照 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §四.2 表逐行核对义务面。
- [ ] C3 修复 PR 不得弱化既有回归钉（2.4-C1-016 唯一真并发钉、3.3 failover 面、R02 三范式钉等）——修复不得弱化钉为通用纪律，违者评审直接打回。

## D. CL-011 FileWatcher.stop footgun（禁 watcher.stop() 统一）

- [ ] D1 任何触及 `FileWatcher`/`ResourceWatcher`/watch 目录扫描面的变更：**禁止调用 `watcher.stop()` 作为停机手段**（CL-011 footgun——plan 2563/R4-29 裁定「MA-02 修复陷阱=禁 watcher.stop() 统一」）；停机一律走资源关闭通道（close/destroy 语义配对）。
- [ ] D2 新增 watch 型组件（文件/watch 目录/zip 产物观察）评审必答：停机路径是否绕开 `stop()`；并发读产物是否原子（tmp+ATOMIC_MOVE 范式，参照 plan 2563 `search-watch-testinfra-batch.md` 裁定 C——使用方层串行化 + 原子写，平台 `IZipTool.zipDirTo` 不触碰）。

## E. 通用接线卫生（从 campaign 结构性发现提炼）

- [ ] E1 生命周期配对：新组件须有显式 init/destroy 或 AutoCloseable 配对，禁止「close 零调用方」形态入库（R07/R08 fd 计数回归先例：`nop-network` TestFileDownloadSinkClose）。
- [ ] E2 停机拒绝语义：关闭/死亡路径须有拒绝语义（future 族纪律——execute 入队后复查+同引用移除，参照 plan 2533 JavaScriptWorker 形态）。
- [ ] E3 测试替身不得宽于真实契约（4.4 结构性发现）；新增组件的并发测试不得只护历史修复（5.5「测试基建只向后看」形态升级）。
- [ ] E4 验证通道：idea-plugin/Gradle 隔离站点不得以 `./mvnw test` 冒充验证（CL-301——通道决定见产出物⑥ `gradle-channel-decision.md`）。

## F. 评审出口

- [ ] F1 全部命中项已回查产出物①登记册并更新对应组状态（active→已激活已承接）。
- [ ] F2 cra-index 对应 P2-G 行追加激活注记（激活日期/触发信号/承接批次）。
- [ ] F3 零命中亦留痕：评审记录声明「B1-11/S6/B1-12 扫描零命中」，checklist 项勾选。
