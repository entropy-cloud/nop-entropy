# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-api 是孤儿模块——定义的 API 接口无任何消费者

- **文件**: `nop-code/nop-code-api/pom.xml` 及 `nop-code/nop-code-api/src/main/java/io/nop/code/api/CodeIndexApi.java`
- **证据片段**:
  ```xml
  <!-- nop-code-api/pom.xml 行 5-11 -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  ```
  ```java
  // CodeIndexApi.java 行 12-29
  @BizModel("NopCodeIndexApi")
  public interface CodeIndexApi {
      @BizMutation
      ApiResponse<String> fullIndex(ApiRequest<Map<String, Object>> request);
      @BizQuery
      ApiResponse<List<Map<String, Object>>> searchCode(ApiRequest<Map<String, Object>> request);
  }
  ```
  全仓库搜索 `CodeIndexApi` 的 import 或 implements：**0 个匹配**。nop-code-service 定义了自己的 `ICodeIndexService`，BizModel 类均使用 `ICodeIndexService` 而非 `CodeIndexApi`。没有任何模块的 pom.xml 声明对 `nop-code-api` 的依赖。
- **严重程度**: P2
- **现状**: nop-code-api 定义了一个带 `@BizModel("NopCodeIndexApi")` 注解的外部 RPC 接口 `CodeIndexApi`，但该接口在整个仓库中从未被实现、从未被引用、从未被注册到 IoC 容器。该模块被 Maven 编译并安装到本地仓库，但不会出现在任何运行时 classpath 上。
- **风险**: (1) 开发者看到 `CodeIndexApi` 可能误认为它是有效的外部 API 契约。(2) 该模块增加构建时间和仓库维护成本。(3) 如果未来确实需要外部 RPC 接口，当前孤立的定义会与实际实现产生歧义。
- **建议**: (a) 删除 nop-code-api 模块；(b) 或让 `CodeIndexService` 实现 `CodeIndexApi`，并将 nop-code-api 加入 nop-code-service 的依赖。
- **误报排除**: 这是一个定义了公共 API 契约但没有任何实现或消费者的结构性空洞。
- **复核状态**: 未复核

---

### [维度01-02] nop-code-api 使用独立 POM 脱离 nop-code 继承体系

- **文件**: `nop-code/nop-code-api/pom.xml`
- **证据片段**:
  ```xml
  <!-- nop-code-api/pom.xml 行 1-21, 完整 POM 无 <parent> 引用 nop-code -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>
  </properties>
  ```
  对比其他子模块：
  ```xml
  <!-- nop-code-core/pom.xml 行 4-8 -->
  <parent>
      <artifactId>nop-code</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>1.0.0-SNAPSHOT</version>
  </parent>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 的 pom.xml 没有 `<parent>` 引用 nop-code 父 POM，使用独立 groupId 和版本号。手动定义了 properties 和 build plugins，不共享父 POM 的依赖管理和插件配置。
- **风险**: 当父 POM 更新依赖版本、编译器设置或插件版本时，nop-code-api 不会同步更新。
- **建议**: 为 nop-code-api 添加 `<parent>` 引用指向 nop-code 父 POM。
- **误报排除**: 独立 POM 意味着脱离 12 个兄弟模块的统一管理，是结构性漂移风险。
- **复核状态**: 未复核

---

### [维度01-03] nop-code-service 依赖 nop-sys-dao 但无代码级引用

- **文件**: `nop-code/nop-code-service/pom.xml:79-81`
- **证据片段**:
  ```xml
  <!-- nop-code-service/pom.xml 行 79-81 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-sys-dao</artifactId>
  </dependency>
  ```
  全 nop-code-service/src/main 的 grep 结果：`import io.nop.sys` → **0 匹配**。
- **严重程度**: P3
- **现状**: nop-code-service 在 compile scope 声明了对 nop-sys-dao 的依赖，但手写代码中没有任何 import 或引用使用该模块提供的类型。参考模块 nop-auth 将 nop-sys 依赖放在 app 层。
- **风险**: 不必要的编译期耦合；违反"依赖应在最低必要层声明"原则。
- **建议**: 将 `nop-sys-dao` 依赖从 nop-code-service 移至 nop-code-app。
- **误报排除**: nop-sys-dao 不是 nop-code 内部模块的传递依赖，而是一个跨模块的 DAO 层依赖。nop-auth 将同类依赖放在 app 层。
- **复核状态**: 未复核

---

### [维度01-04] 部分子模块硬编码版本号而非使用 ${project.version}

- **文件**: `nop-code/nop-code-graph/pom.xml:14-16`, `nop-code/nop-code-lang-java/pom.xml:13-16`
- **证据片段**:
  ```xml
  <!-- nop-code-graph/pom.xml 行 14-16 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>  <!-- 硬编码 -->
  </dependency>
  ```
  对比同模块中正确用法：
  ```xml
  <!-- nop-code-flow/pom.xml -->
  <version>${project.version}</version>  <!-- 正确 -->
  ```
- **严重程度**: P3
- **现状**: nop-code-graph 和 nop-code-lang-java 等多个模块对 nop-code-core 的版本引用硬编码为 `1.0.0-SNAPSHOT`，而 nop-code-flow 等使用 `${project.version}`。
- **风险**: 版本变更时硬编码的版本号将无法解析到正确的 artifact。
- **建议**: 将所有 nop-code 内部模块间的依赖版本统一替换为 `${project.version}`。
- **误报排除**: 硬编码版本号在版本升级时会导致可量化的构建失败。
- **复核状态**: 未复核

---

## 合规模块清单

| 模块 | 评估 |
|------|------|
| nop-code-core | ✅ 合规 |
| nop-code-graph | ✅ 合规（版本号硬编码 P3） |
| nop-code-flow | ✅ 合规 |
| nop-code-lang-java | ✅ 合规（版本号硬编码 P3） |
| nop-code-lang-python | ✅ 合规 |
| nop-code-lang-typescript | ✅ 合规 |
| nop-code-codegen | ✅ 合规 |
| nop-code-dao | ✅ 合规 |
| nop-code-meta | ✅ 合规 |
| nop-code-web | ✅ 合规 |
| nop-code-app | ✅ 合规（Quarkus 正确隔离） |
| nop-code-api | ⚠️ 孤立模块 |
| nop-code-service | ⚠️ 冗余 nop-sys-dao 依赖 |

## 循环依赖检查

**未发现循环依赖。** 所有模块间的依赖关系形成有向无环图（DAG）。

## 总结评估

nop-code 模块的依赖架构整体合规，分层清晰，无循环依赖，Quarkus 运行时正确隔离在 app 层。主要问题：nop-code-api 是完全孤立的模块（P2），nop-code-service 对 nop-sys-dao 的冗余依赖（P3），版本引用不一致（P3）。
