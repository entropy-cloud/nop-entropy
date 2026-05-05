# Checkstyle / SonarQube 代码质量配置

> Status: resolved
> Last Reviewed: 2026-05-05
> Scope: 全项目 Java 源码（`src/main/java`，排除自动生成文件）
> Related: `checkstyle.xml`、`pmd-ruleset.xml`、`spotbugs-exclude.xml`、`pom.xml`（qa profile）

## 概述

Nop Entropy 使用 Checkstyle + PMD + SpotBugs 三件套做本地代码质量审计，SonarQube 做深度分析。
所有工具仅通过主动调用触发（`mvn checkstyle:check` 或 `mvn sonar:sonar`），不参与日常 `mvn install` 流程。

配置文件：

| 文件 | 用途 |
|------|------|
| `checkstyle.xml` | Checkstyle 规则集 |
| `pmd-ruleset.xml` | PMD 规则集 |
| `spotbugs-exclude.xml` | SpotBugs 排除过滤器 |
| `pom.xml` 中 `qa` profile | Maven 插件配置 |

## 运行方式

```bash
# Checkstyle（不需要编译）
./mvnw org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:check \
  -Dcheckstyle.config.location="file://$(pwd)/checkstyle.xml" \
  -Dcheckstyle.excludes="**/_gen/**,**/_*.java" \
  -Dcheckstyle.failOnViolation=false

# PMD（不需要编译）
./mvnw pmd:pmd -Pqa

# SpotBugs（需要先编译）
./mvnw compile spotbugs:check -Pqa

# SonarQube（需要 SonarQube Server）
./mvnw sonar:sonar -Dsonar.host.url=... -Dsonar.login=...
```

## 自动生成文件排除

Nop 平台大量使用代码生成，以下文件不参与质量检查：

| 排除模式 | 含义 | 示例 |
|----------|------|------|
| `**/_gen/**` | `_gen` 目录下所有文件 | `_gen/XxxGen.java` |
| `**/_*.java` | `_` 开头的 Java 文件 | `_XxxOrmModel.java`、`_XxxGraphQLObj.java` |
| `**/_*.xml` | `_` 开头的 XML 文件 | `_app.orm.xml`、`_beans.spring.xml` |
| `**/*Errors.java` | 错误码文件 | `NopAuthErrors.java` |
| `**/*Configs.java` | 配置常量文件 | `NopCoreConfigs.java` |
| `**/*Constants.java` | 常量文件 | `NopRpcConstants.java` |
| `**/parse/antlr/**` | ANTLR 生成的解析器 | `XLangParser.java` |

排除在以下位置统一配置：

- **SonarQube**：`pom.xml` 中 `sonar.exclusions`、`sonar.coverage.exclusions`、`sonar.cpd.exclusions`
- **Checkstyle**：命令行 `-Dcheckstyle.excludes`
- **SpotBugs**：`spotbugs-exclude.xml`
- **Jacoco**：`pom.xml` 中 coverage profile 的 `excludes`

## Checkstyle 规则设计

### 设计原则

1. **只检测真正的 BUG 和潜在缺陷，不强制编码风格**
2. **风格由开发者自行把握，不做门禁拦截**
3. **复杂度阈值按项目实际代码水平设定，避免大量噪音**
4. **与 SonarQube 互补**：Checkstyle 做编译前快速拦截，SonarQube 做深度分析

### 启用的规则及原因

#### BUG 检测（质量红线）

| 规则 | 检测内容 | 启用原因 |
|------|----------|----------|
| `EmptyBlock` | 空代码块必须有注释说明原因 | 防止遗漏的空逻辑 |
| `EmptyCatchBlock` | 空 catch 块仅允许变量名 `expected` | 防止吞掉异常 |
| `CovariantEquals` | 定义了 `equals()` 但未覆盖 `Object.equals()` | 常见 BUG |
| `NoFinalizer` | 禁止 `finalize()` 方法 | 性能和正确性问题 |
| `StringLiteralEquality` | 字符串用 `==` 比较而非 `equals()` | 典型 BUG |
| `IllegalToken` (LITERAL_NATIVE) | 禁止 native 方法 | 安全和可移植性 |
| `UnusedImports` | 未使用的 import | 代码整洁 |
| `RedundantImport` | 冗余 import | 代码整洁 |
| `MissingDeprecated` | `@Deprecated` 标注一致性 | API 演进可追踪 |
| `RegexpSingleline` (System.out/err) | 核心模块禁止 System.out | 项目规范：使用 SLF4J |

