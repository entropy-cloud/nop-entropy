# Nop入门：增加DSL模型解析器

Nop平台为开发自定义DSL（Domain Specific Language，领域特定语言）提供了一系列基础设施，核心机制是通过XDef元模型（Nop平台的元模型语言）定义XML格式的语法结构，然后自动根据XDef生成AST（Abstract Syntax Tree，抽象语法树）节点类，并实现解析器、
生成器、验证器等工具。具体介绍参见[XDef：一种面向演化的元模型及其构造哲学](https://mp.weixin.qq.com/s/gEvFblzpQghOfr9qzVRydA)。

虽然XDef元模型语言采用XML语法格式，但这并不意味着Nop平台中的DSL只能使用XML格式表达。实际上，根据**可逆计算理论**, 同一信息结构具有多种表达形式，这些表达形式之间可以自动进行可逆转换。

本文将介绍如何为DSL引入Markdown语法格式，并演示如何实现Markdown与XML/JSON之间的双向转换。

为了更好地理解如何扩展DSL加载器，我们首先需要了解Nop平台的核心模块结构：

## 1. nop-kernel核心模块

Nop平台内置了丰富的模块，但用于支持DSL定义和实现的核心部分（也是可逆计算理论的实现核心）完全集中在`nop-kernel`目录下。该目录包含以下关键子模块：

1. **nop-dependencies**: 统一管理Nop平台使用的外部第三方依赖库版本
2. **nop-api-core**: 提供各类注解以及`ApiRequest`/`ApiResponse`等框架级别的DTO定义
3. **nop-commons**: 包含各种工具类（Utility Helpers）
4. **nop-core**: 实现虚拟文件系统、XML/JSON等基础格式解析器和反射机制
5. **nop-javac**: 对Java内置JavaC编译器的封装
6. **nop-dataset**: 封装`IRecordInput`/`IDataSet`接口，统一处理各类列表数据和表格数据
7. **nop-xdefs**: 集中存放Nop平台内置的所有XDef元模型
8. **nop-xlang**: 实现Xpl模板语言、XScript脚本语言以及Delta合并算法
9. **nop-antlr4**: 为XScript脚本语言提供antlr4扩展和封装支持
10. **nop-codegen**: 提供`XCodeGenerator`代码生成器
11. **nop-record-mapping**: 实现异构对象映射模型，用于辅助实现Markdown结构与一般模型对象之间的双向映射
12. **nop-markdown**: 为Markdown格式的DSL解析提供额外封装，包含一个简易的Markdown解析器
13. **nop-kernel-cli**: 演示用的命令行工具，提供`convert`（模型格式转换）和`gen`（模板驱动代码生成）两个指令

* 其中，`nop-kernel-cli`模块下的`demo`目录包含了DSL自定义解析器和自定义代码生成模板的示例

了解了核心模块结构后，接下来我们将学习如何为DSL注册自定义加载器：

## 2. 注册加载器

在Nop平台中，所有DSL文件格式都对应一个唯一的文件类型(fileType)。
全局模型加载器`ResourceComponentManager`在加载文件时会根据文件类型动态确定使用的加载器。

因此，为了给`orm.xml`这种文件定义DSL加载器，需要编写`/nop/core/registry/orm.register-model.xml`

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
  <loaders>
    <!-- 为XML/JSON/YAML格式注册内置加载器 -->
    <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    <xdsl-loader fileType="orm.json" schemaPath="/nop/schema/orm/orm.xdef"/>
    <xdsl-loader fileType="orm.yaml" schemaPath="/nop/schema/orm/orm.xdef"/>

    <!-- 为Markdown格式注册自定义加载器 -->
    <loader fileType="orm.md" mappingName="orm.Md_to_OrmModel"
            class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  </loaders>
</model>
```

上述配置文件定义了ORM模型的加载器注册信息：

- `xdsl-loader`：用于配置XML/JSON/YAML格式的模型加载器。Nop平台内置了`DslJsonResourceLoader`和`DslXmlResourceLoader`，它们会根据文件类型自动选择合适的解析器。
- `loader`：用于定义扩展加载器，可支持自定义文件格式。
  - `fileType`：指定文件类型标识符
  - `mappingName`：指定用于解析的映射规则名称
  - `class`：指定加载器工厂类的实现路径，需实现`IResourceObjectLoaderFactory`或`IResourceObjectLoader`接口

以下是相关接口的定义：

```java
// 资源对象加载器工厂接口
interface IResourceObjectLoaderFactory<T> {
    IResourceObjectLoader<T> newResourceObjectLoader(ComponentModelConfig config, Map<String, Object> attributes);
}

// 资源对象加载器接口
interface IResourceObjectLoader<T> {
  T loadObjectFromResource(IResource resource);
}

// 资源对象保存器接口
interface IResourceObjectSaver<T> {
  void saveObjectToResource(IResource resource, T obj);
}
```

MarkdownDslResourceLoaderFactory返回的MarkdownDslResourceLoader实现了如下四个接口

- `IResourceObjectLoader`: 解析资源文件得到模型对象
- `IResourceObjectSaver`: 将模型对象保存到资源文件中
- `IResourceDslNodeLoader`: 解析资源文件得到XNode（Nop平台中通用的DSL抽象语法树节点，用于统一表示不同格式的DSL结构）
- `IResourceDslNodeSaver`: 将XNode保存到资源文件中

除了`IResourceObjectLoader`接口之外，其他三个接口都是可选的。实现这些接口后，就可以支持双向转换功能。

注册完加载器后，我们需要定义Markdown与模型对象之间的映射规则。这正是`RecordMapping`机制的用武之地：

## 3. 基于Mapping模型的Markdown解析和生成

`RecordMapping`是Nop平台内置的对象映射机制，用于定义两个异构Java对象之间的相互映射规则，支持字段映射、类型转换、表达式计算等功能。
在RecordMapping格式的基础上补充`md:format`等扩展字段信息，就可以实现MarkdownSection对象与DSL模型对象之间的映射。

```xml
<definitions xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/record/record-mappings.xdef"
             xmlns:md="md" x:dump="true">


  <!-- 定义从Markdown到ORM模型的映射规则 -->
  <mapping name="Md_to_OrmModel" md:titleField="displayName">
    <fields>
      <!-- 直接映射displayName字段 -->
      <field name="displayName" from="displayName"/>

      <!-- 映射extends字段，并指定别名 -->
      <field name="x:extends" from="extends" alias="Extends">
        <schema stdDomain="string"/>
      </field>

      <!-- 映射实体定义，使用Md_to_EntityModel作为子项映射规则 -->
      <field name="entities" from="实体定义" alias="Entities" keyProp="name"
             itemMapping="Md_to_EntityModel">
      </field>
    </fields>
  </mapping>

  <!-- 定义从Markdown到实体模型的映射规则 -->
  <mapping name="Md_to_EntityModel" md:titleField="name">
    <fields>
      <!-- 映射对象名，使用表达式生成完整类名 -->
      <field name="name" from="对象名" alias="Object Name">
        <schema stdDomain="class-name"/>
        <valueExpr>
          value?.$fullClassName(rootRecord['ext:entityPackageName'])
        </valueExpr>
      </field>

      <!-- 映射表名，设置为必填字段 -->
      <field name="tableName" from="表名" mandatory="true" alias="Table Name">
        <schema stdDomain="prop-name"/>
      </field>

      <!-- 映射中文名，设置为必填字段 -->
      <field name="displayName" from="中文名" mandatory="true" alias="Chinese Name">
        <schema stdDomain="string"/>
      </field>

      <!-- 映射字段列表，指定为表格格式 -->
      <field name="columns" from="字段列表" keyProp="name"
             alias="Column List" itemMapping="Md_to_ColumnModel" md:format="table">
      </field>

      <!-- 映射关联列表，指定为表格格式 -->
      <field name="relations" from="关联列表" alias="Relation List"
             itemMapping="Md_to_RelationModel" md:format="table">
      </field>

    </fields>
  </mapping>

</definitions>
```

Nop平台通过`MappingBasedMarkdownParser`和`MappingBasedMarkdownGenerator`根据Mapping模型配置实现解析与生成功能：

- `md:titleField`: 用于指定标题对应的解析字段
- `md:format`: 指定字段对应的Markdown格式，目前支持`table|code`，分别对应表格格式和代码块格式

我们约定了简单的Markdown编码规则：
1. 每个section只能是List/Table/CodeBlock等几种格式
2. 通过子section实现复杂对象，标题对应属性名
3. 若section对应于List，则每个子section对应一个对象，此时section的标题对应对象的titleField

以下是一个完整的Markdown格式ORM模型示例：

````markdown
# AI模型管理

- extends:  demo.orm.md

## gen-extends
```xml
<orm-gen:DefaultPostExtends xpl:lib="/xlib/orm-gen.xlib"/>
```

## 5 实体定义

### 5.1 NopAiProject

- 表名: nop_ai_project
- 类名: NopAiProject
- 中文名: AI项目
- 备注: 存储AI项目基本信息

#### 5.1.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|英文名|数据域|标准域|类型|长度|小数位数|字典|备注|缺省值|控件|根节点级别|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | seq | true | true | id | id | X | 主键 |  |  |  | VARCHAR | 36 |  |  |  |  |  |  |
| 2 |  | false | true | language | language |  | 项目语言 |  |  |  | VARCHAR | 4 |  | ai/project_language | 项目使用的编程语言类型：JAVA, PYTHON等 |  |  |  |
| 3 |  | false | true | name | name |  | 项目名称 |  |  |  | VARCHAR | 100 |  |  |  |  |  |  |
| 4 |  | false | false | prototype_id | prototypeId |  | 模板项目ID |  |  |  | VARCHAR | 36 |  |  |  |  |  |  |
| 5 |  | false | false | project_dir | projectDir |  | 项目目录 |  |  |  | VARCHAR | 400 |  |  | 项目在文件系统中的存储路径，例如：/data/projects/order-system |  | textarea |  |

````

上述Markdown文件解析后得到的XML格式如下：

```xml
<orm ext:mavenArtifactId="nop-ai" ext:entityPackageName="io.nop.ai.dao.entity" ext:allowIdAsColName="true"
     ext:basePackageName="io.nop.ai" ext:appName="nop-ai" ext:registerShortName="true"
     ext:mavenGroupId="io.github.entropy-cloud" x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     xmlns:ext="ext" displayName="AI模型管理"
     xmlns:ui="ui">
  <entities>
    <entity className="io.nop.ai.dao.entity.NopAiProject" displayName="AI项目" name="io.nop.ai.dao.entity.NopAiProject"
            registerShortName="true" tableName="nop_ai_project">
      <columns>
        <column code="id" displayName="主键" mandatory="true" name="id" precision="36" primary="true" propId="1"
                stdDataType="string" stdSqlType="VARCHAR" tagSet="seq" ui:show="X"/>
        <column code="language" comment="项目使用的编程语言类型：JAVA, PYTHON等" displayName="项目语言"
                mandatory="true"
                name="language" precision="4" propId="2" stdDataType="string" stdSqlType="VARCHAR"
                ext:dict="ai/project_language"/>
        <column code="name" displayName="项目名称" mandatory="true" name="name" precision="100" propId="3"
                stdDataType="string" stdSqlType="VARCHAR"/>
        <column code="prototype_id" displayName="模板项目ID" name="prototypeId" precision="36" propId="4"
                stdDataType="string" stdSqlType="VARCHAR"/>
        <column code="project_dir" comment="项目在文件系统中的存储路径，例如：/data/projects/order-system"
                displayName="项目目录"
                name="projectDir" precision="400" propId="5" stdDataType="string" stdSqlType="VARCHAR"
                ui:control="textarea"/>
      </columns>
      <comment>存储AI项目基本信息</comment>
    </entity>
  </entities>
</orm>
```

可以看到，Markdown中定义的字段列表（表格格式）被正确转换为XML中的`<columns>`节点，包括字段名称、数据类型、约束条件等信息都被准确映射。

完成了加载器注册和映射规则定义后，我们就可以使用`nop-kernel-cli`工具进行实际的模型转换和代码生成操作了：

## 4. 通过元编程自动生成反向映射

Nop平台提供了强大的元编程能力，可以根据已定义的正向映射自动生成反向映射配置。例如，当我们定义了从Markdown到ORM模型的映射`Md_to_OrmModel`后，系统可以自动生成从ORM模型到Markdown的反向映射`OrmModel_to_Md`。

### 4.1 自动生成反向映射的配置

通过在RecordMapping配置中添加`record-mapping-gen:GenReverseMappings`扩展，系统会自动为所有正向映射生成对应的反向映射：

```xml
<definitions xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/record/record-mappings.xdef"
             xmlns:md="md" x:dump="true">
    <x:post-extends>
        <c:import from="/nop/record/xlib/record-mapping-gen.xlib"/>
        <record-mapping-gen:GenReverseMappings/>
    </x:post-extends>

    <!-- 正向映射配置 -->
    <mapping name="Md_to_OrmModel" ...>
        <!-- 正向映射规则 -->
    </mapping>

    <!-- 系统会自动生成OrmModel_to_Md反向映射 -->
</definitions>
```

执行后，系统会自动生成名为`OrmModel_to_Md`的反向映射，其字段映射关系与`Md_to_OrmModel`相反，确保数据可以在两种格式之间无损转换。

### 4.2 差量修正反向映射配置

自动生成的反向映射可能无法完全满足所有需求，这时可以在当前文件中添加差量修正部分来微调配置。根据可逆计算理论，差量定义与全量定义格式完全一致。

```xml
<definitions xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/record/record-mappings.xdef"
             xmlns:md="md" x:dump="true">
    <x:post-extends>
        <c:import from="/nop/record/xlib/record-mapping-gen.xlib"/>
        <record-mapping-gen:GenReverseMappings/>
    </x:post-extends>

    <!-- 正向映射配置 -->
    <mapping name="Md_to_OrmModel" ...>
        <!-- 正向映射规则 -->
    </mapping>

    <mapping name="EntityModel_to_Md">
        <fields>
            <field name="对象名">
                <valueExpr>
                    value?.$removePackageName(sourceRoot['ext:entityPackageName']);
                </valueExpr>
            </field>

            <field name="类名">
                <valueExpr>
                    value?.$removePackageName(sourceRoot['ext:entityPackageName']);
                </valueExpr>
            </field>
        </fields>
    </mapping>
</definitions>
```


## 5. 使用nop-kernel-cli执行模型转换和代码生成

完成DSL加载器配置后，可以使用`nop-kernel-cli`工具执行模型格式转换和代码生成操作。以下是常用命令示例：

```shell
# 将XML格式的ORM模型转换为Markdown格式
java -jar nop-kernel-cli.jar convert demo.orm.xml -o=demo.orm.md

# 将Markdown格式的ORM模型转换回XML格式
java -jar nop-kernel-cli.jar convert demo.orm.md -o=demo.orm.xml

# 基于Markdown格式的ORM模型生成代码
java -jar nop-kernel-cli.jar gen demo.orm.md -t=/nop/templates/orm -o=target
```

通过上述命令，我们可以实现不同格式DSL模型之间的双向转换，以及基于这些模型的代码生成，充分体现了Nop平台的灵活性和可逆计算理论的实践价值。
