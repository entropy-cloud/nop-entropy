# 344 check 审计剩余 P1/P2/P3 条目全量处置

> Plan Status: active
> Last Reviewed: 2026-08-22
> Source: `ai-dev/audits/check/SUMMARY.md`（修复进展两节）、fix-ai-check 分支 2026-08-21/22 修复批次
> Related: 无前置 plan（P0 批次为直接执行，本 plan 承接其未覆盖的 P1/P2/P3）

## Purpose

把 `ai-dev/audits/check/` 54 份单元报告中**剩余未处置的 P1/P2/P3 条目**全部收口：每条获得明确的终态裁定并在原报告中标注，修复项附带回归测试，非修复项写清理由，使后续任何复查不再产生状态歧义。

## Current Baseline

- 2026-08-21：dev-tools / file-retry-tcc / format-misc / format-office / format-pdf-svg / format-record 六单元全部 118 条已处置完毕（74 修复 + 27 暂缓 + 4 维持现状 + 1 审计有误 + 其他文档化）。这六份报告无剩余工作。
- 2026-08-22：其余 48 份报告中的全部 36 条 P0 已按"live code 复核 → 红测试 → 最小修复 → 绿 → 模块全量测试 → 报告条目标注"闭环清零。
- 剩余：48 份报告共约 750 条 P1/P2/P3（P1≈190、P2≈290、P3≈270，以各报告发现列表为准）均未处置，报告条目保持原状。
- fix-ai-check 分支全量构建 SUCCESS（2026-08-21 收尾日志）。

## Goals

- 48 份报告中每条 P1/P2/P3 条目末尾出现 `> **处置（fix-ai-check 分支，YYYY-MM-DD）**: ...` 标注，终态为以下四类之一：
  1. **已修复**：附修复说明 + 回归测试（行为类修复必须有能区分对错的测试；纯日志文案/死代码删除等无行为语义变化的修复注明无需新测试的理由）。
  2. **复查非问题**：注明审计前提有误或现状已正确的原因。
  3. **裁定暂缓**：需要设计决策/大范围改造的，写明决策点与影响面（沿用 2026-08-21 六单元的暂缓标注惯例）。
  4. **裁定不修复**：从长期产品化维护角度出发（如死代码无调用方、低频元数据路径、修复引入的回归风险大于收益），写明不修复理由，防止下轮检查误判为遗漏。
- 修复后对应模块全量测试通过；每份报告处置完成后该模块 `./mvnw test -pl <module> ` 绿。
- SUMMARY.md 增补本战役进展节。

## Non-Goals

- 不重开已关闭的 P0 条目（含其"超出审计的新发现"记录项，除非用户明确要求）。
- 不做超出条目最小修复范围的重构/优化；发现的新缺陷只记录在报告标注里，不扩大战役范围。
- 不修改 `docs-for-ai/` 规范内容（除非修复行为与 owner doc 冲突，此时按 AGENTS.md 上报）。
- 不处理六份已收口报告的任何条目。

## Scope

### In Scope

- 48 份报告（见 Phase 划分）的 P1/P2/P3 条目处置：复核、修复、测试、标注。
- 与修复直接相关的测试文件新增/调整。
- SUMMARY.md 进展更新、`ai-dev/logs/` 每日日志。

### Out Of Scope

- 各报告"补充说明（非缺陷观察）"段落。
- 检查脚本/门禁维护（除非修复直接导致既有门禁断言失败，此时按最小改动重钉并注明）。

## Execution Plan

> 执行顺序按模块分层（框架核心 → 业务 → 子系统 → 外围），每份报告为一个独立处置单元。单条处置流程：live code 复核（行号/证据以当前分支为准）→ 需要测试的先写红测试 → 最小修复 → 绿 → 模块测试 → 报告条目标注。每份报告全部条目标注完成后才算该单元完成。

### Phase 1 - 持久层（nop-dao / db-migration / nop-orm / orm-periph / nop-orm-eql）