#### 复杂度控制（按项目实际水平设定）

| 规则 | 当前值 | 原值 | 调整原因 |
|------|--------|------|----------|
| `CyclomaticComplexity` | **15** | 10 | 框架代码逻辑分支多，10 产生大量噪音 |
| `MethodLength` | **150 行** | 150 | 未变，项目大部分方法在 50 行内 |
| `ParameterNumber` | **7 个** | 7 | 未变，Nop API 方法参数较多 |
| `AnonInnerLength` | **40 行** | 20 | Lambda/Stream 使用广泛，20 太严格 |
| `MagicNumber` | 允许 -1,0,1,2,**3,10,100** | -1,0,1,2 | 框架代码大量使用常见字面量 |
| `IllegalThrows` | 允许 `throws Exception` | 禁止 Exception | Nop 统一使用 NopException 体系 |
| `AvoidStarImport` | 允许 `import static xxx.*` | 禁止所有星号导入 | 项目常用静态导入 |

### 删除的规则及原因

以下规则曾存在于旧 `checkstyle.xml` 中，经审计后删除。每条删除都有明确理由：

#### 与项目架构冲突

| 删除的规则 | 旧违规数 | 删除原因 |
|-----------|---------|----------|
| `DesignForExtension` | 11,888 | Nop 核心设计理念就是可扩展（Delta 定制、IoC 注入），这个规则与架构直接冲突 |
| `VisibilityModifier` | 705 | NopIoC **不支持** private 字段注入，protected/package-private 是设计要求，不是违规（参见 AGENTS.md） |
| `FinalParameters` | 21,890 | 项目风格不使用 `final` 修饰参数，20 年内不会改变 |
| `NeedBraces` | 6,729 | 项目中有大量单行 if/return/guard 语句，这是风格偏好不是 BUG |

#### Javadoc 相关（全部删除）

| 删除的规则 | 旧违规数 | 删除原因 |
|-----------|---------|----------|
| `MissingJavadocMethod` | 12,784 | 低代码平台方法名即文档，不需要每个方法写 Javadoc |
| `JavadocVariable` | 7,507 | 字段 Javadoc 对代码质量无实际贡献 |
| `JavadocStyle` | 1,832 | Javadoc 格式不影响运行正确性 |
| `JavadocMethod` | 1,818 | 同 MissingJavadocMethod |
| `JavadocPackage` | — | package-info.java 不是必需的 |
| `JavadocType` | — | 类级别 Javadoc 非必需 |
| `HideUtilityClassConstructor` | — | 项目中工具类不一定需要私有构造器 |
| `Naming` 整个模块 | — | 命名规则项目已合规（PascalCase/camelCase），无需每次构建检查 |

#### 格式化相关（交给 IDE/Formatter）

| 删除的规则 | 旧违规数 | 删除原因 |
|-----------|---------|----------|
| `LineLength` (80字符) | 17,113 | 80 字符在现代显示器上过时，项目大量行超 80。格式化交给 IDE |
| `LeftCurly` / `RightCurly` | 890+1431 | 大括号位置是风格偏好，不影响正确性 |
| `WhitespaceAfter` / `WhitespaceAround` | 3,344+3,121 | 空格格式交给 IDE 自动处理 |
| `ParenPad` | 1,164 | 括号内空格是风格偏好 |
| `OperatorWrap` | 1,431 | 运算符换行位置是风格偏好 |
| `NewlineAtEndOfFile` | 1,270 | 对代码质量无影响 |
| `RegexpSingleline`（两处） | 2,149 | 合并为一处，仅检测 `System.(out\|err).print` |

#### 逻辑检查（放宽或删除）

| 删除的规则 | 旧违规数 | 删除原因 |
|-----------|---------|----------|
| `HiddenField` | 3,273 | 构造器和 setter 中变量遮蔽是 Java 惯用法，项目大量使用 |
| `JavaNCSS` | — | 已有 `MethodLength` 和 `CyclomaticComplexity` 覆盖，重复检测 |
| `MethodName` | 1,048 | 项目方法命名已合规，规则只产生噪音 |
| `ConstantName` | 538 | 项目常量命名已合规 |

### 审计数据背景

2026-05-05 对 `nop-kernel` 模块（16 个子模块，~7095 个 Java 源文件）运行旧 checkstyle.xml：

- **旧配置**：106,708 条违规
- **新配置**：0 条违规

