# XDSL文件格式与Delta合并

## 概述

XDSL（X Domain Specific Language）是Nop平台中所有领域特定语言的统一框架，所有orm.xml、beans.xml、api.xml、task.xml、batch.xml等文件都遵循XDSL规范。

**核心特点**：
- **Delta格式**：Delta文件与全量文件结构完全相同，只添加了x:override等少量属性
- **稳定定位**：通过id、name等唯一键属性进行元素定位（不是数组索引）
- **差量定制**：基于可逆计算理论：`App = Delta x-extends Generator<DSL>`

## XDSL文件格式

所有XDSL文件根节点必须包含以下属性：

```xml
<your-tag x:schema="/nop/schema/your-xdef.xdef"
           xmlns:x="/nop/schema/xdsl.xdef"
           x:extends="base-model.xml">
    ...
</your-tag>
```

**必须属性**：
- `x:schema`：指定使用的xdef元模型文件路径
- `xmlns:x`：声明x命名空间（DSL公共语法）

**可选属性**：
- `x:extends`：引入被继承的基础模型（支持逗号分隔多个）
- `x:dump`：设置为true时打印差量合并过程和结果（调试用）

**示例**：
```xml
<!-- my.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="base.orm.xml">
    <entities>
        <entity name="io.nop.app.MyEntity">
            <columns>
                <column name="id" type="string" primary="true"/>
                <column name="name" type="string"/>
            </columns>
        </entity>
    </entities>
</orm>

<!-- my.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef"
       xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc"
       x:extends="base.beans.xml">
    <bean id="myBean" class="MyClass">
        <property name="prop1" value="value1"/>
    </bean>
</beans>
```

## Delta格式与JSON Patch区别

### Delta格式

**特点**：结构与全量格式完全相同，只补充x:override等控制属性

```xml
<!-- Delta格式：结构与全量相同 -->
<orm x:extends="base.orm.xml">
    <entities>
        <!-- 通过name属性稳定定位 -->
        <entity name="User">
            <columns>
                <column name="phone" x:override="remove"/>
                <column name="email">
                    <!-- 通过name属性定位，修改属性 -->
                    <maxLength x:override="replace">200</maxLength>
                </column>
            </columns>
        </entity>
    </entities>
</orm>
```

### JSON Patch格式

**特点**：使用RFC 6902格式，通过数组索引定位，结构完全不同

```json
[
  {
    "op": "replace",
    "path": "/entities/0/columns/2/maxLength",
    "value": 200
  }
]
```

### 关键区别

| 特性 | Delta | JSON Patch |
|------|-------|------------|
| 结构 | 与全量相同 | 与全量不同 |
| 定位 | 唯一键属性 | 数组索引 |
| 稳定性 | 高（基于键） | 低（基于位置） |

## x:override合并模式

`x:override`控制节点合并时的行为：

| 值 | 说明 | 使用频率 |
|-----|------|----------|
| `merge` | 合并属性和子节点（默认值） | ⭐⭐⭐ |
| `replace` | 整体替换 | ⭐⭐⭐ |
| `remove` | 删除节点 | ⭐⭐ |
| `append` | 合并属性，追加子节点 | ⭐ |
| `prepend` | 合并属性，前插子节点 | ⭐ |

**示例**：
```xml
<!-- 删除节点 -->
<entity name="io.nop.app.OldEntity" x:override="remove"/>

<!-- 整体替换 -->
<form id="myForm" x:override="replace">
    <fields>
        <!-- 全部重新定义 -->
    </fields>
</form>

<!-- 追加字段 -->
<entity name="io.nop.app.User">
    <columns>
        <column name="newColumn" type="string"/>
    </columns>
</entity>

<!-- 修改属性 -->
<entity name="io.nop.app.User">
    <columns>
        <column name="name">
            <length x:override="replace">200</length>
        </column>
    </columns>
</entity>
```

## 常用合并模式

### 1. x:extends继承

```xml
<!-- 继承单个基础模型 -->
<orm x:extends="base.orm.xml">
    ...
</orm>

<!-- 继承多个基础模型（逗号分隔，按顺序合并） -->
<orm x:extends="std.orm.xml,base.orm.xml">
    ...
</orm>
```

### 2. x:gen-extends动态生成

**执行时刻**：编译期执行，在x:extends之前