Status: completed
Targets: `ai-dev/audits/check/nop-dao.md`、`db-migration.md`、`nop-orm.md`、`orm-periph.md`、`nop-orm-eql.md`；对应 `nop-persistence/*` 模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-dao.md（P1×3 P2×9 P3×4）— 2026-08-22 完成：13 修复 + 1 部分修复暂缓 + 2 免测试裁定，全部标注，102 tests 绿，红测试 3 条验证；超出审计新发现 2 处（QuerySpaceEnv.leave 恢复语义、Hikari setDriverClassName(null) NPE）已修复
- [x] db-migration.md（P1×8 P2×11 P3×2）— 2026-08-22 完成（子代理，commit 5737e7dbb5）：13 修复 + 3 部分修复暂缓 + 4 暂缓 + 1 非问题；58 tests 绿、17 条红验证；超出审计新发现 3 项已记录（DbMigrationErrors 错误码形态误用、H2 接受分号多语句、元数据 API 名错误）
- [x] nop-orm.md（P1×5 P2×8 P3×8）— 2026-08-22 完成（子代理，commit 640cf0b359）：20 修复 + 1 非问题（GenSqlHelper 排序 NPE 审计前提有误）；169 tests 绿、17 条红验证；超出审计新发现 4 项已记录
- [x] orm-periph.md（P1×7 P2×7 P3×8）— 2026-08-22 完成：22 条全部修复（含 4 条错误码体系建设：TdEngineErrors/OrmGeoErrors/PdmModelErrors 新建 + ERR_ORM_MODEL_JOIN_COLUMNS_NOT_MATCH_PK），六模块 40 tests 绿，红验证抽查 6 条（tdengine SQL 生成 3 + blocking source 3，修复前全挂、呈现形态与审计一致）；顺带同步 nop-cli-errors i18n 聚合（含前两次提交遗漏的 4 条新错误码）
- [x] nop-orm-eql.md（P1×3 P2×7 P3×4）— 2026-08-22 完成（子代理，commit 58e934a36f）：13 修复 + 1 非问题；45 tests 绿、18 条红验证；超出审计新发现 4 项（NOT-ILIKE 同族已修、极性/镜像方言/回显前缀 3 项裁定记录）

Exit Criteria:

- [x] 每份报告全部 P1/P2/P3 条目均有处置标注，四类终态之一，无遗漏（grep 验证：五报告 findings 计数均 ≤ 处置标注计数）
- [x] 修复项对应模块 `./mvnw test -pl <module>`（必要时 `-am`）通过（nop-dao 102 / db-migration 58 / nop-orm 169 / orm-periph 六模块 40 / nop-orm-eql 45 tests 绿）
- [x] 行为类修复附新回归测试；其余注明免测试理由（orm-periph 红验证抽查 6 条，其余报告见各自标注）
- [x] No owner-doc update required（修复均为缺陷收敛，不改变文档化契约语义；TDengine update 拒绝属"修复静默数据丢失"而非契约变更）

### Phase 2 - 内核与核心（kernel-small / nop-commons / nop-core / nop-core-framework / nop-utils / nop-xlang）

