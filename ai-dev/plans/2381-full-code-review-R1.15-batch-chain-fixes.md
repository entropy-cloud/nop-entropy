# 2381 full-code-review R1.15 — nop-batch 执行链修复批 a（P1-3.3-001/002/003）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.15；findings `P1-3.3-001`（DSL-01）+ `P1-3.3-002`（DSL-02）+ `P1-3.3-003`（ENG-01），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.3-batch-deep-audit-a.md`
> Related: plan 2382（R1.16 记录面批 b，同审计 b 面）；P1-4.2-003 upsert 归 R1.41 勿双立
> Owner Doc: `docs-for-ai/03-modules/nop-batch.md` + `docs-for-ai/02-core-guides/batch-dsl.md`

## Purpose

收口 nop-batch 执行链三条 P1：①orm-reader `<eql>` 死配置面移除+全表加载兜底 Guard 可见化；②processor order 排序接线（consumer :275 同型）+文档勘正；③allOf 异常竞态掩蔽 fail-fast 根因——AtomicReference CAS 收集首个非取消异常作 primaryError，终态不再误持久化 KILLED。

## Current Baseline

- **P1-3.3-001 五环链**：batch.xdef orm-reader 声明 `<eql>xpl-sql</eql>`（:127）→ BatchOrmReaderModel 生成 `getEql(): ISqlGenerator` → `OrmBatchSupport.newOrmReader:33` 接线被注释（`//loader.setSqlGenerator(loaderModel.getEql())`——指向**不存在**的方法）→ queryBuilder 为 null → `OrmQueryBatchLoaderProvider.newLoaderState:107` 静默兜底 `new QueryBean()` 全表加载。用户声明 EQL 过滤被静默丢弃=全表扫描。**EQL 文本→QueryBean 无现成转换器**（全仓核查：无 EqlTextParser 类设施；loader 消费 QueryBean.filter/orderBy 走 `dao.findNext`），故裁定**移除节点**（接线成本=自建 EQL 文本解析器，超出修复批范围且 `<query>`（xpl-node→QueryBean 树）是既有受支持路径）。`init()` 的 `Guard.notNull(queryBuilder)` 仅覆盖 IoC bean 形态——`OrmBatchSupport` 直接 new 的实例不经 @PostConstruct，兜底路径对直构实例不可见。
- **P1-3.3-002**：`ModelBasedBatchTaskBuilderFactory:261-267` processor 循环按模型声明序加入 list，**零 sort**；consumer 侧 :275 `consumers.sort(BatchConsumerModel::compareTo)` 为同型先例。batch.xdef :151 processor `order="!int=0"`；batch-dsl.md:15 与 nop-batch.md:76 双文档声明「按 order 排序」=失实承诺。
- **P1-3.3-003 竞态机理**：`BatchTask.executeChunkLoop:227-234` chunk 异常 → `future.completeExceptionally(e)` → `context.cancel(STOP)`；兄弟线程循环顶 :218-219 随即抛 `BatchCancelException`。`CompletableFuture.allOf(futures).whenComplete(:139-141)` 交付的 err 取决于完成时序——cancel 异常先完成时掩蔽真根因 → `saveTaskState(true, BatchCancelException, ...)` → 终态 KILLED(60) → 重启 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED` 无旁路（探针 5/5 确定性复现，审计已实测）。
- **测试基建**：nop-batch-core 既有 `TestBatchTaskCheckpoint`/`TestBatchBugVerification` 等布景（stateStore mock/内存 store 可扩展）。

## Goals

- `<eql>` 节点从 xdef/生成模型/OrmBatchSupport 全链移除；`OrmQueryBatchLoaderProvider.newLoaderState` 在 queryBuilder==null 时输出 loud WARN（全表加载可见化——兜底行为保留：显式无过滤读全表是合法用途，但必须可见）。
- processor 按 order 排序接线（镜像 consumer compareTo），双文档表述转为真实。
- allOf 回调改用 primaryError（AtomicReference CAS 收集首个非 BatchCancelException 异常，null 时回落 allOf err）——KILLED 误分类路径消除。
- 三修复各有判别/回归测试；受影响模块测试全绿。

## Non-Goals

- P2-3.3 全部 11 条（台账行）；P3 watch-only。
- 不实现 EQL 文本→QueryBean 解析器（`<query>` 节点是受支持路径；如需 EQL 过滤由后续 feature 计划承载）。
- 不动 consumer 排序既有行为（已正确）。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：batch.xdef 删 orm-reader `<eql>` 节点
- nop-batch-dsl：`BatchOrmReaderModel` 生成类再生成（删 getEql）；`OrmBatchSupport` 删 :33 注释行；`ModelBasedBatchTaskBuilderFactory` processor sort
- nop-batch-orm：`OrmQueryBatchLoaderProvider.newLoaderState` queryBuilder==null WARN
- nop-batch-core：`BatchTask` primaryError CAS + allOf 回调改造
- 测试：三修复判别/回归测试（nop-batch-core/dsl）
- owner doc：nop-batch.md / batch-dsl.md 勘正（orm-reader 移除 eql 说明、order 排序真实化、allOf primaryError 语义）

### Out Of Scope

- P2-3.3 台账行；EQL 解析器；consumer 行为变更

## Execution Plan

### Phase 1 - P1-3.3-001 + P1-3.3-002（DSL 面两修）

Status: completed
Targets: batch.xdef、OrmBatchSupport、OrmQueryBatchLoaderProvider、ModelBasedBatchTaskBuilderFactory、测试

- Item Types: `Fix`

- [x] batch.xdef orm-reader 删 `<eql>` 节点 → xdefs install → nop-batch-dsl generate-sources 再生成（_BatchOrmReaderModel diff 仅删 eql 字段段）；OrmBatchSupport 删 :33 注释行与多余 import
- [x] `OrmQueryBatchLoaderProvider.newLoaderState`：queryBuilder==null 时 LOG.warn `nop.batch.orm-reader-no-query`（全表加载可观测；全表合法用途有实证——batch-demo.task.xml 无子节点 orm-reader）
- [x] `ModelBasedBatchTaskBuilderFactory` :261-267 processor 循环前新建 ArrayList 拷贝后排序（`Comparator.comparingInt(BatchProcessorModel::getOrder)`——共享缓存模型 KeyedList 未被原地排序，B1 约束满足）
- [x] 测试：①processor 排序判别测试（TestBatchProcessorOrderAndEqlRemoval——fixture 声明序 order=2,1 与断言 order 序相反，行为断言经真实任务执行收集器链序 1/1 绿；**判别证据**=修复前实际结果为声明序 [p2:x,p1:x] 与断言相反即 FAIL）；②③合并裁定：`<eql>` 声明残留经 xdef 校验天然响亮报错（fixture 迭代中 attr-not-allowed/undefined-node 错误族即该机制的实证）；WARN 路径由 LOG 语句+全表合法场景记录覆盖
- [x] **判别验证**：首轮运行即实证——排序代码在位时实际序 [p1:x,p2:x] 与「预期声明序 [p2:x,p1:x]」的断言失败（此刻代码已带 sort；反证=声明序被打破），修正断言期望后绿

Exit Criteria:

- [x] orm-reader `<eql>` 全链移除（xdef/生成模型/支撑类零残留）且声明残留经 xdef 校验响亮报错
- [x] 全表加载兜底可观测（WARN）
- [x] processor order 排序真实生效（判别测试绿）
- [x] nop-batch-dsl 21/nop-batch-core 61 全绿（orm 无测试源）
- [x] owner doc 勘正归 Phase 3（已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1-3.3-003：primaryError CAS

Status: completed
Targets: `BatchTask`、nop-batch-core 测试

- Item Types: `Fix`

- [x] `BatchTask` 增 `final AtomicReference<Throwable> primaryErrorRef`（package-private——同包机制直测；executeAsync 起点重置；全仓调用点核实无实例复用）；`executeChunkLoop` catch 内 `!(e instanceof BatchCancelException)` 时 CAS 收集首个非取消异常
- [x] 优先级内嵌 `onTaskComplete`（**执行裁定**：初版置于 allOf 回调 lambda——机制直测暴露直调路径未覆盖，上移至 onTaskComplete 解包后统一生效：primary 非空且 err 非空且 err≠primary 时以 primary 替代；成功路径 err==null 不受扰）——KILLED 误分类消除
- [x] 测试（TestBatchPrimaryError，impl 包 2/2）：①primaryError 预置真根因 + allOf err=BCE → saveTaskState/future 均以真根因收尾（修复前该路径交付 BCE——直调判定）；②对照：primaryError 空 → BCE 原样驱动（KILLED 语义保持）+executeAsync 重置语义。**判别形式裁定**：真实竞态依赖线程时序（probe 5 轮可复现非单测确定性），机制直测替代；CAS 收集点与优先级接线由代码审查锚定
- [x] **判别验证**：机制直测的失败输出即判别——优先级未上移前（初版置回调层）testPrimaryErrorBeatsAllOfDeliveredCancelException 实测 FAIL（交付 BCE），上移后 2/2 绿

Exit Criteria:

- [x] 机制直测 2/2 绿（真根因优先交付 + 纯 cancel 对照）
- [x] nop-batch-core 既有测试全绿（61 tests，TestBatchTaskCheckpoint 等 fail-fast 既有语义零回归）
- [x] owner doc 处置归 Phase 3（已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner doc 勘正 + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/02-core-guides/batch-dsl.md`、`docs-for-ai/04-reference/xdefs/batch.xdef`（参考副本——INDEX:208/batch-dsl.md:28/:277 指认的 DSL 权威定义，手工维护需同步）

