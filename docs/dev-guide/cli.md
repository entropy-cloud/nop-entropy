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

## 常见问题

1. 如何调整日志输出级别

```
java -Dquarkus.config.locations=application.yaml -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

缺省配置了`quarkus.log.level=INFO`，以及`quarkus.log.category."io.nop".level=ERROR`，可以通过外部的application.yaml来覆盖缺省的quarkus配置。

2. 如何使用外部的application.yaml配置文件

通过`-Dnop.config.location=application.yaml`参数指定外部配置文件
