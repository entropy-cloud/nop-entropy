# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-job-dao 编译期依赖 nop-job-core，违反标准分层规则 2

- **文件**: `nop-job/nop-job-dao/pom.xml:31-34`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-job-core</artifactId>
  </dependency>
  ```
  实际 import 证据（4 个手写源文件）：
  - `JobFireStoreImpl.java:12-14` → `import io.nop.job.core.ITriggerEvalContext; import io.nop.job.core._NopJobCoreConstants; import io.nop.job.core.trigger.JobTriggerCalculator;`
  - `JobScheduleStoreImpl.java:11` → `import io.nop.job.core._NopJobCoreConstants;`
  - `JobTaskStoreImpl.java:11` → `import io.nop.job.core._NopJobCoreConstants;`
  - `TriggerSpecHelper.java:7-8` → `import io.nop.job.core.ITriggerEvalContext; import io.nop.job.core._NopJobCoreConstants;`

- **严重程度**: P2
- **现状**: nop-job-dao 编译期依赖 nop-job-core，使用了 `_NopJobCoreConstants`（领域常量）、`ITriggerEvalContext`（纯接口）、`JobTriggerCalculator`（触发器计算）、`JobCoreErrors`（错误码）。违反规则 2 "dao 层只依赖 api 和 nop-persistence 框架"。
- **风险**: dao→core 反向依赖破坏标准分层独立性。若 core 层接口签名变化，dao 层需同步修改。
- **建议**: 将 `_NopJobCoreConstants` 和 `ITriggerEvalContext` 下沉至 api 层。`JobTriggerCalculator` 调用保持现状或提升至 coordinator 层。
- **信心水平**: 确定
- **误报排除**: 不是"传递依赖声明"类误报（手写代码显式 import 了 4 个 core 层类型，形成真实编译期依赖）。
- **复核状态**: 未复核

## 完整依赖图

```
nop-job-api → nop-api-core
nop-job-core → nop-commons, nop-job-api
nop-job-codegen → nop-ooxml-xlsx, nop-orm
nop-job-dao → nop-api-core, nop-orm, nop-job-api, nop-job-core (⚠️)
nop-job-coordinator → nop-job-dao, nop-job-core, nop-job-api, nop-config, nop-ioc, nop-cluster-core
nop-job-worker → nop-job-dao, nop-job-api, nop-job-core, nop-config, nop-ioc
nop-job-meta → nop-job-codegen(test), nop-job-dao(test)
nop-job-service → nop-job-dao, nop-job-meta, nop-job-core, nop-biz, nop-config, nop-ioc, nop-rpc-cluster
nop-job-web → nop-job-meta, nop-web, nop-job-service(test)
nop-job-app → nop-quarkus-web-orm-starter, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, Quarkus
nop-job-retry-adapter → nop-job-api, nop-retry-engine, nop-ioc
```

## 合规模块清单

| 子模块 | 规则 | 合规 |
|--------|------|------|
| nop-job-api | 1 | ✅ |
| nop-job-core | 3 | ✅ |
| nop-job-codegen | 7 | ✅ |
| nop-job-dao | 2 | ⚠️ 依赖 nop-job-core |
| nop-job-coordinator | 引擎模块 | ✅ |
| nop-job-worker | 引擎模块 | ✅ |
| nop-job-meta | 8 | ✅ (test scope) |
| nop-job-service | 4 | ✅ |
| nop-job-web | 5 | ✅ |
| nop-job-app | 6,10 | ✅ |
| nop-job-retry-adapter | 适配器 | ✅ |

无循环依赖。Quarkus 依赖正确隔离在 app 模块。
