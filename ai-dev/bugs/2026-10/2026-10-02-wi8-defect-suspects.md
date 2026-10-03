# 2026-10-02 WI8 测试暴露的产品缺陷嫌疑清单（已修复/裁定，plan 2306 收口）

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


## Fix（2026-10-03 plan 2306 回填）

- **1 GatewayHttpFilter null method NPE：`fixed`**。buildGatewayContext 对 null method 跳过元数据落点（不伪造 GET 语义）。回归：TestGatewayHttpFilterSemantics.testBuildGatewayContextToleratesNullMethod。nop-gateway 106 测试全绿。
- **2 saveOrUpdate 按 "id" 键判定：`fixed`（▲ 公开 mutation 语义变化）**。saveOrUpdate/batchModify（同源点）/buildEntityDataForUpdate 三处改经既有 `getId(data, dao)` 按实体真实主键属性名取主键；validated 数据按 getPkColumnNames 清理主键键（并兼容旧 "id" 键）；:1429 反转的 @Description 文案修正（"如果没有主键就新增记录，否则就修改记录"）。TestGraphQLCrudSemantics 四个用例按实体主键名 sid 改写（原按缺陷语义分别携 sid/id 两键）；TestIndex__saveOrUpdate 两次调用统一携 sid 即完成 upsert（修复前第二次会被误判为插入并以 duplicate-key 报错）。▲ 下游：CRUD 消费模块（nop-biz 111、nop-wf-service 115 等）当期全绿。docs-forai 裁定：service-layer 文档对 saveOrUpdate 的描述为"没有主键就新增，否则更新"，与修复后行为一致，无需变更。
- **3 @InjectValue 纯 JVM 默认值：`fixed`（Decision：字段初始化路线）**。returnUserId/autoRefreshToken 字段初始化为与 @InjectValue 配置注解一致的默认 true（容器外语义对齐，容器内注入覆盖不受影响）。回归：新增 TestInjectValuePureJvmDefaults（纯 JVM 构造断言 isReturnUserId/isNeedRefresh 缺省语义）。

## Affected Files

- nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java
- nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java
- nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractLoginService.java
- nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java
- 测试：TestGatewayHttpFilterSemantics / TestGraphQLCrudSemantics / 新增 TestInjectValuePureJvmDefaults
