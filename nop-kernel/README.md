# nop-kernel

nop-kernel 是 Nop Platform 的核心部分，基于可逆计算原理实现的下一代软件构造框架。它提供了一套完整的领域语言工作台（Domain Language Workbench），支持面向语言编程范式，为构建复杂的企业级应用提供了坚实的基础。

作为一个独立的 GitHub 项目，nop-kernel 包含了可逆计算理论的完整实现，以及一套轻量级、高性能的核心框架组件，可以单独使用或与其他框架集成。

## 核心功能

- **可逆计算实现**：基于可逆计算原理的模型驱动开发框架
- **领域语言工作台**：支持自定义DSL的创建、解析、验证和转换
- **元编程支持**：强大的模板引擎和代码生成能力
- **多格式支持**：XML、JSON、YAML、Markdown等格式的统一处理
- **轻量级架构**：不依赖Spring等第三方框架，可独立运行或与其他框架集成
- **高性能**：优化的内部结构和算法，支持GraalVM原生编译

## 子模块说明

nop-kernel 包含以下核心子模块：

| 模块 | 说明 |
|------|------|
| **nop-dependencies** | 统一管理外部依赖库版本 |
| **nop-api-core** | 提供核心API接口和注解定义 |
| **nop-commons** | 通用工具类库 |
| **nop-core** | 核心框架，包含虚拟文件系统、反射机制、数据格式解析等 |
| **nop-xlang** | XLang脚本语言和模板语言实现，支持Delta合并算法 |
| **nop-xdefs** | 内置的各种DSL的XDef元模型定义 |
| **nop-antlr4** | ANTLR4解析器的封装和扩展 |
| **nop-codegen** | 模板驱动的代码生成器 |
| **nop-javac** | Java编译器的封装 |
| **nop-dataset** | 数据集合处理框架 |
| **nop-markdown** | Markdown格式解析和处理 |
| **nop-record-mapping** | 异构对象映射机制 |
| **nop-kernel-cli** | 命令行工具，提供模型转换和代码生成功能 |

## 技术特性

### 1. 可逆计算原理

nop-kernel 基于可逆计算原理设计，实现了模型的正向构建和反向转换，支持：
- 模型的增量式开发和演进
- 自动生成反向映射
- 模型格式的双向转换

### 2. 面向语言编程范式

采用面向语言编程范式，允许开发者：
- 为特定领域设计专用语言(DSL)
- 自动生成解析器、验证器和IDE支持
- 使用DSL表达业务逻辑，提高开发效率和可维护性

### 3. 轻量级设计

- 不依赖Spring等重型框架
- 模块化设计，可按需使用
- 支持与Quarkus、Spring或Solon等框架集成
- 支持GraalVM原生编译，提升性能和启动速度

### 4. 多格式支持

统一处理多种数据格式：
- XML：基于XDef元模型的严格验证
- JSON/YAML：与XML格式的双向转换
- Markdown：支持表格、代码块等结构化数据

## 快速开始

### 环境要求

- Java 11 或更高版本
- Maven 3.6 或更高版本

### 安装

将以下依赖添加到你的Maven项目中：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-kernel</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### 基本使用

#### 1. 创建自定义DSL加载器

```xml
<!-- /nop/core/registry/orm.register-model.xml -->
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef" name="orm">
  <loaders>
    <xdsl-loader fileType="orm.xml" schemaPath="/nop/schema/orm/orm.xdef"/>
    <xdsl-loader fileType="orm.json" schemaPath="/nop/schema/orm/orm.xdef"/>
    <loader fileType="orm.md" mappingName="orm.Md_to_OrmModel" class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  </loaders>
</model>
```

#### 2. 定义模型映射规则

