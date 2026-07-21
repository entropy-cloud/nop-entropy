# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-metadata-codegen 将开发调试工具声明为 compile scope 依赖

- **文件**: `nop-metadata/nop-metadata-codegen/pom.xml:30-33`
- **证据代码片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang-debugger</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: `nop-metadata-codegen` 声明了 `nop-xlang-debugger` 作为 compile 依赖，但该模块位于 `nop-dev-tools/nop-xlang-debugger`，是一个 XLang 模板调试工具。在整个 nop-metadata 代码库中，没有任何 Java 文件 `import` 来自 `io.nop.xlang.debugger` 的类。其他 codegen 模块（如 `nop-auth-codegen`、`nop-job-codegen`）均未以 compile 方式依赖 `nop-xlang-debugger`。
- **风险**: 中等。虽然 codegen 模块的消费者通常不会将 codegen 作为 compile 依赖带出，但 `nop-xlang-debugger` 作为 dev-tools 类模块被声明为 compile scope 会使其不经意的消费者承担不必要的传递依赖。
- **建议**: 将 `nop-xlang-debugger` 的 scope 改为 `test`。
- **信心水平**: 高
- **误报排除**: 其他 Nop codegen 模块均未以 compile 方式依赖此模块，且模块内无任何 import 引用。
- **复核状态**: 未复核

### [维度01-02] nop-metadata-service 声明了 nop-biz-file-core 和 nop-config 作为 compile 依赖但无直接引用

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:58-65`
- **证据代码片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-biz-file-core</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-config</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `nop-biz-file-core`、`nop-config` 在 `nop-metadata-service` 的所有 Java 文件、beans.xml、xmeta、xbiz 中无任何 import 或引用。对比之下，`nop-http-api` 被正确使用（`IHttpClient` 在 `CheckpointActionDispatcher.java` 中通过 webhook 分发引用），`nop-biz` 被正确使用（`CrudBizModel`）。
- **风险**: 低。`nop-config` 可能通过 `nop-biz` 的传递依赖链稳定获得。`nop-biz-file-core` 可能是历史遗留或预留给未来文件导入功能。主要风险是在未来重构时这些依赖会成为"幽灵"声明。
- **建议**: 对 `nop-biz-file-core` 和 `nop-config` 添加注释说明用途（如 `<!-- runtime: IoC annotation processing -->`），或确认未使用后移除。
- **信心水平**: 中
- **误报排除**: `nop-biz-file-core` 既无 import 引用也不属于平台核心包（不被排除），它在模块间的角色与其他被排除的平台核心包不可类比。
- **复核状态**: 未复核

## 完整依赖图

```text
nop-metadata (root pom.xml)
  │
  ├── nop-metadata-api ─────────────────► nop-api-core
  ├── nop-metadata-core ────────────────► nop-api-core
  ├── nop-metadata-codegen
  │     ├── nop-ooxml-xlsx
  │     ├── nop-orm
  │     ├── nop-graphql-core
  │     └── nop-xlang-debugger
  ├── nop-metadata-dao
  │     ├── nop-api-core
  │     └── nop-orm
  ├── nop-metadata-meta
  │     └── (无 compile 依赖)
  ├── nop-metadata-service
  │     ├── nop-metadata-core, nop-metadata-dao, nop-metadata-meta
  │     ├── nop-biz, nop-http-api, nop-biz-file-core, nop-config, nop-ioc
  │     ├── nop-wf-core, nop-wf-meta, nop-sys-dao, nop-job-api
  │     └── 测试依赖
  ├── nop-metadata-web
  │     ├── nop-metadata-meta, nop-metadata-service
  │     └── nop-web
  └── nop-metadata-app
        ├── nop-quarkus-web-orm-starter, nop-metadata-service, nop-metadata-web
        ├── nop-auth-web, nop-auth-service
        └── quarkus-jdbc-mysql, quarkus-jdbc-h2