Status: completed
Targets: 对应 6 份报告；`nop-kernel`、`nop-core`、`nop-xlang`、`nop-commons`、相关模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] kernel-small.md（P1×2 P2×8 P3×6）— 2026-08-22 完成：13 修复 + 1 非问题（表格尾空单元格：探针实证与 GFM 一致、审计例子有误）+ 1 非问题附带加固（getErrorDetail 越界路径经 janino getMessage 不可达）+ 2 处局部不修复裁定（CLI stderr 惯例 / Guard 风格 IAE）；五模块 156 tests 绿（含 kernel-cli 回归），红验证抽查 9 条（markdown 6 + dataset 4 + record-mapping 3 中 9 失败形态与审计一致）；超审计新发现 1 项（changeLinkUrl/addImageSummarization 只排 posList 不排并行列表的配对缺陷）已修复
- [x] nop-commons.md（P1×6 P2×8 P3×6）— 2026-08-22 完成：17 条修复（Lazy.loaded 标志/LocalFileLock 恒 false 返回+异常路径句柄清理/LocalResourceLockManager 租约条件反转+ResourceLock 空锁 NPE/writeTextWithLock 字节截断/findLocalIp 懒初始化/LocalCache.putIfAbsent 原子化/RateLimitExecutor throttle 失效+map 泄漏/RoundRobinSupplier 扩容失败泄漏/DefaultRateLimiter 失败计数/SequentialTaskExecutor 单位+volatile/GlobalCacheRegistry 先替换后抛/IoHelper BOM 短流 EOF/DateHelper formatter 回填/FileHelper 资源缺失 NPE/裸 RuntimeException×2/静态 volatile×4/MutableInt javadoc+HighWatermarkSemaphore 统计/StringTrie 空串）+ 2 暂缓（JavaSerializer ObjectInputFilter 安全基线决策/ExecutorHelper config 突变系 refreshConfig 承重约定）+ 1 子项非问题（MapCache async：FutureHelper.futureCall 实为同步执行，探针测试实证）；219 tests 绿（nop-commons）+ 回归 nop-core 235 / nop-xlang 551 绿；红验证 22 处失败形态与审计一致
- [x] nop-core.md（P1×3 P2×4 P3×10）— 2026-08-22 完成：17 条全部修复（P1: StringHelper.isCanonicalFilePath 拒绝裸 "."/".." 段根因修复【跨 nop-commons】/loadComponentModelByUrl 按接口文档解析 ?transform&sub 查询参数/resolveResourceInDir 改 isCanonicalFilePath 校验+NopException；P2: unregisterTypeConverter 改操作 registeredConverters+清 converterCache/getDisabled 判空 bootstrapConfig/FileResource.mkdirs 条件纠正/SqlLikeUtils 三异常工厂 NopException 化；P3: reloadObject 对齐 getObject 的 Null 占位语义/initLength HttpURLConnection disconnect+DEBUG 日志/AopCodeGenerator 生成 catch 改抛 NopException fail-fast/DVFS 字段 volatile+destroy 同步/ResourceDependencySet.lastModified volatile/readBytes 超 int 范围拒绝/clearErrorCodeMappings 补 subMappings.clear/DeltaResourceStore 租户无子路径 NopException/ClassModelLoader 记忆化 double-check/resolveRelativeResource NopException 化）；新增 CoreErrors 错误码 7 个（i18n zh-CN/en 按序同步）；nop-core 252 tests 绿 + 回归 nop-commons/nop-xlang/nop-dataset 绿；红验证 17 处失败形态与审计一致（P3-2/4/5 为资源释放/内存可见性加固，免测试并注明理由）
- [x] nop-core-framework.md（P1×4 P2×5 P3×6）— 2026-08-22 完成：11 修复 + 2 暂缓（P2-6 跨线程构造器环死锁【单线程路径已随 P1-3 消除】/P2-9 require-pinned-hash 严格模式【hash 优先级系设计 05 W7 显式裁定】）+ P3-15 暂缓 + P2-9 javadoc 如实化（部分修复）+ P3-10 部分复查非问题（ProducedBeanInstance.STATUS_* 已 final）。P1: unless-property 读对模型/通配 pattern 订阅注册对 map/构造器环 in-creation 检测抛 ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE/文件配置源刷新容错+vars 回写（超审计新发现：不回写致热更新读旧值+重复触发）；P2: loadPluginFromJar classloader 泄漏/插件僵尸容器三层修复（destroySelf 锁内 remove+doActivate 注册表校验 ERR_PLUGIN_INSTANCE_ALREADY_DESTROYED+manager reconcileLock）/SecurityHelper adapt；P3: BeanScopeImpl add/close 互斥+ISE→NopException/BeanDefinitionBuilder 错误码化（ERR_IOC_INVALID_PROP_VALUE_TYPE+i18n 同步）/volatile×5/final×4；新增回归测试 6 个类 18 用例（红验证 16 处失败形态与审计一致，P2-5/P3 免测试已注明理由）；nop-ioc 62/nop-config 30/nop-plugin-manager 166/nop-security 2 tests 绿 + 回归 nop-cli-core i18n 校验/nop-auth-service 393 绿
- [x] nop-utils.md（P1×7 P2×7 P3×7）— 2026-08-22 完成：21 条全部修复（含超审计新发现 6 处一并修复：GitRepositoryImpl.commit null email 必抛 IAE/RowWiseCollector 对 emptyMap put 崩溃/TableValidatorCompiler condition 包裹节点不解包 unknown-op/Myers change 逆序+hunk 合并重建丢改动/Trie._match 精确子树死路不回退祖先 tillEnd 通配/OsUserHelper.getGroup 读恒空的 error 流），红验证 17 处失败形态与审计一致（image rgb=0,0,0、cat stdin 挂起超时、C:\tools\bin→C:toolsbin、not-a-tree、Index -1 token manager 损坏等）；免红测试 4 条注明理由（Windows 平台路径/headless 环境约束/时序竞态/纯日志顺序调整）；8 子模块 167 tests 绿（router36/match3/diff70/shell18/git6/table-validator13/image1/java-parser20）+ 回归 nop-gateway 68/nop-ai-toolkit 202 绿；报告 21 条全部标注
- [x] nop-xlang.md（P1×1 P2×3 P3×10）— 2026-08-22 完成：10 修复（feature:off 误用 onAttr 求值/XDefMergeLoader 重复引用悬空 ref+吞异常补 WARN/XDslExtender yaml null def 抛 ERR_XDSL_NO_SCHEMA/DeltaMerger uniques 死分支删除/LazyCompiledFunction volatile×2+依赖收集异常守卫（缓存异常重抛子项复查非问题：Java 重抛不覆盖构造期堆栈）/TypeDefinitionParser 裸异常转 NopEvalException×2 新错误码/ObjMetaRefResolver source 笔误/validateUnion null 值 NPE/DslModelHelper Excel 裸 IAE 转错误码+warn 带 e/XPathHelper 编译缓存对齐 JPath）+ 2 暂缓（defTypeCache loc 漂移需统一 loc 重绑定设计/唯一键空键语义需契约方向裁定+xdef 契约文档同步）+ 1 不修复（SimpleExprHelper loc=null 属缓存设计取舍，无排障阻塞案例）；559 tests 绿（+1 新增）+ nop-core 回归绿；红验证 6 条（P1 两条分别呈现审计推演的两种错误形态、P2-1 第二次引用得悬空名 MergeRefTarget1、P2-3 NPE 而非 NopException、validateUnion NPE、xpath 无缓存两实例）；附带修复既有 testVirtualNode 环境敏感失败（变量名 test 与 maven -Dtest 系统属性冲突，stash 对照证实与 P1 修复无关）；i18n 聚合同步 3 条新错误码

