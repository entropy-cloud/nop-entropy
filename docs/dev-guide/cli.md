# NopCli命令行工具

## 逆向工程
可以通过JDBC连接数据库，获取数据库中的元数据生成Excel格式的数据模型定义。

```shell
java -Dfile.encoding=UTF8 -jar nop-cli.jar reverse-db litemall -c=com.mysql.cj.jdbc.Driver --username=litemall --password=litemall123456 --jdbcUrl="jdbc:mysql://127.0.0.1:3306/litemall?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC"
```

nop-cli的reverse-db命令需要传入参数【数据库模式名】，例如litemall，然后通过jdbcUrl等选项传入JDBC连接字符串等信息。


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

````shell

java -Xbootclasspath/a:bsin-codegen-template/src/main/resources/ -jar nop-cli-2.0.0-BETA.1.jar  gen bsin-demo/model/bsin-demo.orm.xlsx -t=/bsin/templates/orm -o=bsin-demo
````

通过-Xbootclasspath/a:bsin-codegen-template/src/main/resources/引入外部的jar包或者目录到classpath中，然后通过-t=/bsin/templates/orm来引用classpath下的模板文件，就可以生成代码


## 动态监听文件目录，发现修改后执行代码生成
使用NopCli工具可以监听指定目录，当目录下的文件发生变动时自动执行脚本代码。

````shell
java -jar nop-cli.jar watch app-meta -e=scripts/gen-web.xrun
````

以上配置表示监控 app-meta目录，当其中的文件发生变化时执行gen-web.xrun脚本文件

在这个脚本文件中，我们可以通过GenWithDependsCache等Xpl模板标签来动态生成代码

````
````