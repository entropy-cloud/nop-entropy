# 2026-10-02 WI7 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

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
