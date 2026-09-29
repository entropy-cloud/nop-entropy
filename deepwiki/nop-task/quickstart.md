# 快速上手

> 本页源文件基准（相对于 `deepwiki/nop-task/`，链接先退到 wiki 根再按仓库相对路径解析，写法与 [modules/task-service-dao.md](./modules/task-service-dao.md) 一致）：
>
> - [../nop-task/pom.xml](../../nop-task/pom.xml)
> - [../nop-task/nop-task-core/pom.xml](../../nop-task/nop-task-core/pom.xml)
> - [../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java)
> - [../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java)
> - [../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java)
> - [../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)
> - [../nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java)
> - [../nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml](../../nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml)
> - [../nop-task/nop-task-core/src/test/java/io/nop/task/impl/AbstractTaskTestCase.java](../../nop-task/nop-task-core/src/test/java/io/nop/task/impl/AbstractTaskTestCase.java)

本页覆盖 nop-task 的构建入口、测试资产清单和最小可运行示例。引擎分层与执行语义见[总览](./overview.md)与[模块：task-core](./modules/task-core.md)。

## 1. 构建：模块坐标与依赖

聚合模块坐标为 `io.github.entropy-cloud:nop-task:2.0.0-SNAPSHOT`，packaging 为 pom，下挂 10 个子模块（`nop-task/pom.xml:5-9,13-17,19-30`）：nop-task-core、nop-task-dao、nop-task-web、nop-task-queue、nop-task-codegen、nop-task-api、nop-task-meta、nop-task-service、nop-task-app、nop-task-ext。

引擎本体只有 nop-task-core 一个模块，依赖仅三项（`nop-task/nop-task-core/pom.xml:16-30`）：

| 依赖 | scope | 用途 |
|---|---|---|
| `nop-xlang` | compile | XPL 模板执行、DSL 模型解析 |
| `nop-ioc` | compile | bean 装配（`nopTaskFlowManager` 等） |
| `junit-jupiter` | test | 测试 |

常用构建命令：

```bash
./mvnw test -pl nop-task/nop-task-core          # 只跑 core 测试（依赖需已 install 到本地仓库）
./mvnw test -pl nop-task/nop-task-core -am      # 连带构建上游依赖（首次或上游有改动时）
./mvnw install -pl nop-task -am                 # 安装整个 nop-task 聚合模块
```

core 的 build 段还注册了 exec-maven-plugin（`nop-task/nop-task-core/pom.xml:33-40`），供 xlib 代码生成使用，普通开发不需要直接调用。

## 2. 测试：类清单与数量

以下清单核对自当前工作区（快照日期 2026-09-26）。注意：并行会话正在执行 plan 364 向 nop-task 增补测试，计数会随后续提交变动；同名提示中以工作区实际类名为准——`TestReliabilityDecorators` 在 nop-task-ext 而非 core，core 中 graph 相关测试类是 `TestGraphDrainRace`/`TestGraphErrorSuccessRace`/`TestGraphOnErrorEdge`/`TestGraphStepNodeDefensiveCopy`。

### nop-task-core：36 个测试类，163 个 @Test

