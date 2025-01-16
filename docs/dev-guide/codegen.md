# Maven集成代码生成器

Nop平台提供了与Maven相集成的代码生成能力，但是并没有做成maven插件，而是利用`exec-maven-plugin`插件来执行`CodeGenTask`类的`main`函数来实现。

只需要在pom文件中增加以下配置，在执行`maven package`的时候，就会自动执行工程的precompile和postcompile目录下的xgen代码，其中precompile在compile阶段之前执行，执行环境可以访问所有依赖库，但是不能访问当前工程的类目录，而postcompile在compile阶段之后执行，可以访问已编译的类和资源文件。

```xml
<pom>
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
            </plugin>
        </plugins>
    </build>
</pom>
```

如果不使用pom的parent继承机制，则需要为`exec-maven-plugin`插件提供更多的参数配置，具体可以参见[nop-entropy/pom.xml](../../pom.xml)中的配置

> 例如`nop-auth-service`模块中precompile阶段根据`nop-auth-dao`模块中的orm模型生成meta模型，而postcompile阶段根据当前工程中的meta模型生成i18n配置文件。

## 在Maven之外调用代码生成器

`CodeGenTask`是一个普通的java类，可以在Maven外直接调用。例如

```java
public class NopOrmCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopOrmCodeGen.class);
            String targetRootPath = FileHelper.getFileUrl(new File(projectDir, "src/main/java"));
            XCodeGenerator generator = new XCodeGenerator("/nop/templates/orm-entity", targetRootPath);
            IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
            OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(resource);
            generator.execute("", Collections.singletonMap("ormModel", ormModel), XLang.newEvalScope());
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

Nop平台提供了XLang语言的Idea调试插件，可以在xgen文件中增加断点进行调试。参见[idea-plugin.md](../user-guide/idea/idea-plugin.md)

根据Excel数据模型生成的代码工程中， `xxx-codegen`模块以及`xxx-web`模块中都包含了一个`CodeGen.java`类，例如`NopAuthCodeGen`和`NopAuthWebCodeGen`，
使用它们可以在IDEA中直接执行代码生成逻辑，而不用通过Maven工具来执行。Maven工具执行时总是先执行Java编译过程，影响性能。

## Analyze模式

代码生成的时候一般不需要启动IoC容器，因此可以如下配置控制Nop平台的初始化级别。

```
  AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
```

设置最大初始化级别这个配置变量为ANALYZE之后，再调用CoreInitialization就不会调用IoC容器的start方法。

analyze是Nop平台提供的最强的静态分析模式。在这个模式下，Nop平台会解析所有配置文件，但是并不会创建任何的bean，不会真的进入运行状态。

### 通过nop-cli命令行工具执行代码生成

`nop-cli`工具的`gen`指令封装了`CodeGenTask`工具类的功能，将它包装为一个命令行调用。

```shell
nop-cli gen model/app-mall.orm.xlsx -t=/nop/templates/orm
```

上面的例子表示读取`app-mall.orm.xlsx`模型，应用虚拟文件系统中的`/nop/templates/orm`目录下的模板文件，生成代码到当前工程目录下。

## 在Nop平台之外增加代码生成模板

1. 在自己的工程（例如`myapp-templates`）中增加模板目录，必须要放到`src/resources/_vfs`目录下，例如`src/resources/_vfs/xxx/yyy`，然后在其中增加xgen文件。
2. 代码生成器执行时引入`myapp-templates.jar`的依赖，这样虚拟文件系统初始化时会自动扫描所有jar包中的`_vfs`目录，并把它们合成为一个统一的虚拟文件系统。
   此时就可以在代码生成时指定`-t=/xxx/yyy`来生成代码。

```
java -Xbootclasspath/a:myapp-templates.jar -jar nop-cli-2.0.0-BETA.1.jar  gen model/demo.orm.xlsx -t=/xxx/yyy
```

如果是使用maven集成的代码生成工具，可以在test scope中引入自己新建的模板工程

```xml

<pom>
    <dependencies>
        <dependency>
            <groupId>me.app</groupId>
            <artifactId>myapp-template</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</pom>