```xml
<orm x:extends="base.orm.xml">
    <!-- 编译期生成实体 -->
    <x:gen-extends>
        <pdman:GenOrm src="test.pdma.json"
                      xpl:lib="/nop/orm/xlib/pdman.xlib"
                      versionCol="REVISION"
                      createrCol="CREATED_BY"
                      createTimeCol="CREATED_TIME"
                      updaterCol="UPDATED_BY"
                      updateTimeCol="UPDATED_TIME"
                      tenantCol="TENANT_ID"/>
    </x:gen-extends>

    <entities>
        <entity name="io.nop.app.MyEntity">
            ...
        </entity>
    </entities>
</orm>
```

### 3. x:post-extends扩展

**执行时刻**：编译期执行，在当前模型之后，最终覆盖

```xml
<orm x:extends="base.orm.xml">
    <!-- 后置扩展：为json字段自动生成component -->
    <x:post-extends>
        <orm-gen:JsonComponentSupport xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>

    <entities>
        <entity name="io.nop.app.MyEntity">
            <columns>
                <column name="jsonExt" tagSet="json" stdSqlType="VARCHAR" precision="4000"/>
            </columns>
            <!-- 自动生成component配置 -->
            <components>
                <component name="jsonExtComponent"
                           class="io.nop.orm.component.JsonOrmComponent">
                    <prop name="jsonText" column="jsonExt"/>
                </component>
            </components>
        </entity>
    </entities>
</orm>
```

### 4. x:override与x:post-extends组合

```xml
<orm x:extends="base.orm.xml">
    <!-- post-extends整体替换基础扩展 -->
    <x:post-extends x:override="replace">
        <orm-gen:CustomPostExtends xpl:lib="/nop/orm/xlib/custom.xlib"/>
    </x:post-extends>
</orm>
```

### 5. xpl:lib导入标签库

```xml
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:xpl="/nop/schema/xpl.xdef"
      xmlns:ai="/nop/ai/xlib/ai.xlib"
      x:extends="/nop/task/lib/common.task.xml">

    <!-- 导入xpl标签库 -->
    <c:import from="/nop/task/xlib/log.xlib"/>
    <c:import from="/nop/ai/xlib/ai.xlib" as="ai"/>

    <!-- 使用导入的标签库 -->
    <log:info>任务开始执行</log:info>
    <ai:chatOptions provider="deepseek" model="deepseek-chat"/>
</task>
```

### 6. feature:on特性表达式

```xml
<orm x:extends="base.orm.xml">
    <entities>
        <!-- 只有nop.orm.user-use-tenant特性启用时，此entity才存在 -->
        <entity name="io.nop.auth.dao.entity.NopAuthUser"
                feature:on="nop.orm.user-use-tenant">
            <columns>
                <column name="tenantId" type="string"/>
            </columns>
        </entity>
    </entities>
</orm>
```

### 7. xmlns:xxx声明其他命名空间

```xml
<beans x:schema="/nop/schema/beans.xdef"
       xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc"
       xmlns:ext="ext"
       xmlns:orm-gen="orm-gen">

    <bean id="myBean" class="MyClass">
        <ioc:aop="false"/>
        <ext:basePackageName="app.demo"/>
    </bean>

    <x:gen-extends>
        <orm-gen:DefaultGenExtends xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:gen-extends>
</beans>
```

## 元编程执行顺序

完整执行顺序：

```
1. 解析x:extends引入的基础模型（按逗号分隔顺序）
2. 执行x:gen-extends（编译期生成，覆盖x:extends）
3. 与当前模型合并
4. 执行x:post-extends（编译期扩展，最终覆盖）
5. 删除x命名空间的所有属性和子节点
```

**示例**：
```xml
<model x:extends="A,B">
    <x:gen-extends>
        <C/>
        <D/>
    </x:gen-extends>

    <node id="existing" modified="true"/>
    <new-node/>

    <x:post-extends>
        <E/>
        <F/>
    </x:post-extends>
</model>
```

**合并结果**：
```
F x-extends E x-extends model x-extends D x-extends C x-extends B x-extends A
```

## 唯一键定位

Delta合并通过唯一键属性进行稳定定位：

### 1. 定位属性

通过XDef元模型中的`xdef:key-attr`或`xdef:unique-attr`指定：