Exit Criteria:

- [x] 同 Phase 1 三条（六报告 103 条全部标注：kernel-small 16/nop-commons 20/nop-core 17/nop-core-framework 15/nop-utils 21/nop-xlang 14，grep 对账 findings=annotated；各模块测试数见上方条目摘要）
- [x] `ai-dev/logs/` 对应日期条目已更新（六单元各一节，见 08-22.md）

### Phase 3 - 服务框架与业务骨架（nop-api-core / nop-biz / nop-graphql / nop-auth / nop-sys / nop-job / nop-task / nop-wf）

Status: in progress
Targets: 对应 8 份报告；`nop-api-core`、`nop-biz`、`nop-graphql`、`nop-auth`、`nop-sys`、`nop-job`、`nop-task`、`nop-wf` 模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-api-core.md（P1×5 P2×11 P3×3）— 2026-08-22 完成：16 修复 + 2 部分修复暂缓（P2-6 registerInstance API 重设计 / P2-8 executeBlocking worker 池语义）。P1: ApiRequest.cloneInstance properties 笔误 / OrderFieldBean 克隆取反 / monthDayToString day 段误用 monthValue / localDateTimeToMillis DST 对称化 / ContextTaskQueue flush 退出窗口竞态（endProcessIfIdle 锁内原子判定）。P2: ErrorBean forPublic / ApiResponse bizFatal+tryResponse / rightJoin 常量 / dimFields 死分支归一化 / FilterBeans.or 空析取恒假+null 容忍（含 or(List) 同族）/ thenOnContext0 异常完成 promise / toFalsy NaN / flattenFields 补 add+点分前缀 / SysConverterRegistry ConcurrentHashMap。P3: 静态字段 volatile×5+final×5 / IntRangeSet 空集防护 / ApiMessage headers volatile+DCL+setHeader 串行化（保留 TreeMap null 值语义，实证 HEADER_BIZ_FAIL null 依赖）。新增 8 测试类 + 3 既有类扩展，17 处红验证失败形态与审计一致（P1-5 竞态经内部锁冻结确定性复现；headers 并发首写 round=0 即丢写）；80 tests 绿 + 回归 nop-commons 220 / nop-core 252 / nop-xlang 559 / nop-job-core 39 绿；超审计新发现 3 项（setHeader/addHeadersIfAbsent 第二条非同步懒初始化路径已修、or(List) 同族恒真已修、null header 值依赖实证排除 ConcurrentMap 方案）
- [ ] nop-biz.md（P1×2 P2×6 P3×7）
- [ ] nop-graphql.md（P1×3 P2×5 P3×7）
- [ ] nop-auth.md（P1×1 P2×3 P3×9）
- [ ] nop-sys.md（P1×4 P2×8 P3×7）
- [ ] nop-job.md（P1×3 P2×4 P3×9）
- [ ] nop-task.md（P1×5 P2×4 P3×2）
- [ ] nop-wf.md（P1×5 P2×8 P3×4）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 可复用业务（nop-batch / nop-dyn / nop-excel / nop-report / nop-rule / nop-metadata / nop-search / nop-datav / nosql-cdc）

