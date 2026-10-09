# 2357 WI11 — `<sideInputs>` 有限维表注入声明面

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI11（v9）
> Related: plan 2346（WI0(c) 裁定承载）、sql roadmap WI14（ITableLookup 既有形态）、multi-engine WI11（lookup 异步增量，R5 裁定复用既有路径）、plan 2356（WI10，同族声明面先例）

## Purpose

把 roadmap WI11 收口到：`<sideInputs>` 注册表按 WI0(c) 裁定语义（构建期物化的有限维表只读快照注入；无 BroadcastState）获得可用的声明面接线；交付差异化判定记录（四类场景 vs 既有 lookup 形态的形态边界）与富化等价性测试；既有 `keyBy+process+ITableLookup` 形态保持主线不回归。WI0(c) 裁定明文「roadmap WI11 按此语义落地」= 本项整体 go；差异化判定记录裁定的是**形态边界**（sideInput 适用哪些场景），不是「做不做」。

## Current Baseline

live 核实（2026-10-09，HEAD cad0c20976）：

- **xdef 声明面已存在但恒拒绝**：`stream.xdef:277-285` 定义 `<sideInputs>`（`<sideInput id from to broadcast>`，broadcast 必填默认 false——该默认值在放行后成为**历史遗留毒默认**：省略属性经 xdef 默认值注入 false，而新契约 typed 拒绝 false，即每个合法 sideInput 必须显式 `broadcast="true"`；xdef 注释与校验消息双向记录此裁定）；`StreamModelDslBuilder.failFastOnUnsupportedRegistries:446-450` 对任何非空 `<sideInputs>` 抛 `ERR_STREAM_NOT_IMPLEMENTED`（"no execution consumer"）——WI1 合同账钉（`TestStreamModelDslBuilderFailFast.sideInputsRegistryFailsFast:90-95` 正向钉此行为；每注册表独立 @Test + 共享 helper，移除 sideInput 分支不牵连 streams/environments 等 WI12 钉子）。
- **L2 lint 镜像面**：`StreamModelLinter.scanRegistries:272-274` 对 sideInputs 报 `CODE_NOT_IMPLEMENTED` warning（自标注 "roadmap WI11 release face"）——放行时同步移除。
- **WI0(c) 裁定（vision §六 #WI0-XDSL (c)，owner 委托已裁）**：`<sideInputs broadcast>` 语义钉死为「有限维表注入方式」标记——构建期或低频更新的有限维表只读快照，声明该 sideInput 复制到所有并行实例；**不引入流式广播状态、不引入 BroadcastState 类型**（守 §七 #G36）。
- **注入面事实（xpl-fn 形态分布）**：map（`XplMapFunction.java:37`）、filter（`XplFilterFunction.java:37`）、flatMap（`XplFlatMapFunction.java:40-41`）三类有 xpl-fn 体，统一经 `resolveFunctionOrXpl`（`StreamModelDslBuilder.java:1397-1408`）构造；**`<process>` 无 xpl 形态**（xdef 空元素 `stream.xdef:230`、`buildProcess:919-946` bean 必填）——process 不在注入面（bean 走 IoC 自行装配，与既有形态一致）；sink 的 xpl 形态（`XplSinkFunction.java:37`）不纳入消费方集合（本 WI 裁定，见 Non-Goals）。
- **bean resolve 机制**：`BeanFunctionResolver.resolve(String, Class<T>)`（`BeanFunctionResolver.java:33`）全类型泛型接口；`GlobalBeanFunctionResolver` 经 `BeanContainer.tryGetBean` 解析任意 bean，缺失/类型错分别 `ERR_STREAM_BEAN_NOT_FOUND`/`ERR_STREAM_BEAN_TYPE_MISMATCH`。构建期 resolve 快照 bean 机械零障碍；需一个以 `StreamSideInputModel` location 锚定的 resolve 变体（既有 `resolveBean` 以 transform 模型锚定）。
- **既有富化形态（差异化基线）已完备**：sql roadmap WI14 交付 `keyBy+process+ITableLookup`（core `io.nop.stream.core.connector.lookup`，IJdbcTemplate 适配、`TestDimLookupPipelineE2E` 全链、restore 缓存命中证据）；multi-engine WI11 交付有界并发异步 + 本地物化 + TTL 刷新。**E11 场景矩阵已裁定富化主线走 lookup 形态**。
- **相关机制现状**：edge `partition="BROADCAST"` 被 `validateEdgeDeclarations:1117` typed 拒绝——与 `<sideInputs>` 注册表是两个独立面，本 WI 不改 edge 分区面。xpl 绑定先例：`XplFunctionSupport.newCallScope().newChildScope(Map)`（SideOutputTapOperator 的 `event` 绑定）。
- **R5 sink 可达性守卫**：`validateSinkReachability:964` 只遍历 transforms 与 edges；sideInput 的 from（bean id）/to 不进 byId、不产生 edge——守卫无影响（审查核实）。
- **真正剩余的 gap**：sideInput 无任何消费方接线——from 物化、to 注入、lint/builder 双钉子、差异化判定记录、等价性测试全部缺失。

