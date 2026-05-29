# 维度 01：依赖图与模块边界

**审计日期**: 2026-05-29
**审计范围**: nop-code 全部 14 个子模块的 pom.xml 依赖关系

---

## 第 1 轮（初审）

### [维度01-01] nop-code-api 为空壳模块，API 接口和 DTO 实际放在 nop-code-service 中

- **文件**: `nop-code/nop-code-api/pom.xml` (全文 1-45 行) + `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/`
- **证据片段**:
  ```xml
  <!-- nop-code-api/pom.xml -->
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
      </dependency>
  </dependencies>
  ```
  ```java
  // nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java
  // 178-line service interface with 40+ methods
  ```
- **严重程度**: P2
- **现状**: nop-code-api 在父 pom modules 中声明，但其 src/ 目录完全不存在。实际的 ICodeIndexService 接口和 30+ 个 DTO 类全部放在 nop-code-service 模块中。其中 3 个 DTO 引用了 nop-code-core 的模型类型，但大多数 DTO 是纯 DataBean 仅依赖 nop-api-core。
- **风险**: 后续开发者无法判断新的 API 类型应放在 nop-code-api 还是 nop-code-service，导致约定漂移。空壳模块增加构建时间但无产出。
- **建议**: (A) 将不依赖 nop-code-core 的 DTO 迁移到 nop-code-api，添加 parent 继承；或 (B) 从父 pom modules 中移除 nop-code-api。
- **信心水平**: 高
- **误报排除**: nop-code-api 在 Maven Reactor 中参与构建排序但实际贡献为零，同时对外部 API 契约放置位置发出混乱信号。
- **复核状态**: 未复核

### [维度01-02] nop-code-api 使用独立 POM 且 Java 版本设为 11，与项目其余部分（Java 21）不一致

- **文件**: `nop-code/nop-code-api/pom.xml:5-21`
- **证据片段**:
  ```xml
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>
      <maven.compiler.source>${java.version}</maven.compiler.source>
      <maven.compiler.target>${java.version}</maven.compiler.target>
  </properties>
  ```
  对比其他 12 个子模块：
  ```xml
  <parent>
      <artifactId>nop-code</artifactId>
  </parent>
  ```
- **严重程度**: P2
- **现状**: nop-code-api 是唯一不继承父 pom 的子模块，硬编码 java.version=11，而项目使用 Java 21。
- **风险**: Java 版本不一致会导致编译错误；版本管理脱节，升级时可能遗漏。
- **建议**: 如果保留 nop-code-api，添加 parent 继承，移除手动 properties 和 compiler 配置。
- **信心水平**: 高
- **误报排除**: Java 版本不一致在编译时会产生真实错误，版本管理脱节在升级时会产生真实遗漏。
- **复核状态**: 未复核

### [维度01-03] 模块间依赖版本声明不一致 — 部分硬编码 1.0.0-SNAPSHOT，部分使用 ${project.version}

- **文件**: 多个子模块 pom.xml
- **证据片段**:
  ```xml
  <!-- nop-code-graph/pom.xml:13-16 — 硬编码 -->
  <dependency>
      <artifactId>nop-code-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  ```
  ```xml
  <!-- nop-code-flow/pom.xml:12-16 — 正确使用变量 -->
  <dependency>
      <artifactId>nop-code-core</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 10 个子模块内部依赖使用硬编码版本，只有 3 个使用 ${project.version}。当前功能无影响但版本升级时需修改 20+ 处。
- **风险**: 版本升级时遗漏硬编码的依赖版本，导致构建失败或运行时类路径冲突。
- **建议**: 统一将所有模块间依赖版本改为 ${project.version}。
- **信心水平**: 高
- **误报排除**: ${project.version} 是 Maven 多模块项目的标准实践，硬编码版本是可量化的维护成本。
- **复核状态**: 未复核

## 已验证通过的区域

| 检查项 | 结论 |
|--------|------|
| 循环依赖 | 无 |
| core 层边界 | 通过 |
| dao 层边界 | 通过 |
| service 层边界 | 通过 |
| web→dao 跨层 | 通过 |
| 框架特定依赖隔离 | 通过 |
| meta 模块模式 | 通过 |
| codegen 无内部依赖 | 通过 |
| app 层组合 | 通过 |
| 生成产物引用 | 通过 |
| 隐性耦合 | 未发现 |
