# 2026-10-02 WI8 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2300（WI8 service-framework 补强）执行期发现。按 roadmap 硬边界记录，修复独立立项。

## 1. GatewayHttpFilter.buildGatewayContext null method NPE（P2 嫌疑）

- Problem：`IHttpServerContext.getMethod()` 为 null 时 `GatewayContextImpl.setHttpMethod(null)` 落 ConcurrentHashMap 抛 NPE（CHM 拒绝 null value）。
- 修复方向：null 守卫（缺省 GET 或跳过元数据落点）。

## 2. saveOrUpdate 按 "id" 键存在性判断插入/更新（P2 嫌疑）

- Problem：以 data 中是否存在 `"id"` 键区分插入/更新——实体主键名为 `sid` 等非 `id` 时静默走插入分支，最终以 `nop.err.dao.sql.duplicate-key` 报错，错误语义不直指根因。
- 修复方向：按实体模型主键名判断，或错误信息携带判定依据。

## 3. @InjectValue 默认值纯 JVM 环境不生效（P3 嫌疑/设计注记）

- Problem：`nop.login.return-user-id`、`nop.auth.auto-refresh-token` 等默认值在 IoC 容器外调用 `AbstractLoginService.getUserInfo`/`isNeedRefresh` 时不生效（字段缺省 false），与配置默认语义不一致。
- 修复方向：字段初始化为配置默认值，或注释标明仅容器内语义。