## Goals

- `<sideInputs>` 放行：builder fail-fast 分支与 L2 lint warning 双面移除，代之以结构校验 + 消费方接线。
- 接线语义（方案乙——bean 快照源）：`from` 钉为 bean id，类型 **`java.util.function.Supplier<Map<String,Object>>`**（JDK 类型，零新增公共契约——M5 裁定）；builder 构建期调用 `get()` 一次物化只读快照并**冻结**（绑定固化进函数对象，bean 运行期不再被调用——低频更新语义即「重启作业刷新」）；to 的 map/filter/flatMap xpl-fn 经 `sideInputs` 绑定变量访问快照（绑定名 `sideInputs`，Map 类型 key=sideInput id，访问形如 `sideInputs['dict'][event.code]`）；`broadcast` 属性按 M4 裁定 typed 拒绝 false（仅支持 true 注入方式）。
- 差异化判定记录：四类场景形态边界（sideInput=构建期小/静态维表声明式注入；lookup=运行时大/热维表），落 user guide + 矩阵迁移。
- 富化等价性测试：同一富化任务 sideInput 形态与既有 lookup 形态产出等价。

## Non-Goals

- 不引入流式广播 / BroadcastState（#G36 永久排除，WI0(c) 守门）。
- 不改 edge `partition="BROADCAST"` typed 拒绝（独立面）。
- 不动既有 ITableLookup / 异步增量 / 物化刷新路径（E11 富化主线）。
- 不为 `<process>`/`<sink>` 增加 xpl-fn 体或 sideInput 注入（process/sink 保持 bean 形态；sink xpl 形态不纳入消费方集合——本 WI 裁定，若未来需要属新 scope）。
- 不做 sideInput 运行时热刷新协议（快照 build() 一次物化冻结；「低频更新」= 作业重启刷新——能力边界写入判定记录，用户不可误期 bean 侧热刷）。
- 不新增公共接口/SPI（`Supplier<Map>` 复用 JDK 类型；如未来出现新公共契约需求，按 WI0b/WI21 补记先例走 owner 人审）。

## Scope

### In Scope

- `StreamModelDslBuilder`：sideInput 结构校验（from bean 存在且为 Supplier 类型、to 引用存在且为 map/filter/flatMap 之一的 xpl 形态（bean 形态排除）、id 唯一、broadcast=false typed 拒绝）+ 构建期物化 + xpl 绑定注入。
- `StreamModelLinter.scanRegistries` sideInputs warning 分支移除。
- `TestStreamModelDslBuilderFailFast.sideInputsRegistryFailsFast` 改写为新契约（空 `<sideInputs/>` 合法、非法声明 typed reject）。
- xdef `<sideInput>` 注释更新（裁定语义）。
- 差异化判定记录 + user guide 声明面支持矩阵**表 B → 表 A 迁移**（放行三步法第三步）。
- 等价性测试 + fail-fast 测试。

