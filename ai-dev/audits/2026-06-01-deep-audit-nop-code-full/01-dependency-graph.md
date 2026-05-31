# 维度 01：依赖图与模块边界 — nop-code 模块

## 完整依赖图

```
nop-code-api (standalone, version=1.0.0-SNAPSHOT)
  └── nop-api-core

nop-code-core
  ├── nop-api-core
  ├── nop-commons
  ├── nop-core (框架核心)
  ├── jakarta.inject-api
  └── slf4j-api

nop-code-graph
  └── nop-code-core

nop-code-flow
  ├── nop-code-core
  └── nop-code-graph

nop-code-lang-java
  ├── nop-code-core
  ├── javaparser-core (第三方)
  ├── javaparser-symbol-solver-core (第三方)
  └── nop-commons

nop-code-lang-python
  ├── nop-code-core
  ├── tree-sitter (第三方)
  └── tree-sitter-python (第三方)

nop-code-lang-typescript
  ├── nop-code-core
  ├── tree-sitter (第三方)
  └── tree-sitter-typescript (第三方)

nop-code-codegen
  ├── nop-ooxml-xlsx (框架)
  ├── nop-orm (框架)
  ├── nop-graphql-core (框架)
  └── nop-xlang-debugger (框架)

nop-code-dao
  ├── nop-api-core
  ├── nop-orm (框架)
  └── nop-code-codegen [test]

nop-code-meta
  ├── nop-code-codegen [test]
  └── nop-code-dao [test]

nop-code-service
  ├── nop-code-dao
  ├── nop-code-core
  ├── nop-code-graph
  ├── nop-code-flow
  ├── nop-code-lang-java
  ├── nop-code-lang-python
  ├── nop-code-lang-typescript
  ├── nop-code-codegen [test]
  ├── nop-code-meta
  ├── nop-biz, nop-config, nop-ioc (框架)
  └── nop-search-api [opt]

nop-code-web
  ├── nop-code-meta
  ├── nop-code-service
  └── nop-web (框架)

nop-code-app
  ├── nop-code-service
  ├── nop-code-web
  ├── nop-quarkus-web-orm-starter (框架运行时)
  ├── nop-auth-web, nop-auth-service, nop-web-site
  ├── quarkus-jdbc-mysql, quarkus-jdbc-h2
  └── h2 [test]
```

## 第 1 轮（初审）

### [维度01-01] 兄弟模块版本引用不一致

- **文件**: 多个子模块的 `pom.xml`（nop-code-graph, nop-code-lang-java, nop-code-dao, nop-code-meta, nop-code-service, nop-code-web, nop-code-app 使用硬编码 `1.0.0-SNAPSHOT`；nop-code-flow, nop-code-lang-python, nop-code-lang-typescript 使用 `${project.version}`）
- **证据片段**:
  ```xml
  <!-- 硬编码版本 (graph/pom.xml:13-16) -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  
  <!-- 变量引用 (flow/pom.xml:14-16) -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 13 个子模块中，兄弟模块间引用有两种写法：4 个依赖声明使用 `${project.version}`，约 12 个使用硬编码 `1.0.0-SNAPSHOT`。
- **风险**: 版本升级时（如从 SNAPSHOT 到 release），硬编码引用需逐个手动更新，遗漏会导致构建失败。
- **建议**: 统一所有兄弟模块引用为 `${project.version}`。
- **信心水平**: 确定
- **误报排除**: 这是结构性风险（版本升级时依赖解析失败），不是纯风格问题。但由于 nop-code 是 WIP 模块且当前所有值一致，定级 P3。
- **复核状态**: 未复核

## 分层规则合规检查

| 规则 | 状态 |
|------|------|
| api 不依赖业务实现 | PASS |
| dao 只依赖 api + nop-persistence | PASS |
| core 不依赖 dao | PASS |
| service 依赖 api + core + dao | PASS |
| web 不直接依赖 dao | PASS |
| app 依赖 web + service + 运行时 | PASS |
| codegen 依赖 model + nop-kernel 工具 | PASS |
| meta 依赖 dao（test scope） | PASS |
| 无循环依赖 | PASS |
| 框架运行时依赖只在 app 模块 | PASS |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 01-01 | P3 | 多个子模块 pom.xml | 兄弟模块版本引用风格不一致（硬编码 vs ${project.version}） |
