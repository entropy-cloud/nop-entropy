# 维度 10：XDSL 与 XLang 正确性

## 检查范围

`app.orm.xml`/`beans.xml`/`register-model.xml` 的 x:schema；agent.xdef/agent-plan.xdef 与 _gen 模型一致性；测试 fixture；gen-agent-xdsl.xgen；delta fixture。

**核验通过（无问题）**：x:schema 引用全部正确（app.orm.xml→orm.xdef、beans.xml→beans.xdef、register-model→register-model.xdef、xdef→xdef.xdef、record-mappings→record-mappings.xdef）；_AgentModel 字段与 agent.xdef 完全一致；delta fixture `x:extends="super"`+`x:override="replace"` 正确；gen-agent-xdsl.xgen 引用 xdef 路径正确。

## 第 1 轮（初审）发现

### [维度10-01] agent-plan.xdef 重设计后残留 5 个孤儿 plan 模型类，record-mapping 引用已不存在的 AgentPlanModel

- **文件**: `nop-kernel/nop-xdefs/.../nop/schema/ai/agent-plan.xdef:12-17`（当前根名 AgentPlan，无 AgentPlanModel 等 xdef:name）；`plan/model/_gen/_AgentPlanModel.java:13-19`；`plan/model/AgentPlanModel.java:3-5`；`test resources/.../agentPlan.record-mappings.xml:54-77`
- **证据片段**:
  ```java
  // _AgentPlanModel.java:13-19 头部声称由 agent-plan.xdef 生成，但字段集与当前 xdef 不匹配
   * generate from /nop/schema/ai/agent-plan.xdef
  public abstract class _AgentPlanModel extends ... {
      private KeyedList<...AgentPlanNoteModel> _notes ...;
      private java.lang.String _overview ;
      private AgentExecStatus _planStatus ;
      private KeyedList<...AgentPlanTaskModel> _tasks ...;
  ```
  ```xml
  <!-- agentPlan.record-mappings.xml:54-77 mapping 名 ...AgentPlanModel，字段全是 AgentPlan 的 -->
  <mapping name="Markdown_to_AgentPlanModel" md:titleField="title">
      <field name="status" .../>     <!-- AgentPlan.status，AgentPlanModel 无此字段 -->
      <field name="goal" .../>        <!-- AgentPlan.goal -->
  ```
- **严重程度**: P2
- **现状**: 当前 agent-plan.xdef 根模型为 AgentPlan，但 main 仍保留旧模型簇：AgentPlanModel、AgentPlanNoteModel、AgentPlanNote、AgentPlanQuestion、AgentPlanDecision（及 _gen 基类）。全仓 grep 确认这 5 类仅自身与 _gen 互引用，无 engine/service/测试实例化。record-mapping `Markdown_to_AgentPlanModel` 字段实际对应 AgentPlan（TestAgentPlanMarkdownLoader 断言 result instanceof AgentPlan）。
- **风险**: (1)误导后续维护者以为 AgentPlanModel 是主模型；(2)mapping 名与产出类型不符，认知陷阱；(3)若 _gen 被清空按当前 xdef 重新生成，保留类将找不到基类而编译失败——潜在 clean-build 破坏。
- **建议**: 删除 5 个孤儿保留类及 _gen 基类；将 mapping 名 `Markdown_to_AgentPlanModel` 重命名为 `Markdown_to_AgentPlan`（同步 TestAgentPlanRecordMapping:141）。
- **信心水平**: 高
- **误报排除**: gen-agent-xdsl.xgen 仅 render 两个 xdef；agent-plan.xdef 全文无这 5 个 xdef:name；全仓 grep 无外部使用；TestAgentPlanMarkdownLoader 断言产物为 AgentPlan。保留类（非 _ 前缀）和 mapping 名均为审计对象。
- **复核状态**: 未复核

### [维度10-02] 测试 fixture test-unknown-mode-agent.agent.xml 用非法 mode="unknown"，违反 xdef 枚举，且无测试引用

- **文件**: `test/resources/_vfs/test-unknown-mode-agent.agent.xml:1-2`；`agent.xdef:9`
- **证据片段**:
  ```xml
  <agent x:schema="/nop/schema/ai/agent.xdef" ... name="test-unknown-mode-agent" mode="unknown">
  ```
  ```xml
  <!-- agent.xdef:9 -->
  mode="enum:react,plan,single-turn|react">
  ```
- **严重程度**: P3
- **现状**: fixture 的 mode="unknown" 不在 enum 内；正常加载会被 xdef 校验拒绝。grep test 代码无任何引用（无"批量加载所有 agent"测试）。
- **风险**: 孤儿且不合规 fixture 残留 _vfs 根下，误导维护者；将来批量加载测试会意外踩校验失败。
- **建议**: 若原意是负向用例，补断言抛校验异常的测试；否则删除。
- **信心水平**: 高
- **误报排除**: grep 全模块 test 无引用；对比 test-plan-agent(mode=plan)/test-single-turn-agent(mode=single-turn) 合法。
- **复核状态**: 未复核

### [维度10-03] app.orm.xml 中 tokenEstimate 复用 epochMillis、messageCount 复用 seqNumber（domain 语义误用）

- 与维度04-02 同一发现。详见 `04-orm-model.md` [维度04-02]。严重程度 P3。

## 维度复核结论

10-01/10-02 以保留类（非 _ 前缀）与 fixture 为审计对象，事实成立。10-03 与 04-02 去重。XDSL schema 引用、_AgentModel↔xdef、delta 语义均核验通过。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 10-01 | P2 | plan/model/AgentPlanModel.java 等5类 | xdef 重设计后残留 5 孤儿 plan 模型类+mapping 名不符 |
| 10-02 | P3 | test/resources/_vfs/test-unknown-mode-agent.agent.xml | 非法 mode=unknown 孤儿 fixture |
| 10-03 | P3 | app.orm.xml | domain 语义误用（同04-02） |
