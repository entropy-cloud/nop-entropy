# 维度03：API表面积与契约一致性

## 第 1 轮（初审）

## 审计范围
- 目标模块：nop-job
- BizModel 目录：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/`
- I*Biz 接口目录：`nop-job/nop-job-dao/src/main/java/io/nop/job/biz/`
- xmeta 元数据目录：`nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/`

### 问题1：triggerNow 方法使用不安全的 Map<String, Object> 参数

**文件路径**: `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java`

**行号**: 26-28

**证据代码**:
```java
@BizMutation("triggerNow")
void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                IServiceContext context);
```

**实现代码** (`NopJobScheduleBizModel.java` 行 95-105):
```java
@Override
@BizMutation
public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                       IServiceContext context) {
    NopJobSchedule schedule = requireEntity(id, "triggerNow", context);
    validateManualTriggerSchedule(schedule, "triggerNow");
    NopJobFire fire = buildManualFire(schedule, overrideParams, context);
    scheduleStore.insertManualFire(schedule, fire);
    afterEntityChange(schedule, "triggerNow", context);
}
```

**使用方式** (`NopJobScheduleBizModel.java` 行 198-215):
```java
private Map<String, Object> resolveJobParams(NopJobSchedule schedule, Map<String, Object> overrideParams) {
    Map<String, Object> jobParams = new LinkedHashMap<>();
    String paramsText = schedule.getJobParams();
    if (paramsText != null) {
        Map<String, Object> parsed = JsonTool.parseBean(paramsText, Map.class);
        if (parsed != null) {
            jobParams.putAll(parsed);
        }
    }
    if (overrideParams != null && !overrideParams.isEmpty()) {
        jobParams.putAll(overrideParams);
    }
    return jobParams;
}
```

**严重程度**: P2

**现状**: `triggerNow` 方法的 `overrideParams` 参数使用 `Map<String, Object>` 类型，破坏了类型安全性。该方法在测试代码中有实际使用（TestNopJobScheduleBizModel.java 行 287）。

**风险**:
1. 调用方无法在编译期获得类型检查
2. 可能传入无效的参数键名或值类型，导致运行时错误
3. IDE 无法提供代码补全和参数提示
4. 重构时无法自动修正调用代码
5. 违反了 Nop 平台强类型 API 的设计原则

**建议**:
1. 定义专门的 DTO 类（如 `JobTriggerOverrideParams`）来封装参数
2. 如果参数结构相对固定，应定义明确的属性字段
3. 检查是否有类似的参数定义类可以复用

**误报排除**:
- 检查了 `io.nop.job.api.spec` 包下的 `TriggerSpec`、`JobSpec` 等类，这些类是用于作业定义的内部结构，不适合作为 `triggerNow` 的参数
- 该方法在测试代码中被实际使用，不是死代码
- 该方法的设计意图是允许覆盖作业参数，虽然参数结构是用户自定义的 JSON，但仍建议引入类型安全的包装层

---

## 其他审计项检查结果

### BizModel 与 I*Biz 接口一致性

**检查结果**: ✓ 通过

**检查内容**:
- 3 个 BizModel 类：NopJobFireBizModel、NopJobScheduleBizModel、NopJobTaskBizModel
- 3 个对应的 I*Biz 接口：INopJobFireBiz、INopJobScheduleBiz、INopJobTaskBiz
- 所有 @BizMutation 方法都在接口中有声明，签名一致

### @BizModel 类和公开方法清单

**NopJobFireBizModel** (2 个方法):
- `cancelFire(String id, IServiceContext context)` - 取消触发批次
- `rerunFire(String id, IServiceContext context)` - 重新运行触发批次

**NopJobScheduleBizModel** (6 个方法):
- `enableSchedule(String id, IServiceContext context)` - 启用调度
- `disableSchedule(String id, IServiceContext context)` - 禁用调度
- `pauseSchedule(String id, IServiceContext context)` - 暂停调度
- `resumeSchedule(String id, IServiceContext context)` - 恢复调度
- `triggerNow(String id, Map<String, Object> overrideParams, IServiceContext context)` - 立即触发
- `archiveSchedule(String id, IServiceContext context)` - 归档调度

**NopJobTaskBizModel**: 无自定义方法，仅使用 CRUD 基类功能

### 死 API 检查

**检查结果**: ✓ 通过

**检查内容**:
- 搜索了所有 @BizMutation 方法的调用位置
- TestNopJobScheduleBizModel.java 中测试了所有 6 个方法
- TestNopJobFireBizModel.java 中测试了 2 个方法
- 未发现死 API

### xmeta 字段权限控制检查

**检查结果**: ✓ 通过

**检查内容**:
- 检查了 6 个 xmeta 文件
- 字段权限控制合理，internal="true" 标记用于内部技术字段
- _NopJobSchedule.xmeta: 3 个内部字段
- _NopJobTask.xmeta: 4 个内部字段
- _NopJobFire.xmeta: 3 个内部字段

### 类型安全问题

**检查结果**: ⚠️ 发现 1 个问题（已在问题1中详述）

**检查内容**:
- 搜索了所有使用 Map<String, Object> 作为参数或返回值的方法
- 除了 triggerNow 方法外，其他 API 方法都使用了明确的类型定义

### 外部调用一致性

**检查结果**: ✓ 通过

**检查内容**:
- 搜索了所有 I*Biz 接口方法的调用位置
- 所有接口定义的方法都在对应的 BizModel 类中实现
- 所有方法都在测试代码中被调用
- 未发现接口定义与实现不一致的情况

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 0 |
| P2 | 1 |
| P3 | 0 |

唯一的问题是 `triggerNow` 方法使用 `Map<String, Object>` 参数，建议引入类型安全的 DTO 包装。其他审计项（BizModel/I*Biz 一致性、死 API、xmeta 字段权限、类型安全）均通过检查。
