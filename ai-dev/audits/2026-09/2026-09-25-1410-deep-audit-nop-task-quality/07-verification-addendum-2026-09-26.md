# 复核附录（2026-09-26）— 第 1 轮初审逐条复核判定

- **方法**: 2026-09-26 由 8 个独立子代理对本目录 01~06 六份报告的全部 69 条发现逐条对 live code 重验证（重跑 grep、核对行号锚点、检查遗漏缓解代码、审查"很可能"条目推理链）。
- **总判定**: 66 CONFIRMED / 3 PARTIAL / 0 REFUTED。全部修正与锚点勘误由后续计划 `ai-dev/plans/364-nop-task-audit-confirmed-defect-fixes.md` 吸收。
- **PARTIAL 条目修正口径**:
  - [维度03-02]：往返计数是把父 composite 回写摊入子步的口径（叶子步骤实为 3 SELECT + 2 写）；"updateEntityDirectly 全列直写"不成立——ORM 按 `orm_dirtyPropIds()` 生成部分列 UPDATE（`JdbcEntityPersistDriver.java:282-290`）。
  - [维度06-01]：`TestTaskFlowAnalyzer.java:62-72,108-116` 已断言 `ARG_NEXT_STEP`（plan 255）；盲区收窄为 newError 5 个调用点参数无断言。
  - [维度06-06]：测试树并非零并发原语（`TestGraphDrainRace` CyclicBarrier+双线程、`TestTaskAuditFixes` CountDownLatch）；缺口收窄为三种交错场景无用例；owner 承诺锚点实为 `docs-for-ai/03-modules/nop-task.md:45`。
- **新增事实**（复核发现、报告未覆盖）：01-02 的 enter 循环与 exit 循环同样缺 `ARG_GRAPH_STEP_NAME` 绑定；01-12 实为零引用常量 10 个（VAR_REQUEST 在 nop-task 内亦零引用）；04-02 补静态证据 `XDslExtender.java:349` gen-extends 上下文 `setAllowUnknownTag(true)` → 更可能静默空模型 fail-open（精确失败模式仍 RUNTIME-REQUIRED）；04-02 坏标签 live 波及面实测为 12 个 app 配置 + 2 docs + 1 xgen 模板。

## 逐条判定表

