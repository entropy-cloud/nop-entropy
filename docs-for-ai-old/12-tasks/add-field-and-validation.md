# 新增字段与校验（优先改模型，不写 Java）

## 适用场景

- 你要给实体增加字段、加必填/范围/格式校验
- 你希望尽量走 Nop 的模型驱动路径（生成/运行期适配）

## AI 决策提示

- ✅ 优先：修改 xmeta（字段定义 + 校验规则）
- ✅ 如果字段影响 API/前端：同步更新相关 XDSL（如 xview）
- ✅ 只有当校验需要复杂逻辑时，才考虑 BizModel 扩展点

## 最小闭环（占位）

本仓库中实际存在的模型文件可以作为参考模板，最短闭环是：

1) 找到对应对象的 `.xmeta`（定义字段、校验、可查询/可排序等能力）
2) 如需 UI/页面：`.view.xml` 通常 `x:extends` 生成稿 `_gen/_*.view.xml` 并只覆写少量节点
3) 如果是“差量定制”：在 `src/main/resources/_vfs/_delta/default/...` 下创建同结构的差量文件，用 `x:extends="super"` 或 `x:extends="..."` 做增量覆盖

### 1) 实体/对象的 XMeta 在哪里？

仓库里大量模块使用如下 layout（示例来自工作流模块）：

- 元数据定义：
	- `nop-wf/nop-wf-meta/src/main/resources/_vfs/nop/wf/model/NopWfLog/_NopWfLog.xmeta`
	- 入口文件（extends 生成稿）：`nop-wf/nop-wf-meta/src/main/resources/_vfs/nop/wf/model/NopWfLog/NopWfLog.xmeta`

`NopWfLog.xmeta` 的形式非常典型：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopWfLog.xmeta"/>
```

而 `_NopWfLog.xmeta` 里才是完整字段定义（含 mandatory/queryable/sortable 等）：

```xml
<props>
	<prop name="logLevel" displayName="日志级别" mandatory="true" queryable="true" sortable="true">
		<schema type="java.lang.Integer" dict="core/log-level"/>
	</prop>
	<prop name="logMsg" displayName="日志消息" queryable="true" sortable="true">
		<schema type="java.lang.String" precision="4000"/>
	</prop>
</props>
```

### 2) UI（XView）如何关联到 XMeta？

以系统模块的页面为例：

- 业务覆写页：`nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysTag/NopSysTag.view.xml`

```xml
<view x:extends="_gen/_NopSysTag.view.xml" x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef">
	...
</view>
```

- 生成页（包含 objMeta 绑定）：`nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysTag/_gen/_NopSysTag.view.xml`

```xml
<objMeta>/nop/sys/model/NopSysTag/NopSysTag.xmeta</objMeta>
```

### 3) 如何通过 _delta 扩展字段（不改原文件）

仓库中存在真实差量 xmeta 示例（Quarkus demo）：

- `nop-demo/nop-quarkus-demo/src/main/resources/_vfs/_delta/default/nop/sys/model/NopSysNoticeTemplate/NopSysNoticeTemplate.xmeta`

其结构如下：

```xml
<meta x:extends="super" x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
	<props>
		<prop name="extFldA" displayName="扩展字段A" queryable="true" sortable="true" insertable="true" updatable="true">
			<schema type="String" domain="email"/>
		</prop>
	</props>
</meta>
```

### 4) 验证方式（AI 友好）

- 如果你扩展的是 `.xmeta` 字段：
	- 对应的 GraphQL/REST selection 中通常会出现新字段（取决于类型暴露策略）
	- 保存/更新时，会触发 mandatory/schema 的校验与类型转换（由平台 XMeta 驱动）

> 注意：具体字段是否暴露、是否生成/运行期合并，和模块生成策略/元模型配置有关；遇到不确定时，以 `objMeta` 链路（XView 中的 `<objMeta>...xmeta</objMeta>`）和对应模块的 `_vfs` 结构为准。
