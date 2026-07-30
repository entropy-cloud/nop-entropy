# 结束审计提示

在独立检查计划切片是否实际完成时使用此提示。

所有创建的计划都需要结束审计。

```text
阅读 `AGENTS.md`、`docs-for-ai/INDEX.md`、活动需求/设计文档、活动计划、最新相关日志条目和实时更改代码。

审计声称的实现是否真正关闭。

检查自主权策略文档中的审查者可用性。冷重播不是第二位审查者，从不批准保护区域、未解决的产品风险或真相源冲突。

此审计必须由独立子代理或审查者运行，而不是由实施代理继续同一关闭决策。

重点关注：
- 实时行为是否符合规定的需求
- 计划的关闭门控是否实际满足
- 证明是否存在于文件和验证结果中，而不仅仅是聊天中
- 在支持的基线更改的地方是否更新了文档
- 是否有任何剩余差距仍在范围内
- 任务路由和记录的技能使用是否仍然与交付的工作匹配
- 是否在没有持久证据的情况下放宽了任何自主权或待办事项状态
- 是否隐藏了验证失败或未运行的命令
- **Owner-doc → 代码一致性抽样核查**：对本计划涉及的每个 owner doc 随机抽 2 个关键断言（状态名/字段名/角色名/迁移路径/ErrorCode），对照 `*.orm.xml` / BizModel / `*Errors.java` / `*Constants.java` 核验是否漂移。**发现 ≥2 处漂移**则升级为 `needs revision`；1 处漂移记 Minor 但仍 passes；0 处漂移正常 passes。

**强制验证范围检查（历史教训：声明"full-green"时实际只跑了局部模块测试）：**
- `mvn -pl <aggregator> -am` 因 `-am` 引入所有传递依赖，效果等价于 full reactor 编译/测试，**可以**作为完成依据。
- 真正的问题是**选错了验证目标**：如果只 `mvn test -pl :specific-service`（不包含聚合器），则只测该模块及其声明依赖，不完全等同于全栈。
- 验证范围检查规则：
  - `mvn clean install -DskipTests`（全 reactor）= full-build ✅
  - `mvn install -DskipTests -pl <aggregator> -am`（聚合器 + 全部传递依赖）= effective full-build ✅
  - `mvn test -pl <aggregator> -am`（聚合器测试 + 全部传递依赖测试）= effective full-test ✅
  - `mvn test -pl :specific-module`（单模块，不含聚合器下游）= scoped ⚠️ 不能作为 full-green 依据
- 如果验证执行的是 scoped（不含聚合器），在 Closure 中注明 `⚠️ scoped only` 并列出未验证模块。
- 例外：如果 full reactor 已知存在计划范围外的基线失败（经 `git stash --include-untracked` 证明是前置失败），可以基于 effective full-build 声明完成，但必须在 Closure 中附前置失败证据。

按严重性排序，首先返回发现。
如果关闭被阻止，说 `needs revision` 并列出确切缺少的证明或更改。
如果切片可接受，说 `passes closure audit` 并记录任何剩余风险。关闭结果必须在计划的 `## Closure` 部分留下持久证据，可选择性链接到每日日志或存储的审计文件。不要仅基于实施代理的自我记录证据批准关闭。
```
