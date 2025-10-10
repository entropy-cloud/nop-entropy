# NopCli命令行工具

## 逆向工程

可以通过JDBC连接数据库，获取数据库中的元数据生成Excel格式的数据模型定义。

```shell
java -jar nop-cli.jar reverse-db litemall -c=com.mysql.cj.jdbc.Driver --username=litemall --password=litemall123456 --jdbcUrl="jdbc:mysql://127.0.0.1:3306/litemall?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC"
```

nop-cli的reverse-db命令需要传入参数【数据库模式名】，例如litemall，然后通过jdbcUrl等选项传入JDBC连接字符串等信息。

```
Usage: nop-cli reverse-db [-dhV] -c=<driverClassName> -j=<jdbcUrl>
                          [-o=<outputFile>] [-p=<password>] [-t=<table>]
                          -u=<username> <catalog>
对数据库进行逆向工程分析，生成Excel模型文件
      <catalog>             数据库模式名
  -c, --driverClass=<driverClassName>
                            JDBC驱动类
  -d, --dump                输出文件（缺省输出到命令行窗口中）
  -h, --help                Show this help message and exit.
  -j, --jdbcUrl=<jdbcUrl>   jdbc连接
  -o, --output=<outputFile> 输出文件（缺省输出到命令行窗口中）
  -p, --password=<password> 数据库密码
  -t, --table=<table>       数据库表模式，例如litemal%表示匹配litemall为前缀的表
  -u, --username=<username> 数据库用户名
  -V, --version             Print version information and exit.
```

## 代码生成

如果已经获得Excel数据模型，则可以使用nop-cli命令行工具的gen命令来生成初始工程代码

```shell
java -jar nop-cli.jar gen -t=/nop/templates/orm model/app-mall.orm.xlsx
```

具体生成的内容如下:

```
├─app-mall-api       对外暴露的接口定义和消息定义
├─app-mall-codegen   代码生成辅助工程，根据ORM模型更新当前工程代码
├─app-mall-dao       数据库实体定义和ORM模型
├─app-mall-service   GraphQL服务实现
├─app-mall-web       AMIS页面文件以及View模型定义
├─app-mall-app       测试使用的打包工程
├─deploy             根据Excel模型生成的数据库建表语句
```

### 只生成dao模块

如果只想使用NopORM，不需要生成前端代码，也不需要生成GraphQL服务，则可以使用orm-dao模板

```shell
java -jar nop-cli.jar gen -t=/nop/templates/orm-dao -o=app-dao model/app-mall.orm.xlsx
```

### 使用自己定制的生成模板

