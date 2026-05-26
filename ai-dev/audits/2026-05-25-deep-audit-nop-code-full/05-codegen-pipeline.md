# 维度05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] nop-code-api 模块 POM 未继承 nop-code 父 POM

- **文件**: `nop-code/nop-code-api/pom.xml:1-44`
- **证据片段**:
```xml
<!-- 无 <parent> 声明 -->
<modelVersion>4.0.0</modelVersion>
<groupId>io.github.entropy-cloud</groupId>
<artifactId>nop-code-api</artifactId>
<version>1.0.0-SNAPSHOT</version>
<properties>
    <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
    <java.version>11</java.version>
</properties>
```
- **严重程度**: P3
- **现状**: nop-code-api 独立声明了 groupId/version/java.version，未通过 `<parent>` 继承 nop-code 父 POM。其余 12 个子模块均正确继承。
- **风险**: 硬编码版本号与父 POM 可能不一致，无法受益于统一依赖管理。
- **建议**: 为 nop-code-api 添加 `<parent>` 声明。
- **误报排除**: 不属于生成管线断裂，但影响构建一致性。
- **复核状态**: 未复核

---

### [维度05-02] nop-code-dao POM 声明了 nop-code-codegen 的 test 依赖但无 exec-maven-plugin

- **文件**: `nop-code/nop-code-dao/pom.xml:25-29`
- **证据片段**:
```xml
<dependency>
    <artifactId>nop-code-codegen</artifactId>
    <groupId>io.github.entropy-cloud</groupId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```
- **严重程度**: P3
- **现状**: nop-code-dao 引入了 nop-code-codegen 作为 test scope 依赖，但自身没有任何 xgen 脚本，也未配置 exec-maven-plugin。
- **风险**: 不必要的依赖传递和构建时间。
- **建议**: 确认是否被实际使用，若未使用则移除。
- **误报排除**: 不构成生成管线断裂，是依赖卫生问题。
- **复核状态**: 未复核

---

### [维度05-03] 部分层间生成产物时间戳不完全同步

- **文件**: 多文件时间戳对比
- **证据片段**:
```
源模型: 2026-05-25 22:09
_NopCodeCall.java: 2026-05-11 00:22 (早于源模型)
_NopCodeUsage.xmeta: 2026-03-25 22:55 (远早于源模型)
_NopCodeCall.view.xml: 2026-03-25 22:55 (远早于源模型)
```
- **严重程度**: P3
- **现状**: 增量生成仅重新生成内容发生变化的文件。旧时间戳的文件内容与当前源模型一致。
- **风险**: 低风险，内容经比对后一致，不存在遗漏或错误。
- **建议**: 执行一次全量构建 `./mvnw clean install` 确保所有产物完全同步。
- **误报排除**: 增量代码生成的正常行为，不是管线断裂。
- **复核状态**: 未复核

---

## 管线闭合验证

| 层 | 期望产物数 | 实际产物数 | 状态 |
|---|---|---|---|
| DAO _gen Entity | 10 | 10 | ✅ |
| DAO Entity | 10 | 10 | ✅ |
| DAO I*Biz | 10 | 10 | ✅ |
| Meta _Xxx.xmeta | 10 | 10 | ✅ |
| Meta Xxx.xmeta | 10 | 10 | ✅ |
| Web _Xxx.view.xml | 10 | 10 | ✅ |
| Service BizModel | 10 | 10 | ✅ |
| Service beans.xml | 1(10条) | 1(10条) | ✅ |

**结论: 生成管线完整且正确闭合，3 个 P3 问题均为构建卫生问题。**
