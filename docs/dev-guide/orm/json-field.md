# JSON字段支持

在NopORM中，JSON字段提供了对JSON数据的原生支持，通过标准域(stdDomain)配置和自动生成的组件类，实现了JSON数据的便捷操作。

## 基础配置

在ORM模型中为列设置`stdDomain`为`json`，系统会在`<orm-gen:DefaultPostExtends>`处理过程中自动执行`JsonComponentSupport`
，为每个JSON字段生成对应的Component字段。

**示例配置**：

```xml
<!-- NopRuleNode.orm.xml -->
<entity name="NopRuleNode">
  <column name="outputs" stdDomain="json" length="2000"/>
</entity>
```

## 自动生成的Component字段

以上配置会自动生成`outputsComponent`字段，提供以下功能：

- 自动JSON解析和序列化
- 提供结构化访问方法
- 支持直接操作JSON内部结构

### 元数据映射配置

在`xmeta`文件中，可以配置别名映射到Component的内部属性：

```xml
<!-- NopRuleNode.xmeta -->
<prop name="outputsMap"
      mapToProp="outputsComponent._jsonMap"
      displayName="输出值"
      lazy="true"/>
```

**配置说明**：

- 前端提交`outputsMap`字段时，实际调用`entity.getOutputsComponent().set_JsonMap(data)`
- 读取操作通过`outputsComponent`对象完成
- `lazy="true"`表示延迟加载JSON数据

## Component概念详解

这里的Component是**后端概念**，类似于Hibernate的Embedded组件：

- 一个或多个字段组合成Component对象
- 提供增强的组合方法
- JsonComponent封装JSON解析/序列化逻辑
- FileComponent封装附件文件存储功能