- Item Types: `Fix`

- [x] 04-reference/xdefs/batch.xdef 参考副本 orm-reader 段同步移除 `<eql>`（B2 封堵）
- [x] nop-batch.md：orm-reader 查询模型说明改写（仅 `<query>`；eql 移除缘由+全表 WARN）+processor order 接线注记；batch-dsl.md「按 order」表述核对=接线后即为真实（零改动）；primaryError 语义注记归 nop-batch.md（随 2382 记录面批一并核对或在本 plan Closure 记录核对结论——已核对：文档无 allOf 异常语义表述，无需新增）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.15 翻 done、index P1-3.3-001/002/003 → fixed、MR 表 R1.15 行）

Exit Criteria:

- [x] owner doc 与 live 行为一致（closure audit 指出 nop-batch.md:146/:177 两处 `<eql>` 残留——已修正）
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.3-001/002/003）
- [x] 行为/契约结果已达成（移除+Guard 可见化+排序判别+primaryError 机制直测）
- [x] 必要 focused verification 已完成（nop-batch-dsl 21/nop-batch-core 61 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步（nop-batch.md 三处勘正含 closure audit 指出的 :146/:177 残留；04-reference 副本；batch-dsl.md 核对零改动）
- [x] 独立子 agent closure-audit 已完成并记录证据（closure-audit agent_01f40c6f——初判 Cannot Close 指出 nop-batch.md 两处残留，修正后满足关闭条件，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）eql 移除后零消费点+声明残留经 xdef 校验响亮报错+WARN 在位；（b）TestBatchPrimaryError 断言 primaryError 真实改变 saveTaskState/future 所见异常
- [x] `./mvnw test -pl nop-batch/nop-batch-dsl,nop-batch/nop-batch-orm,nop-batch/nop-batch-core -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-batch-core --severity high` 退出码 0

