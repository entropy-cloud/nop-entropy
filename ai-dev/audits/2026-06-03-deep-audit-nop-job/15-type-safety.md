# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] Store 实现 daoProvider.daoFor() 强制转换缺少 @SuppressWarnings

- **文件**: `JobScheduleStoreImpl.java:391-401`, `JobFireStoreImpl.java:325-335`, `JobTaskStoreImpl.java:121-123`
- **证据片段**:
```java
private IOrmEntityDao<NopJobSchedule> scheduleDao() {
    return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
}
```
- **严重程度**: P3
- **现状**: 需要强制转换但缺少 @SuppressWarnings("unchecked")。BizModel 层已有此注解，Store 层缺失。
- **建议**: 添加 @SuppressWarnings("unchecked") 保持一致。
- **信心水平**: 95%
- **复核状态**: 未复核

### 正向确认

- I*Biz 接口泛型参数 T 全部正确指定
- Map<String, Object> 用于 jobParams 是 Nop 平台 JSON 组件存储的标准做法
- resolveTriggeredBy 重复已在维度 02/07 标记