| 包 | 测试类（@Test 数） |
|---|---|
| impl（11 类，79） | TestTaskFlowManager(25)、TestPlan349Fixes(19)、TestSuspendContract(8)、TestTaskAuditFixes(6)、TestPhase4TerminalAndConcurrency(5)、TestTaskErrorParamDiagnostics(5)、TestFirstClassConfigValidation(4)、TestRepeatedStepPathExecution(3)、TestTaskFlowManagerGlobalStats(2)、TestTaskMetricsGuard(1)、TestChildRuntimeCancelPropagation(1) |
| step（12 类，32） | TestTerminalStatePersistence(6)、TestTaskStepCancelledTerminalDriver(5)、TestTaskStepContinuationSkipReader(5)、TestSuspendSemantics(4)、TestAsyncSuspendPropagation(3)、TestExecutorTaskStepWrapperAsyncBranch(2)、TestGraphOnErrorEdge(2)、TestGraphDrainRace(1)、TestGraphErrorSuccessRace(1)、TestGraphStepNodeDefensiveCopy(1)、TestParallelAggregationPlaceholder(1)、TestSimpleBeanStepIsolation(1) |
| state（5 类，29） | TestTaskStepStateBeanLifecycle(9)、TestTaskKilledTimeoutResumeE2E(7)、TestTaskStepStateBeanExceptionPersistence(6)、TestTaskTerminalStateLifecycle(6)、TestTaskStepStateBeanGetStateBeanWriteBack(1) |
| utils（5 类，16） | TestTaskStepHelperIsCancelledException(9)、TestTaskStepHelperRetryAttemptPersistOrder(1)、TestTaskStepHelperRetryDelayExecutionCount(2)、TestTaskStepHelperRetrySuspend(2)、TestTaskStepHelperRetrySyncReturn(2) |
| builder（2 类，6） | TestTaskFlowAnalyzer(5)、TestTaskFlowAnalyzerIfRecursion(1) |
| reflect（1 类，1） | TestReflectionTaskStepBuilder(1) |

core 测试目录中另有 6 个 0 @Test 的支撑类，不计入测试类：`AbstractTaskTestCase`、`FakeStepRt`、`AbstractGraphTestCase`（均在 impl/step 包）以及 state 包三个内存 state store 夹具 `FullSnapshotTaskStateStore`/`SnapshotTaskStateStore`/`TaskLevelSnapshotTaskStateStore`。

### 其他子模块

| 模块 | 测试类（@Test 数） | 说明 |
|---|---|---|
| nop-task-ext | 21 类，124 @Test | 最大类为 TestReliabilityDecorators(37)；DaoTaskStateStore 往返序列化 8 类（RoundTrip 3、FullFieldRoundTrip 7、ErrorBeanRoundTrip 5、ErrorStackRoundTrip 5、ExceptionSubclassAndCauseStackRoundTrip 9、StateDataWrapperRoundTrip 4、RemarkBoundary 4、ConcurrentSameRow 1）；跨重启恢复 8 类（TestMainStepEnvelopeResume 9、TestTaskLevelResume 4、TestDbBackedTerminalStateResume 3、TestMainStepEnvelopeResumeCrossRestart 4、TestTaskStateLifecycleHooks 4、TestTaskStateLifecycleHookCrossRestart 3、TestStepContinuationSkipResume 2、TestStepSaveStateConsumption 1）；另有 TestTaskExceptionRegistry(11)、TestTaskFlowDemo(6)、TestTransactionDecorator(1)、TestTaskOrmModelIndex(1)。8 个 0 @Test 夹具 bean 不计入 |
| nop-task-app | 2 类，7 @Test | TestTaskAppCrudWriteProtection(5)、TestTaskAppDataAuthConfig(2) |
| nop-task-service | 1 类，1 @Test | TestTaskFlow(1)；MyDemoHandler 为夹具 |
| nop-task-web | 1 类，1 @Test | NopTaskWebPagesTest(1)；NopTaskWebCodeGen 是代码生成入口，非测试 |
| nop-task-codegen | 0 类 | NopTaskCodeGen 为代码生成入口（0 @Test） |

全模块合计：296 个 @Test。运行方式：

```bash
./mvnw test -pl nop-task/nop-task-ext            # 状态落库与可靠性测试
./mvnw test -pl nop-task/nop-task-ext -Dtest=TestReliabilityDecorators   # 单类
```

## 3. 最小用法示例

### 3.1 用 task.xdef 定义两步骤任务