## Deferred But Adjudicated

（无——P2-3.3 属台账行非本 plan 域）

## Non-Blocking Follow-ups

- P2-3.3-001..011 台账行（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.3-001/002/003 全部交付：`<eql>` 死配置面全链移除（xdef/生成类/注释行+全表兜底 WARN 可观测）、processor order 拷贝后排序接线（共享缓存禁原地）、primaryError CAS 优先级内嵌 onTaskComplete（执行裁定上移覆盖直调路径）。三项执行裁定（eql 移除/排序拷贝语义/机制直测判别形式）与 closure audit 指出的两处 doc 残留修正均透明记录。三 Phase 全 completed，独立 closure audit 修正后满足关闭条件。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: closure-audit agent_01f40c6f（fresh read-only session）
- Evidence:
  - Req1 Phase/勾选：Phase 1-3 全 completed；bookkeeping 行 work 已核实（roadmap :146/index :124-126+MR :271/log），PASS
  - Req2 live-code：xdef 无 `<eql>`+生成类 diff 仅删段+OrmBatchSupport 注释删+WARN :110-114；factory :263-264 拷贝后排序（共享 KeyedList 未动）+consumer 先例 :278-279；BatchTask :48 字段/:247-248 CAS/:94 重置/:183-185 优先级（unwrap 后、成败分支前），PASS
  - Req3 Anti-Hollow：排序 fixture order=2 先于 order=1+真实任务执行断言 [p1:p2:consumed]（1/1）；TestBatchPrimaryError 2/2 机制直测（引用同一性断言 primaryError==saveTaskState 所见）；首轮失败（期望倒置）记录于 plan/log 为判别证据，PASS
  - Req4 测试证据：surefire 同批 dsl 21/0、core 61/0（TestBatchPrimaryError 2/2），PASS
  - Req5 一致性：roadmap/index/log/04-reference 副本/batch-dsl.md 全过——**唯一 FAIL**：nop-batch.md :177 常用 loader 表与 :146 lead-in 残留 `<eql>` 表述（与同文件 :160-163 勘正自相矛盾）——**已在收口 pass 修正**（表行改「`<eql>` 已移除」+lead-in 改「仅支持 `<query>`」）
  - Req6 裁定诚实性：eql 移除依据（无转换器）/优先级上移（机制直测暴露）/判别形式，plan+log 双记录，PASS
  - Req7/8：变更 hunk 零 hollow/no-op；三工具退出码 0，PASS
  - 工具退出码：check-plan-checklist --strict=0；scan-hollow-implementations（nop-batch-core）=0；check-doc-links --strict=0

Follow-up:

- P2-3.3-001..011 台账行（R1.0 P2 裁决通道）
- 无其余 plan-owned 剩余工作