违规类型分布（旧配置，Top 10）：

| 排名 | 规则 | 数量 | 新配置处理 |
|------|------|------|-----------|
| 1 | FinalParameters | 21,890 | 删除 |
| 2 | LineLength | 17,113 | 删除 |
| 3 | MissingJavadocMethod | 12,784 | 删除 |
| 4 | DesignForExtension | 11,888 | 删除 |
| 5 | JavadocVariable | 7,507 | 删除 |
| 6 | NeedBraces | 6,729 | 删除 |
| 7 | MagicNumber | 3,761 | 放宽 |
| 8 | WhitespaceAfter | 3,344 | 删除 |
| 9 | HiddenField | 3,273 | 删除 |
| 10 | WhitespaceAround | 3,121 | 删除 |

其他代码质量发现（Explore 代理审计）：

| 反模式 | 数量 | 状态 |
|--------|------|------|
| 空 catch 块 | 0 | 优秀 |
| 硬编码中文字符串 | 0 | 优秀 |
| System.out/err（核心模块） | ~30 | 待治理 |
| TODO/FIXME | 61 | 信息级别 |
| Raw Type（List/Map 无泛型） | 618 | 中期治理 |
| @Deprecated API | 61 | 正常 API 演进 |

## SonarQube 配置

### 排除配置（pom.xml properties）

```xml
<!-- 排除自动生成文件 -->
<sonar.exclusions>
    **/_gen/**,**/_*.java,**/_*.xml,**/*Errors.java,**/*Configs.java,**/*Constants.java
</sonar.exclusions>

<!-- 覆盖率统计排除 -->
<sonar.coverage.exclusions>
    **/_gen/**,**/_*.java,**/*Errors.java,**/*Configs.java,**/*Constants.java,**/parse/antlr/**
</sonar.coverage.exclusions>

<!-- 重复代码检测排除 -->
<sonar.cpd.exclusions>
    **/_gen/**,**/_*.java,**/*Errors.java,**/*Configs.java,**/*Constants.java
</sonar.cpd.exclusions>
```

### 推荐 Quality Profile

基于审计结果，建议在 SonarQube 上创建自定义 Profile（继承 `Sonar way`），做以下调整：

**关闭的规则：**

- `S1174`（FinalParameters）— 项目不用 final 参数
- `S1161`（@Override 要求 javadoc）— 低代码平台方法名自解释
- `S00101`（DesignForExtension）— 与 Nop 可扩展架构冲突
- `S121`/`S1143`（括号/空格格式）— IDE 处理

**放宽的规则：**

- `S103` 行宽：80 → **120**
- `S109` 魔法数字：允许 -1, 0, 1, 2, 3, 10, 100
- `S1541` 圈复杂度：10 → **15**

**保留开启：**

- 安全类全部保留（SQL 注入、XSS、路径遍历等）
- Bug 类全部保留（空指针、资源泄漏等）

### 模块级别排除

| 模块 | 操作 | 原因 |
|------|------|------|
| `nop-benchmark` | `sonar.skip=true` | 性能测试代码（已配置） |
| `**/cli/**` | 排除 S106 | CLI 工具合法使用 System.out |
| `**/boot/**` | 排除 S106 | 启动 banner 使用 System.out |

## 文件变更记录

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `checkstyle.xml` | 重写 | 从 160 行风格规则集改为 135 行 BUG 检测规则集 |
| `pom.xml` | 新增 qa profile | checkstyle 3.6.0 + PMD 3.26.0 + SpotBugs 4.9.3 |
| `pom.xml` | 新增 sonar 属性 | sonar.coverage.exclusions、sonar.cpd.exclusions、sonar.sourceEncoding |
| `pom.xml` | 更新 sonar.exclusions | 新增 `**/_*.java`、`**/_*.xml` |
| `pom.xml` | 更新 jacoco excludes | 新增 `_*.java`、`_*.xml`，路径改为 `**` 递归匹配 |
| `spotbugs-exclude.xml` | 新增 | 排除生成文件 |
| `pmd-ruleset.xml` | 新增 | 精选规则集 |
| `nop-kernel/pom.xml` | 同步 sonar 属性 | 与根 pom 保持一致 |

## Open Questions

- [ ] SonarQube Server 部署方案（Docker 本地 / SonarCloud）
- [ ] CI/CD 中集成 qa profile 的时机和阻断策略
- [ ] 核心模块 ~30 处 System.out 替换为 SLF4J 的优先级排序