```

## 数据驱动的代码生成器

`CodeGenTask`实际调用的是`nop-codegen`模块中的数据驱动的代码生成器`XCodeGenerator`。所谓的数据驱动，指的是**生成过程的所有逻辑控制由输入的模板文件来指定**，即由外部提供的模板数据来驱动代码生成的过程。

### 一. 1.2 通过模板路径编码判断和循环

`XCodeGenerator`的做法与传统的代码生成器不同，它**将模板路径看作是一种微格式的DSL，把判断和循环逻辑编码在路径格式中**，从而由模板自身的组织结构来控制代码生成过程。具体规则如下：

1. 所有以xgen为后缀的文件作为模板文件，而没有xgen后缀的文件为静态文件

```
xxx.java.xgen --> xxx.java  生成到去除xgen后缀的文件中
xxx.xrun      --> ignore    如果是xrun后缀，则直接作为xpl模板代码运行
xxx.java      --> xxx.java  没有xgen后缀的直接拷贝
```

2. 所有以`@`为前缀的文件为内部使用，不作为模板解析，也不拷贝到目标目录。其中`@init.xrun`文件为初始化文件，当运行该目录下的模板之前需要先执行`@init.xrun`完成初始化。例如在`@init.xrun`中可以定义哪些变量是循环变量，同时规定这些循环变量之间的关系

```xml
<gen:DefineLoop xpl:lib="/nop/codegen/xlib/gen.xlib" xpl:slotScope="builder">
<c:script>
builder.defineGlobalVar("ormModel",ormModel);
builder.defineLoopVar("entityModel","ormModel", model => model.entityModelsInTopoOrder);
</c:script>
</gen:DefineLoop>
```

3. 目录和文件名中通过`{a.b.c}`形式的变量表达式来指定循环变量，从而以一种自然的方式表达多重嵌套循环，例如

```javascript
/nop/base/generator/test/{globalVar}/{var1}/sub/{var2.packagePath}/{var3}.java.xgen

// 相当于三重循环
if(globalVar){
    for(let var1 of ...){
     for(let var2 of ...){
        for(let var3 of ...){
            if(var1 && var2 && var2.packagePath && var3){
                let path = '/nop/base/generator/test/'
                +globalVar+'/'+var1+'/sub/'+var2.packagePath
                +'/'+var3+'.java';
                ...
            }
        }
     }
    }
}
```

- `{var2.packagePath}`表示按照循环变量`var2`进行循环，每次循环时取`var2.packagePath`属性

- 嵌套循环引用父循环变量的值为固定值。例如对于`{var1}/{var2}/{var1}_{var2}.java.xgen`, 在`{var2}`的子目录中再引用`var1`和`var2`时，它们都是固定值。

4. 目录和文件名中通过`{a.b.c}`形式的变量表达式来指定开关变量。当变量值返回`false`或者`null`的时候表示跳过该目录或者文件，而返回`true`的时候则自动忽略该内容。例如

```
控制是否生成某个目录下的文件
/src/{package.name}/{webEnabled}/{model.name}Controller.java.xgen
                                /{model.name}Service.java.xgen

也可以控制单个文件是否生成
/src/{package.name}/{webEnabled}{model.name}Controller.java.xgen
```

### 具体示例
```xml
<gen:DefineLoop xpl:lib="/nop/codegen/xlib/gen.xlib" xpl:slotScope="builder">
<c:script>
builder.defineLoopVar("task","tasks",model=>model);
builder.defineLoopVar("response","task", model => model.taskResponse?.responseList);
</c:script>
</gen:DefineLoop>
```

这样就定义了两个上下文变量，task和response，然后在路径中就可以使用`{response.responseCode}`这种方式来引用变量属性。

`Task{task.taskCode}{response.responseCode}ResponseTrigger.java`这种路径会自动识别出task和response之间的循环依赖关系，自动执行循环展开。

### 表达式特殊约定

1. `{!!entity.field}` 这样的表达式中!表示取反，`!!`表示连续取反，因此对于空值它会返回false。
   表达式如果返回false，则会跳过此次处理，但是对于空值，则该表达式的值将会被忽略。因此空值与false并不等价。

2. `{data.@mapper}` 属性表达式存在一种特殊的约定。对于HashSet或者LinkedHashSet类型，`@mapper`属性会判断集合中是否存在该文本值。
   相当于 `((Set)data).contains('mapper')`

### xgen模板文件
xgen本质上就是xpl模板语言，它动态执行输出内容。xpl模板语言中可以通过`<c:for>`，`<c:script>`等标签执行逻辑，并通过`<c:import>`来导入标签库。
xpl还提供了`<c:print>`这种标签用于原样输出它的body内容，即使其中包含`c:script`等标签。

例如
```xml
<c:unit>
  <c:script>
    let n = 100; // 设置一个变量，可以执行复杂的XScript代码
  </c:script>

  <!-- 通过表达式可以使用当前环境中的变量。xpl模板语言提供了c:for等标签用于实现循环逻辑 -->
  <c:for var="i" begin="${1}" end="${n}">
    <div/>
  </c:for>