### Out Of Scope

- BroadcastState / 流式广播（#G36）。
- sideInput 参与 transforms 主图 / edge 图（from 是 bean id 非 transform 引用）。
- `<process>`/`<sink>` 的注入面扩展。
- remote/deployment wire 面的快照传输（嵌入模式无算子序列化路径——快照经函数对象闭包持有；remote 面条件性检查登记 WI27 统一核，同 plan 2356 先例）。

## Execution Plan

### Phase 1 - 声明面放行、校验与消费方接线（含放行验证测试）

Status: completed
Targets: `StreamModelDslBuilder.java`、`StreamModelLinter.java`、`stream.xdef`、`TestStreamModelDslBuilderFailFast.java`、`TestStreamModelLinter.java`、flow 新测试类 `TestWi11SideInputDeclaration.java`

- Item Types: `Feature` + `Proof`（guide #25：新增功能测试同 Phase）

- [x] `failFastOnUnsupportedRegistries` 移除 sideInput 分支；`StreamModelLinter.scanRegistries` 移除 sideInputs NOT_IMPLEMENTED warning
- [x] 结构校验（validateDag 阶段，typed reject；**执行期事实修正**：duplicate id 在 XML 路径由 xdef key-attr 唯一性于 parse 层先行拒绝——builder 层校验保留为程序化构建路径的防御，测试改钉 parse 层契约）：from bean 不存在 → `ERR_STREAM_BEAN_NOT_FOUND`；from bean 非 `Supplier` 类型 → `ERR_STREAM_BEAN_TYPE_MISMATCH`；to 引用不存在的 transform → `ERR_STREAM_REF_UNKNOWN`；to 非 map/filter/flatMap → `ERR_STREAM_INVALID_ARG`；to 的 map/filter/flatMap 为 **bean 形态**（`getBean() != null`，语法层可检测）→ `ERR_STREAM_INVALID_ARG`（detail 指明 sideInput 绑定仅注入 xpl-fn 体，bean 形态走 IoC 自行装配——防静默无操作）；重复 id → `ERR_STREAM_DUPLICATE_ID`；`broadcast="false"`（含省略属性经 xdef 默认值注入 false 的形态）→ `ERR_STREAM_INVALID_ARG`（detail 指明当前仅支持 broadcast=true 注入方式；xdef 默认值 false 为历史遗留、现已拒绝——注释与校验消息双向记录）
- [x] 构建期物化：builder resolve 全部 sideInput 的 Supplier 并调用 `get()` 一次，缓存 `Map<String,Map<String,Object>>`（id → 快照）——null 快照 typed reject（`ERR_STREAM_NULL_ARG`，静默空表会伪装成缺数据）
- [x] xpl 绑定注入：map/filter/flatMap 的 xpl-fn scope 绑定 `sideInputs`（快照 Map，key=sideInput id）；bean 形态不注入（校验层已拒绝 bean 形态 to）
- [x] xdef `<sideInput>` 注释更新（from=Supplier bean id、to=map/filter/flatmap、broadcast=仅 true）——再生成漂移恰 1 文件（`_StreamModel.java` javadoc）
- [x] fail-fast 契约迁移：`sideInputsRegistryFailsFast` 改写（空声明合法 + broadcast 遗留默认拒绝；七类契约全量钉在 `TestWi11SideInputDeclaration`）
- [x] lint 放行回归测试：合法 sideInputs 模型经 `StreamModelLinter` 扫描不再报 sideInputs 相关 warning（`TestStreamModelLinter.releasedWindowFacesAreNotReported:257` 先例同构）
- [x] 注入数据面单测：to 的 xpl-fn 经 `sideInputs` 绑定读到快照内容并反映到输出（含「声明但未被引用的 sideInput 不注入无害」行为）

Exit Criteria:

