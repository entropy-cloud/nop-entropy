# Nop平台问题诊断和调试指南

## 1. 概述

Nop平台是一个基于可逆计算原理构建的通用领域语言工作台，它包含了大量创新设计和复杂的内部机制。在开发和使用过程中，遇到问题是不可避免的。本指南旨在帮助开发者和AI大模型理解Nop平台的调试机制、错误定位方法以及常见问题的解决方案，从而提高开发效率和问题解决能力。

## 2. Debug模式配置

### 2.1 启用Debug模式

**设置`nop.debug=true`会开启debug模式**，此时才会注册DevDoc等用于调试的后台服务，才会输出`_dump`文件。

### 2.2 环境配置文件

缺省配置文件是`application.yaml`，如果要启用`application-dev.yaml`这样的特殊环境配置文件，必须在bootstrap.yaml或者application.yaml中配置对应的`nop.profile`。

## 3. 错误定位

### 3.1 NopException异常类

后台抛出异常时一般会统一使用NopException异常类，它具有SourceLocation属性，会提示错误发生时对应的XLang程序源码位置。NopException类上还包含了XLang执行堆栈，打印异常消息时会输出Xpl堆栈信息。

**示例异常信息**：
```
io.nop.api.core.exceptions.NopException: NopException[seq=4,status=-1,errorCode=nop.err.xui.ref-view-not-exists,params={viewPath=/app/mall/LitemallGoods/attributes.page.yaml},desc=view配置不存在：/app/mall/LitemallGoods/attributes.page.yaml]@_loc=[114:22:0:0]/app/mall/pages/LitemallGoods/LitemallGoods.view.xml
  @@getFormSelection(formModel,objMeta)@[7:30:0:0]/nop/web/xlib/web/page_simple.xpl
  @@</_delta/default/nop/web/xlib/web.xlib#GenPage>("/app/mall/pages/LitemallGoods/LitemallGoods.view.xml","add",null)@[1:17:0:0]/app/mall/pages/LitemallGoods/add.page.yaml
  @@__fn_1()@[1:17:0:0]/app/mall/pages/LitemallGoods/add.page.yaml
```

**异常信息解读**：
1. 错误发生在`/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`文件的第114行
2. 调用栈显示：`add.page.yaml` → `web.xlib#GenPage` → `page_simple.xpl` → `getFormSelection`函数
3. 错误原因：引用的view配置`/app/mall/LitemallGoods/attributes.page.yaml`不存在

## 4. 日志系统

### 4.1 日志函数使用

#### 4.1.1 XScript中的日志函数

XScript脚本中内置了`logInfo`/`logDebug`/`logWarn`/`logError`等函数，用于输出不同级别的日志：

```javascript
logInfo("nop.err.invalid-name:name={}", name);
logDebug("debug message: value={}", value);
logWarn("warning message: condition={}", condition);
logError("error message: exception={}", exception);
```

**注意事项**：
- 第一个参数必须是静态字符串，使用slf4j日志消息模板语法，通过`{}`表示变量占位
- 日志函数的具体实现参见 `LogFunctions.java`和`LogHelper.java`
- 日志级别从低到高依次为：TRACE < DEBUG < INFO < WARN < ERROR < FATAL

#### 4.1.2 Java代码中的日志使用

在Java代码中，可以使用SLF4J日志框架来输出日志：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyClass {
    private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);
    
    public void myMethod() {
        LOG.info("info message: param={}", param);
        LOG.debug("debug message: value={}", value);
        // 可以通过条件判断来避免不必要的日志计算
        if (LOG.isDebugEnabled()) {
            LOG.debug("expensive debug message: {}", expensiveOperation());
        }
    }
}
```

#### 4.1.3 调试函数$()

任意对象调用$函数都会导致打印调试语句，这是一种快速调试表达式值的方式：

```javascript
b = a.f().$("test")
```

**调试输出示例**：
```
21:00:01.686 [main] INFO io.nop.xlang.utils.DebugHelper - test:a.f()=>1,loc=[6:8:0:0]file:/C:/can/nop/nop-entropy-bak/nop-xlang/target/test-classes/io/nop/xlang/expr/exprs/debug.test.md
```

**解读**：
- `test`：自定义前缀信息
- `a.f()`：待查看的表达式源码
- `=> 1`：表达式的返回值为1
- `loc=[6:8:0:0]`：调试语句所对应的源码位置

#### 4.1.4 XPL模板中的日志

在XPL模板中，可以使用`<c:log>`标签输出日志：

```xml
<c:log info="${data}" />
<c:log debug="Processing item: ${item.id}" />
```

在XPL标签上标注`xpl:dump="true"`会打印出解析得到的AST抽象语法树：

```xml
<my:MyTag xpl:dump="true" />
```

### 4.2 日志级别配置

#### 4.2.1 全局日志级别配置

通过配置文件设置全局日志级别：

```yaml
quarkus:
  log:
    category:
      "io.nop":
        level: DEBUG