</c:unit>
```
上面的代码会输出100个`<div>`节点。

`c:script`就是编译成一个Expression，然后`expr.invoke(scope)`， 在当前的scope中执行。scope中的数据就是 `@init.xrun`中准备的。在`@init.xurn`中`assign("x",1")`可以向scope中设置变量。另外`builder.defineGlobalVar('basePackagePath', pkgPath)`等也会设置变量。

xpl模板语言的介绍参见[xpl.md](xlang/xpl.md)


## 二. 差量化的代码生成器

如果我们不把代码生成器看作是某种一次性的、临时使用的外围工具，而是把它作为元编程的一个有机组成部分，则代码生成器必然是支持增量生成的。所谓增量生成，是指代码生成器允许反复执行，且同时允许手工修改输出产物，自动生成和手工修改的部分都可以看作是对初次生成结果的增量化修改，并且它们会自动合并在一起。

```
Result = FirstGeneration + AutoGenDelta + ManualDelta
```

可逆计算理论明确提出了可逆的差量合并这一概念，指出全量是差量的一种特例，为一系列差量相关的程序实践提供了统一的理论解释，并指明了未来的发展方向。

> `全量= 单位元 + 差量`，例如`1 = 0 + 1`，`1`既是`0+1`合并得到的全量结果，又是`0`和`1`之间的差量

依据可逆计算理论的思想，`XCodeGenerator`将手工修改部分看作是对自动生成部分的定制化差量，综合利用各种技术手段来实现它们之间的差量合并。

### 2.1. 利用面向对象语言的类继承机制

对于常见的面向对象语言，我们可以利用类继承机制来实现手工修改代码与自动生成代码的隔离。`XCodeGenerator`规定了如下覆盖规则：

1. 以`_`为前缀的文件总是被覆盖
2. `_gen`目录下的文件总是被覆盖
3. 如果文件的前250个字符中包含了`__XGEN_FORCE_OVERRIDE__`这个字符串，则该文件自动被覆盖

实际使用的一般是所谓的**三明治架构**: 定制类继承自动生成的类，而自动生成的类继承平台所提供的某种基础类，这种结构下自动生成的代码可以从平台基类中获取辅助函数和通用变量环境，而定制类可以使用自动生成的变量与函数，并在必要的时候定制自动生成的函数。

```
CustomClass extends _AutoGenClass extends BaseClass
```

```java
public class SqlSubqueryTableSource extends _SqlSubqueryTableSource {
    public boolean isGeneratedAlias() {
        return alias != null && alias.isGenerated();
    }

    public ISqlTableMeta getResolvedTableMeta() {
        return getQuery().getResolvedTableMeta();
    }

    @Override
    public void normalize(){

    }
}
class _SqlSubqueryTableSource extends SqlTableSource{...}
class SqlTableSource extends _SqlTableSource {...}
class _SqlTableSource extends EqlASTNode {...}
```

### 2.2. 针对通用Tree结构的x:extends算子

对于一般性的XML和JSON格式，因为它们都对应于通用的Tree结构，可以使用可逆计算理论中所定义的通用的`x:extends`算子来实现差量化合并。比如百度AMIS框架是一个json格式的前台低代码框架，对它进行差量化改造，可以采用如下形式

```json
{
  "x:extends":"_page_crud.json5",
  "body":{
    "columns":[
      {
        "x:id":"operation",
        "buttons":[
          {
            "x:id":"row-update-button",
            "visibleOn": "chgSts == '1'"
          },
          {
            "x:id":"row-delete-button",
            "visibleOn": "chgSts == '1'"
          }
        ]
      }
    ]
  }
}
```

上面的示例演示了如何为自动生成的标准增删改查列表中的行按钮增加显示状态控制。

### 2.3. 通用的Delta定制

整个Nop平台都是基于可逆计算理论来实现的，因此它在各个层面都支持差量定制。特别是`XCodeGenerator`本身可以通过Nop平台内置的delta customization机制来实现定制。

1. 所有的模板文件都支持delta定制，即如果存在`/_delta/xxx/yyy.xgen`文件，则它将自动取代内置的`/xxx/yyy.xgen`文件

2. 模板文件使用xpl模板语言来实现，因此可以通过xpl标签库来实现函数级别的定制。即通过在`/_delta`目录下增加定制标签库，可以修改系统中被使用的标签定义。

```xml
<lib x:extends="super">
    <!-- 通过x:extends表示继承此前的标签库实现，在本文件中可以
    对继承的标签定义进行增加/修改/删除操作 -->
    <tags>
        <CustomTag>
            <source>
                这里的实现将取代标签库缺省的实现。所有对CustomTag标签的调用都会使用这里的实现
            </source>
        </CustomTag>
    </tags>
</lib>
```

## 常见问题

### 1. precompile和postcompile有什么区别

* precompile是在编译前执行，不能访问当前工程中的类和资源文件。
* precompile2是在编译前执行，但是可以访问当前工程中的资源文件。
* postcompile是在编译后执行，会检查IoC容器中bean的配置，并可以访问当前工程中的类和资源文件。

### 2. 如何定制已有的模板，比如说ORM生成时只生成某些工程

* 增加一个模板目录
* 在其中增加一个`a-impl.xrun`文件。`xrun`表示只执行代码不生成文件。`a-impl`的名称可以随意取，可以通过文件名控制它的执行顺序，所有模板文件按照文本顺序排序。
* 在xrun文件中通过`gen:Render`标签来引用已有的模板

```xml
<c:unit>
    <gen:Render template="/nop/templates/orm/{appName}-dao" targetDir="${targetResource.path.$filePath()}"
                xpl:lib="/nop/codegen/xlib/gen.xlib" inheritCodeGenLoop="true"/>
</c:unit>
```

### 在xgen模版生成的时候如何对${}进行转义
```
${'$'}{varName}
```