```

## 合规检查

| 规则 | 结果 |
|------|------|
| 1. api 不依赖业务实现层 | ✅ 合规 |
| 2. dao 仅依赖 api + nop-persistence | ✅ 合规 |
| 3. core 仅依赖 api + 框架核心 | ✅ 合规 |
| 4. service 依赖 api + core + dao | ✅ 合规 |
| 5. web 依赖 service，不直接依赖 dao | ✅ 合规 |
| 6. app 依赖 web + service + 运行时框架 | ✅ 合规 |
| 7. codegen 依赖模型 + 生成工具 | ✅ 合规 |
| 8. meta 依赖 dao | ✅ 合规（通过 test scope exec 运行） |
| 9. 无循环依赖 | ✅ 合规 |
| 10. 框架特定依赖仅 app 模块 | ✅ 合规 |

## 深挖第 2 轮追加

### [维度01-03] nop-metadata-service 声明了 nop-sys-dao 作为 compile 依赖但无任何代码引用

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:70-73`
- **证据代码片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-sys-dao</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 搜索整个 nop-metadata 模块，`io.nop.sys.dao.*` 和 `nop-sys-dao` 零匹配。该依赖在模块内无任何代码使用。且 `nop-auth-service` 使用 `nop-sys-service` 而非 `nop-sys-dao`，遵循标准的 service→service 依赖模式。
- **风险**: 无用依赖增加构建时间和制品体积。
- **建议**: 移除该依赖声明，或在确需 sys 集成时改用 `nop-sys-service`。
- **信心水平**: 高
- **误报排除**: nop-auth-service 使用 nop-sys-service，遵循 service→service 模式。
- **复核状态**: 未复核

### [维度01-04] nop-metadata-service 的 nop-biz-file-core 依赖无代码引用，非标准 service 模块依赖

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:58-61`
- **证据代码片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-biz-file-core</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `nop-biz-file-core` 不是标准 service 模块依赖——`nop-auth-service` 和 `nop-wf-service` 均未声明此依赖。整个 nop-metadata 模块无任何 Java 或 XML 文件引用 `io.nop.biz.file.*`。
- **风险**: 多余依赖增加耦合和构建时间。
- **建议**: 移除 `nop-biz-file-core` 依赖声明。
- **信心水平**: 高
- **误报排除**: 对比 nop-auth-service 和 nop-wf-service 均无此依赖。
- **复核状态**: 未复核

### [维度01-05] nop-metadata-dao 直接使用 nop-core 类型但未显式声明依赖

- **文件**: `nop-metadata/nop-metadata-dao/pom.xml:15-23`
- **证据代码片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-api-core</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-orm</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 多个 `INopMeta*Biz` 接口导入 `io.nop.core.context.IServiceContext`（属于 `nop-core`），但 `nop-core` 未在 `nop-metadata-dao/pom.xml` 中声明，仅通过 `nop-orm` 传递性引入。
- **风险**: 若 `nop-orm` 调整传递依赖链，dao 模块可能编译失败。
- **建议**: 在 `nop-metadata-dao/pom.xml` 中添加 `nop-core` 作为 compile 依赖。
- **信心水平**: 高
- **误报排除**: Maven 最佳实践要求直接引用的类型应有显式依赖声明。
- **复核状态**: 未复核

### [维度01-06] 校准说明：nop-xlang-debugger 的 compile scope 是平台标准模式（驳回）

- **说明**: 经跨模块对比，`nop-auth-codegen` 和 `nop-wf-codegen` 均同样声明了 `nop-xlang-debugger` 作为 compile 依赖。这是 Nop 平台 codegen 模块的标准模式——在 Maven exec 插件运行时 classpath 上需要 `nop-xlang-debugger` 用于 XLang 模板调试。
- **结论**: [维度01-01] 驳回。该依赖是平台约定模式。

## 总结评估

依赖图整体符合 Nop 平台标准分层架构。深挖后确认 1 项驳回（代码生成器标准模式），新增 3 项发现（P3）。最终保留发现：维度01-02 (P3)、01-03 (P3)、01-04 (P3)、01-05 (P3)。
