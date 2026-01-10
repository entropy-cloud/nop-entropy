# XDef核心概念

## XDSL文件基本格式

所有XDSL文件（包括xdef文件本身）的根节点必须包含两个关键属性：

```xml
<your-tag x:schema="/nop/schema/your-xdef.xdef"
           xmlns:x="/nop/schema/xdsl.xdef">
    ...
</your-tag>
```

- `x:schema`：指定当前文件所使用的xdef元模型定义文件路径
- `xmlns:x`：声明x命名空间，对应DSL公共语法（extends、dump等）

**示例**：
```xml
<!-- api.xdef - 定义API模型的元模型 -->
<api x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xdef:name="ApiModel">
    ...
</api>

<!-- app.api.xml - 使用api.xdef的DSL文件 -->
<api x:schema="/nop/schema/api.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:app="app">
    <services>...</services>
</api>

<!-- beans.xml - 使用beans.xdef的配置文件 -->
<beans x:schema="/nop/schema/beans.xdef"
       xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc">
    <bean id="myBean" class="MyClass"/>
</beans>
```

## 核心概念

### 1. xdef:name - Java类名

将标签映射为Java类名，配合根节点的`xdef:bean-package`确定完整类名。

```xml
<api xdef:bean-package="io.nop.rpc.model">
    <service xdef:name="ApiServiceModel" name="!string"/>
    <!-- 生成类: io.nop.rpc.model.ApiServiceModel -->
</api>
```

### 2. xdef:ref - 引用结构

引用已定义的结构，支持复用和扩展。

**本文件引用**：引用`xdef:define`定义的片段
```xml
<xdef:define xdef:name="BaseFields">
    <createTime type="datetime"/>
    <updateTime type="datetime"/>
</xdef:define>

<!-- 使用定义 -->
<entity name="User">
    <fields xdef:ref="BaseFields"/>
    <name type="string"/>
</entity>
```

**外部引用**：引用其他xdef文件
```xml
<dict name="!string" xdef:ref="/nop/schema/orm/dict.xdef"/>
```

### 3. xdef:define - 定义可复用片段

定义模板，可通过`xdef:ref`引用。

```xml
<xdef:define xdef:name="BeanPropValue" xdef:body-type="union">
    <bean/>
    <ref bean="!bean-name"/>
    <value xdef:name="BeanSimpleValue" type="class-name">string</value>
</xdef:define>

<!-- 使用 -->
<prop xdef:ref="BeanPropValue"/>
```

### 4. xdef:mandatory - 必填标记

标记某个子节点必须存在。

```xml
<service xdef:name="ApiServiceModel">
    <description>string</description>  <!-- 必须提供 -->
</service>
```

### 5. xdef:body-type - 子节点组织方式

- `list`：允许多个子节点，按顺序组织为List
- `set`：子节点唯一，组织为Set或KeyedList
- `union`：可以是多种类型之一
- `map`：解析为Map

```xml
<services xdef:body-type="list" xdef:key-attr="name">
    <service name="userService"/>
    <service name="orderService"/>
</services>
```

### 6. xdef:key-attr - 指定key属性

当`body-type`为`list`或`set`时，用哪个属性作为key。

```xml
<options xdef:body-type="list" xdef:key-attr="name">
    <option name="cacheEnabled" value="true"/>
    <option name="cacheTTL" value="3600"/>
</options>
<!-- 可以通过 "cacheEnabled" 快速找到对应option -->
```

### 7. xdef:unique-attr - 唯一性约束

指定属性值在同一父节点下必须唯一。

```xml
<option xdef:name="ApiOptionModel" xdef:unique-attr="name"
        name="!string" value="!any"/>

<!-- 所有option的name必须唯一 -->
```

## 实际示例

### 定义API模型

```xml
<!-- api.xdef -->
<api x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xdef:bean-package="io.nop.rpc.model"
      xdef:name="ApiModel">

    <description>string</description>

    <!-- 唯一、key、list类型 -->
    <services xdef:body-type="list" xdef:key-attr="name">
        <service xdef:name="ApiServiceModel"
                 xdef:unique-attr="name"
                 name="!var-name" className="!class-name">
            <description>string</description>

            <!-- option引用 -->
            <option name="!string" xdef:ref="ApiOptionModel"
                    xdef:unique-attr="name"/>
        </service>
    </services>
</api>
```

### 使用API模型

```xml
<!-- app.api.xml -->
<api x:schema="/nop/schema/api.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:app="app">

    <description>示例API</description>

    <services>
        <service name="userService" className="UserService">
            <description>用户服务</description>

            <option name="cacheEnabled" value="true"/>
            <option name="cacheTTL" value="3600"/>
        </service>

        <service name="orderService" className="OrderService">
            <description>订单服务</description>

            <option name="cacheEnabled" value="false"/>
        </service>
    </services>
</api>
```

### 定义ORM模型

```xml
<!-- orm.xdef (简化示例) -->
<orm x:schema="/nop/schema/xdef.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xdef:bean-package="io.nop.orm.model"
      xdef:name="OrmModel">

    <entities xdef:body-type="list" xdef:key-attr="name">
        <entity xdef:name="EntityModel"
                xdef:unique-attr="name"
                name="!var-name" table="!var-name">
            <columns xdef:body-type="list" xdef:key-attr="name">
                <column xdef:name="ColumnModel"
                         xdef:unique-attr="name"
                         name="!string" type="!string"/>
            </columns>
        </entity>
    </entities>
</orm>
```

## 类型说明

**def-type格式**：`(!~#)?{stdDomain}:{options}={defaultValue}`

- `!`：必填
- `~`：内部或已废弃
- `#`：支持编译期表达式
- `stdDomain`：标准域（如email等）
- `options`：参数（如enum路径）
- `defaultValue`：默认值

**示例**：
- `!string`：必填字符串
- `!boolean=false`：必填布尔，默认false
- `enum:io.nop.xxx.MyEnum`：枚举类型
- `!int=100`：必填整数，默认100

## 注意事项

1. **所有XDSL文件必须声明**：`x:schema`和`xmlns:x`
2. **类型区分**：xdef文件（定义）vs xdsl文件（使用），都需要声明
3. **xdef.xdef特殊性**：涉及命名空间重命名，一般AI生成不需要处理
4. **引用路径**：`xdef:ref`使用虚拟文件系统路径（如`/nop/schema/orm.xdef`）