任务模型放在虚拟文件系统 `_vfs/nop/task/<任务名>/v<版本>.task.xml` 下（测试样例位置见 `nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/xpl-01/v1.task.xml`）。根节点 `x:schema` 指向 `/nop/schema/task/task.xdef`（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:9-16`）。推荐用 `step` 节点承载 XPL 源码（`task.xdef:155-158`）；`xpl` 节点同构但已标记 Deprecated（`task.xdef:160-164`）。步骤缺省顺序执行：未指定 `next` 时跳到下一个兄弟节点（`task.xdef:126`）。

```xml
<task x:schema="/nop/schema/task/task.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <!-- 步骤一：产生输出变量 RESULT -->
        <step name="init">
            <source>
                return 10
            </source>
            <output name="RESULT"/>
        </step>

        <!-- 步骤二：读取上一步输出（缺省顺序跳转） -->
        <step name="check">
            <input name="prev">
                <source>
                    STEP_RESULTS.init.outputs.RESULT
                </source>
            </input>
            <source>
                return prev == 10 ? 'OK' : 'FAIL'
            </source>
        </step>
    </steps>

    <!-- 任务级输出：最终结果写入 RESULT 变量 -->
    <output name="RESULT">
        <source>
            return STEP_RESULTS.check.outputs.RESULT
        </source>
    </output>
</task>
```

`<input>`/`<output>` 是每个步骤的输入输出声明，`input` 缺省 `persist=true`、`output` 缺省 `persist=false`（`task.xdef:51-53,65-67`）。除 `step` 外，task.xdef 还定义了约 25 种步骤节点：simple（引用 bean 中的 ITaskStep，`:150`）、script（`:167-170`）、graph（`:175-176`）、sequential（`:181`）、selector（`:184`）、parallel（`:187-191`）、invoke/invoke-static（`:236-241`）、loop/loop-n（`:246-257`）、choose/if（`:262-278`）、fork/fork-n（`:218-231`）、call-task/call-step（`:281-288`）、suspend（`:293-295`）、end/exit（`:196-203`）、delay/sleep（`:208-211`）。所有步骤共享的可执行装饰——retry、throttle、rate-limit、when、catch、finally、timeout——定义在 `TaskExecutableModel` 段（`task.xdef:30-121`）；`next`/`nextOnError`/`saveState`/`waitSteps`/`sync` 等通用属性在 `TaskStepModel` 段（`task.xdef:133-139`）。

### 3.2 编程式启动：ITaskFlowManager 三行调用

入口接口 `ITaskFlowManager` 的核心签名（`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java:18-34`）：`getTask(taskName, taskVersion)`、`newTaskRuntime(task, saveState, svcCtx[, scope])`、`loadTaskFromPath(path)`、`parseTask(resource)`。实现类 `TaskFlowManagerImpl` 经 `getTaskFlowModel` 把 `resolve-task:<name>` 解析为 `_vfs` 内的模型文件（`TaskFlowManagerImpl.java:143-147`；路径拼接见 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/version/ResourceVersionHelper.java:104-117`，版本传 0 表示取最新，落为 `v1.task.xml`）。

最小启动代码与 core 测试基类 `AbstractTaskTestCase.runTask`（`:33-43`）同构：

```java
// 容器内取 id=nopTaskFlowManager 的 bean；独立进程也可直接 new TaskFlowManagerImpl()
ITaskFlowManager taskFlowManager = new TaskFlowManagerImpl();

ITask task = taskFlowManager.getTask("demo-two-step", 0);        // 版本 0 = 最新
ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null); // saveState=false：纯内存执行
Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();     // 同步等待跑完
Object result = outputs.get(TaskConstants.VAR_RESULT);           // 即上面任务级 <output> 的 RESULT
```

`ITask.execute(ITaskRuntime)` 定义在 `nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java:29-33`；`TaskStepReturn.syncGetOutputs()` 在 `TaskStepReturn.java:173-177`（返回 null 时表示无输出）。整条执行链建立在 `TaskStepReturn` 之上：同步场景用静态工厂 `RETURN_RESULT`/`RETURN(outputs)`/`RETURN(nextStepName, outputs)`（`TaskStepReturn.java:48-65`），终态用 `RETURN_RESULT_END` 与 `EXIT`（`:39-46`），异步用 `ASYNC`/`ASYNC_RETURN`（`:67-80`），哨兵值 `CONTINUE`/`SUSPEND`（`:36-37`）。自定义步骤只需实现 `ITaskStep.execute(ITaskStepRuntime)` 返回 `TaskStepReturn`（`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:49-50`），经 `<simple name="..." bean="..."/>` 挂入任务（`task.xdef:150`）。

### 3.3 外部应用启用状态落库：注入 taskStateStore

