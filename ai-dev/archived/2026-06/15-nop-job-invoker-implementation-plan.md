# 15-nop-job-invoker-implementation-plan.md

> Plan Status: deferred
> Deferred Reason: nop-job 模块已由 Plans 14-21 全面重写。Invoker 实现作为独立工作延后
> Created: 2026-05-17
> Design Doc: `ai-dev/design/nop-job/invoker-design.md` (v3)

## Goals

1. 修正 `DefaultJobInvokerResolver`：prefix `jobInvoker_` → `nopJobInvoker_`，移除直接 bean 查找，找不到时抛异常
2. 实现 `RpcJobInvoker`：注入 `IRpcServiceInvoker`，从 `jobParams` 解析 serviceName/serviceMethod/headers/data
3. 新增 `IJobTaskBuilder` 接口 + `DefaultJobTaskBuilder` 默认实现 + Dispatcher 集成
4. 注册 beans：`nopJobInvoker_test`、`nopJobInvoker_rpc`
5. 所有变更通过单元测试验证
6. 回退之前错误的 `nop-job-invokers` 模块

## Non-Goals

- 不实现 `RpcBroadcastTaskBuilder`（广播为 V2，当前只设计接口）
- 不修改 ORM 模型
- 不修改前端页面

## Current Baseline

- `DefaultJobInvokerResolver` 使用 `jobInvoker_` prefix，返回 null 表示找不到
- `nopE2eTestInvoker` 注册在 `app-service.beans.xml`（不符合 nopJobInvoker_ 约定）
- 存在错误的 `nop-job-invokers` 模块（未提交，需删除）
- `SpecificServiceInstanceFilter` 已改为从 `nop-svc-target-host` header 读取（已提交？未提交）
- `ApiConstants.HEADER_SVC_TARGET_HOST` 已添加，`PROP_TARGET_HOST` 已删除
- `JobCoreErrors` 定义了 `ERR_JOB_INVOKER_NOT_FOUND`（用于 task 完成时的 errorCode 记录）
- `IJobFireStore.insertTaskAndMarkFireDispatching(fire, task)` 只支持单个 task
- `JobDispatcherScannerImpl.buildTask()` hardcoded `taskNo=1`

---

## Execution Slice 1: Cleanup & Resolver Fix

**Status**: pending

### Phase 1.1: 删除 nop-job-invokers 模块，恢复 pom 文件

**Exit Criteria**:
- [ ] `nop-job/nop-job-invokers/` 目录已删除
- [ ] `nop-job/pom.xml` 恢复（移除 nop-job-invokers module）
- [ ] `nop-job/nop-job-app/pom.xml` 恢复（移除 nop-job-invokers 依赖）

**Files**:
- 删除: `nop-job/nop-job-invokers/` (整个目录)
- 修改: `nop-job/pom.xml`
- 修改: `nop-job/nop-job-app/pom.xml`

### Phase 1.2: 修正 DefaultJobInvokerResolver

**Exit Criteria**:
- [ ] prefix 改为 `nopJobInvoker_`
- [ ] 移除直接用 executorRef 作为 bean name 的查找
- [ ] executorRef 为空或 blank 时抛异常
- [ ] 找不到 bean 时抛异常
- [ ] 新增错误码 `ERR_JOB_EXECUTOR_REF_EMPTY`

**Files**:
- 修改: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/DefaultJobInvokerResolver.java`
- 修改: `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java`

**QA**: 单元测试 `TestDefaultJobInvokerResolver`
- `nopJobInvoker_test` bean → 找到 invoker
- 空白 executorRef → 抛异常
- 不存在的 executorRef → 抛异常
- 从 executorSnapshot fallback 到 schedule.getExecutorRef()

### Phase 1.3: 重命名 nopE2eTestInvoker → nopJobInvoker_test

**Exit Criteria**:
- [ ] bean id 改为 `nopJobInvoker_test`
- [ ] Java 类保持 `NopE2eTestJobInvoker` 不变（只改 beans.xml）

**Files**:
- 修改: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/beans/app-service.beans.xml`

---

## Execution Slice 2: RpcJobInvoker

**Status**: pending

### Phase 2.1: 添加 nop-rpc-cluster 依赖

**Exit Criteria**:
- [ ] `nop-job/nop-job-service/pom.xml` 添加 `nop-rpc-cluster` 依赖

**Files**:
- 修改: `nop-job/nop-job-service/pom.xml`

