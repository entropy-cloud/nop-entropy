# 自定义DSL模型开发指南

Nop平台基于可逆计算理论，提供了一套完整的机制来简化自定义DSL（领域特定语言）模型的开发。本指南通过一个完整的示例，演示如何开发一个简单的DSL模型并注册它的加载器。

## 1. 项目结构概览

在开始之前，先了解完整的项目结构：

您说得对，`precompile` 和 `src` 应该是平级目录。以下是修正后的项目结构：

```
project-root/
├── src/
│   └── main/
│       ├── resources/
│       │   └── _vfs/
│       │       ├── schema/
│       │       │   └── simple.xdef              # DSL元模型定义
│       │       ├── nop/core/registry/
│       │       │   └── simple.register-model.xml # 模型注册配置
│       │       └── simple/
│       │           └── test.simple              # 自定义格式
│       │           └── test.simple.xml          # XML文件
│       └── java/
│           └── test/simple/                     # 生成的Java模型类
├── precompile/
│   └── gen-dsl.xgen                         # 代码生成配置
└── pom.xml                                  # Maven构建配置
```

## 2. 定义元模型 `simple.xdef`

在`_vfs`目录下创建 `/simple/simple.xdef` 元模型定义文件：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<simple name="!string"
        x:schema="/nop/schema/xdef.xdef"
        xmlns:x="/nop/schema/xdsl.xdef"
        xdef:bean-package="test.simple"
        xdef:name="SimpleModel">

  <description xdef:value="string"/>

  <columns xdef:body-type="list" xdef:key-attr="name">
    <column name="!string" displayName="string" type="!string" xdef:name="SimpleColumnName"/>
  </columns>

</simple>
```

**关键属性说明：**

- `x:schema="/nop/schema/xdef.xdef"`：指定此文件遵循xdef元模型规范
- `xdef:bean-package="test.simple"`：生成的Java类包名
- `xdef:name="SimpleModel"`：生成的Java类名
- `xdef:key-attr="name"`：指定name属性作为唯一标识
- `!string`：表示该属性必填且为字符串类型

**结构定义：**

- `description`：模型描述信息
- `columns`：列表示例属性

## 3. 注册模型加载器

在`_vfs`目录下创建 `/nop/core/registry/simple.register-model.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<model x:schema="/nop/schema/register-model.xdef"
       xmlns:x="/nop/schema/xdsl.xdef"
       name="simple">


  <loaders>
    <!-- 基于XDef的解析器，用于simple.xml文件 -->
    <xdsl-loader fileType="simple.xml" xdefPath="/schema/simple.xdef"/>

    <!-- 自定义解析器，用于simple文件 -->
    <loader fileType="simple"
            class="io.nop.xlang.xdsl.SimpleDslParser"
            returnXNode="true"/>
  </loaders>

</model>
```

**配置说明：**

- **name="simple"**：DSL模型名称，在系统中唯一标识
- **xdsl-loader**：基于xdef定义的通用解析器，处理XML格式
- **自定义loader**：通过Java类处理特定格式，`returnXNode="true"`表示返回XNode语法树

## 4. 实现自定义解析器

创建Java解析器类 `io.nop.xlang.xdsl.SimpleDslParser`：

```java
public class SimpleDslParser implements IResourceParser<XNode> {
  private static final Logger LOG = LoggerFactory.getLogger(SimpleDslParser.class);

  @Override
  public XNode parseResource(IResource resource) {
      ...
  }
}
```

## 5. 创建DSL示例文件

创建XML格式的示例 `/simple/test.simple.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<simple name="testApp" xmlns:x="/nop/schema/xdsl.xdef" x:schema="/schema/simple.xdef">

  <description>测试应用程序配置</description>

  <columns>
    <column name="id" displayName="ID" type="Long"/>
    <column name="fieldA" displayName="Field A" type="String"/>

  </columns>


</simple>
```

## 6. 代码生成配置

在 `precompile/gen-dsl.xgen` 中配置代码生成：

```xml

<c:script>
  codeGenerator.renderModel('/schema/simple.xdef','/nop/templates/xdsl', '/',$scope);
</c:script>
```

根据`simple.xdef`元模型生成模型对象，使用`_vfs`目录下的`/nop/templates/xdsl`目录下的代码生成模板。

## 7. Maven构建配置

在 `pom.xml` 中配置代码生成：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <parent>
    <artifactId>nop-entropy</artifactId>
    <groupId>io.github.entropy-cloud</groupId>
    <version>2.0.0-SNAPSHOT</version>
  </parent>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>codegen</id>
            <phase>generate-sources</phase>
            <goals>
              <goal>java</goal>
            </goals>
            <configuration>
              <mainClass>io.nop.codegen.CodeGenTask</mainClass>
              <arguments>
                <argument>precompile</argument>
              </arguments>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <dependencies>
    <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang</artifactId>
    </dependency>
    <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-core</artifactId>
    </dependency>
  </dependencies>

</project>
```

## 8. 使用DSL模型

### Java代码中加载DSL：

```java
// 方式1：使用统一资源管理器加载
SimpleModel model = (SimpleModel) ResourceComponentManager.instance()
    .loadComponentModel("/simple/test.simple");

// 方式2：直接解析XML格式
SimpleModel model2 = (SimpleModel) ResourceComponentManager.instance()
  .loadComponentModel("/simple/test.simple.xml");

// 使用模型数据
System.out.println("应用名称: "+model.getName());

```

调试模式下Nop平台解析DSL文件得到XNode后（此过程会执行Delta合并），会将最终结果输出到`_dump`目录下。

### 手动触发代码生成：

```java
public class CodeGenerator {
  public static void main(String[] args) {
    File projectDir = new File(".");
    XCodeGenerator.runPrecompile(projectDir, "/", false);
    System.out.println("代码生成完成！");
  }
}
```

## 9. 核心概念总结

### 可逆计算理论应用：

1. **差量（∆）定制**：通过自定义解析器扩展基础解析能力
2. **模型驱动**：从xdef元模型自动生成Java代码，保证类型安全
3. **统一抽象**：不同格式的文件（.simple和.simple.xml）解析为同一模型

### 文件类型识别规则：

- `app.simple.xml` → `simple.xml`
- `config.simple` → `simple`
- `User.xmeta` → `xmeta`

### 解析流程：

```
test.simple
    → SimpleDslParser (自定义解析逻辑)
    → 返回XNode语法树
    → DslModelParser (根据xdef验证和转换)
    → SimpleModel Java对象
```

### 弱类型对象
如果不想生成强类型的模型对象，可以不配置代码生成器，`simple.xdef`上不要指定`xdef:bean-package`。这种情况下解析结果是DynamicObject对象，类似于JSON对象。