```

#### 4.2.2 特定模块日志级别配置

可以为特定模块设置不同的日志级别，实现更精细的日志控制：

```yaml
quarkus:
  log:
    category:
      "io.nop":
        level: INFO
      "io.nop.dao":
        level: DEBUG  # 数据访问层使用DEBUG级别
      "io.nop.ioc":
        level: TRACE  # IoC容器使用TRACE级别，查看更详细的Bean装配信息
```

#### 4.2.3 运行时调整日志级别

在开发环境中，可以通过JMX或其他监控工具在运行时调整日志级别，无需重启应用。

## 5. 调试技巧

### 5.1 查看动态装配的Bean

将`io.nop.ioc`的调试级别设置为debug，系统启动时会打印出条件Bean的执行情况：

```
disabled-bean:id=nopLoginService
    loc=[15:6:0:0]/nop/auth/beans/sso-defaults.beans.xml,trace=null
    check-if-property-fail:nop.auth.sso.enabled=null
```

### 5.2 查看数据库访问

所有的数据库访问SQL都会记录在日志中，并且显示SQL执行时间：

```
2023-07-11 21:14:39,637 INFO  [io.nop.dao.jdb.imp.JdbcTemplateImpl] (Quarkus Main Thread) nop.jdbc.run:usedTime=1,querySpace=default,range=0,1,name=jdbc:null,sql=select o.SEQ_NAME as c1
from nop_sys_sequence as o
 where o.DEL_FLAG =  0
```

### 5.3 使用DevDoc调试接口

在debug模式下，可以通过以下链接查看系统内部信息：

DevDoc 在源码中对应 BizModel：`io.nop.biz.dev.DevDocBizModel`（`@BizModel("DevDoc")`）。

不同运行框架（Quarkus/Spring 等）和网关配置下，DevDoc 暴露为 HTTP/GraphQL 的具体 URL 可能不同；因此这里建议按**GraphQL 调用名**理解，而不是固定的 `/p/...`、`/r/...` 路径：

- `DevDoc__beans`：查看实际装载的 Bean
- `DevDoc__globalFunctions`：查看系统中定义的全局函数
- `DevDoc__globalVars`：查看系统中定义的全局变量
- `DevDoc__configVars`：查看当前启用的配置变量集合
- `DevDoc__graphql`：查看后台所有的 GraphQL 服务以及类型定义
### 5.4 GraphQL调试工具

若运行框架/依赖中启用了 GraphQL UI（例如某些 Quarkus 配置可能提供 `/q/graphql-ui`），可使用其进行调试；具体是否可用以及访问路径以当前启动模块的配置为准。

## 6. 模型Dump

### 6.1 _dump目录

Nop平台大量使用了元编程来动态生成代码，为了有效跟踪代码生成的细节过程，在调试模式下会自动输出合并后的结果模型文件到项目根目录下的`_dump`目录中。

**启用条件**：
- 必须设置`nop.debug=true`开启debug模式
- 系统启动时会自动创建`_dump`目录（如果不存在）

**目录位置**：
- 位于项目根目录下，例如：`/your-project/_dump`
- 与`src`、`target`等目录同级

### 6.2 Dump文件结构

_dump目录下的文件结构反映了Nop平台内部模型的层次结构：

```
_dump/
├── nop/                   # Nop平台核心模型
│   ├── main/              # 主应用模型
│   │   ├── beans/         # IoC容器Bean定义
│   │   │   └── merged-app.beans.xml  # 合并后的Bean配置
│   │   └── config/        # 配置信息
│   └── schema/            # 元模型定义
└── app/                   # 应用自定义模型
    └── your-app/          # 应用名称
        ├── orm/           # ORM模型
        └── xui/           # 前端UI模型
