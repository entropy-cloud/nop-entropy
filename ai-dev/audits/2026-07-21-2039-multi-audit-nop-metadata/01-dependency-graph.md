# 维度01：依赖图与模块边界 — 第1轮（初审）

> 审计模块: nop-metadata

## 完整依赖图

```
nop-entropy (root)
  └── nop-metadata (aggregator POM)
       ├── nop-metadata-core ─────────> nop-api-core
       ├── nop-metadata-api ──────────> [empty - no src/]
       ├── nop-metadata-codegen ──────> nop-orm, nop-ooxml-xlsx,
       │                                nop-graphql-core, nop-xlang-debugger
       ├── nop-metadata-dao ──────────> nop-api-core, nop-metadata-core, nop-orm
       ├── nop-metadata-meta ─────────> (test: nop-metadata-codegen, nop-metadata-dao)
       ├── nop-metadata-service ──────> nop-metadata-core, nop-metadata-dao,
       │                                nop-metadata-meta, nop-biz, nop-http-api,
       │                                nop-biz-file-core, nop-config, nop-ioc,
       │                                nop-search-api, nop-search-lucene (opt),
       │                                nop-job-api, nop-wf-core, nop-wf-meta
       ├── nop-metadata-web ──────────> nop-metadata-meta, nop-metadata-service, nop-web
       └── nop-metadata-app ──────────> nop-metadata-service, nop-metadata-web,
                                        nop-quarkus-web-orm-starter, nop-auth-web,
                                        nop-auth-service, nop-web-amis-editor,
                                        nop-web-site, quarkus-jdbc-mysql, quarkus-jdbc-h2
```

**无循环依赖**。

## 发现清单

### [维度01-01] nop-metadata-api POM 父模块指向 nop-entropy 而非 nop-metadata

- **文件**: `nop-metadata/nop-metadata-api/pom.xml:5-12`
- **证据片段**:
  ```xml
  <parent>
      <artifactId>nop-entropy</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </parent>
  ```
  对比 `nop-auth-api/pom.xml`:
  ```xml
  <parent>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-auth</artifactId>
      <version>2.0.0-SNAPSHOT</version>
      <relativePath>../pom.xml</relativePath>
  </parent>
  ```
- **严重程度**: P2
- **现状**: `nop-metadata-api` 被声明为 `nop-metadata/pom.xml` 的子模块，但其 parent POM 是根 `nop-entropy`，而不是模块聚合器 `nop-metadata`。所有其他子模块的 parent 都是 `nop-metadata`。Nop 平台其他业务模块的 api 子模块 parent 都为各自的模块聚合器。
- **风险**: 不从 `nop-metadata/pom.xml` 继承属性/依赖管理，导致构建行为与兄弟模块不一致。若在 Reactor 外单独构建，Maven 会从仓库解析 parent。
- **建议**: 将 parent 改为 `nop-metadata`，添加 `<relativePath>../pom.xml</relativePath>`。
- **信心水平**: 确定
- **误报排除**: 这是 POM 继承结构违反模块内统一模式。Nop 平台 `nop-auth-api`、`nop-job-api` 等均使用模块级 parent。
- **复核状态**: 未复核

### [维度01-02] nop-metadata-api 模块完全为空但仍被声明和构建

- **文件**: `nop-metadata/nop-metadata-api/pom.xml`，`nop-metadata/pom.xml:29`
- **证据片段**:
  ```xml
  <!-- nop-metadata/pom.xml -->
  <module>nop-metadata-api</module>
  ```
  目录验证：`nop-metadata/nop-metadata-api/src` 不存在。
- **严重程度**: P3
- **现状**: `nop-metadata-api` 模块没有任何源代码或资源文件，仅包含 pom.xml。文档将其列为"跨模块 API 接口定义"。
- **风险**: 不必要的模块加载开销，给开发者造成困惑。
- **建议**: 从 `<modules>` 中移除，或添加注释说明"预留模块"。
- **信心水平**: 确定
- **误报排除**: 不是关于"api 模块应该有什么内容"，而是空模块参与 Reactor 构建。
- **复核状态**: 未复核

### [维度01-03] nop-metadata-dao 依赖 nop-metadata-codegen（test scope）但无任何测试代码

- **文件**: `nop-metadata/nop-metadata-dao/pom.xml:30-35`
- **证据片段**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-codegen</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
      <scope>test</scope>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: dao 模块没有 `test/` 目录，无测试代码。test scope 依赖冗余。
