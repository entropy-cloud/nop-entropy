# 维度 13：安全与权限模型

## 第 1 轮（初审）

## 发现数量：0

所有检查项均通过：

| 检查项 | 结论 |
|--------|------|
| BizModel 方法权限 | 通过 action-auth.xml 完整配置 FNPT 权限 |
| xmeta 字段级权限 | 引擎控制字段标记为不可写 |
| 敏感字段可见性 | errorMessage 可读（运维系统合理），无预设敏感字段 |
| SQL 注入风险 | 所有查询通过 QueryBean + FilterBeans 参数化 |
| 未验证用户输入 | overrideParams 仅用于 JSON 序列化存储 |
| data-auth 行级权限 | 无行级数据权限规则（内部调度系统常见做法） |
