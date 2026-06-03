# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### 检查范围

所有 11 个子模块的 pom.xml 依赖声明。

### 检查结果

**依赖图**：
```
nop-job-api → nop-api-core
nop-job-core → nop-commons, nop-job-api
nop-job-codegen → nop-ooxml-xlsx, nop-orm
nop-job-dao → nop-api-core, nop-orm, nop-job-api, nop-job-core, nop-cluster-core
nop-job-meta → nop-job-codegen(test), nop-job-dao(test)
nop-job-service → nop-job-dao, nop-job-meta, nop-job-core, nop-biz, nop-config, nop-ioc, nop-sys-dao
nop-job-coordinator → nop-job-dao, nop-job-core, nop-job-api, nop-config, nop-ioc, nop-cluster-core
nop-job-worker → nop-job-dao, nop-job-api, nop-job-core, nop-config, nop-ioc
nop-job-web → nop-job-meta, nop-web
nop-job-app → nop-quarkus-web-orm-starter, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, nop-auth-web, nop-auth-service
nop-job-retry-adapter → nop-job-api, nop-retry-engine, nop-ioc
```

**合规模块清单**（全部合规）：
- api：只依赖 nop-api-core ✓
- core：只依赖 nop-commons + nop-job-api ✓
- codegen：只依赖 nop-ooxml-xlsx + nop-orm ✓
- dao：依赖 api + 框架 + nop-job-api + nop-job-core + nop-cluster-core（cluster-core 用于分区解析，合理）✓
- meta：只依赖 test scope 的 codegen + dao ✓
- service：依赖 dao + meta + core + 框架层 ✓
- coordinator：依赖 dao + core + api + 框架层 ✓
- worker：依赖 dao + api + core + 框架层 ✓
- web：依赖 meta + nop-web ✓（service 为 test scope）
- app：依赖所有子模块 + Quarkus 运行时 ✓
- retry-adapter：依赖 api + nop-retry-engine + ioc ✓

**注**：nop-job 模块有 coordinator/worker 两个额外的运行时子模块（非标准业务模块骨架），这是分布式调度器的合理架构（协调器与执行器分离）。

### 零发现

无违规依赖。所有模块依赖关系合规，无循环依赖，无跨层违规。

## 维度复核结论

依赖图基于主 agent 收集的基线数据构建，各子模块 pom.xml 已逐项检查。结论可靠。

## 最终保留项

无。
