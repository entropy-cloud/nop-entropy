# 维度 01：依赖图谱与分层合规性

## 依赖图

```
nop-job-api → nop-api-core
nop-job-core → nop-commons, nop-job-api
nop-job-codegen → nop-ooxml-xlsx, nop-orm
nop-job-dao → nop-api-core, nop-orm, nop-job-api, nop-job-core, nop-cluster-core
nop-job-meta → (no compile deps, test-scope only)
nop-job-service → nop-job-dao, nop-job-meta, nop-job-core, nop-biz, nop-config, nop-ioc, nop-sys-dao, nop-rpc-cluster
nop-job-web → nop-job-meta, nop-web
nop-job-app → nop-quarkus-web-orm-starter, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, nop-auth-web, nop-auth-service, nop-web-amis-editor, nop-web-site
nop-job-coordinator → nop-job-dao, nop-job-core, nop-job-api, nop-config, nop-ioc, nop-cluster-core
nop-job-worker → nop-job-dao, nop-job-api, nop-job-core, nop-config, nop-ioc
nop-job-retry-adapter → nop-job-api, nop-retry-engine, nop-ioc
```

## 总体评估

- 分层正确，无循环依赖
- Quarkus 仅在 app 模块引入
- 3 个 P3 未使用依赖

## 发现

### [01-01] P3 — nop-job-dao 声明未使用的 nop-cluster-core 编译依赖

- **文件**: nop-job-dao/pom.xml:36-39
- **现状**: nop-job-dao 声明了 `nop-cluster-core` 作为 compile 依赖，但模块内无任何 import 或代码引用该依赖。
- **建议**: 移除该依赖声明。

### [01-02] P3 — nop-job-service 声明未使用的 nop-sys-dao 编译依赖

- **文件**: nop-job-service/pom.xml:44-46
- **现状**: nop-job-service 声明了 `nop-sys-dao` 作为 compile 依赖，但模块内无任何 import 或代码引用该依赖。
- **建议**: 移除该依赖声明。

### [01-03] P3 — nop-job-service 声明未使用的 nop-rpc-cluster 编译依赖

- **文件**: nop-job-service/pom.xml:47-50
- **现状**: nop-job-service 声明了 `nop-rpc-cluster` 作为 compile 依赖，但模块内无任何 import 或代码引用该依赖。
- **建议**: 移除该依赖声明。