Status: planned
Targets: 对应 9 份报告；相关模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] nop-batch.md（P1×5 P2×7 P3×6）
- [ ] nop-dyn.md（P1×3 P2×9 P3×3）
- [ ] nop-excel.md（P1×3 P2×2 P3×7）
- [ ] nop-report.md（P1×6 P2×7 P3×11）
- [ ] nop-rule.md（P1×1 P2×4 P3×3）
- [ ] nop-metadata.md（P1×1 P2×5 P3×3）
- [ ] nop-search.md（P1×5 P2×8 P3×5）
- [ ] nop-datav.md（P1×1 P2×4 P3×2）
- [ ] nosql-cdc.md（P1×1 P2×8 P3×10）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - AI 子系统（ai-core-api / ai-rest / ai-toolkit-skills / nop-ai-agent）

Status: planned
Targets: 对应 4 份报告；`nop-ai` 相关模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] ai-core-api.md（P1×3 P2×10 P3×8）
- [ ] ai-rest.md（P1×7 P2×6 P3×6）
- [ ] ai-toolkit-skills.md（P1×6 P2×7 P3×8）
- [ ] nop-ai-agent.md（P1×4 P2×5 P3×2）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 流处理（stream-core / stream-cep / stream-runtime / stream-flow-conn）

Status: planned
Targets: 对应 4 份报告；`nop-stream` 相关模块代码与测试；`ai-dev/tools/check-nop-stream-invariants.mjs` 等门禁

- Item Types: `Fix | Decision | Proof`

- [ ] stream-core.md（P1×4 P2×7 P3×3）
- [ ] stream-cep.md（P1×1 P2×2 P3×5）
- [ ] stream-runtime.md（P1×6 P2×5 P3×4）
- [ ] stream-flow-conn.md（P1×3 P2×4 P3×6）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（若发射点行号因修复漂移，按最小改动重钉并注明）

### Phase 7 - 集成外围（net-http-rpc / net-misc / msg-cluster-cred / gateway-bizauth / nop-integration / runner-cli / spring-quarkus）

Status: planned
Targets: 对应 7 份报告；相关模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] net-http-rpc.md（P1×4 P2×11 P3×5）
- [ ] net-misc.md（P1×7 P2×7 P3×7）
- [ ] msg-cluster-cred.md（P1×8 P2×7 P3×4）
- [ ] gateway-bizauth.md（P1×5 P2×9 P3×9）
- [ ] nop-integration.md（P1×4 P2×7 P3×5）
- [ ] runner-cli.md（P1×2 P2×7 P3×8）
- [ ] spring-quarkus.md（P1×1 P2×7 P3×8）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - 工具与示例（nop-autotest / nop-code / nop-graph / demo-migration / frontend-benchmark）+ 收口

Status: planned
Targets: 对应 5 份报告；`nop-autotest`、`nop-code`、`nop-graph`、demo 模块、前端基准；`SUMMARY.md`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] nop-autotest.md（P1×3 P2×6 P3×5）
- [ ] nop-code.md（P1×5 P2×6 P3×8）
- [ ] nop-graph.md（P1×4 P2×7 P3×10）
- [ ] demo-migration.md（P1×2 P2×4 P3×4）
- [ ] frontend-benchmark.md（P1×2 P2×8 P3×9）
- [ ] SUMMARY.md 增补本战役进展节（处置统计 + 四类终态分布）

Exit Criteria:

- [ ] 同 Phase 1 三条
- [ ] SUMMARY.md 进展节与各报告实际标注一致
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Closure Gates

- [ ] 48 份报告全部 P1/P2/P3 条目均有处置标注（grep `^### \[P[123]\]` 计数 = grep `处置（fix-ai-check` 相关计数覆盖，无未标注条目）
- [ ] 所有"已修复"终态条目对应模块测试绿
- [ ] 无 in-scope 条目处于未裁定状态
- [ ] `./mvnw test`（或逐模块 `-pl` 等价覆盖全部修改模块）通过
- [ ] check-doc-links 通过
- [ ] 独立子 agent closure audit 完成并写入证据

## Deferred But Adjudicated

（执行中按报告逐条填充：暂缓/不修复条目登记于此并链接报告标注）

## Non-Blocking Follow-ups

（执行中填充：超出条目范围的新发现）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）
