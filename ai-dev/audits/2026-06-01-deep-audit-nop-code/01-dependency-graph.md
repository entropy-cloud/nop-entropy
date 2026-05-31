# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-api 缺少父 POM 继承，硬编码版本属性存在漂移风险

- **文件**: `nop-code/nop-code-api/pom.xml:1-21`
- **证据片段**:
  ```xml
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
      <maven.compiler.source>${java.version}</maven.compiler.source>
      <maven.compiler.target>${java.version}</maven.compiler.target>
      <maven.compiler.release>${java.version}</maven.compiler.release>
  </properties>
  ```
- **严重程度**: P2
- **现状**: nop-code-api 是唯一没有 `<parent>` 引用的子模块。它独立硬编码了 `nop-entropy.version`、`java.version`、编码属性和编译器版本，而其他 12 个子模块全部继承 nop-code 父 POM 并通过父链获得统一配置。
- **风险**: 当 nop-entropy 根 POM 或 nop-code 父 POM 的版本号变更时，nop-code-api 不会自动继承新版本。需要人工同步 `<nop-entropy.version>` 属性。此外，该模块还声明了独立的 build plugin 配置（maven-site-plugin、maven-project-info-reports-plugin），其他子模块均未声明这些插件，增加了维护表面。
- **建议**: 为 nop-code-api 添加父 POM 引用，然后移除硬编码的 properties 和冗余的 build plugins。
- **信心水平**: 确定
- **误报排除**: 12 个子模块中有 1 个脱离统一版本管理，属于结构性配置风险。如果未来有人仅更新了 nop-code 父 POM 的版本而未同步 api 的硬编码属性，将导致 api 模块使用不同版本的框架依赖，引发运行时不兼容。
- **复核状态**: 未复核

### [维度01-02] nop-code-api 为空壳模块，无源代码且无消费者

- **文件**: `nop-code/nop-code-api/`（目录，无 `src/` 目录）
- **证据片段**: 无 Java 文件，无资源文件。
- **严重程度**: P3
- **现状**: nop-code-api 是一个空壳模块，参与 reactor 构建但不产出任何制品，也没有被其他模块消费。所有 API DTO 均留在 service 模块中。
- **风险**: 增加无意义的 reactor 构建时间；新开发者可能误以为应该将 DTO 放入 api 模块；与 Nop 平台标准分层约定不一致。
- **建议**: 将 service 中的 DTO 类迁移到 nop-code-api 并为 api 添加父 POM；或从父 POM 的 `<modules>` 中移除 nop-code-api。
- **信心水平**: 确定
- **误报排除**: 存在于 reactor 中但无代码、无消费者、无父 POM 的模块，是真实的结构性问题。
- **复核状态**: 未复核

### [维度01-03] 内部模块版本声明方式不一致（硬编码 vs ${project.version}）

- **文件**: 多个子模块的 pom.xml（service、app、web、meta、dao 共 19 处硬编码，flow、lang-python、lang-typescript 共 4 处参数化）
- **证据片段**:
  ```xml
  <!-- 硬编码版本 -->
  <dependency>
      <artifactId>nop-code-dao</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <!-- 参数化版本 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 内部模块依赖版本声明存在两种风格：硬编码 `1.0.0-SNAPSHOT`（19 处）和参数化 `${project.version}`（4 处）。
- **风险**: 版本从 `1.0.0-SNAPSHOT` 变更为正式版本时，需手动修改 19 处硬编码版本号。
- **建议**: 统一使用 `${project.version}` 或在 nop-code 父 POM 的 `<dependencyManagement>` 中统一管理内部模块版本。
- **信心水平**: 确定
- **误报排除**: 19 处硬编码版本号在版本发布时需逐一修改，是真实可量化的维护成本。
- **复核状态**: 未复核

## 依赖图

```
nop-code-app → [quarkus, nop-code-web, nop-code-service, nop-auth-web, nop-auth-service, nop-web-site]
nop-code-web → [nop-code-meta, nop-code-service, nop-web]
nop-code-service → [nop-code-dao, nop-code-core, nop-code-graph, nop-code-flow, nop-code-lang-java, nop-code-lang-python, nop-code-lang-typescript, nop-code-meta, nop-biz, nop-config, nop-ioc, nop-search-api(opt)]
nop-code-meta → (纯资源模块，test-scope 内部依赖)
nop-code-dao → [nop-api-core, nop-orm]
nop-code-codegen → [nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger]
nop-code-core → [nop-api-core, nop-commons, nop-core]
nop-code-graph → [nop-code-core]
nop-code-flow → [nop-code-core, nop-code-graph]
nop-code-lang-java → [nop-code-core, nop-commons]
nop-code-lang-python → [nop-code-core]
nop-code-lang-typescript → [nop-code-core]
nop-code-api → [nop-api-core]（空壳）
```

## 合规检查

所有分层规则均合规。无循环依赖。框架隔离正确。Quarkus 仅出现在 app 模块。

## 深挖第 2 轮追加

第 1 轮已完整覆盖所有子模块 pom.xml，无新发现。深挖结束。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 01-01 | P2 | nop-code-api/pom.xml | api 模块缺少父 POM 继承，硬编码版本存在漂移风险 |
| 01-02 | P3 | nop-code-api/ | api 为空壳模块，无源代码且无消费者 |
| 01-03 | P3 | 多个 pom.xml | 内部模块版本声明方式不一致（19处硬编码 vs 4处参数化） |
