# 维度03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] BizModel 方法 @BizMutation 注解与接口风格不一致

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:45-47`
- **证据片段**:
  ```java
  // BizModel实现 - 无value
  @Override
  @BizMutation
  public void cancelFire(...)
  
  // INopJobFireBiz接口 - 有显式value
  @BizMutation("cancelFire")
  void cancelFire(@Name("id") String id, IServiceContext context);
  ```
- **严重程度**: P3
- **现状**: Nop 平台 @BizMutation 不指定 value 时默认以方法名作为 mutation name，运行时无差异。但接口和实现注解风格不统一。
- **风险**: 纯代码一致性问题，不影响运行时行为。
- **建议**: 在 BizModel 实现类上也统一使用显式 value。
- **信心水平**: 确定
- **误报排除**: 框架默认行为与显式指定等效。
- **复核状态**: 未复核

### [维度03-02] INopJobTaskBiz 暴露 delete 方法但 BizModel 以抛异常覆盖

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobTaskBiz.java:8-10`
- **证据片段**:
  ```java
  public interface INopJobTaskBiz extends ICrudBiz<NopJobTask>{
      // 空 — 继承了 ICrudBiz<NopJobTask> 的 delete 方法
  }
  // NopJobTaskBizModel.java:24-27
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED);
  }
  ```
- **严重程度**: P3
- **现状**: delete 方法仍在 GraphQL schema 上暴露，运行时抛异常。更彻底的做法是从 schema 层面消除。
- **建议**: 在 xmeta 配置中移除 delete action，或在接口层不继承 delete。
- **信心水平**: 确定
- **误报排除**: 抛异常方式在功能上是有效防护。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 03-01 | P3 | NopJobFireBizModel.java:45 | @BizMutation注解接口有value实现类无value |
| 03-02 | P3 | INopJobTaskBiz.java:8 | delete方法在接口暴露但BizModel抛异常禁止 |