引擎的持久化契约是 `ITaskStateStore`（8 个状态方法 + `isSupportPersist()`，实现为 dao 模块的 `DaoTaskStateStore`）。以 `saveState=true` 调用 `newTaskRuntime` 时，`TaskFlowManagerImpl` 走 `requirePersistStateState()`：字段 `taskStateStore` 为 null 则抛 `ERR_TASK_NO_PERSIST_STATE_STORE`（`TaskFlowManagerImpl.java:55-57,91-98,114-118`；setter 带 `@Inject @Nullable`，`:91-94`）。不落库的调用走缺省内存实现 `DefaultTaskStateStore.INSTANCE`（`:57,98`）。

关键结论（与 [modules/task-service-dao.md](./modules/task-service-dao.md) 的核实一致，本页已重新验证）：`nopTaskFlowManager` bean 定义在引擎自带配置中且不含任何 store 属性（`nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml:5-6`）；全 nop-task 主树（`src/main` 的 java 与 resources）grep `taskStateStore` 只命中 `TaskFlowManagerImpl.java` 一个文件——即主树没有默认装配 `DaoTaskStateStore`，落库必须由应用侧主动注入。外部应用的启用方式是覆盖该 bean 并注入 `taskStateStore` 属性（setter 名对应属性名）：

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">

    <!-- 应用自己的 ITaskStateStore 实现：nop-task-dao 提供 io.nop.task.dao.store.DaoTaskStateStore -->
    <bean id="daoTaskStateStore" class="io.nop.task.dao.store.DaoTaskStateStore"/>

    <!-- 覆盖引擎默认的 nopTaskFlowManager，注入持久化 store -->
    <bean id="nopTaskFlowManager" class="io.nop.task.impl.TaskFlowManagerImpl"
          ioc:allow-override="true">
        <property name="taskStateStore" ref="daoTaskStateStore"/>
    </bean>
</beans>
```

仓库内的装配示范是 ext 测试的同款覆盖写法——用 `ioc:allow-override="true"` 重定义 `nopTaskFlowManager` 并注入 store（那里注入的是 `nonPersistStateStore` 以便断言状态快照，`nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml:15-18`）。注入后，启动与恢复分别走 `newTaskRuntime(task, true, svcCtx)` 与 `getTaskRuntime(taskInstanceId, svcCtx)`（`TaskFlowManagerImpl.java:97-106,121-134`），挂起/恢复如何消费落库行见[阅读指南](./reading-guide.md)指向的状态页。

## Sources

- [nop-task/pom.xml:5-30](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/pom.xml#L5-L30)
- [nop-task/nop-task-core/pom.xml:16-40](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/pom.xml#L16-L40)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java:18-41](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java#L18-L41)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java:20-42](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java#L20-L42)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:22-51](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java#L22-L51)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:36-80](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java#L36-L80)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:173-177](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java#L173-L177)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:21](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java#L21)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:50-159](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java#L50-L159)
- [nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml:5-15](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/resources/_vfs/nop/task/beans/task-defaults.beans.xml#L5-L15)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:9-16](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef#L9-L16)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:126-139](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef#L126-L139)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:150-303](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef#L150-L303)
- [nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/version/ResourceVersionHelper.java:104-117](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/version/ResourceVersionHelper.java#L104-L117)
- [nop-task/nop-task-core/src/test/java/io/nop/task/impl/AbstractTaskTestCase.java:33-43](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/test/java/io/nop/task/impl/AbstractTaskTestCase.java#L33-L43)
- [nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/xpl-01/v1.task.xml:1-23](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/xpl-01/v1.task.xml#L1-L23)
- [nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/graph-01/v1.task.xml:1-64](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/graph-01/v1.task.xml#L1-L64)
- [nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml:15-18](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml#L15-L18)
- [nop-task/nop-task-core/src/test/java/io/nop/task/impl/TestTaskFlowManager.java:31-52](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/test/java/io/nop/task/impl/TestTaskFlowManager.java#L31-L52)

---

## On this page

- 1. 构建：模块坐标与依赖
- 2. 测试：类清单与数量
- 3. 最小用法示例
