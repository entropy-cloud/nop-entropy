# 维度 03：API 表面积与契约一致性

## 审计范围

3 个 BizModel 类、3 个 I*Biz 接口、6 个 xmeta 文件。

## 第 1 轮（初审）发现

### [维度03-01] triggerNow 的 overrideParams 使用 Map<String, Object> 而非类型安全结构

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:107-122`
- **证据片段**:
  ```java
  @Override
  @BizMutation
  public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                         IServiceContext context) {
  ```
- **严重程度**: P3
- **现状**: `triggerNow` 的 `overrideParams` 参数使用 `Map<String, Object>`，是唯一一个使用 Map 作为公开 API 参数的方法。
- **风险**: jobParams 本身是用户自由定义的 JSON 结构，使用 Map 有一定合理性。但丢失编译期类型约束。
- **建议**: P3 优先级即可。如需强化可定义 TriggerOverrideParams value object。
- **信心水平**: 高
- **误报排除**: 在 job scheduler 语义下，jobParams 是自由 JSON 结构，Map 有合理性。
- **复核状态**: 未复核

## 正面评价

- 所有 @BizMutation 方法与 I*Biz 接口声明完全一致
- 无死 API（所有方法均有明确业务调用场景）
- xmeta 字段权限控制设计合理（scheduleStatus updatable=false 强制走专用 mutation）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 03-01 | P3 | NopJobScheduleBizModel.java:107 | triggerNow overrideParams 用 Map |
