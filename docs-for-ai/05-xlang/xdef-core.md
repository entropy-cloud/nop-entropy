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

### 注释约定：用 @attrName 说明属性语义

XDef / XDSL 文件通常会在 XML 注释块中，用类似 JavaDoc 的方式记录关键属性的语义。

```xml
<!--
支持异步执行的任务引擎示例（节点级别的注释）

@firstStep  当 graphMode=true 时，第一个执行的步骤 id（属性级别的注释）
-->
<task firstStep="string" >
        ...
</task>
```


### XDef的本质：与最终XML同构

**关键理解**：XDef模型应该与最终生成的XML结构同构，只是用类型信息替换具体的值。

并且在实践中建议遵循一个简单约定：

- **简单标量字段优先用属性**（string/int/bool/date 等）
- **长文本或复杂结构用子节点**（典型是 `description`，或嵌套对象/集合）

#### 推荐写法示例：

```xml
<!-- XDef定义（推荐：简单字段用属性） -->
<user id="!string" name="string" age="integer" active="boolean">
    <description>string</description>
</user>

<!-- 最终生成的XML示例 -->
<user id="u-1" name="张三" age="25" active="true">
    <description>这是一个很长的说明……</description>
</user>
```

#### 常见错误（避免使用）：
```xml
<!-- ❌ 错误：使用type属性定义类型 -->
<user>
    <name type="string"/>
    <age type="integer"/>
</user>

```

### 正确语法模式

#### 1. 属性类型定义（推荐）
```xml
<user id="!string" name="string" email="string"/>
<product price="double" quantity="integer"/>
<settings active="boolean" visible="boolean=false"/>
```

#### 2. 子节点类型定义（仅用于长文本/复杂结构）

```xml
<service name="!var-name">
    <description>string</description>
</service>
```

#### 3. 列表类型定义
```xml
<users xdef:body-type="list" xdef:key-attr="id">
    <user id="!string" name="string"/>
</users>
```

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
<xdef:define xdef:name="BeanPropValue" xdef:body-type="union">
    <bean/>
    <ref bean="!bean-name"/>
    <value xdef:name="BeanSimpleValue" type="class-name">string</value>
</xdef:define>

<!-- 使用 -->
<prop xdef:ref="BeanPropValue"/>
```

**本文件引用（更常见）**：引用“普通节点上声明的 `xdef:name` 模型”

也就是说，`xdef:ref` 的目标不一定必须写在 `<xdef:define>` 里；只要某个节点声明了 `xdef:name="SomeModel"`，就可以在同一个 xdef 文件中通过 `xdef:ref="SomeModel"` 复用它的结构。

例如在下面`api.xdef`的示例中：

```xml
<!-- 定义一个可复用的 option 结构（普通节点上声明 xdef:name） -->
<option name="!string" value="!any" xdef:name="ApiOptionModel" xdef:unique-attr="name">
    <description>string</description>
</option>

<!-- 在 service/method/message 等位置复用同一个 option 结构 -->
<option name="!string" xdef:ref="ApiOptionModel" xdef:unique-attr="name"/>
```

**外部引用**：引用其他xdef文件
```xml
<dict name="!string" xdef:ref="/nop/schema/orm/dict.xdef"/>
```

### 3. 集合节点：xdef:body-type + xdef:key-attr + xdef:unique-attr


- `xdef:body-type`：决定子节点是 **list/set/union/map** 以及对应的解析结构
- `xdef:key-attr`：当 body-type 为 `list` 或 `set` 时，指定“key 属性名”（便于稳定定位、快速查找、差量合并）
- `xdef:unique-attr`：约束同一父节点下同名子节点集合中，某个属性必须唯一，此时相当于省略了父节点。

常见组合：

1) **有序列表（list）+ key**：

```xml
<services xdef:body-type="list" xdef:key-attr="name">
    <service name="!var-name" className="!class-name"/>
</services>
```

2) **列表中声明唯一属性（unique-attr）**：

```xml
<option xdef:unique-attr="name" name="!string" value="any"/>

 它等价于

 <options xdef:body-type="list" xdef:key-attr="name">
   <option name="!string" value="any" />
 </options>

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

    <!-- options定义 -->
    <option name="!string" xdef:name="ApiOptionModel"
            xdef:unique-attr="name" value="string" />

    <!-- 唯一、key、list类型 -->
    <services xdef:body-type="list" xdef:key-attr="name">
        <service xdef:name="ApiServiceModel"
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
3. **xdef.xdef特殊性**：涉及命名空间重命名，一般AI生成固定使用xdef名字空间，而不需要重命名为meta
4. **引用路径**：`xdef:ref`使用虚拟文件系统路径（如`/nop/schema/orm.xdef`）或者`xdef:name`指定的名称（如MessageModel）
