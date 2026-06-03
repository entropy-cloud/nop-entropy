# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] BizModel 自定义方法无显式权限注解

- **文件**: `NopJobScheduleBizModel.java:57-139`, `NopJobFireBizModel.java` 全部自定义方法
- **证据片段**:
```java
@Override
@BizMutation
public void enableSchedule(@Name("id") String id, IServiceContext context) {
```
全部 8 个自定义方法均无 @BizAuth 或其他权限注解。
- **严重程度**: P2
- **现状**: 无方法级权限控制。所有已认证用户均可执行全部调度操作。
- **风险**: 多租户/多角色场景下，普通用户可能执行本应受限的管理操作（如归档、手动触发）。
- **建议**: 为敏感操作添加 @BizAuth 注解，或在文档中说明权限策略。
- **信心水平**: 85%
- **误报排除**: 可能误报——若应用层在网关或 app 层统一做权限控制。
- **复核状态**: 未复核

### [维度13-02] data-auth.xml 完全为空

- **文件**: `nop-job-service/src/main/resources/_vfs/nop/job/auth/nop-job.data-auth.xml:1-5`
- **证据片段**:
```xml
<data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <objs/>
</data-auth>
```
- **严重程度**: P2
- **现状**: 无数据权限规则。任何已认证用户可查看所有命名空间的调度和触发记录。
- **风险**: 多租户部署中，租户 A 可看到租户 B 的作业。
- **建议**: 至少基于 namespaceId 配置数据权限规则。
- **信心水平**: 80%
- **误报排除**: 可能误报——单租户部署影响较小。
- **复核状态**: 未复核

### 正向确认

- 无 SQL 注入风险（所有查询通过 QueryBean + FilterBeans 参数化）
- 所有 BizModel 方法参数通过 @Name 绑定，框架处理类型转换