```xml
<!-- record-mappings.xml -->
<definitions xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/record/record-mappings.xdef" xmlns:md="md">
  <mapping name="Md_to_OrmModel" md:titleField="displayName">
    <fields>
      <field name="displayName" from="displayName"/>
      <field name="entities" from="实体定义" keyProp="name" itemMapping="Md_to_EntityModel"/>
    </fields>
  </mapping>
  
  <mapping name="Md_to_EntityModel" md:titleField="name">
    <fields>
      <field name="name" from="对象名"/>
      <field name="tableName" from="表名" mandatory="true"/>
      <field name="columns" from="字段列表" keyProp="name" itemMapping="Md_to_ColumnModel" md:format="table"/>
    </fields>
  </mapping>
</definitions>
```

#### 3. 使用命令行工具

```bash
# 转换模型格式
java -jar nop-kernel-cli.jar convert demo.orm.xml -o=demo.orm.md

# 生成代码
java -jar nop-kernel-cli.jar gen demo.orm.md -t=/nop/templates/orm -o=target
```

#### 4. Markdown格式的ORM模型示例

以下是在Markdown中定义ORM模型的精简示例

```markdown
# AI项目管理系统

- registerShortName: true
- entityPackageName: com.example.entity

## 5 实体定义

### 5.1 NopAiProject

- 表名: nop_ai_project
- 是否视图: false
- 类名: NopAiProject
- 中文名: AI项目
- 备注: 存储AI项目基本信息

#### 5.1.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|类型|长度|字典|备注|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | seq | true | true | id | id | X | 主键 | VARCHAR | 36 |  | 自动生成UUID |
| 2 |  | false | true | language | language |  | 项目语言 | VARCHAR | 4 | ai/project_language | 项目使用的编程语言类型：JAVA, PYTHON等 |
| 3 |  | false | true | name | name |  | 项目名称 | VARCHAR | 100 |  | 项目的显示名称 |
| 4 |  | false | false | project_dir | projectDir |  | 项目目录 | VARCHAR | 400 |  | 项目在文件系统中的存储路径 |

#### 5.1.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|
| --- | --- | --- | --- | --- | --- | --- |
|  | to-many | projectRules | NopAiProjectRule | project | id=projectId, | 项目规则 |
|  | to-many | configs | NopAiProjectConfig | project | id=projectId, | 配置项 |

### 5.2 NopAiProjectRule

- 表名: nop_ai_project_rule
- 是否视图: false
- 类名: NopAiProjectRule
- 中文名: 项目规则
- 备注: 存储项目规则配置

#### 5.2.1 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|类型|长度|备注|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | seq | true | true | id | id | X | 主键 | VARCHAR | 36 | 自动生成UUID |
| 2 |  | false | true | project_id | projectId |  | 项目ID | VARCHAR | 36 | 关联到AI项目表 |
| 3 |  | false | true | rule_type | ruleType |  | 规则类型 | VARCHAR | 20 | 规则的分类标识 |

#### 5.2.2 关联列表

|标签|关联类型|属性名|关联对象|关联属性名|关联条件|中文名|
| --- | --- | --- | --- | --- | --- | --- |
|  | to-one | project | NopAiProject | projectRules | projectId=id, | 所属项目 |
```

nop-kernel-cli/demo目录下演示了如何增加自定义的代码生成模板。

## 架构设计

nop-kernel 采用分层架构设计：

1. **基础层**：包含nop-commons、nop-core等核心组件，提供基本功能支持
2. **语言层**：包含nop-xlang、nop-antlr4等，实现DSL解析和处理
3. **元模型层**：包含nop-xdefs、nop-record-mapping等，定义模型结构和映射规则
4. **工具层**：包含nop-codegen、nop-kernel-cli等，提供代码生成和命令行工具

## 构建项目

```bash
# 克隆仓库
git clone https://github.com/entropy-cloud/nop-kernel.git
cd nop-kernel

# 构建项目
mvn clean install -DskipTests
```


## 许可证

中小企业可以在Apache2.0协议下免费商用，详细条款请参考nop-entropy项目。

## 联系方式

- 项目主页：https://github.com/entropy-cloud/nop-entropy
- 问题反馈：https://gitee.com/entropy-cloud/nop-entropy/issues
- 更多信息：请参考 [Nop Platform 主项目](https://github.com/entropy-cloud/nop-entropy)

