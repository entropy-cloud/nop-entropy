# 维度 01：依赖图与模块边界 — nop-code 模块审计报告

**审计目标**: nop-code 模块（14 个子模块）
**审计基线**: live code（pom.xml + 实际 import 引用）

---

## 第 1 轮（初审）

### [维度01-01] nop-code-core 未依赖 nop-code-api（与标准分层指南的偏离）

- **文件**: `nop-code/nop-code-core/pom.xml:11-27`
- **证据片段**:
  ```xml
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
      </dependency>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-commons</artifactId>
      </dependency>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-core</artifactId>
      </dependency>
      <!-- 无 nop-code-api 依赖 -->
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: 标准分层规则要求"core 依赖 api"，但 nop-code-core 未声明对 nop-code-api 的依赖。经代码引用验证，nop-code-core 中不存在任何 `import io.nop.code.api.*` 引用（0 个匹配），core 的职责是定义领域模型（CodeSymbol、CallGraph、SymbolTable 等）和数据结构，不使用外部 DTO 类型。
- **风险**: 低。当前架构中 core 完全独立于 api，两个模块的职责边界清晰。但若未来 core 需要引用 api 中的类型（如 DTO），需手动添加依赖。
- **建议**: 无需修复。这是 nop-code 模块独特的分层设计（core 提供领域模型，api 仅定义外部 DTO），偏离有合理原因。建议在模块文档中注明此设计选择。
- **信心水平**: 高（95%）
- **误报排除**: 这不是"看起来不优雅"的发现。标准规则明确说"core 依赖 api"，这里有可量化的偏离（0 个 import 引用 vs 规则要求），但经交叉验证确认偏离无害。
- **复核状态**: 未复核

### [维度01-02] nop-code-meta 所有内部依赖均为 test scope（与标准 meta→dao 编译期依赖模式不同）

- **文件**: `nop-code/nop-code-meta/pom.xml:15-28`
- **证据片段**:
  ```xml
  <dependencies>
      <dependency>
          <artifactId>nop-code-codegen</artifactId>
          <groupId>io.github.entropy-cloud</groupId>
          <version>${project.version}</version>
          <scope>test</scope>
      </dependency>
      <dependency>
          <artifactId>nop-code-dao</artifactId>
          <groupId>io.github.entropy-cloud</groupId>
          <version>${project.version}</version>
          <scope>test</scope>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: 标准 Nop 业务模块中，meta 通常在 compile scope 依赖 dao。nop-code-meta 将所有内部依赖（codegen、dao）放在 test scope，通过 exec-maven-plugin 的 `classpathScope=test` 在构建期执行代码生成。nop-code-meta 无 Java 源码，仅包含生成的 XMeta/i18n/dict 资源文件，运行时无需任何 Java 类依赖。
- **风险**: 低。构建和运行正常。但如果有人尝试在编译期引用 nop-code-meta 中的 Java 类型（当前不存在），会遇到 classpath 缺失问题。
- **建议**: 无需修复。这是一种有效的变体模式——纯资源容器模块不需要编译期依赖。构建链路（codegen → dao → meta）通过 test classpath 正确衔接。
- **信心水平**: 高（95%）
- **误报排除**: 这不是"看起来不优雅"。标准 meta→dao 模式是 compile scope，这里有结构性偏离。但偏离有明确的技术原因（纯资源模块 + exec-maven-plugin test classpath），且构建链路验证正确。
- **复核状态**: 未复核

---

## 完整内部依赖图

```
                          ┌─────────────────┐
                          │  nop-code-api    │ → nop-api-core (框架)
                          └─────────────────┘
                                  ▲
                                  │
                          ┌─────────────────┐
                          │  nop-code-core   │ → nop-api-core, nop-commons, nop-core (框架)
                          └─────────────────┘
                           ▲    ▲    ▲    ▲
                 ┌─────────┘    │    │    └──────────┐
                 │              │    │               │
         ┌───────────────┐     │    │     ┌─────────────────────┐
         │ nop-code-graph │     │    │     │ nop-code-lang-java  │
         └───────────────┘     │    │     │ nop-code-lang-python│
                  ▲            │    │     │ nop-code-lang-ts    │
                  │            │    │     └─────────────────────┘
          ┌──────────────┐     │    │
          │ nop-code-flow │─────┘    │
          └──────────────┘          │
                                    │
┌──────────────────┐     ┌─────────────────────┐
│ nop-code-codegen │     │    nop-code-dao      │ → nop-api-core, nop-orm (框架)
│ (无内部依赖)      │     │    (无编译期内部依赖) │
└──────────────────┘     └─────────────────────┘
         ▲ test                ▲ test
         │                     │
         │              ┌─────────────────┐
         │              │  nop-code-meta   │
         │              │ (无编译期内部依赖) │
         │              └─────────────────┘
         │                     ▲ test
         │                     │
         └───────┬─────────────┘
                 │
         ┌──────────────────────────────────────────────────────────────┐
         │                      nop-code-service                        │
         │ → nop-code-api, nop-code-dao, nop-code-core,                │
         │   nop-code-graph, nop-code-flow,                            │
         │   nop-code-lang-java, nop-code-lang-python,                 │
         │   nop-code-lang-typescript, nop-code-meta                   │
         │ + 框架: nop-biz, nop-config, nop-ioc                        │
         │ + optional: nop-search-api                                   │
         └──────────────────────────────────────────────────────────────┘
                                  ▲
                          ┌───────┴──────────┐
                          │   nop-code-web    │ → nop-code-meta, nop-code-service, nop-web
                          └──────────────────┘
                                  ▲
                          ┌───────┴──────────┐
                          │   nop-code-app    │ → nop-code-service, nop-code-web
                          │                    │   nop-auth-web, nop-auth-service,
                          │                    │   nop-web-site, Quarkus runtime
                          └──────────────────┘
```

## 违规清单（按严重程度排序）

| 序号 | 严重程度 | 发现 | 模块 |
|------|---------|------|------|
| 01 | P3 | nop-code-core 未依赖 nop-code-api（有理偏离） | nop-code-core |
| 02 | P3 | nop-code-meta 全部内部依赖为 test scope（有效变体） | nop-code-meta |

**P0/P1 发现**: 无。

## 合规模块清单

| 模块 | 合规说明 |
|------|---------|
| nop-code-api | 纯外部 DTO 契约，仅依赖框架 API 核心 |
| nop-code-dao | 纯 ORM 层，仅依赖 nop-api-core + nop-orm |
| nop-code-graph | 正确依赖 nop-code-core，无反向/跨层依赖 |
| nop-code-flow | 正确依赖 nop-code-core + nop-code-graph |
| nop-code-lang-java/python/typescript | 正确依赖 nop-code-core |
| nop-code-codegen | 正确依赖框架代码生成工具，无内部依赖 |
| nop-code-service | 正确聚合所有下层模块 + 框架服务层 |
| nop-code-web | 正确依赖 service + meta，无直接 dao 引用 |
| nop-code-app | Quarkus 运行时正确隔离 |

## 总结评估

nop-code 模块的依赖结构整体**健康**，严格遵守了 Nop 平台的标准分层规则。依赖图是严格的 DAG（无环），层级边界清晰。两个 P3 发现均为有理偏离而非架构缺陷。