- [x] 放行后声明面全链路：声明 → 校验 → 构建期物化 → xpl 绑定 → to 输出反映快照内容（flow 单测可观察）
- [x] **接线验证**：to transform 的处理函数确实经 `sideInputs` 绑定拿到快照（数据面断言 + runtime E2E 变异探针断线红/恢复绿）
- [x] **无静默跳过**：七类非法声明全部 typed reject；lint/builder 双面同步放行（lint 对合法 sideInputs 不再误报）
- [x] 新增测试（fail-fast 迁移 + 注入数据面）与实现同 Phase 全绿（TestWi11SideInputDeclaration 12 + FailFast 10）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 全绿（全量 273 绿）
- [x] owner-doc 裁定：文档更新集中 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 富化等价性测试（runtime E2E）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/test/java/.../TestSideInputEnrichmentE2E.java`（新，inline DSL）

- Item Types: `Proof`

- [x] E2E：同一维表富化任务的两种形态——sideInput 形态（`<sideInput from="dictSupplierBean" to="enrichMap">` + `<map id="enrichMap"><source>...</source></map>`）vs 既有 process+ITableLookup 形态（`InMemoryTableLookup` 先例），断言两形态对同一输入流产出等价的富化结果
- [x] 主路径回归：`TestDimLookupPipelineE2E` 等既有 lookup 测试不改动且全绿（runtime 全量 1352 绿）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿（1352/0；豁免口径具名：仅 `TestE2ETwoPhaseCommitSink`/`TestDistributedExactlyOnce`/`TestCheckpointEndToEnd`/`TestStreamConnectorRegistryE2E` 四个已记录时序抖动测试允许隔离复跑核实，其余一律须绿——本轮零豁免触发）（豁免口径具名：仅 `TestE2ETwoPhaseCommitSink`/`TestDistributedExactlyOnce`/`TestCheckpointEndToEnd`/`TestStreamConnectorRegistryE2E` 四个已记录时序抖动测试允许隔离复跑核实，其余一律须绿）

Exit Criteria:

- [x] 等价性断言含数据内容（两形态输出逐条一致）
- [x] **端到端验证**：sideInput 形态从声明到富化输出全链路（audit 亲跑 1/1 绿）
- [x] 既有 lookup 形态无回归（audit 亲跑 TestDimLookupPipelineE2E 1/1 绿）
- [x] `./mvnw test`（涉及模块）全绿（flow 273 + runtime 1352）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 判定记录落档 + closure audit + 提交

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`、roadmap、本 plan

- Item Types: `Follow-up`（文档）/ `Proof`（audit）

- [x] user guide：sideInput 段落（声明语法、`sideInputs` 绑定访问、快照 build() 冻结语义能力边界、与 lookup 形态的四象限判定记录：多维表→两形态均可/大维表→lookup 胜（全量复制内存放大）/构建期注入→sideInput 胜（声明式免装配）/共享只读快照→sideInput 胜（一处声明多处消费）；E11 富化主线维持 lookup）
- [x] 声明面支持矩阵：表 B sideInputs 行迁表 A（放行三步法收口）
- [x] roadmap WI11 勾选（日期/plan 号/audit agent），header 升 v10
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 复核 0 errors）
- [x] 独立子 agent closure audit（fresh session）——**APPROVE**（agent_90e44a09：A1-A4/B1-B4/C1-C3/D1-D3/F 全 PASS；亲跑 flow 12+10、E2E 1/1、lookup 回归 1/1 全绿）
- [x] `check-plan-checklist.mjs --strict` 与 `scan-hollow-implementations.mjs`（直接路径形式）退出码 0（audit 实测 hollow-scan 0 findings）
- [x] 文本一致性核对 + 单次提交

Exit Criteria:

- [x] audit APPROVE，evidence 写入 Closure 段
- [x] roadmap WI11 已勾选且与 plan/日志一致

## Closure Gates

