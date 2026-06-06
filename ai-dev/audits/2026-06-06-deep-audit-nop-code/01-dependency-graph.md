# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-codegen 缺少 nop-codegen 框架模块的显式依赖声明

- **文件**: `nop-code/nop-code-codegen/pom.xml:17-34`
- **证据片段**:
```xml
<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-ooxml-xlsx</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-orm</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-graphql-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-xlang-debugger</artifactId>
    </dependency>
</dependencies>
```
- **严重程度**: P3
- **现状**: nop-code-codegen 的测试代码 `NopCodeCodeGen.java` 直接导入并使用了 `io.nop.codegen.XCodeGenerator`，但其 pom.xml 未显式声明对 `nop-codegen`（框架模块，位于 nop-kernel/）的依赖。该类通过传递路径 `nop-code-codegen → nop-graphql-core → nop-codegen` 间接可用，当前构建正常。
- **风险**: 若 nop-graphql-core 未来移除对 nop-codegen 的依赖，nop-code-codegen 的测试编译将静默中断。缺少显式声明也降低了 pom.xml 作为模块契约的可读性，与其他标准模块（如 nop-auth-codegen）不一致。
- **建议**: 在 nop-code-codegen/pom.xml 的 dependencies 中增加 `io.github.entropy-cloud:nop-codegen` 显式声明。
- **信心水平**: 确定
- **误报排除**: nop-codegen 不是平台核心包（nop-api-core、nop-commons、nop-core 等），而是 nop-kernel 下的代码生成工具模块，nop-code-codegen 对其有直接的 import 依赖。标准参考模块 nop-auth-codegen 显式声明了该依赖。
- **复核状态**: 未复核

## 完整依赖图

```
nop-code-api
  └── nop-api-core (框架 API)

nop-code-core
  ├── nop-api-core (框架 API)
  ├── nop-commons (工具库)
  └── nop-core (框架核心)

nop-code-graph
  ├── nop-code-core
  ├── jgrapht-core (外部: 图算法)
  ├── jgrapht-io (外部: 图 I/O)
  └── networkanalysis (外部: 社区检测)

nop-code-flow
  ├── nop-code-core
  └── nop-code-graph

nop-code-lang-java
  ├── nop-code-core
  ├── javaparser-core (外部: Java 解析)
  ├── javaparser-symbol-solver-core (外部: 符号求解)
  └── nop-commons

nop-code-lang-python
  ├── nop-code-core
  ├── tree-sitter (外部: 解析器基础设施)
  └── tree-sitter-python (外部: Python 语法)

nop-code-lang-typescript
  ├── nop-code-core
  ├── tree-sitter (外部: 解析器基础设施)
  └── tree-sitter-typescript (外部: TS 语法)

nop-code-codegen
  ├── nop-ooxml-xlsx (ORM 模型解析)
  ├── nop-orm (ORM 框架)
  ├── nop-graphql-core (GraphQL 代码生成)
  └── nop-xlang-debugger (XLang 调试)
  ⚠ 缺少: nop-codegen (XCodeGenerator 所在模块)

nop-code-dao
  ├── nop-api-core (框架 API)
  ├── nop-orm (ORM 框架)
  └── (test) nop-code-codegen

nop-code-meta
  ├── (test) nop-code-codegen
  └── (test) nop-code-dao

nop-code-service
  ├── nop-code-api
  ├── nop-code-dao
  ├── nop-code-core
  ├── nop-code-graph
  ├── nop-code-flow
  ├── nop-code-lang-java
  ├── nop-code-lang-python
  ├── nop-code-lang-typescript
  ├── nop-code-meta
  ├── nop-biz (BizModel 框架)
  ├── nop-config (配置框架)
  ├── nop-ioc (IoC 框架)
  └── (optional) nop-search-api

nop-code-web
  ├── nop-code-meta
  ├── nop-code-service
  ├── nop-web (Web 框架)
  └── (test) nop-code-codegen, nop-autotest-junit

nop-code-app
  ├── nop-code-service
  ├── nop-code-web
  ├── nop-quarkus-web-orm-starter (Quarkus 运行时)
  ├── nop-auth-web, nop-auth-service
  ├── nop-web-site
  ├── quarkus-jdbc-mysql
  └── quarkus-jdbc-h2
```

## 违规清单

| 序号 | 严重程度 | 模块 | 违规类型 | 说明 |
|------|---------|------|---------|------|
| 01 | P3 | nop-code-codegen | 缺少显式依赖 | 未声明 nop-codegen |

## 合规模块

api, core, graph, flow, lang-java, lang-python, lang-typescript, dao, meta, service, web, app — 均合规

## 总结

nop-code 模块依赖图整体健康，13 个子模块中 12 个完全合规。标准分层完整且无反向依赖。领域扩展层 (core → graph → flow → lang-*) 独立于标准分层，边界清晰。零循环依赖。唯一发现为 P3 级显式依赖缺失。