### Phase 2.2: 实现 RpcJobInvoker

**Exit Criteria**:
- [ ] 注入 `IRpcServiceInvoker`
- [ ] invokeAsync: 从 jobParams 取 serviceName（必填）、serviceMethod（默认 invokeJob）
- [ ] invokeAsync: 从 jobParams.headers 设置 ApiRequest headers
- [ ] invokeAsync: 从 jobParams.data 设置请求体（不指定则自动构建 jobName/jobGroup/execCount）
- [ ] invokeAsync: 返回值转换 ApiResponse → JobFireResult
- [ ] cancelAsync: 调用 cancelJob 方法
- [ ] 必填参数缺失时抛异常（新增 `ERR_RPC_INVOKER_MISSING_PARAM`）

**Files**:
- 新增: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java`
- 修改: `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobErrors.java` (新增错误码)

**QA**: 单元测试 `TestRpcJobInvoker`（不需要 IoC 容器，直接 mock IRpcServiceInvoker）
- 成功响应 → CONTINUE
- 错误响应 → ERROR（含 status/code/msg）
- cancel 成功 → true
- cancel 失败 → false
- 缺少 serviceName → 抛异常
- 自定义 headers 传递
- 自定义 data 传递

### Phase 2.3: 注册 beans

**Exit Criteria**:
- [ ] `nopJobInvoker_rpc` 注册在 beans.xml

**Files**:
- 修改: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/beans/app-service.beans.xml`

---

## Execution Slice 3: IJobTaskBuilder + Dispatcher 集成

**Status**: pending

### Phase 3.1: 定义 IJobTaskBuilder 接口

**Exit Criteria**:
- [ ] 接口定义在 nop-job-coordinator 中（Dispatcher 所在模块）
- [ ] 方法签名: `List<NopJobTask> buildTasks(NopJobFire fire)`

**Files**:
- 新增: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/IJobTaskBuilder.java`

### Phase 3.2: 实现 DefaultJobTaskBuilder

**Exit Criteria**:
- [ ] 将 `JobDispatcherScannerImpl.buildTask()` 逻辑移入
- [ ] 注册为 bean `nopJobTaskBuilder_default`（ioc:default=true）

**Files**:
- 新增: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java`
- 修改: nop-job/nop-job-coordinator/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml

### Phase 3.3: 扩展 IJobFireStore 支持批量 task 插入

**Exit Criteria**:
- [ ] IJobFireStore 新增 `insertTasksAndMarkFireDispatching(fire, List<NopJobTask>)`
- [ ] JobFireStoreImpl 实现：循环 saveEntityDirectly，然后 mark fire RUNNING

**Files**:
- 修改: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobFireStore.java`
- 修改: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

### Phase 3.4: 修改 Dispatcher 使用 IJobTaskBuilder

**Exit Criteria**:
- [ ] 注入 defaultTaskBuilder
- [ ] resolveTaskBuilder: 查找 `nopJobTaskBuilder_{executorRef}`，找不到 fallback default
- [ ] scanOnce: 调用 builder.buildTasks(fire) → insertTasksAndMarkFireDispatching

**Files**:
- 修改: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`

**QA**: 单元测试 `TestDefaultJobTaskBuilder`
- 默认构建返回 1 个 task
- task 的 taskNo=1，status=WAITING，payload 包含 executorSnapshot

---

## Execution Slice 4: 编译验证 + 全量测试

**Status**: pending

**Exit Criteria**:
- [ ] `./mvnw compile -pl nop-job -am -DskipTests` 通过
- [ ] `./mvnw test -pl nop-job/nop-job-worker,nop-job/nop-job-service,nop-job/nop-job-coordinator` 通过
- [ ] SpecificServiceInstanceFilter 测试通过

---

## Closure Gates

- [ ] 所有 Phase 的 Exit Criteria 已满足
- [ ] 无新增 `as any`、`@ts-ignore` 等类型安全违规（Java 项目：无 raw type、unchecked warning）
- [ ] 新增代码遵循 Nop bean 命名约定（nopJobInvoker_、nopJobTaskBuilder_ prefix）
- [ ] 设计文档 `invoker-design.md` 标记为 confirmed

## Deferred Note

nop-job 模块已由 Plans 14-21 全面重写。Invoker 实现作为 Plan 16（核心调度器重构）的一部分或独立于当前活跃工作。本计划保留为 deferred。