- [x] Phase 1/2/3 全部 completed
- [x] 声明面全链路 live 可验证（builder+lint 双钉子放行完成）
- [x] 差异化判定记录四类场景齐备且与测试证据一致
- [x] 既有 lookup 形态无回归
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步（user guide 段落 + 矩阵迁移 + roadmap）
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：调用链连通 + 无空壳/静默跳过
- [x] `./mvnw test`（涉及模块）全绿
- [x] `check-plan-checklist.mjs --strict` 与 `scan-hollow-implementations.mjs` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- remote/deployment wire 面的 sideInput 快照传输条件性检查 → WI27 门禁统一核（嵌入模式快照经函数对象闭包持有，无算子序列化路径；同 plan 2356 先例）。

## Closure

Status Note: 声明面按 WI0(c) 裁定语义全链路落地并有双重证据：结构校验（flow 12 用例，七类 typed reject + lint 放行回归 + 注入数据面）+ 等价性 E2E（sideInput xpl 绑定形态 vs WI14 process+ITableLookup 形态对同一维表输出逐条一致 [merchant-a, merchant-b, MISS]，含缺失键 MISS 语义）+ 变异探针（buildMap 绑定断开 → E2E 红；恢复 grep wired=3/probe=0 → 复绿）。lint/builder 双钉子放行；broadcast 遗留毒默认 typed 拒绝双向记录。独立 closure audit（agent_90e44a09，fresh session）全项 PASS/APPROVE：Anti-Hollow 调用链逐环代码追踪（validateSideInputs → materializeSideInputs get() 恰一次 → sideInputScopes → XplXxxFunction(body, bindings) → newChildScope(Map)）+ 数据面双证；差异化判定四象限落 user guide（互补共存，E11 富化主线维持 lookup）；flow 273 / runtime 1352 回归绿。执行期两项事实修正（均实测依据并记录）：duplicate id 在 XML 路径由 xdef 唯一性 parse 层先行拒绝（builder 校验保留为程序化路径防御）；SourceContext 为 SourceFunction 嵌套接口。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_90e44a09（独立 fresh-session closure auditor）
- Audit Session: agent_90e44a09-31ea-416b-80b9-378fed6c1033
- Evidence:
  - 放行双面：A1 PASS（builder :451-491 sideInput 分支移除）；A2 PASS（linter :267-295 warning 分支删除）；A3 PASS（七类校验 :932-1002 逐一定位 + null 快照 :1022-1029）；A4 PASS（get() 全文件恰一处 :1021、注入三处 buildMap:1561/buildFilter:1569/buildFlatMap:1577）
  - 测试（audit 亲跑）：B1 PASS（flow 12+10 全绿）；B2 PASS（E2E 1/1 + TestDimLookupPipelineE2E 1/1）；B3 PASS（内容级等价断言 [merchant-a,merchant-b,MISS]）；B4 PASS（lookup 主路径零改动回归）
  - Anti-Hollow：C1 PASS（调用链逐环追踪 buildTransforms:506-507 → … → IEvalScope.newChildScope:nop-kernel:77）；C2 PASS（daily log 变异探针记录在案）；C3 PASS（bean 形态 to 拒绝 + null 快照拒绝，无空方法体）
  - 文档：D1 PASS（user guide L175 专段 + L176 四象限 + L259 矩阵表 A 行 + L180 清单）；D2 PASS（roadmap L136 WI11 [x] + header v10 + v9/v8 历史行完整）；D3 PASS（doc-links --strict exit 0）
  - 工具：check-plan-checklist --strict exit 0（Closure 回填后终验）；scan-hollow 直接路径形式 exit 0（四档全 0）
  - Deferred 分类检查：F PASS（remote wire 面 WI27 降级诚实——非本 WI 引入的先在疑问，Out-of-Scope 先行声明）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2357-wi11-sideinputs-dim-table-injection.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- remote/deployment wire 面快照随函数对象过线的行为核验 → WI27 门禁统一核（non-blocking：对所有 xpl-fn 体均先在存在，非本 WI 引入；嵌入模式为进程内直接引用无算子序列化）