```

### 6.3 关键Dump文件说明

1. **merged-app.beans.xml**
   - 位置：`_dump/nop/main/beans/merged-app.beans.xml`
   - 内容：所有最终被激活的Bean以及它们所对应的配置文件源码位置
   - 用途：查看Bean的最终配置，调试Bean注入问题

2. **模型文件Dump**
   - 位置：根据模型类型不同，位于不同的子目录下
   - 例如：`_dump/app/your-app/orm/your-entity.orm.xml`
   - 内容：合并后的模型文件，包含所有Delta定制的结果
   - 用途：查看模型合并结果，调试Delta定制问题

3. **配置文件Dump**
   - 位置：`_dump/nop/main/config/`
   - 内容：最终生效的配置信息
   - 用途：查看配置合并结果，调试配置问题

### 6.4 调试模型合并过程

在XDSL模型的根节点上，增加`x:dump="true"`属性，会把更细节的合并算法执行过程打印到日志中：

```javascript
DslNodeLoader.INSTANCE.loadFromResource(resource, null, XDslExtendPhase.validate);
```

### 6.5 Dump文件的使用技巧

1. **查找特定模型**
   - 根据模型类型和名称在_dump目录下查找对应的文件
   - 例如：查找ORM模型可以在`_dump/app/your-app/orm/`目录下查找

2. **比较不同环境的Dump文件**
   - 可以将不同环境下生成的Dump文件进行比较，找出配置差异
   - 有助于调试环境相关的问题

3. **调试Delta合并问题**
   - 通过查看合并后的模型文件，可以了解Delta定制是否生效
   - 每个Delta定制的节点都会包含`ext:resolved-loc`属性，指示其来源

4. **清理Dump文件**
   - Dump文件可能会占用较多磁盘空间
   - 可以在生产环境中禁用Dump功能（设置`nop.debug=false`）
   - 开发环境中可以定期手动清理`_dump`目录

### 6.6 常见问题

**问题**：为什么没有生成_dump目录？
**解决方案**：
1. 检查是否设置了`nop.debug=true`
2. 检查是否有写入权限创建_dump目录
3. 查看日志中是否有相关错误信息

**问题**：为什么某些模型没有Dump文件？
**解决方案**：
1. 检查模型是否被正确加载
2. 检查模型文件的路径是否符合规范
3. 查看日志中是否有相关错误信息

## 7. 常见问题诊断

### 7.1 前端问题

#### 7.1.1 字段对应的前端控件显示为空

**问题**：字段对应的前端控件显示为空，无法录入

**诊断**：可能是control.xlib中没有针对该字段定义对应的编辑器。在debug日志级别下，XuiHelper.getControlTag函数会打印控件映射结果：

```
nop.xui.resolve-control-tag:controlTag=edit-int,prop=id,domain=null,stdDomain=null,stdDataType=int,mode=add
```

#### 7.1.2 上传文件时提示 415 Unsupported Media Type

**问题**：上传文件时提示 415 Unsupported Media Type

**诊断**：这是Quarkus框架报错，可以将quarkus.log.level设置为DEBUG查看详细错误信息。

### 7.2 后端问题

#### 7.2.1 后端的Bean未按预期注入

**诊断步骤**：
1. 查看启动工程的`_dump`目录下`/{appName}/nop/mai/beans/merged-app.beans.xml`中的结果
2. 检查Bean的配置是否正确，是否有`ext:autowired="true"`属性
3. 检查依赖的Bean是否存在，以及是否被正确激活

#### 7.2.2 数据库表未自动创建

**问题**：配置了`nop.orm.init-database-schema=true`，但数据库表未自动创建

**诊断**：
- 建表语句执行失败会自动忽略错误，并且不再执行后续的建表语句
- 可以将Log级别设置为TRACE级别，建表失败的时候会打印日志信息
- 一般只是新加个别表的时候，目前并不会自动识别并新建

### 7.3 开发问题

#### 7.3.1 修改列名后保存，刷新前台列表页面后发现修改没有应用

**问题**：修改列名后保存，刷新前台列表页面后发现修改没有应用

**原因**：page.yaml中label携带了i18n key，前台得到的页面实际上会被国际化文本替换

**解决方案**：
- 删除i18n key，直接使用修改后的值
- 或者确保i18n key对应的国际化文本已经更新

**示例**：
```yaml
# 修改前：带有i18n key
label: '@i18n:col.NopAuthDept.deptType,prop.label.NopAuthDept.deptType|改变类型'