- **风险**: 维护者看到 test scope 依赖会期望测试代码存在。构建时增加不必要的依赖解析开销。
- **建议**: 移除该冗余依赖声明。
- **信心水平**: 确定
- **误报排除**: 不是 test scope 本身有问题，而是声明了但没有对应测试代码使用它。
- **复核状态**: 未复核

### [维度01-04] nop-metadata-service 对 nop-metadata-meta 的 compile scope 依赖可改为 runtime

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:46-49`
- **证据片段**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-meta</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: meta 模块仅含资源文件（XMeta、dict、i18n），无 Java 代码。service 模块 compile scope 依赖 meta 是为了运行时加载 VFS 资源，scope 语义不精确。
- **风险**: 极低。compile scope 能工作但可能误传依赖给下游消费者。
- **建议**: 将 scope 改为 `runtime`。
- **信心水平**: 很可能
- **误报排除**: scope 粒度不够精确的优化。
- **复核状态**: 未复核

### [维度01-05] nop-metadata 全模块未注册到 nop-bom

- **文件**: `nop-bom/pom.xml`
- **证据片段**: BOM 中无任何 `nop-metadata-*` 条目。同级别模块 `nop-stream` 已注册。
- **严重程度**: P2
- **现状**: `nop-bom` 中 `nop-metadata` 所有子模块缺失。外部应用通过 BOM 引入时无法自动获得版本管理。
- **风险**: BOM 驱动的版本一致性保障不覆盖 nop-metadata 模块。
- **建议**: 在 `nop-bom/pom.xml` 的 `<dependencyManagement>` 中添加所有 nop-metadata 子模块条目。
- **信心水平**: 确定
- **误报排除**: 同一仓库、同一分组下的模块应保持一致的版本管理策略。
- **复核状态**: 未复核

### [维度01-06] nop-metadata-codegen 未声明对 nop-metadata-core 的依赖（推测）

- **文件**: `nop-metadata/nop-metadata-codegen/pom.xml`
- **证据片段**:
  ```xml
  <!-- codegen 依赖 nop-orm, nop-ooxml-xlsx, nop-graphql-core, nop-xlang-debugger -->
  <!-- 缺少 nop-metadata-core -->
  ```
- **严重程度**: P3
- **现状**: codegen 模块未声明对本模块 core 的依赖。如果 codegen 模板引用 core 中的常量，运行时可能 ClassNotFoundException。
- **风险**: 需确认 codegen 模板是否引用了 core 中的内容。
- **建议**: 确认后添加依赖（至少 test scope）。
- **信心水平**: 有趣的猜测
- **误报排除**: 推测性质，需进一步验证。
- **复核状态**: 未复核

### [维度01-07] nop-metadata-service 因单一功能 QualityAlertWorkflowService 依赖 nop-wf

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:27-33`
- **证据片段**:
  ```xml
  <dependency><artifactId>nop-wf-core</artifactId></dependency>
  <dependency><artifactId>nop-wf-meta</artifactId></dependency>
  ```
- **严重程度**: P3（观察性）
- **现状**: service 模块因质量告警功能依赖 `nop-wf-core` 和 `nop-wf-meta`。无反向依赖，无循环依赖。
- **风险**: 低。如果 nop-wf API 变更需要适配。
- **建议**: 考虑通过 SPI/事件机制解耦，目前可接受。
- **信心水平**: 确定
- **误报排除**: 架构观察，非违规。
- **复核状态**: 未复核

## 分层规则对照

| 规则 | 结果 |
|------|------|
| 1. api 不依赖业务实现层 | 通过 |
| 2. dao 只依赖 api 和框架 | 通过 |
| 3. core 只依赖 api 和框架核心 | 通过 |
| 4. service 依赖 api + core + dao | 通过 |
| 5. web 依赖 service | 通过 |
| 6. app 依赖 web + service | 通过 |
| 7. codegen 依赖模型和框架 codegen 工具 | 基本通过 |
| 8. meta 依赖 dao | 通过 |
| 9. 无循环依赖 | 通过 |
| 10. 框架特定依赖只出现在 app | 通过 |

## 总结评估

依赖图整体结构良好，遵循 Nop 平台标准分层模式。无循环依赖，无架构红线突破。主要问题（P2）：(1) nop-metadata-api 的 parent 指向错误；(2) nop-metadata 未注册到 nop-bom。其余为 P3 低优先级问题。
