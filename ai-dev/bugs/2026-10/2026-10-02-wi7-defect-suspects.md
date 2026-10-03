# 2026-10-02 WI7 测试暴露的产品缺陷嫌疑清单（已修复/裁定，plan 2306 收口）

> 来源：plan 2299（WI7 sys/rule/dyn 补强）执行期发现。按 roadmap 硬边界记录，修复独立立项。

## 1. detached 实体访问 ref 属性即抛 session-not-attached（P2 嫌疑）

- Problem：`NopDynPropMeta.getDomain()` / `NopDynEntityMeta.getModule()` 在实体脱离 session 时经 `internalGetRefEntity`→`requireEnhancer` 直接抛错，导致 `DynEntityMetaToOrmModel.toColumnModel` 对未挂域的 detached propMeta 不可用（测试被迫为每个属性 setDomain；既有测试注释亦承认此行为）。
- 修复方向：区分"未设置"与"需懒加载"两种语义。

## 2. SysDictLoader.loadDict 忽略 locale 参数（P2 嫌疑）

- Problem：`loadDict` 恒用默认 locale，多语言字典查询语义缺失。

## 3. existsDict 租户旁路（P2 嫌疑）

- Problem：useTenant 且无租户上下文时直接返回 true 跳过 DB 检查（注释声明为启动期优化，但运行期调用方同样命中）——租户字典存在性判断可能误报。

## 4. OrmEntityChangeLogInterceptor.postSave null 值审计区分缺失（P3 嫌疑）

- Problem：对 null 属性值写 `newValue=null`（ConvertHelper.toString(null)），审计行丢失"列存在但值为空"与"列未初始化"的区分。


## Fix（2026-10-03 plan 2306 回填）

- **1 detached 实体 ref 访问抛错：`fixed`**。DynamicOrmEntity.internalGetRefEntity 在 enhancer 不可用（detached/transient）时返回已缓存 ref 或 null（"未设置"语义），不再经 requireEnhancer 抛 session-not-attached；enhancer 可用时懒加载路径不变。getDomain/getModule 同条目一并修复（生成 getter 共用 internalGetRefEntity）。回归：新增 TestDetachedRefAccess（未设置→null；显式设置→detached 下仍可读）。
- **2 SysDictLoader 忽略 locale：`fixed`（最小修复 + 限制记录）**。bean.setLocale 使用调用方传入 locale（null 回退默认）。限制：sys 字典表无 locale 列，选项级多语言过滤需 ORM 模型扩展（protected area，超出本 plan 最小修复边界，记录为后续立项候选）。回归：新增 TestSysDictLoaderLocale（proxy stub dao，断言 locale 透传 + null 回退默认）。
- **3 existsDict 租户旁路：`fixed`（文档化+日志裁定）**。无租户上下文时保留启动期乐观语义（return true），但显式 LOG.warn 记录而非静默放行（运行期命中此分支即暴露误报风险）；租户上下文存在时走 DB 检查路径不变。完全移除旁路会导致启动期无租户上下文的租户过滤查询失败，属行为破坏，裁定记录于此。nop-sys-dao 64 测试全绿。
- **4 ChangeLogInterceptor null 审计：`fixed`**。postSave/postUpdate 的 oldValue/newValue 均改为 `ConvertHelper.toString(value, "")`——空串="列存在但值为空"，NULL 保留给"记录未初始化"。回归：新增 TestOrmEntityChangeLogInterceptorNullMarker（proxy stub 全链，断言 null 列写出 newValue=""）。

## Affected Files

- nop-persistence/nop-orm/src/main/java/io/nop/orm/support/DynamicOrmEntity.java
- nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/dict/SysDictLoader.java
- nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java
- 新增测试：nop-orm/src/test/.../TestDetachedRefAccess.java、nop-sys-dao/src/test/.../TestSysDictLoaderLocale.java、TestOrmEntityChangeLogInterceptorNullMarker.java