```xml
<!-- columns定义 -->
<columns xdef:key-attr="name" xdef:body-type="list">
    <column name="id" type="string"/>
    <colunn name="name" type="string"/>
</columns>

<!-- entities定义 -->
<entities xdef:key-attr="name" xdef:body-type="list">
    <entity name="io.nop.app.MyEntity"/>
</entities>
```

### 2. 定位规则

- 优先使用`xdef:key-attr`指定的属性
- 如果没有key-attr，使用`xdef:unique-attr`指定的属性
- 通过定位属性找到基础模型中的对应元素进行合并

### 3. 实际示例

```xml
<!-- base.orm.xml -->
<entities>
    <entity name="User">
        <columns>
            <column name="id" type="string" primary="true"/>
            <column name="name" type="string"/>
        </columns>
    </entity>
</entities>

<!-- my.orm.xml -->
<orm x:extends="base.orm.xml">
    <entities>
        <!-- 通过name属性定位到User实体 -->
        <entity name="User">
            <columns>
                <!-- 通过name属性定位到name列，修改type -->
                <column name="name" type="text" length="500"/>

                <!-- 新增email列 -->
                <column name="email" type="string"/>

                <!-- 通过name属性定位到id列，删除它 -->
                <column name="id" x:override="remove"/>
            </columns>
        </entity>
    </entities>
</orm>
```

## 实际应用示例

### 1. 标准ORM模型定制

```xml
<!-- app.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="base.orm.xml">

    <!-- 标准后置扩展 -->
    <x:post-extends x:override="replace">
        <orm-gen:DefaultPostExtends xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>

    <entities>
        <entity name="io.nop.app.MyEntity">
            <columns>
                <column name="id" type="string" primary="true"/>
                <column name="name" type="string"/>
            </columns>
        </entity>
    </entities>
</orm>
```

### 2. 多环境配置

```xml
<!-- dev.app.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      x:extends="base.orm.xml">

    <x:post-extends>
        <dev:EnableDevMode xpl:lib="/nop/dev/xlib/dev.xlib"/>
    </x:post-extends>

    <entities>
        <entity name="io.nop.app.MyEntity">
            <columns>
                <column name="debugInfo" type="text" feature:on="dev.enabled"/>
            </columns>
        </entity>
    </entities>
</orm>
```

### 3. Task文件继承

```xml
<task x:schema="/nop/schema/task/task.xdef"
      xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:task="task"
      xmlns:ai="/nop/ai/xlib/ai.xlib"
      x:extends="/nop/task/lib/common.task.xml">

    <steps x:def:key-attr="name" xdef:body-type="list">
        <!-- 继承common.task.xml中的步骤定义 -->
        <step name="step1">
            <description>分析代码</description>
        </step>
        <step name="step2">
            <description>生成代码</description>
        </step>
    </steps>
</task>
```

### 4. Beans配置定制

```xml
<!-- my-app.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef"
       xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc"
       x:extends="base.beans.xml">

    <!-- 添加自定义bean -->
    <bean id="myCustomBean" class="com.example.MyService">
        <property name="config" value="${cfg:my.config}"/>
    </bean>

    <!-- 覆盖基础bean的属性 -->
    <bean id="baseBean" class="com.example.BaseService">
        <property name="timeout" value="${cfg:my.timeout}"/>
    </bean>
</beans>
```

## 最佳实践

1. **合理使用x:extends**：将公共配置抽取到基础模型中
2. **善用x:override**：明确指定合并模式（merge/replace/remove）
3. **善用x:post-extends**：封装扩展逻辑，保持基础模型简洁
4. **善用xpl:lib**：封装常用功能到标签库
5. **保持结构一致**：Delta格式与全量格式结构相同
6. **唯一键命名规范**：确保id、name等唯一键属性稳定且唯一
7. **多环境隔离**：使用不同的基础模型支持多环境配置
8. **善用feature:on**：基于特性表达式控制节点存在
9. **简化继承链**：避免过深的继承关系
10. **调试模式**：使用x:dump="true"查看合并结果

## 注意事项

1. **x:extends支持多路径**：可以使用逗号分隔多个基础模型
2. **x:post-extends在最后执行**：确保覆盖当前模型的所有修改
3. **x:gen-extends编译期执行**：不能访问运行时变量
4. **唯一键必须稳定**：唯一键属性值不应频繁变更
5. **合并顺序很重要**：理解x:gen-extends、x:extends、x:post-extends的执行顺序
6. **调试模式**：使用x:dump="true"查看合并过程和结果
