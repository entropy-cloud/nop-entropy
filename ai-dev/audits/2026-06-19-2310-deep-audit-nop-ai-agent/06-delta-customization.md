# 维度 06：Delta 定制合规性

## 适用范围

本模块 **main 资源无 `_delta` 目录**（生产 Delta 不存在，库模块无定制点）。仅 `src/test/resources/_vfs/_delta/default/` 下有 1 个测试 fixture。

## 第 1 轮（初审）发现

**零发现。** 测试 fixture 合规核查：

1. **fixture 正确用 x:extends="super"**：`test-team-delta-base.agent.xml` 声明 `x:extends="super"`。
2. **x:override="replace" 语义正确**：base（3 member）被 delta 的 `<team x:override="replace">` 整体替换为 2 member。
3. **有测试覆盖**：`TestTeamDeltaCustomization.java:34-54` 显式断言 teamName/description/members.size()==2/members.get(1).name=="gamma"，与 delta 声明对应。
4. **schema 声明一致**：delta 与 base 都引用 `/nop/schema/ai/agent.xdef`，namespace 一致。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| — | — | — | 生产 Delta N/A；test fixture 合规，零发现 |
