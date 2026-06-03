# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-job-service 对 nop-sys-dao 的未使用编译依赖

- **文件**: `nop-job/nop-job-service/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-sys-dao</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: nop-job-service 在 compile scope 依赖 nop-sys-dao，但在 nop-job-service 的整个 src/main 目录中，没有任何一行 import 或引用指向 `io.nop.sys` 包。该依赖完全未被使用。
- **风险**: nop-sys-dao 传递引入 nop-sys-api、nop-sys-core 等额外模块，扩大编译类路径。同时会让维护者误以为 service 层包含系统管理逻辑。
- **建议**: 移除 nop-sys-dao 从 nop-job-service 的编译依赖。如果运行时需要，应通过 app 层引入。
- **信心水平**: 确定
- **误报排除**: 已通过 grep import io.nop.sys 确认零匹配。这不是"隐式 IoC 使用"的误报。
- **复核状态**: 未复核

### [维度01-02] nop-job-service 对 nop-rpc-cluster 的未使用编译依赖

- **文件**: `nop-job/nop-job-service/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-rpc-cluster</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: nop-job-service 在 compile scope 依赖 nop-rpc-cluster，但 RpcJobInvoker 仅需要 nop-api-core 的 IRpcServiceInvoker 接口，不需要 rpc-cluster 的传递依赖。
- **风险**: nop-rpc-cluster 传递引入额外的 RPC 和集群模块，扩大编译类路径。
- **建议**: 将 nop-rpc-cluster 替换为 nop-api-core（已通过传递依赖引入），或将其改为 provided/test scope。
- **信心水平**: 很可能
- **误报排除**: 已确认 RpcJobInvoker 的 import 来自 nop-api-core 而非 nop-rpc-cluster。
- **复核状态**: 未复核

### [维度01-04] nop-job-dao 对 nop-cluster-core 的未使用编译依赖

- **文件**: `nop-job/nop-job-dao/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-cluster-core</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: nop-job-dao 在 compile scope 依赖 nop-cluster-core，但在整个 src/main 目录中没有任何 import 指向 `io.nop.cluster` 包。该依赖完全未被使用。
- **风险**: 传递引入 nop-core 等额外模块。让维护者误以为 dao 层包含集群感知逻辑。
- **建议**: 移除 nop-cluster-core 从 nop-job-dao 的编译依赖。如果运行时需要集群发现功能，应通过 coordinator 或 app 层引入。
- **信心水平**: 确定
- **误报排除**: 已通过 grep import io.nop.cluster 确认零匹配。dao 层的 store 实现类均不注入任何 cluster 相关的 bean。
- **复核状态**: 未复核

### [维度01-05] nop-job-worker 和 nop-job-coordinator 的硬编码版本号

- **文件**: `nop-job/nop-job-worker/pom.xml`, `nop-job/nop-job-coordinator/pom.xml`, `nop-job/nop-job-dao/pom.xml`, `nop-job/nop-job-meta/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-job-codegen</artifactId>
      <version>2.0.0-SNAPSHOT</version>
      <scope>test</scope>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 多个子模块在引用 nop-job-codegen 作为 test scope 依赖时使用了硬编码版本号 `2.0.0-SNAPSHOT`。其他内部依赖通过父 pom 的 dependencyManagement 不需要硬编码版本。
- **风险**: 版本升级时需要逐个文件修改硬编码版本号，容易遗漏。
- **建议**: 在父 pom 的 dependencyManagement 中统一声明 nop-job-codegen 的版本。
- **信心水平**: 确定
- **误报排除**: 同一模块中的其他内部依赖都没有硬编码版本号，说明这些硬编码是遗漏而非设计。
- **复核状态**: 未复核

### [维度01-06] nop-job-app 对内部子模块使用硬编码版本

- **文件**: `nop-job/nop-job-app/pom.xml`
- **证据片段**:
  ```xml
  <dependency>
      <artifactId>nop-job-service</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-job-app 对四个内部兄弟模块全部硬编码了 `2.0.0-SNAPSHOT` 版本号。而 nop-job-retry-adapter 使用了 `${project.version}` 引用，风格不一致。
- **风险**: 版本升级时需手动修改四个硬编码版本。
- **建议**: 使用 `${project.version}` 替代硬编码版本号，或在父 pom 的 dependencyManagement 中统一管理。
- **信心水平**: 确定
- **误报排除**: nop-job-retry-adapter 已使用 `${project.version}`，说明项目内已有这种做法。
- **复核状态**: 未复核

## 依赖图

```
nop-job-api  →  nop-api-core
nop-job-core →  nop-commons, nop-job-api, nop-core(test)
nop-job-dao  →  nop-api-core, nop-orm, nop-job-api, nop-job-core, nop-cluster-core, nop-job-codegen(test)
nop-job-service → nop-job-dao, nop-job-meta, nop-job-core, nop-biz, nop-config, nop-ioc, nop-sys-dao, nop-rpc-cluster
nop-job-coordinator → nop-job-dao, nop-job-core, nop-job-api, nop-config, nop-ioc, nop-cluster-core, nop-job-codegen(test)
nop-job-worker   → nop-job-dao, nop-job-api, nop-job-core, nop-config, nop-ioc, nop-job-codegen(test)
nop-job-meta     → nop-job-codegen(test), nop-job-dao(test)
nop-job-web      → nop-job-meta, nop-job-service, nop-web, nop-codegen(test), nop-ooxml-xlsx(test)
nop-job-app      → nop-quarkus-web-orm-starter, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, nop-auth-web, nop-auth-service, nop-web-amis-editor, nop-web-site
nop-job-codegen  → nop-ooxml-xlsx, nop-orm
nop-job-retry-adapter → nop-job-api, nop-retry-engine, nop-ioc
```

## 合规模块清单

| 子模块 | 合规评价 |
|--------|---------|
| nop-job-api | 合规 |
| nop-job-core | 合规 |
| nop-job-codegen | 合规 |
| nop-job-meta | 合规 |
| nop-job-coordinator | 合规 |
| nop-job-worker | 合规 |
| nop-job-web | 合规 |
| nop-job-app | 合规（Quarkus 运行时依赖限定在自身） |
| nop-job-retry-adapter | 合规 |

## 总结评估

nop-job 模块整体依赖结构健康，11 个子模块中有 9 个完全合规。发现 3 个结构性依赖问题（01-01/02/04，均为未使用的编译依赖 P2），2 个维护性问题（01-05/06，硬编码版本号 P3）。无循环依赖，无 P0/P1 级问题。
