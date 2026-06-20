# 维度 01：依赖图与模块边界

## 检查范围

读取并核验：`nop-ai-agent/pom.xml`、`nop-ai/pom.xml`、`nop-ai-core/toolkit/api/pom.xml`、`nop-task-core/pom.xml`、`module-groups.md`、`domain-module-pattern.md`；提取 main 全部 `import io.nop.*`（431 个 java 文件）与 pom 对账；test 源码 import 对账；循环依赖检查；生成产物引用对账。

## 第 1 轮（初审）发现

**零发现。** 逐项核验执行步骤 1-8，均合规。

完整依赖图（文本）：

```
nop-ai-agent (单模块库)
├──[compile] nop-ai-toolkit → nop-ai-api → nop-api-core(platform); nop-xlang→nop-core; nop-http-api; nop-search-api; nop-diff
├──[compile] nop-ai-core → nop-ai-api; nop-api-core; nop-dao(compile); nop-http-api; nop-http-client-jdk(optional); nop-xlang; nop-markdown; nop-diff
├──[compile] nop-task-core → nop-xlang; nop-ioc
├──[test]    nop-dao            (显式 test，收窄覆盖经 nop-ai-core 传来的 compile)
├──[test]    nop-message-core
├──[test]    nop-autotest-junit
└──[test]    nop-record-mapping
```

合规要点（C1-C7）：

- **C1 compile 依赖最小且正确**：nop-ai-toolkit（工具调用）、nop-ai-core（LLM 方言/模型）、nop-task-core（flow 编排）精准对应三类职能；无应降级却声明 compile 的依赖；无 system/provided/optional 异常作用域。
- **C2 无反向/跨层依赖**：main 源码 grep `import io.nop.ai.(service|web|app|dao|meta|codegen|coder|rag|shell|mcp|spring)` 结果为空。
- **C3 无循环依赖**：所有直接 compile 依赖及其上游 pom 均 grep `nop-ai-agent` 为空，依赖图为严格 DAG。
- **C4 无 test-scope 依赖泄漏 main**：main grep `import io.nop.(dao|message|record|autotest|jdbc)` 为空。关键校准：main 确有 `import io.nop.api.core.message.*`（IMessageService 等 7 个），但定义在平台核心 `nop-api-core`，**不是** test-scope 的 `nop-message-core`（包 `io.nop.message.core.*`）。
- **C5 无未声明的 io.nop.* 隐性耦合**：main 全部非自身 import 都能映射到声明依赖或其合法传递闭包（平台核心包除外）。
- **C6 nop-dao 的 test 收窄不破坏 main 编译**：DB 类（DBSessionStore 等）使用原生 `javax.sql.DataSource` + JDBC，不 import `io.nop.dao.*`；app.orm.xml 实体在 main 无对应 Java 实体，改用轻量 `*Table.java` 元数据持有类。该 test 收窄是 `module-groups.md` 记载的既定设计。
- **C7 生成产物引用的工件都在依赖中**：app.orm.xml 是手写源模型（无 codegen 子模块、无 _app.orm.xml 聚合），引用实体全在自身包内；ai-agent-tools.beans.xml 引用 bean 类全部存在；nop-ai-agent.beans autoconfig 路径正确。

## 非违规观察（不计为发现）

`module-groups.md` 第 17 行将 nop-dao 的 test 收窄理由表述为"不泄漏给下游消费者"。从 Maven 传递解析看，下游经 `下游 → nop-ai-agent → nop-ai-core → nop-dao(compile)` 仍会获得 nop-dao，故该收窄主要表达"nop-ai-agent 自身不直接需要 nop-dao"的诚实声明，而非真正阻断下游传递。这是 docs-for-ai 措辞精度问题（不在被审模块代码内），不构成代码边界违规。

## 维度复核结论

[维度01] 独立复核确认：零发现成立。初审的合规论证（C1-C7）经 live code 核验自洽，无遗漏的真实违规。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| — | — | — | 本维度零发现 |
