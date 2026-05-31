# 维度 13：安全与权限模型

## 第 1 轮（初审）

**检查范围**：全部 BizModel 的 @Auth 注解、xmeta 字段权限、SQL 注入风险、路径校验。
**排除项**：detectFlows() 缺少 @Auth（已在维度 07 报告）。

## 零发现（排除已报告的 detectFlows 问题）

1. 所有 @BizMutation（除 detectFlows 外）均有 @Auth(roles="admin")。
2. sourceCode 敏感字段在 xmeta（published=false）和 BizLoader（@Auth(permissions="code-source-read")）双重保护。
3. 无 SQL 注入风险（全部参数化查询）。
4. 路径校验合理（检查 `..` 防止路径遍历）。

## 最终保留项

无（detectFlows 问题见维度 07-01）。
