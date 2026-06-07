# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-graph 及 lang-* 子模块第三方库版本未纳入父 POM 集中管理

- **文件**: `nop-code/nop-code-graph/pom.xml:17-31`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>org.jgrapht</groupId>
      <artifactId>jgrapht-core</artifactId>
      <version>1.5.2</version>
  </dependency>
  <dependency>
      <groupId>org.jgrapht</groupId>
      <artifactId>jgrapht-io</artifactId>
      <version>1.5.2</version>
  </dependency>
  <dependency>
      <groupId>nl.cwts</groupId>
      <artifactId>networkanalysis</artifactId>
      <version>1.3.0</version>
  </dependency>
  ```
  同理 `nop-code-lang-python/pom.xml:18-26` 和 `nop-code-lang-typescript/pom.xml:18-26`：
  ```xml
  <dependency>
      <groupId>io.github.bonede</groupId>
      <artifactId>tree-sitter</artifactId>
      <version>0.25.3</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: jgrapht（core+io）、networkanalysis、tree-sitter（含语言绑定）的版本硬编码在各自子模块 pom.xml 中，未被 nop-dependencies 父 POM 的 `<dependencyManagement>` 管理。同一基础库 tree-sitter `0.25.3` 的版本号在 lang-python 和 lang-typescript 中各出现一次。
- **风险**: 如果需要对 jgrapht 或 tree-sitter 做安全升级或兼容性更新，需手动定位多个 pom.xml 并逐一修改。若未来有其他模块引入相同库，可能出现版本不一致。当前风险受限于：这些库仅被 nop-code 子模块使用，影响范围可控。
- **建议**: 将 jgrapht、networkanalysis、tree-sitter 的版本提升至 `nop-dependencies/pom.xml` 的 `<dependencyManagement>` 或 nop-code 父 pom 的 `<properties>` + `<dependencyManagement>` 中统一管理。与 javaparser（已被 nop-dependencies 管理）的做法保持一致。
- **信心水平**: 确定
- **误报排除**: 这不是"看起来不优雅"的偏好问题——javaparser 已被父 POM 管理，同项目的第三方依赖管理方式不一致，存在可量化的维护成本差异。当升级 tree-sitter 版本时，需要在 2 个文件中同步修改而非 1 处。
- **复核状态**: 未复核

## 完整依赖图（compile scope io.nop.* 依赖）

```
nop-code (parent pom)
 │
 ├── nop-code-api ────────────────────────→ nop-api-core
 │
 ├── nop-code-core ───────────────────────→ nop-api-core, nop-commons, nop-core
 │
 ├── nop-code-graph ──────────────────────→ nop-code-core
 │     [第三方: jgrapht-core:1.5.2, jgrapht-io:1.5.2, networkanalysis:1.3.0]
 │
 ├── nop-code-flow ───────────────────────→ nop-code-core, nop-code-graph
 │
 ├── nop-code-lang-java ──────────────────→ nop-code-core, nop-commons
 │     [第三方: javaparser-core, javaparser-symbol-solver-core]
 │
 ├── nop-code-lang-python ────────────────→ nop-code-core
 │     [第三方: tree-sitter:0.25.3, tree-sitter-python:0.23.4]
 │
 ├── nop-code-lang-typescript ────────────→ nop-code-core
 │     [第三方: tree-sitter:0.25.3, tree-sitter-typescript:0.23.2]
 │
 ├── nop-code-codegen ────────────────────→ nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger
 │
 ├── nop-code-dao ────────────────────────→ nop-api-core, nop-orm
 │     [test: nop-code-codegen]
 │
 ├── nop-code-meta ───────────────────────→ (无 compile 依赖)
 │     [test: nop-code-codegen, nop-code-dao]
 │
 ├── nop-code-service ────────────────────→ nop-code-api, nop-code-dao, nop-code-core,
 │     │                                     nop-code-graph, nop-code-flow,
 │     │                                     nop-code-lang-java, nop-code-lang-python, nop-code-lang-typescript,
 │     │                                     nop-code-meta, nop-biz, nop-config, nop-ioc,
 │     │                                     nop-search-api (optional)
 │     [test: nop-code-codegen, nop-autotest-junit, h2, mysql-connector-j, junit-jupiter, junit-jupiter-params]
 │
 ├── nop-code-web ────────────────────────→ nop-code-meta, nop-code-service, nop-web
 │     [test: nop-code-codegen, nop-ooxml-xlsx, nop-autotest-junit, nop-xlang-debugger, junit-jupiter]
 │
 └── nop-code-app ────────────────────────→ nop-code-service, nop-code-web,
       │                                     nop-quarkus-web-orm-starter, nop-auth-web,
       │                                     nop-auth-service, nop-web-site
       [runtime: quarkus-jdbc-mysql, quarkus-jdbc-h2]
```

## 合规检查总结

| # | 规则 | 检查结果 |
|---|------|---------|
| 1 | api 层不依赖任何业务实现层 | **合规** |
| 2 | dao 层只依赖 api 和 nop-persistence 框架 | **合规** |
| 3 | core 层只依赖 api 和框架核心 | **合规** |
| 4 | service 层依赖 api + core + dao | **合规** |
| 5 | web 层依赖 service（不直接依赖 dao） | **合规** |
| 6 | app 层依赖 web + service | **合规** |
| 7 | codegen 依赖 model 和代码生成工具 | **合规** |
| 8 | meta 依赖 dao | **合规**（test scope） |
| 9 | 不允许循环依赖 | **合规** |
| 10 | 框架特定依赖只出现在 app 模块 | **合规** |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度01-01] | P3 | nop-code-graph/pom.xml, lang-*/pom.xml | jgrapht/tree-sitter 版本硬编码未纳入父 POM 集中管理 |
