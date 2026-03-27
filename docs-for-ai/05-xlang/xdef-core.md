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

**重要规范**：

1. **enum 类型不支持在类型声明中指定默认值**
   ```xml
   <!-- ❌ 错误：enum 类型不能有默认值 -->
   <attr type="enum:io.nop.xxx.MyEnum=defaultValue"/>
   
   <!-- ✅ 正确：enum 类型只指定类型 -->
   <attr type="enum:io.nop.xxx.MyEnum"/>
   ```
   默认值应该在 Java 代码层面或者通过其他机制处理。

2. **逗号分隔的字符串使用 `csv-set` 类型**
   ```xml
   <!-- ❌ 错误：使用 string 类型表示逗号分隔的列表 -->
   <attr columnNames="!string"/>
   
   <!-- ✅ 正确：使用 csv-set 类型 -->
   <attr columnNames="csv-set"/>
   ```
   `csv-set` 会在解析时自动转换为 `Set<String>`，提供类型安全和去重。

3. **简单文本内容的元素定义**
   对于只包含简单文本内容的元素，有两种方式：
   
   ```xml
   <!-- 方式1：直接在元素标签内写类型（推荐） -->
   <description>string</description>
   <labels>string</labels>
   
   <!-- 方式2：使用 xdef:body-type（不推荐用于简单文本） -->
   <description xdef:body-type="string"/>
   ```
   
   **推荐使用方式1**，更简洁且符合 Nop 平台惯例。

## 匽号分隔列表使用 csv-set

当属性值是逗号分隔的字符串时，应使用 `csv-set` 类型：

```xml
<!-- ❌ 错误：使用 string 类型 -->
<attr columnNames="!string"/>

<!-- ✅ 正确：使用 csv-set 类型 -->
<attr columnNames="csv-set"/>
```

`csv-set` 会在解析时自动转换为 `Set<String>`，提供类型安全和去重。

## 简单文本内容的元素定义

对于只包含简单文本内容的元素，有两种方式：

```xml
<!-- 方式1：直接在元素标签内写类型（推荐） -->
<description>string</description>
<labels>string</labels>

<!-- 方式2：使用 xdef:body-type（不推荐用于简单文本） -->
<description xdef:body-type="string"/>
```

**推荐使用方式1**，更简洁且符合 Nop 平台惯例。 `xdef:body-type` 主要用于复杂结构（list、map等）。

## 模型继承：xdef:define + xdef:ref

当多个元素具有共同的属性时，可以使用 `xdef:define` 定义基础结构，然后通过 `xdef:ref` 引用并扩展它。

### 基本语法

```xml
<!-- 1. 定义基础模型（类似接口/抽象类） -->
<xdef:define xdef:name="BaseModel" id="!string" type="string">
    <description>string</description>
</xdef:define>

<!-- 2. 引用基础模型并扩展 -->
<concreteA xdef:ref="BaseModel" xdef:name="ConcreteModelA" extraAttr="int">
    <extraElement>string</extraElement>
</concreteA>

<concreteB xdef:ref="BaseModel" xdef:name="ConcreteModelB" anotherAttr="boolean"/>
```

### 生成的 Java 类结构

上述 xdef 定义会生成具有继承关系的 Java 类：

```java
// BaseModel 的属性会被合并到每个具体类中
public class ConcreteModelA extends AbstractComponentModel {
    // 来自 BaseModel
    private String id;
    private String type;
    private String description;
    // 自身扩展
    private Integer extraAttr;
    private String extraElement;
}

public class ConcreteModelB extends AbstractComponentModel {
    // 来自 BaseModel
    private String id;
    private String type;
    private String description;
    // 自身扩展
    private Boolean anotherAttr;
}
```

### 实际示例：工作流步骤（参考 wf.xdef）

```xml
<!-- 定义基础步骤模型 -->
<step name="!string" displayName="string" xdef:name="WfStepModel" 
      internal="!boolean=false" optional="!boolean=false">
    <description>string</description>
    <on-enter>xpl</on-enter>
    <on-exit>xpl</on-exit>
    <source>xpl</source>
</step>

<!-- Join步骤继承基础步骤，添加join特有属性 -->
<join name="!string" xdef:ref="WfStepModel" 
      joinType="!enum:io.nop.wf.core.model.WfJoinType=and"
      waitStepNames="csv-set" 
      xdef:name="WfJoinStepModel">
    <join-group-expr>xpl</join-group-expr>
</join>

<!-- 子流程步骤继承基础步骤，添加子流程特有配置 -->
<flow name="!string" xdef:ref="WfStepModel" xdef:name="WfSubFlowModel">
    <start wfName="!string" wfVersion="long" xdef:name="WfSubFlowStartModel">
        <arg name="!string" displayName="string" xdef:name="WfSubFlowArgModel">
            <source>xpl</source>
        </arg>
    </start>
</flow>
```

### 关键点

1. **xdef:define**：定义可复用的基础结构，不会单独生成类
2. **xdef:ref**：引用已定义的结构，属性和子元素会被合并
3. **扩展方式**：在 `xdef:ref` 基础上添加新属性和子元素
4. **命名要求**：`xdef:ref` 引用的目标可以是：
   - 同文件中 `xdef:define` 定义的模型
   - 同文件中其他元素声明的 `xdef:name`
   - 外部 xdef 文件（使用路径，如 `xdef:ref="/nop/schema/assignment.xdef"`）

### 与接口的区别

| 特性 | xdef:ref | Java Interface |
|-----|----------|----------------|
| 属性继承 | ✅ 属性合并到子类 | ❌ 不支持属性 |
| 代码生成 | 自动生成完整类 | 需手动实现 |
| 多态支持 | 通过 `xdef:bean-sub-type-prop` | 原生支持 |

## 常见的 def-type 类型

### 基础类型
- `string`：字符串
- `int`：整数
- `long`：长整数
- `boolean`：布尔值
- `double`：双精度浮点数

### 字符串变体
- `!string`：必填字符串
- `~string`：内部字符串（不对外暴露）
- `xml-name`：XML名称（符合XML命名规范）
- `var-name`：变量名（符合Java变量命名规范）
- `class-name`：类名（符合Java类命名规范）
- `package-name`：包名（符合Java包命名规范）
- `bean-name`：Bean名称
- `v-path`：虚拟文件路径

### 集合类型
- `csv-set`：逗号分隔的字符串集合，自动转换为 `Set<String>`
- `class-name-set`：类名集合（逗号分隔）
- `tag-set`：标签集合（逗号分隔）
- `word-set`：单词集合（逗号分隔）

### 复杂类型
- `std-sql-type`：标准SQL类型（Nop平台定义的数据库类型）
- `std-domain`：标准域（Nop平台预定义的数据域）
- `std-data-type`：标准数据类型
- `enum:包名.枚举类名`：枚举类型，**注意：不支持在类型声明中指定默认值**
- `json`：JSON格式
- `xml`：XML格式
- `xpl`：XPL脚本
- `eval-code`：可执行代码（XPL或表达式）
- `any`：任意类型
- `map`：映射类型
- `list`：列表类型
