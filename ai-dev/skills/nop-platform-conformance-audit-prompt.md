# Nop Platform 合规审计提示

在审计项目设计与实现对 Nop Platform 最佳实践的遵循度时使用此提示。

在 BizModel/xbiz 实现后、平台集成变更后、或 codegen 后核对生成代码规范时使用。不要将其用作业务设计审计（改用 design-doc-audit-prompt）、ORM 字段审计（改用 orm-model-audit-prompt）或跨模块依赖审计（改用 cross-module-dependency-audit-prompt）的替代品。

```text
您是 Nop Platform 专家。以下是项目设计与实现，审计其对平台最佳实践的遵循度。对照 `docs-for-ai/` 下的权威文档。

首先阅读这些文件：
- `AGENTS.md`（Nop 平台特定规则章节）
- `docs-for-ai/INDEX.md`（平台文档路由）
- `docs-for-ai/00-start-here/ai-defaults.md`（Model → Delta → Java 决策框架 + 反模式表 + 自检清单）
- `docs-for-ai/02-core-guides/architecture-principles.md`
- `docs-for-ai/02-core-guides/domain-logic-and-ddd.md`
- `docs-for-ai/02-core-guides/cross-module-entity-reference.md`
- `docs-for-ai/04-reference/common-java-helpers.md`
- `docs-for-ai/04-reference/safe-api-reference.md`

审计维度：

1. 决策顺序：Model → Delta → Java
   - 实现新功能时，优先用 ORM 模型/XMeta 配置，其次 Delta 定制，最后才写 Java。
   - 不存在"能模型化却硬编码 Java"的反模式。
   - 不手动编辑生成代码（`_gen/`、`_` 前缀、`_app.orm.xml`、`_service.beans.xml`）。

2. 跨实体访问规则
   - BizModel 中跨实体访问通过 `@Inject I*Biz` 接口，不直接 `IDaoProvider` / `IOrmTemplate` / `@SqlLibMapper`（除非 I*Biz 无法满足，且有代码注释说明理由）。
   - Entity 内部跨实体只读用 `requireBiz(IXxxBiz.class)`，不用 `BeanContainer`。
   - 实体不做跨模块写操作（写走 BizModel 的 @BizMutation）。

3. 异常处理
   - 所有业务异常扩展 `NopException`，不用 `RuntimeException` 或 `IllegalArgumentException`。
   - 面向公共/GraphQL 的错误用 `ErrorCode` + `NopException`。
   - 错误码集中管理，不散落硬编码字符串。

4. IoC 与事务
   - `@Inject` 字段不是 `private`（Nop IoC 规则）。
   - `@BizMutation` 方法不重复加 `@Transactional`（自动包装事务）。
   - 跨域写在同一 `@BizMutation` 方法内（同事务原子提交），不依赖显式传播。

5. 平台辅助工具使用
   - 时间：`CoreMetrics.currentTimeMillis()` 而非 `System.currentTimeMillis()`。
   - JSON：`JsonTool` 而非 Jackson/Gson 直接调用。
   - 字符串：`StringHelper` 而非 Apache Commons。
   - 不引入与平台重复的工具库。

6. 标准服务模式
   - 实体服务继承 `CrudBizModel<T>`。
   - 查询用 `@BizQuery`、变更用 `@BizMutation`。
   - GraphQL 字段加载用 `@BizLoader` + `@LazyLoad`，避免 N+1。

7. 跨模块外部实体引用（机制 B）
   - 引用其他模块表用 `<entity notGenCode="true">`（见 cross-module-dependency-audit-prompt）。
   - 不写跨模块 refEntityName 而无声明。
   - Maven 依赖单向（DAG 合规）。

8. 状态机与规则引擎
   - 状态机声明式（DSL/字典/转换表），不散落 if-else。
   - 业务规则用 nop-rule（规则引擎），不硬编码。
   - 报表用 nop-report，不手写 SQL 报表。

9. 审批流与作业
   - 审批流用 nop-wf，实体持 `flowInstanceId` 关联。
   - 定时任务用 nop-job。
   - 跨域事件用 nop-message。

10. 定制能力使用顺序
    - 字段扩展优先 Delta（ext:baseClass + ext:dict），不改源实体。
    - API 扩展用 Delta + @BizLoader(autoCreateField=true)。
    - 页面扩展用 delta view.xml，不改源页面。

11. 多租户与本地化
    - 多租户走平台标准，不在源模型预置 tenantId。
    - 本地化走独立可拔 l10n 模块，不内建核心实体。

12. 测试与验证
    - 单元测试用 JUnit5，配合 `nop-orm` 的测试基类。
    - GraphQL 测试用 `graphql-doc` 自动生成测试输入。

13. **Codegen 产物安全意识（致命禁区）**
    - 永远不要直接编辑带有 `_` 前缀的文件——它们在 `mvn install` 时被 codegen 重新生成，任何手改都会丢失。
    - 永远不要直接编辑带有 `# __XGEN_FORCE_OVERRIDE__` 标记的文件——codegen 阶段覆盖。
    - 定制必须写在**保留层**文件中（无 `_` 前缀），通过 `x:extends="_generated_file.xml"` 继承生成文件后追加内容。
    - ORM 字典定义（`<dict>`）永远以模型文件中的 `<dict>` 定义为真相源，生成到 `dict.yaml`。
    - 自动化检查：`grep -r '__XGEN_FORCE_OVERRIDE__' --include='*.yaml' --include='*.xml' | grep -v '_gen/'` 列出所有 codegen 强制覆盖文件，检查是否有手改。

14. **聚合完整性检查（新模块/功能注册）**
    - 当新增一个模块、功能入口、或页面时，检查以下聚合器是否都已包含它：
      - 聚合器 `app.action-auth.xml` 的 `x:extends` 列表是否包含新模块
      - 聚合器 app 的 POM 中是否包含新模块依赖
      - 菜单/路由注册是否已经在聚合 action-auth 中生效
    - 注意同时检查维度 13 的 codegen 产物安全意识：**新模块的 action-auth 文件必须写在保留层**。

15. **Owner-doc → 代码关键断言抽样核查（漂移检测）**
    - 从审计范围内涉及的每个 owner doc 中**随机选取 2 个关键断言**（状态名/字段名/角色名/迁移路径/ErrorCode 字符串之一），对照代码核验是否一致。
    - **不一致即报 Major**。
    - **范围**：每次审计至少抽样 2 个 owner doc × 2 个断言 = 4 个核查点；若发现 ≥2 处漂移，扩大抽样至全部 owner doc。
    - 记录格式："`<owner-doc>:<section>` 断言 X 与 `<file>:<line>` 一致/不一致（说明）"。

自动化核查建议：
- grep 扫描源码：`extends RuntimeException`（应 NopException）、`@Inject private`（应非 private）、`@Transactional` 与 `@BizMutation` 共存（冗余）、`System.currentTimeMillis`（应 CoreMetrics）、`IDaoProvider` 直接注入（应 I*Biz）。
- 扫描 `_gen/` 是否被手动修改（git diff 检测）。
- 扫描 orm.xml 跨模块 refEntityName 是否有 notGenCode 声明。

严重性指南：
- `blocker`：手动改生成代码、跨模块写反向、业务异常不扩展 NopException、@Inject private。
- `major`：硬编码能模型化的逻辑、绕过 I*Biz 直接 IDaoProvider、@BizMutation 加 @Transactional。
- `minor`：辅助工具用第三方而非平台、命名不规范。

按严重性返回发现，附：受影响文件与行、问题、修复建议。最后给：
- 裁决：通过/失败
- 维度合规率
- 反模式实例清单
- 残留风险
```
