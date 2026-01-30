# 任务型开发手册（Runbook）

本目录提供 **按任务组织** 的最短路径指南，面向 AI/开发者在 Nop 平台上快速生成/修改实现。

## 使用规则（AI优先级）

1. **先模型/生成**：能通过 xmeta/xbiz/xview 等模型解决的，不写 Java。
2. **再 Delta**：能通过 `_delta` 覆盖/扩展的，不改原实现。
3. **最后才写 Java**：且优先复用 `CrudBizModel` 内置能力与扩展点。

## 目录

- [新增字段与校验](./add-field-and-validation.md)
- [扩展 CRUD 钩子](./extend-crud-with-hooks.md)
- [用 QueryBean 写自定义查询](./custom-query-with-querybean.md)
- [通过 Delta + BizLoader 扩展返回字段](./extend-api-with-delta-bizloader.md)
- [事务边界与回调](./transaction-boundaries.md)
- [错误码与 NopException](./error-codes-and-nop-exception.md)
