# nop-rule — 规则引擎

## 功能概览

业务规则引擎，支持两种规则模式。

- **决策树（TREE）**：基于条件分支的树形决策
- **决策矩阵（MATX）**：基于多维度交叉的矩阵决策
- 规则版本管理
- 角色级访问控制
- 执行日志

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopRuleDefinition | `nop_rule_definition` | 规则定义（ruleType: TREE/MATX） |
| NopRuleNode | `nop_rule_node` | 决策树节点（条件谓词 + 输出） |
| NopRuleRole | `nop_rule_role` | 规则访问权限（按角色） |
| NopRuleLog | `nop_rule_log` | 规则执行日志 |

## 使用方式

规则定义存储在 `modelText` 字段中，以 XML 模型格式描述。

```java
// 注入规则服务
@Inject
IRuleService ruleService;

// 执行规则
Map<String, Object> inputs = new HashMap<>();
inputs.put("amount", 1000);
inputs.put("level", "VIP");

Map<String, Object> outputs = ruleService.evaluateRule("discount-rule", "1.0", inputs);
```

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-rule-core` | 规则核心引擎 |
| `nop-rule-api` | API DTO |
| `nop-rule-dao` | ORM 实体与 DAO |
| `nop-rule-service` | 业务逻辑 |
| `nop-rule-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-rule/model/nop-rule.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`
