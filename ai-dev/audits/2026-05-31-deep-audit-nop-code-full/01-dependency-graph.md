# 审核维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-api 是无源码的空壳模块

- **文件**: `nop-code/nop-code-api/pom.xml` (全文)
- **证据片段**:
  ```xml
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>
  </properties>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
          <version>${nop-entropy.version}</version>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 模块没有 src/ 目录，无任何 Java 源文件或资源文件。整个 nop-code 项目中没有任何模块通过 `<dependency>` 引用 nop-code-api。全项目搜索 `import io.nop.code.api` 返回零结果。
- **风险**: 低。空模块增加少量构建开销，但不影响功能。api 模块在 Nop 架构中通常承载 DTO 和服务接口定义。当前 service 层直接将 DTO 和接口定义在 io.nop.code.service.api 包中。
- **建议**: (1) 如果是预留的但尚未启用，保留现状，注释意图。(2) 如果确认不需要，从父 pom modules 列表中移除。
- **信心水平**: 95%
- **误报排除**: 已搜索全项目所有 Java 文件中的 import io.nop.code.api 和所有 pom.xml 中的 nop-code-api artifactId 引用。
- **复核状态**: 未复核

### [维度01-02] nop-code-api 结构与所有兄弟模块不一致（无 parent、不同 Java 版本）

- **文件**: `nop-code/nop-code-api/pom.xml`, 第 1-21 行
- **证据片段**:
  ```xml
  <!-- 无 parent 声明，java.version=11 -->
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <java.version>11</java.version>  <!-- 父级 nop-entropy 用 17 -->
  </properties>
  ```
  对比兄弟模块：
  ```xml
  <!-- nop-code-core/pom.xml -->
  <parent>
      <artifactId>nop-code</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>1.0.0-SNAPSHOT</version>
  </parent>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 是唯一没有 `<parent>` 声明的子模块，自行管理所有属性和插件配置。其 java.version 为 11，而父级 nop-entropy 使用 17，其余兄弟模块通过 parent 继承 17。
- **风险**: 构建工具链不一致。如果 api 模块有代码，将使用 Java 11 编译而非 Java 17。缺少 parent 继承无法复用父 pom 的 dependencyManagement。当前因为模块为空，风险未体现。
- **建议**: 如果保留 api 模块，应像其他兄弟一样添加 parent 声明并移除冗余配置。
- **信心水平**: 90%
- **误报排除**: 已验证所有其他 12 个子模块均有 parent 声明。
- **复核状态**: 未复核

### [维度01-03] 子模块间依赖版本声明方式不一致（硬编码 vs ${project.version}）

- **文件**: 多个 pom.xml
- **证据片段**:
  ```xml
  <!-- 使用 ${project.version} (flow, lang-python, lang-typescript) -->
  <version>${project.version}</version>
  
  <!-- 硬编码 1.0.0-SNAPSHOT (service, graph, dao, meta, web, app, lang-java 等 ~20 处) -->
  <version>1.0.0-SNAPSHOT</version>
  ```
- **严重程度**: P3
- **现状**: 引用兄弟模块时，部分使用 ${project.version}（推荐），其余硬编码。版本升级时硬编码需要逐一手动修改。
- **风险**: 版本升级时遗漏修改导致构建失败。
- **建议**: 统一使用 ${project.version} 引用兄弟模块。
- **信心水平**: 95%
- **误报排除**: 已通过 grep 验证所有 pom.xml 中的版本声明模式。
- **复核状态**: 未复核

### [维度01-04] nop-code-lang-java 声明了未被直接使用的 nop-commons 编译依赖

- **文件**: `nop-code/nop-code-lang-java/pom.xml`, 第 27-28 行
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-commons</artifactId>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: nop-commons 已通过 nop-code-core 传递引入。lang-java 源码中无 `import io.nop.commons.*`。
- **风险**: 极低。冗余声明增加依赖树理解负担。
- **建议**: 移除直接声明，依赖传递性引入即可。
- **信心水平**: 85%
- **误报排除**: 已搜索 lang-java 所有 Java 源文件中的 import 语句。
- **复核状态**: 未复核

## 依赖图

### 编译期依赖图（仅 nop-code 内部模块间）

```
api (孤立)
core ←── graph ←── flow
  ↑
  ├── lang-java
  ├── lang-python
  └── lang-typescript

codegen (独立)
dao (独立)
meta (纯资源, test scope deps)
service ←── dao + core + graph + flow + lang-java + lang-python + lang-typescript + meta
web ←── meta + service
app ←── service + web + quarkus + auth
```

### 分层合规性

| 规则 | 状态 |
|------|------|
| api 不依赖实现层 | 合规（api 为空但结构正确）|
| dao 只依赖 api + persistence | 合规 |
| core 不依赖 dao | 合规 |
| service 依赖 api + core + dao | 合规 |
| web 不直接依赖 dao | 合规 |
| app 依赖 web + service | 合规 |
| 无循环依赖 | 合规 |
| 框架依赖只在 app | 合规 |

## 总结

整体合规，无 P0/P1/P2 级违规。4 个 P3 发现均为维护性问题。
