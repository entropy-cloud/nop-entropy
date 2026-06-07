# 维度 19：命名与术语一致性

## 第 1 轮（初审）

**零发现**。

验证了以下一致性：
- 实体名在 ORM、BizModel、I*Biz 接口、xmeta 中完全一致（NopJobSchedule/Fire/Task）
- 字段名在数据库列名、Java 属性名、GraphQL 字段名之间标准映射一致
- 术语一致："fire"=触发批次、"task"=执行任务、"schedule"=调度定义，无混用
- dict code 与 `_NopJobCoreConstants` 常量值完全匹配