# 修改后：直接使用文本值
label: '改变类型'
```

#### 7.3.2 Inject Bean没有成功

**问题**：使用`@Inject`注解注入Bean没有成功

**原因**：NopIoC不支持对于private变量进行注入

**解决方案**：
- 使用package protected或者protected变量
- 定义public的set方法

**示例**：
```java
// 错误写法：private变量
@Inject
private MyBean myBean;

// 正确写法：protected变量
@Inject
protected MyBean myBean;
```

#### 7.3.3 虚拟文件系统中的模型文件更新了，没有自动刷新

**问题**：应用运行的时候，虚拟文件系统里面的模型文件更新了，没有自动刷新

**原因**：
- Nop平台内部统一使用ResourceComponentManager来加载模型文件，加载的模型会缓存到内存中
- ResourceLoadingCache内置了依赖追踪能力，修改已有文件会自动失效缓存
- 但新生成的文件不会自动被虚拟文件系统扫描到

**解决方案**：
- 调用`VirtualFileSystem.instance().refresh(true)`刷新虚拟文件系统
- 或者使用前台页面中的【刷新缓存按钮】清空后台全局模型缓存，并自动刷新虚拟文件系统

## 8. 调试最佳实践

### 8.1 开启Debug模式

在开发和调试阶段，始终开启Debug模式，以便获得更详细的调试信息和访问调试接口。

### 8.2 合理设置日志级别

根据需要调整日志级别，在调试阶段可以设置为DEBUG或TRACE，在生产环境中设置为INFO或WARN。

### 8.3 利用调试函数

在开发过程中，充分利用XScript中的`$()`调试函数和XPL中的`<c:log>`标签，实时查看表达式的值和执行流程。

### 8.4 查看模型合并结果

定期查看`_dump`目录下的模型合并结果，了解系统内部模型的最终状态，有助于发现配置错误和合并问题。

### 8.5 使用DevDoc调试接口

熟练使用DevDoc调试接口，查看系统内部的Bean、函数、变量和配置信息，快速定位问题。

### 8.6 理解可逆计算原理

深入理解Nop平台的可逆计算原理，有助于更好地理解系统的设计思想和运行机制，从而更高效地调试和解决问题。

## 9. 常见问题速查

| 问题类型 | 常见问题 | 解决方案 |
|---------|---------|---------|
| 前端 | 字段控件显示为空 | 检查control.xlib中的控件映射，查看debug日志 |
| 前端 | 上传文件415错误 | 检查Quarkus配置，查看DEBUG日志 |
| 后端 | Bean注入失败 | 检查Bean访问修饰符，使用protected或package protected |
| 后端 | 数据库表未自动创建 | 检查nop.orm.init-database-schema配置，查看TRACE日志 |
| 开发 | 列名修改未生效 | 删除i18n key或更新国际化文本 |
| 开发 | 模型文件未自动刷新 | 调用VirtualFileSystem.refresh(true)或使用刷新缓存按钮 |
| 开发 | GraphQL返回Map结构报错 | 使用普通DataBean或List结构替代Map |
| 开发 | 自定义泛型类返回报错 | 为泛型类创建具体的派生类 |

## 10. 总结

Nop平台提供了丰富的调试机制和工具，包括Debug模式、详细的异常信息、日志系统、模型Dump和DevDoc调试接口等。通过熟练掌握这些工具和方法，开发者可以更高效地定位和解决问题，提高开发效率和代码质量。

同时，深入理解Nop平台的可逆计算原理和设计思想，有助于更好地理解系统的运行机制，从而更高效地调试和扩展系统。

希望本指南能够帮助开发者和AI大模型更好地理解和使用Nop平台的调试功能，提高开发效率和问题解决能力。