除了使用Nop平台内置的代码生成模板，我们还可以建立自己的模板工程。具体过程参见项目[bsin-codegen](https://gitee.com/canonical-entropy/bsin-codegen).
B站视频: [可逆计算原理和Nop平台介绍及答疑](https://www.bilibili.com/video/BV1u84y1w7kX/), 从44分钟20秒开始

```shell

java -Xbootclasspath/a:bsin-codegen-template/src/main/resources/ -jar nop-cli-2.0.0-BETA.1.jar  gen bsin-demo/model/bsin-demo.orm.xlsx -t=/bsin/templates/orm -o=bsin-demo
```

通过-Xbootclasspath/a:
bsin-codegen-template/src/main/resources/引入外部的jar包或者目录到classpath中，然后通过-t=/bsin/templates/orm来引用classpath下的模板文件，就可以生成代码

## 动态监听文件目录，发现修改后执行代码生成

使用NopCli工具可以监听指定目录，当目录下的文件发生变动时自动执行脚本代码。

```shell
java -jar nop-cli.jar watch app-meta -e=taks/gen-web.xrun
```

以上配置表示监控 app-meta目录，当其中的文件发生变化时执行gen-web.xrun脚本文件

在这个脚本文件中，我们可以通过GenWithDependsCache等Xpl模板标签来动态生成代码

```xml

<c:unit xmlns:c="c" xmlns:run="run" xmlns:xpl="xpl">
  <run:GenWithCache xpl:lib="/nop/codegen/xlib/run.xlib"
                    srcDir="/meta/test" appName="Test"
                    targetDir="./target/gen"
                    tplDir="/nop/test/meta-web"/>
</c:unit>
```

GenWithCache标签会设置srcDir,appName属性，然后执行tplDir指定的代码生成模板，生成文件的存放路径由targetDir指定。

代码生成的过程中启用了依赖追踪，第一次生成之后再此触发gen-web.xrun运行代码生成任务时会自动检查输出文件所对应的依赖模型文件，只有当依赖文件发生变化时才会重新生成，否则会自动跳过。

## 解析Excel模型导出为JSON或者XML

```
java -jar nop-cli.jar extract test.orm.xlsx -o=my.orm.json
```

extract指令会识别文件的后缀名，选择注册到系统中的解析器进行解析，得到Json对象后再导出为JSON文件。如果存在对应的xdef元模型定义，也可以选择导出为XML格式

通过`-o`导出文件参数可以指定导出为xml或者json，比如 `-o=my.orm.xml`根据文件后缀名可以确定需要导出为xml格式。

## 根据JSON生成Excel文件

```
java -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

`gen-file`会根据`-t`参数指定的模板文件来导出Excel。模板文件可以如果是imp.xml，则使用导入模型关联的导出模板来导出。也可以是xpt.xlsx这种报表模板，
此时将按照报表模型实现导出。JSON文件解析得到的对象在报表导出时对应于名为entity的对象。

## 执行报表文件并导出

通过`gen-file`指令，给定`-t`为报表模板文件

```
java -jar nop-cli.jar gen-file data.json -t=/my/test.xpt.xlsx -o=target/test.xlsx
```

`gen-file`的第一个参数`data.json`会被解析为一个Map，然后传入报表模板作为参数`entity`。通过`-o`参数可以指定导出文件位置和导出类型

## 执行逻辑编排任务

```
java -jar nop-cli.jar run-task my.task.xml -if=inputs.json
```

读取`inputs.json`文件作为task的输入参数，运行`my.task.xml`逻辑编排模型。

## 重打包操作

```
java -jar nop-cli.jar repackage -i=app -o=my-tool.jar
```

repackage指令将根据输入目录下的`_vfs`目录以及`application.yaml`、`bootstrap.yaml`文件，把它们打包到当前`nop-cli.jar`
包中，输出一个新的可执行的jar包。

## 数据库导入导出

```
java -jar nop-cl.jar export-db test.export-db.xml -o=data
```

根据`export-db.xml`中的配置，将数据库中的数据导出到data目录下，可以选择导出为csv或者sql格式。导出时可以执行字段重命名，值的变换等操作。可以选择只导出部分字段。

```
java -jar nop-cl.jar import-db test.import-db.xml -i=data
```

从data目录下导入数据到指定数据库中，导入时可以按照keyFields去重，并可以选择是否允许更新，还是只允许插入。导入时可以进行字段重命名和值的变换。可以选择只导入部分字段。

可以通过指定`-s`参数来保存导入状态。

```
java -jar nop-cl.jar import-db test.import-db.xml -i=data -s=import-status.json
```

## convert格式转换

在 DSL 模型的 XML/JSON/YAML/XLSX 格式间进行转换

### 命令格式

```bash
java -jar nop-cli.jar convert <inputFile>
    [-o|--output <outputFile>]
```

### 参数说明

| 参数             | 描述                                             |
|----------------|------------------------------------------------|
| `<inputFile>`  | 输入文件（必须指定明确的文件类型，比如app.orm.xml, app.orm.xlsx等） |
| `-o, --output` | 输出文件（波希指定明确的文件类型）                              |

具体实现是使用DocumentConvertManager。

1. 通过convert.xml注册文件注册各种格式之间的转换逻辑
2. register-model.xml中注册的DSL各种格式之间可以自动双向转换
3. 如果注册了 A->B和B->C，则可以自动支持A->C的转换（仅支持一级推导）

### 使用示例

 ```bash
 # XML → XLSX
 java -jar nop-cli.jar convert app.orm.xml  -o app.orm.xlsx

 # JSON → YAML
 java -jar nop-cli.jar convert app.orm.json -o app.orm.yaml
 ```

## 根据`page.yaml`文件生成页面json文件

```
java -jar nop-cli.jar run scripts/render-pages.xrun -i="{moduleId:'app/demo'}" -o=target
```

run指令可以用于执行xpl脚本文件，`render-pages.xrun`脚本中调用PageProvider来生成页面json文件，`-i`参数指定输入参数，`-o`
参数指定输出目录。

```xml
<!-- render-pages.xrun文件的内容-->
<c:script>
  import io.nop.web.page.PageProvider;
  import java.io.File;

  const pageProvider = new PageProvider();
  const options = {
  moduleId: moduleId,
  resolveI18n: true,
  useResolver: true,
  threadCount: 4
  };

  pageProvider.renderPagesTo(options, outputDir);
</c:script>
```

renderPagesTo函数会遍历`_vfs/{moduleId}/pages/*/*.page.yaml`文件，并执行模板渲染。在`page.yaml`中可以通过`<web:GenPage>`
等标签来引入View模型。

## 常见问题

1. 如何调整日志输出级别

```
java -Dquarkus.config.locations=application.yaml -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

缺省配置了`quarkus.log.level=INFO`，以及`quarkus.log.category."io.nop".level=ERROR`
，可以通过外部的application.yaml来覆盖缺省的quarkus配置。

2. 如何使用外部的application.yaml配置文件

通过`-Dnop.config.location=application.yaml`参数指定外部配置文件
