# 维度 01：依赖图与模块边界 — 审计报告

> 初审子 agent 输出，待复核

## 1. 完整依赖图（文本表示）

```
nop-metadata-api                  compile: nop-api-core
                                   
nop-metadata-core                 compile: nop-api-core

nop-metadata-codegen              compile: nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger

nop-metadata-dao                  compile: nop-api-core, nop-orm, nop-metadata-core
                                   test:   nop-metadata-codegen

nop-metadata-meta                 compile: (无)
                                   test:   nop-metadata-codegen, nop-metadata-dao

nop-metadata-service              compile: nop-metadata-dao, nop-metadata-meta,
                                            nop-biz, nop-http-api, nop-biz-file-core,
                                            nop-config, nop-ioc, nop-sys-dao, nop-job-api
                                   test:   nop-metadata-codegen, nop-job-local,
                                            nop-autotest-junit, junit-jupiter, h2,
                                            mysql-connector-j, mockito-core

nop-metadata-web                  compile: nop-metadata-meta, nop-metadata-service, nop-web
                                   test:   nop-metadata-codegen, nop-ooxml-xlsx,
                                            junit-jupiter, nop-autotest-junit, nop-xlang-debugger

nop-metadata-app                  compile: nop-quarkus-web-orm-starter,
                                            nop-metadata-service, nop-metadata-web,
                                            nop-auth-web, nop-auth-service,
                                            nop-web-amis-editor, nop-web-site,
                                            quarkus-jdbc-mysql, quarkus-jdbc-h2
```

## 2. 发现条目

### [维度01-01] nop-metadata-dao 编译依赖 nop-metadata-core，违反规则 2

- **文件**: `nop-metadata/nop-metadata-dao/pom.xml:25-28`
- **证据**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-core</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
  `OrmModelImporter.java` 第 54 行引用了 `_NopMetadataCoreConstants.MODULE_STATUS_DRAFTING`。
- **严重程度**: P2
- **现状**: dao 层在 compile 范围依赖 core 模块，标准分层规则要求 dao 只依赖 api 和 persistence 框架。nop-metadata-core 既不是 api 也不是 persistence 框架。
- **风险**: 虽目前危害有限（core 仅含常量），但此依赖开放了 core 耦合进 dao 域的可能性，在模块演化中可能产生扩散风险。
- **建议**: 将 nop-metadata-core 常量合并到 nop-metadata-dao 的 `NopMetadataDaoConstants`，移除 core 模块的依赖。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"问题，直接违反了审计规则 2 的明文。

### [维度01-02] nop-metadata-service 编译依赖 nop-sys-dao，零 Java 引用

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:52-55`
- **证据**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-sys-dao</artifactId>
  </dependency>
  ```
  全模块代码搜索 `io.nop.sys` 以及 `nop-sys`，service 模块的主代码、xbiz 文件、beans.xml 配置中均无任何对 nop-sys-dao 的实际引用。
- **严重程度**: P3
- **现状**: nop-sys-dao 在 compile 范围声明但无任何 Java 引用。无注释说明依赖理由。
- **风险**: 如果不需要编译时存在，应使用 runtime scope，否则对下游消费者传递不必要的编译期符号依赖。跨模块直接依赖另一个业务模块的 DAO 层可能造成隐式实体注册冲突。
- **建议**: 分析 nop-sys-dao 的实际运行时作用。如果是为了系统字典等框架自动发现的 SPI，应转换为 `<scope>runtime</scope>` 并加注释说明意图。
- **信心水平**: 高

### [维度01-03] nop-metadata-codegen 编译依赖 nop-xlang-debugger，零 Java 引用

- **文件**: `nop-metadata/nop-metadata-codegen/pom.xml:30-33`
- **证据**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang-debugger</artifactId>
  </dependency>
  ```
  nop-xlang-debugger 属于 dev-tools 模块组，codegen 代码无任何对该包的 Java 引用。无注释说明。
- **严重程度**: P3
- **现状**: 开发工具依赖泄漏到 codegen 模块的公共 API 中。
- **风险**: codegen 的消费者将意外获得 debugger 的传递依赖。应与 nop-job-api 依赖一样添加注释或改为 test scope。
- **建议**: 将依赖范围改为 test，或添加注释明确为何需要此依赖。
- **信心水平**: 高

### [维度01-04] nop-metadata-core 模块过度轻量

- **文件**: `nop-metadata/nop-metadata-core/src/main/java/io/nop/metadata/core/NopMetadataCoreConstants.java`
- **证据**: core 模块仅包含一个 5 行的常量接口及其 generated 父类。唯一消费点是 dao 模块 `OrmModelImporter.java:54` 引用 `MODULE_STATUS_DRAFTING`。
- **严重程度**: P3
- **现状**: 8 子模块中有一个几乎为空的模块，仅为了给 dao 提供一条常量。且 dao 为此编译依赖了 core，造成发现-01 的架构红线违反。
- **风险**: 模块膨胀增加构建时间和认知负载。维护者可能误认为 core 是"业务逻辑核心层"并向其中添加 dao 依赖，破坏分层。
- **建议**: 将常量合并到 nop-metadata-dao 的 `NopMetadataDaoConstants` 中，移除 nop-metadata-core 模块。
- **信心水平**: 高

### [维度01-05] nop-metadata-service/web 对 meta 使用 compile 而非 runtime scope

- **文件**: `nop-metadata/nop-metadata-service/pom.xml:27-31`, `nop-metadata/nop-metadata-web/pom.xml:17-20`
- **证据**:
  ```xml
  <!-- service pom.xml -->
  <dependency>
      <artifactId>nop-metadata-meta</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
  meta 模块仅包含 _vfs 资源文件（XMeta 定义、dict yaml、i18n 文件），无 Java 源代码。
- **严重程度**: P3
- **现状**: 纯资源模块使用 compile 范围，应使用 runtime。
- **风险**: 极低，meta 模块无 compile 依赖，当前无实际危害。
- **建议**: 将 meta 依赖改为 `<scope>runtime</scope>`。
- **信心水平**: 中

## 3. 合规检查清单

| 规则 | 描述 | 结果 |
|------|------|------|
| 规则1 | api 不依赖业务实现层 | ✅ 通过 |
| 规则2 | dao 只依赖 api 和 nop-persistence 框架 | ❌ **发现-01** |
| 规则3 | core 只依赖 api 和框架核心 | ✅ 通过 |
| 规则4 | service 依赖 api + core + dao | ⚠️ 部分通过 |
| 规则5 | web 依赖 service，不直接依赖 dao | ✅ 通过 |
| 规则6 | app 依赖 web + service | ✅ 通过 |
| 规则7 | codegen 依赖 model 和 codegen 工具 | ✅ 通过 |
| 规则8 | meta 依赖 dao | ✅ 通过 |
| 规则9 | 无循环依赖 | ✅ 通过 |
| 规则10 | 框架依赖仅 app | ✅ 通过 |

## 4. 总结评估

nop-metadata 模块的依赖结构整体健康，无循环依赖，Quarkus 框架依赖正确隔离在 app 模块。最重要的发现是 `nop-metadata-dao` 编译依赖 `nop-metadata-core`（P2），违反了"dao 层只依赖 api 和 persistence 框架"的规则。其余发现均为 scope 不当或冗余依赖（P3）。
