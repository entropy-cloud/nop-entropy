# 维度01：依赖图与模块边界

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度01-01] nop-code-api 为空壳模块，无源码且无消费者

- **文件**: `nop-code/nop-code-api/`（整个目录）
- **证据片段**:
  ```xml
  <!-- nop-code/nop-code-api/pom.xml:23-28 -->
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
          <version>${nop-entropy.version}</version>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 被列为 nop-code 的子模块（`nop-code/pom.xml:34`），但无 `src/` 目录。构建产物为空 JAR。没有任何其他 nop-code-* 模块依赖它。DTO（如 `SymbolDTO`）和接口（如 `ICodeIndexService`）实际定义在 nop-code-service 模块的 `io.nop.code.service.api` 包中。
- **风险**: 空模块增加构建开销但不提供价值。如果有外部消费者期望从 nop-code-api 获取 DTO/接口，会发现空 JAR。
- **建议**: 作为 WIP 实验模块可暂时保留。如果决定正式发布，应在 nop-code-api 中定义不依赖 core 的纯 DTO，或在文档中注明 api 模块当前为空占位。
- **信心水平**: 确定
- **误报排除**: 经源码验证，service 中的 DTO（如 `SymbolDTO.java:6-8`）确实 import 了 `io.nop.code.core.model.*`，因此不能简单地将它们移到 api 模块。当前结构虽不标准但务实正确。
- **复核状态**: 未复核

---

### [维度01-02] nop-code-api 不继承 nop-code 父 POM

- **文件**: `nop-code/nop-code-api/pom.xml:1-21`
- **证据片段**:
  ```xml
  <!-- nop-code/nop-code-api/pom.xml — 无 parent 声明 -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
  </properties>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 是 nop-code 下唯一不继承父 POM 的子模块。它自行声明 groupId/version/properties，不共享父 POM 的插件配置和依赖管理。所有其他 12 个子模块均继承 nop-code 父 POM。
- **风险**: 若父 POM 增加构建配置，api 模块不会同步。版本号管理也可能出现漂移。但因模块为空，当前无实际影响。
- **建议**: 如果保留此模块，添加与其他模块一致的父 POM 声明。如果确定不使用，从 `nop-code/pom.xml` 的 modules 列表中移除。
- **信心水平**: 确定
- **误报排除**: 这是结构性不一致——其他所有模块都有父 POM，唯独 api 没有，且 api 的版本号属性是自定义的而非继承的。
- **复核状态**: 未复核

---

### [维度01-03] 内部模块依赖版本硬编码不一致

- **文件**: 多个子模块的 pom.xml
- **证据片段**:
  ```xml
  <!-- nop-code-graph/pom.xml:14-16 — 硬编码版本 -->
  <artifactId>nop-code-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  
  <!-- nop-code-flow/pom.xml:15-16 — 使用变量 -->
  <artifactId>nop-code-core</artifactId>
  <version>${project.version}</version>
  ```
- **严重程度**: P3
- **现状**: 子模块对 nop-code-* 内部依赖的版本声明方式不统一。7 个模块使用硬编码 `1.0.0-SNAPSHOT`，3 个模块使用 `${project.version}`。根 POM 无 `dependencyManagement` 管理内部模块版本。
- **风险**: 版本升级时需逐一修改硬编码字符串，遗漏会导致构建失败。
- **建议**: 统一使用 `${project.version}` 引用内部依赖，或在父 POM 的 `dependencyManagement` 中集中管理。
- **信心水平**: 确定
- **误报排除**: 有可量化的维护成本：版本升级时需检查并修改 7 个文件的硬编码字符串。
- **复核状态**: 未复核

---

## 依赖图总结

```
nop-code-codegen (独立) → nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger
nop-code-api (空壳) → nop-api-core
nop-code-core → nop-api-core, nop-commons, nop-core
nop-code-graph → nop-code-core, jgrapht
nop-code-flow → nop-code-core, nop-code-graph
nop-code-lang-java → nop-code-core, javaparser
nop-code-lang-python → nop-code-core, tree-sitter
nop-code-lang-typescript → nop-code-core, tree-sitter
nop-code-dao → nop-api-core, nop-orm, nop-code-codegen(test)
nop-code-meta → nop-code-codegen(test), nop-code-dao(test)
nop-code-service → nop-code-dao, nop-code-core, nop-code-graph, nop-code-flow, nop-code-lang-java/python, nop-code-meta, nop-biz, nop-config, nop-ioc
nop-code-web → nop-code-meta, nop-code-service, nop-web, nop-code-codegen(test)
nop-code-app → nop-code-service, nop-code-web, nop-quarkus-web-orm-starter, nop-auth-web/service
```

**无循环依赖。** 合规模块清单：所有 13 个子模块均无 P0/P1 边界违规。
