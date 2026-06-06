# 维度 01：依赖图与模块边界 — nop-code 模块

## 第 1 轮（初审）

### [维度01-01] nop-code-api 模块为空且完全未被使用

- **文件**: `nop-code/nop-code-api/pom.xml` 及整个 `nop-code-api/` 目录
- **证据片段**:
  ```xml
  <!-- nop-code-api/pom.xml -->
  <artifactId>nop-code-api</artifactId>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
      </dependency>
  </dependencies>
  ```
  ```
  $ ls nop-code-api/src
  ls: no such file or directory
  ```
  ```
  $ grep -r "io.nop.code.api" nop-code/ --include="*.java"
  (zero results)
  ```
- **严重程度**: P2
- **现状**: nop-code-api 模块存在于 reactor 构建中，声明了 nop-api-core 依赖，但包含零个源文件（没有 `src/` 目录），整个 nop-code 项目中没有任何地方引用 `io.nop.code.api` 包。
- **风险**: (1) 模块占据了构建槽位和 Maven artifact 名称但不提供价值。(2) 寻找稳定 API 契约的下游消费者什么也找不到。(3) 标准的 Nop 分层架构 (api → dao → service) 在 api 层断裂。
- **建议**: 要么将 nop-code-service 中的服务接口和 DTO 移入 nop-code-api，要么从 reactor 中移除空模块以避免混淆。
- **信心水平**: 确定
- **误报排除**: 对 nop-code/ 下所有 Java 文件 grep 确认零引用 `io.nop.code.api`。模块没有 `src/` 目录、没有资源、没有 Java 类。是纯粹的空壳。
- **复核状态**: 未复核

