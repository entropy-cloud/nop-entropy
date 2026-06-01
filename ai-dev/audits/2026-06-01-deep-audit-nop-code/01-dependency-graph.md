# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-api 是空壳模块，无任何源码或资源

- **文件**: `nop-code/nop-code-api/` (整个模块)
- **证据片段**:
  ```
  nop-code-api/
  ├── pom.xml    (仅依赖 nop-api-core)
  └── target/    (构建产物)
  ```
  无 `src/` 目录，无 Java 文件，无资源文件。
- **严重程度**: P2
- **现状**: `nop-code-api` 被声明为一个 Maven 模块并列为子模块，但实际上没有任何代码或资源。`nop-code-service` 的 `ICodeIndexService` 接口和 DTO 类直接放在 `nop-code-service` 模块的 `service.api` 包下，而不是放在 `nop-code-api` 模块中。
- **风险**: (1) 其他模块如果想仅依赖 API 接口而不引入服务实现，无法做到。(2) 新开发者可能误以为 API 契约在 api 模块中，实际却在 service 模块中，增加认知成本。
- **建议**: 二选一：(a) 将 `ICodeIndexService` 及其 DTO 移入 `nop-code-api`；(b) 如果当前架构有意将 API 接口留在 service 模块，考虑从子模块列表中移除空壳。
- **信心水平**: 很可能
- **误报排除**: Nop 平台标准分层中 api 模块承载接口和 DTO。此模块为空壳是结构性事实。
- **复核状态**: 未复核

### [维度01-02] nop-code-meta 运行时无编译期依赖，meta 文件通过 VFS 资源加载机制解析

- **文件**: `nop-code/nop-code-meta/pom.xml:15-28`
- **证据片段**:
  ```xml
  <dependencies>
      <dependency>
          <artifactId>nop-code-codegen</artifactId>
          <scope>test</scope>
      </dependency>
      <dependency>
          <artifactId>nop-code-dao</artifactId>
          <scope>test</scope>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: meta 模块所有内部依赖都是 test scope。模块本身只包含 xmeta XML 资源文件、字典 YAML、i18n 资源。符合 Nop 平台 meta 模块标准模式。
- **风险**: 无实际风险。
- **建议**: 无需修复。
- **信心水平**: 确定
- **误报排除**: meta 是纯资源模块，由上层模块传递提供运行时类路径。
- **复核状态**: 未复核

### [维度01-03] nop-code-service 缺少对 nop-code-api 的显式依赖声明

- **文件**: `nop-code/nop-code-service/pom.xml:15-106`
- **证据片段**:
  ```xml
  <dependencies>
      <dependency><artifactId>nop-code-dao</artifactId></dependency>
      <dependency><artifactId>nop-code-core</artifactId></dependency>
      <!-- nop-code-api 完全缺失 -->
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: service 未显式声明对 api 的依赖。由于 api 为空壳（见01-01），实际上没有编译期需求。
- **风险**: 若未来 api 添加接口或 DTO，service 模块不会自动获得这些类。
- **建议**: 添加依赖声明以保持分层一致性。
- **信心水平**: 确定
- **误报排除**: 依赖声明缺失是事实，不是风格问题。
- **复核状态**: 未复核

### [维度01-04] nop-code-service 中 ICodeIndexService 接口直接暴露 nop-code-core 内部模型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:6-10`
- **证据片段**:
  ```java
  import io.nop.code.core.incremental.FileFingerprint;
  import io.nop.code.core.model.*;
  import io.nop.code.flow.ChangeAnalysisResult;
  import io.nop.code.flow.DeadCodeReport;
  import io.nop.code.flow.ExecutionFlow;
  ```
- **严重程度**: P2
- **现状**: ICodeIndexService 接口方法签名直接返回 core 层模型类和 flow 层模型类。BizModel 层有 DTO 转换（SymbolDTO 等），但 ICodeIndexService 本身没做隔离。
- **风险**: core 层模型变更影响 API 契约稳定性。
- **建议**: 记录为技术债，排期考虑在接口层统一使用 DTO。
- **信心水平**: 很可能
- **误报排除**: 虽然内部服务接口返回 core 模型在 Nop 平台中可接受，但此接口直接影响 GraphQL API。
- **复核状态**: 未复核

### [维度01-05] nop-code-codegen 缺少对 nop-code-core 的依赖，依赖较重框架模块

- **文件**: `nop-code/nop-code-codegen/pom.xml:17-34`
- **证据片段**:
  ```xml
  <dependency><artifactId>nop-ooxml-xlsx</artifactId></dependency>
  <dependency><artifactId>nop-orm</artifactId></dependency>
  <dependency><artifactId>nop-graphql-core</artifactId></dependency>
  <dependency><artifactId>nop-xlang-debugger</artifactId></dependency>
  ```
- **严重程度**: P3
- **现状**: codegen 模块依赖 nop-orm、nop-graphql-core、nop-xlang-debugger 等框架模块。符合 Nop 平台代码生成模块标准模式。
- **风险**: 无实际风险。codegen 仅在构建期运行。
- **建议**: 无需修复。
- **信心水平**: 确定
- **误报排除**: 平台标准模式。
- **复核状态**: 未复核

### [维度01-06] nop-code-web 依赖 nop-code-meta 而非通过 service 传递

- **文件**: `nop-code/nop-code-web/pom.xml:16-20`
- **证据片段**:
  ```xml
  <dependency><artifactId>nop-code-meta</artifactId></dependency>
  <dependency><artifactId>nop-code-service</artifactId></dependency>
  ```
- **严重程度**: P3
- **现状**: web 层同时依赖 meta 和 service。web 层直接依赖 meta 是因为 view.xml 需引用 xmeta 资源。
- **风险**: 低。Nop 平台标准模式。
- **建议**: 无需修复。
- **信心水平**: 确定
- **误报排除**: meta 是纯资源模块。
- **复核状态**: 未复核

### [维度01-07] nop-code-app 正确引入 Quarkus 运行时

- **文件**: `nop-code/nop-code-app/pom.xml:20-57`
- **证据片段**:
  ```xml
  <dependency><artifactId>nop-quarkus-web-orm-starter</artifactId></dependency>
  <dependency><artifactId>nop-code-service</artifactId></dependency>
  <dependency><artifactId>nop-code-web</artifactId></dependency>
  <dependency><artifactId>quarkus-jdbc-mysql</artifactId></dependency>
  ```
- **严重程度**: P3（合规）
- **现状**: app 层正确引入 Quarkus 运行时和 JDBC 驱动。
- **风险**: 无。
- **建议**: 无需修复。
- **信心水平**: 确定
- **误报排除**: 平台标准模式。
- **复核状态**: 未复核

## 总结

- 完整依赖图为有向无环图（DAG），无循环依赖
- 无 P0/P1 级违规
- 2 个结构性 P2 问题（api 空壳、core 模型暴露），5 个 P3 问题（大多符合平台模式）
- 分层依赖方向正确，框架特定依赖隔离在 app 模块