| 发现 | 判定 | 复核要点 |
|---|---|---|
| 01-01 | CONFIRMED | newError 两重载方法体 throw（TaskStepHelper.java:58-67）；5 调用点 .param 不可达；缺参回退 `{name}` 渲染（ErrorMessageManager.java:144-153）；4 模板逐一核实 |
| 01-02 | CONFIRMED | exit 循环 :78-83 变量名复用且缺 graphStepName 绑定；**追加：enter 循环 :74 同病** |
| 01-03 | CONFIRMED | TaskImpl.java:303 ARG_STEP_PATH 绑 stepType 值；主源码其余 7 处正确对照（报告"30+"夸大，不影响） |
| 01-04 | CONFIRMED | :183 阈值 4000 vs REMARK VARCHAR(200)（DDL:55 + ORM:247 双源）；**修正：:456 是 step 表 ERR_MSG VARCHAR(4000)，:203 才是 500** |
| 01-05 | CONFIRMED | executeWithParentRt :178-396 共 219 行；307-311/379-383 注释逐字重复；:272 行号导航失效（报告"行 234"一处现不存在，不影响） |
| 01-06 | CONFIRMED | :110-111 不可达（TaskStepReturn final、nextStepName 无 setter）；**修正：两条注释彼此一致，是第二条与死代码现实矛盾** |
| 01-07 | CONFIRMED | 5 处失效行号逐条复现；plan NNN 164 处/31 文件双口径精确复现 |
| 01-08 | CONFIRMED | ITaskStepState:122 参数未被唯一实现读取；调用点恰 3 处；路由在 TaskStepExecution:349 |
| 01-09 | CONFIRMED | 守卫 6 处（215,237,268,288,324,345）；runStep 106 行 |
| 01-10 | CONFIRMED | 构造器 :84 removeAll 原地改写模型自有 set；全链无防御复制；缓存当前挡住二次构建 |
| 01-11 | CONFIRMED | 四方法 91/89/85/81 行独立核对精确 |
| 01-12 | CONFIRMED | 9 常量零引用成立；**追加：VAR_REQUEST 亦零引用（实为 10 个）；报告对照语不成立** |
| 01-13 | CONFIRMED | getStepNames/needSave/NopTaskErrors/ITaskQueue 零调用复现 |
| 01-14 | CONFIRMED | 三 driver 4 行同构 + 三收集循环逐字相同 |
| 01-15 | CONFIRMED | helper 行号逐一吻合；readBoolean 已分叉（Boolean vs boolean+default） |
| 01-16 | CONFIRMED | TaskStepEnhancer:88-98 注释方法 + ExecutorTaskStepWrapper:19,34 注释日志在档 |
| 01-17 | CONFIRMED | requirePersistStateState 拼写、裸布尔调用点（含 CliRunTaskCommand:90）、双布尔四分支 |
| 01-18 | CONFIRMED | endStep 形参语义反命名；3-4 条日志缺 task 上下文 |
| 01-19 | CONFIRMED | ID should-no-be-async；i18n en:996 / zh-CN:1120 命中 |
| 02-01 | CONFIRMED | 共享单例 + initAbstractStep 覆写 + 运行期读取点（TaskStepExecution:185,427,441）三方闭环；无 cloneInstance |
| 02-02 | CONFIRMED | async 出口 nextOnError 先于 addXplStack（:319-322）、sync 出口相反（:376-393），漂移实锤 |
| 02-03 | CONFIRMED | 同值双名（30/50）；TestTaskConstantsAlignment 仅存在于注释；DaoTaskStateStore 混用点核实 |
| 02-04 | CONFIRMED | newMainStepState/newStepState 两 store 逐字重复；注释自证漂移 |
| 02-05 | CONFIRMED | 17 行 exception 持久化块 task/step 级重复；交叉注释在位（±1 行） |
| 02-06 | CONFIRMED | first-class 静默跳过 vs decorator 抛错；**修正：decorator 校验 maxRetryDelay>=0 而非 >=retryDelay** |
| 02-07 | CONFIRMED | 唯一实例化点 TaskStepBuilder:81 硬编码；工厂先例 TaskFlowManagerImpl:230 |
| 02-08 | CONFIRMED | throttle 仅 first-class；decorator bean 恰 5 个 |
| 02-09 | CONFIRMED | invalidConfig 三份逐字相同；readInt 两份；readBoolean 已漂移 |
| 02-10 | CONFIRMED | addStepCleanup 0 生产调用方；getException main 无读取方；锁不对称（:171 vs :180） |
| 02-11 | CONFIRMED | succeed 守卫/fail 裸 exception；接口无 javadoc；5 调用点全列 |
| 02-12 | CONFIRMED | check-then-act 非原子；字段非 volatile；task 级 setTaskStatus 无守卫 |
| 02-13 | CONFIRMED | 构造器 removeAll 入参即模型字段；serializeToMap/copyTo 均含该集合 |
| 02-14 | CONFIRMED | beginStep 两参数被丢弃；createCounter 0 调用方 |
| 02-15 | CONFIRMED | 3 处 {@link DaoTaskStateStore} 反向依赖；core pom 无 dao |
| 02-16 | CONFIRMED | bean 零消费方（命中均为定义/反射登记） |
| 02-17 | CONFIRMED | ITaskQueue 无实现无调用；pom 无 dependencies；reactor 在列 |
| 02-18 | CONFIRMED | global 告警 :176-179 vs 非 global 无告警 :182-184；调用链唯一 |
| 03-01 | CONFIRMED | 源模型/三方言 DDL 均无二级索引；nop-wf 有 `<indexes>` 对照 |
| 03-02 | PARTIAL | 往返结构成立但计数为摊派口径（叶步 3S+2W）；"全列直写"被证伪（dirty-prop 部分列更新） |
| 03-03 | CONFIRMED | 每迭代全量序列化（含 items）+ >4000 二次序列化；O(K²) 推导成立 |
| 03-04 | CONFIRMED | Map 分支转换不写回（:210-213）；resume 后异步循环每迭代重转换 |
| 03-05 | CONFIRMED | getSaveState 全仓仅生成物 + 只写不读；xdef:4/127/138 承诺在档 |
| 03-06 | CONFIRMED | parallel/fork/graph 内联串行；唯一离场通道 executor；owner 文档无说明 |
| 03-07 | CONFIRMED | waitUntil 100ms 轮询 vs DelayTaskStep 调度式；阻塞本身 xdef 明示不报 |
| 03-08 | CONFIRMED | 正/负缓存不对称；仅 2 个 reflective FQCN 走此路径（风险表述略夸大，发现本体成立） |
| 04-01 | CONFIRMED | 4 裸 BizModel + 0 处 updatable=false + ICrudApi 全暴露；无字段级校验 |
| 04-02 | CONFIRMED | 改名 commit 8438c69a2f 实证；xlib 无 GenFromModules；12 个 app 配置带坏标签；fail-open 在档；**补证：XDslExtender:349 allowUnknownTag=true → 更可能静默空模型；精确失败模式 RUNTIME-REQUIRED** |
| 04-03 | CONFIRMED | 引擎/service 无 definitionAuth 消费；getTask 无鉴权（:132-135） |
| 04-04 | CONFIRMED | 诊断三列写入链 + ICrudApi 全字段返回；xbiz 无裁剪 |
| 04-05 | CONFIRMED | 四实体 0 租户列；nop-auth 有列有规则对照 |
| 04-06 | CONFIRMED | cacheKey = taskName+":"+key（:172-173,:190）无租户维度 |
| 05-01 | CONFIRMED | succeed 有守卫/fail 无；task 级三步非原子；**锚点修正：文档承诺在 nop-task.md:45** |
| 05-02 | CONFIRMED | 读-改-写无重试；version 条件 + checkUpdateResult 抛错链在档；owner 边界只覆盖跨进程 resume |
| 05-03 | CONFIRMED | cancel 无 driver；SUSPENDED 后两出口关闭；服务层无兜底；挂起 kill 无测试 |
| 05-04 | CONFIRMED | Caffeine maximumSize(10000)；驱逐不销毁 → permit 池分裂机制成立 |
| 05-05 | CONFIRMED | fail/increment 内存、saveState 才落盘（:301-311）；retryAttempt 持久化链在档 |
| 05-06 | CONFIRMED | 仅 cancelToken volatile；其余字段无同步；锁不对称在档 |
| 05-07 | CONFIRMED | 分离读 + 调序修复注释 :330-333；TestGraphDrainRace 275 行无错误×成功组合用例 |
| 06-01 | PARTIAL | 宽口径被推翻（TestTaskFlowAnalyzer 已断言 ARG_NEXT_STEP）；窄口径成立：5 调用点参数无断言 |
| 06-02 | CONFIRMED | nop-task 测试树 data-auth 0 命中；app 无测试树 |
| 06-03 | CONFIRMED | testBuild 零断言逐行确认 |
| 06-04 | CONFIRMED | 矛盾注释在档（:93-95 vs :126；行号漂移、内容属实） |
| 06-05 | CONFIRMED | 首调无 fail()（:466-472）、次调有（:479） |
| 06-06 | PARTIAL | 三种交错无用例成立；"并发原语 0 命中"被推翻；锚点实为 :45 |
| 06-07 | CONFIRMED | 7 助手全实存（Test* 类 26 个 vs 报告 22，不影响） |
| 06-08 | CONFIRMED | dao 0 测试树、service 1 用例、包名越界确认 |
| 06-09 | CONFIRMED | 4 个非测试类 @Test=0 确认 |
| 06-10 | CONFIRMED | sleep 制造时序 + 2000ms/200ms 墙钟在档 |
| 06-11 | CONFIRMED | catch Exception + assertNotNull 宽捕获确认 |