### [维度01-02] 服务接口和 DTO 放在 nop-code-service 而非 nop-code-api

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java` 及 `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/*.java`
- **证据片段**:
  ```java
  // ICodeIndexService.java - service interface in service module
  package io.nop.code.service.api;
  import io.nop.code.core.model.*;
  import io.nop.code.flow.ExecutionFlow;
  import io.nop.code.service.api.dto.*;
  public interface ICodeIndexService { ... }
  ```
  ```java
  // SymbolDTO.java - DTO in service module
  package io.nop.code.service.api.dto;
  import io.nop.code.core.model.CodeSymbol;
  public class SymbolDTO {
      public static SymbolDTO fromCodeSymbol(CodeSymbol symbol) { ... }
  }
  ```
- **严重程度**: P1
- **现状**: 服务接口 `ICodeIndexService` 和约 30 个 DTO 类（`SymbolDTO`、`CallHierarchyDTO`、`TypeHierarchyDTO` 等）全部定义在 `nop-code-service` 内的 `io.nop.code.service.api` 和 `io.nop.code.service.api.dto` 包中，而 `nop-code-api` 模块为空。
- **风险**: 违反标准 Nop 分层架构规则"api 层定义接口和 DTO"。外部模块想调用代码分析服务必须依赖 nop-code-service，传递引入 nop-code-dao、nop-code-graph、nop-code-flow、所有语言适配器、nop-biz、nop-config、nop-ioc 等。无法只针对接口编程。
- **建议**: 将 `ICodeIndexService` 和所有 DTO 类从 `io.nop.code.service.api` 和 `io.nop.code.service.api.dto` 移入 `nop-code-api` 模块。DTO 应重构以移除对 `io.nop.code.core.model.*` 类型的直接依赖。
- **信心水平**: 很可能
- **误报排除**: NOP 平台的标准域模块模式（nop-auth、nop-wf、nop-job 等）一致将服务接口和 DTO 放在 api 模块中。nop-code 是例外。
- **复核状态**: 未复核

### [维度01-03] nop-code-service 直接使用 nop-dao-api 类型但未声明依赖

- **文件**: `nop-code/nop-code-service/pom.xml` 及 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/` 下的 Java 源文件
- **证据片段**:
  ```java
  // CodeIndexService.java lines 81-83
  import io.nop.dao.api.IDaoEntity;
  import io.nop.dao.api.IDaoProvider;
  import io.nop.dao.api.IEntityDao;

  // CodeGraphService.java lines 33-34
  import io.nop.dao.api.IDaoProvider;
  import io.nop.dao.api.IEntityDao;
  ```
- **严重程度**: P2
- **现状**: 6 个 Java 文件直接 import `io.nop.dao.api.IDaoProvider`、`io.nop.dao.api.IEntityDao`、`io.nop.dao.api.IDaoEntity`，但 pom.xml 未显式声明 nop-dao-api 依赖。仅通过传递链 nop-code-service → nop-code-dao → nop-orm → nop-dao-api 获得。
- **风险**: 如果 nop-orm 重构不再导出 nop-dao-api，nop-code-service 将编译失败且原因不明。隐式耦合使开发者难以理解模块真实依赖。
- **建议**: 在 nop-code-service 的 pom.xml 中显式添加 nop-dao-api 依赖声明。
- **信心水平**: 确定
- **误报排除**: nop-dao-api 不在排除的平台包列表（nop-api-core、nop-commons、nop-core、nop-xlang、nop-markdown）中。service 代码直接使用了 7 个来自该模块的类型。
- **复核状态**: 未复核

### [维度01-04] nop-code-service 直接使用 nop-orm 类型但未声明依赖

- **文件**: `nop-code/nop-code-service/pom.xml` 及 Java 源文件
- **证据片段**:
  ```java
  // CodeIndexService.java lines 84-87
  import io.nop.orm.IOrmEntity;
  import io.nop.orm.IOrmSession;
  import io.nop.orm.IOrmTemplate;
  import io.nop.orm.exceptions.OrmException;
  ```
- **严重程度**: P2
- **现状**: 3 个 Java 文件直接 import `io.nop.orm.IOrmEntity`、`io.nop.orm.IOrmSession`、`io.nop.orm.IOrmTemplate`、`io.nop.orm.exceptions.OrmException`，但 pom.xml 未显式声明 nop-orm 依赖。通过 nop-code-service → nop-code-dao → nop-orm 传递获得。
- **风险**: 同发现 01-03。如果 nop-code-dao 移除或更改 nop-orm 依赖，service 将中断。service 直接与 ORM session 和实体交互，代表了对持久化层的深度耦合。
- **建议**: 在 nop-code-service 的 pom.xml 中显式添加 nop-orm 依赖声明。或者重构代码仅通过 DAO 抽象（IDaoProvider/IEntityDao）使用。
- **信心水平**: 确定
- **误报排除**: nop-orm 不在排除的平台包列表中。grep 验证：3 个文件中 7 个 import 语句引用 io.nop.orm.* 类型。
- **复核状态**: 未复核

### [维度01-05] nop-code-service 声明了 nop-ioc 和 nop-config 但无直接 Java 使用

- **文件**: `nop-code/nop-code-service/pom.xml` lines 67-74
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-config</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-ioc</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-code-service 声明了 nop-config 和 nop-ioc 为 compile-scope 依赖，但无 Java 源文件 import 这些包中的类。IoC 容器和配置解析已通过 nop-biz 传递获得。
- **风险**: 最低。冗余声明增加少量维护开销。但可能掩盖版本冲突。
- **建议**: 考虑从 pom.xml 中移除 nop-ioc 和 nop-config，因为 nop-biz 已传递两者。
- **信心水平**: 很可能
- **误报排除**: 其他 Nop 域模块（nop-auth-service）不声明 nop-ioc/nop-config，表明这些是早期开发的遗留声明。
- **复核状态**: 未复核

### [维度01-06] nop-code-meta 零 compile-scope 依赖

- **文件**: `nop-code/nop-code-meta/pom.xml`
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
- **现状**: nop-code-meta 不声明任何 compile-scope 依赖。模块仅包含 XMeta 资源文件、i18n 和字典文件，无 Java 源代码。test-scope 依赖用于 codegen runner。
- **风险**: 极低。模块编译为纯资源 JAR。标准 Nop 模式。
- **建议**: 无需更改。这是标准 Nop 平台的纯 meta 模块模式。
- **信心水平**: 确定
- **误报排除**: 在 Nop 平台中，XMeta 文件由 XLang 框架在运行时加载，不需要 compile-scope 依赖。
- **复核状态**: 未复核

### [维度01-07] nop-code-codegen 无 main 源码但声明 compile-scope 框架依赖

- **文件**: `nop-code/nop-code-codegen/pom.xml`
- **证据片段**:
  ```xml
  <dependencies>
      <dependency><artifactId>nop-ooxml-xlsx</artifactId></dependency>
      <dependency><artifactId>nop-orm</artifactId></dependency>
      <dependency><artifactId>nop-graphql-core</artifactId></dependency>
      <dependency><artifactId>nop-xlang-debugger</artifactId></dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: codegen 模块无 main Java 源码，仅有测试类 `NopCodeCodeGen.java` 作为 codegen 启动器。但声明了 4 个 compile-scope 框架依赖。
- **风险**: codegen 模块的 JAR 将仅包含带有 compile-scope 依赖的 POM。如果其他模块在 compile scope 意外依赖它，会引入不必要的框架依赖。
- **建议**: 标准 Nop 平台 codegen 模式。可考虑将依赖改为 `provided` scope。
- **信心水平**: 很可能
- **误报排除**: Nop 平台 codegen 模式要求这些框架依赖在 exec-maven-plugin 的 classpath 上。这是结构选择而非 bug。
- **复核状态**: 未复核

## 合规性总结

| 规则 | 描述 | 状态 | 备注 |
|------|------|------|------|
| 1 | api 不依赖业务实现 | PASS（平凡） | nop-code-api 为空 |
| 2 | dao 只依赖 api + nop-persistence | PASS | nop-code-dao → nop-api-core, nop-orm |
| 3 | core 只依赖 api + 框架核心，不依赖 dao | PASS | core 模块无 dao 依赖 |
| 4 | service 依赖 api + core + dao | PARTIAL | service 未使用 api 模块 |
| 5 | web 依赖 service，不直接依赖 dao | PASS | |
| 6 | app 依赖 web + service + 运行时框架 | PASS | Quarkus 仅在 app 中 |
| 7 | codegen 依赖 model + nop-kernel 工具 | PASS | |
| 8 | meta 依赖 dao | PASS (test-scope) | |
| 9 | 无循环依赖 | PASS | |
| 10 | 框架依赖仅在 app 模块 | PASS | |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 01-01 | P2 | nop-code-api/ | api 模块为空且完全未使用 |
| 01-02 | P1 | nop-code-service/api/ | 服务接口和 DTO 放在 service 而非 api 模块 |
| 01-03 | P2 | nop-code-service/pom.xml | 使用 nop-dao-api 类型但未声明依赖 |
| 01-04 | P2 | nop-code-service/pom.xml | 使用 nop-orm 类型但未声明依赖 |
| 01-05 | P3 | nop-code-service/pom.xml | 声明 nop-ioc/nop-config 但无直接使用 |
| 01-06 | P3 | nop-code-meta/pom.xml | 零 compile-scope 依赖（信息性） |
| 01-07 | P3 | nop-code-codegen/pom.xml | 无 main 源码但声明框架依赖（信息性） |
