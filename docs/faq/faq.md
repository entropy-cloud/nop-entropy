# 可视化设计器

## 1. 修改列名后保存，刷新前台列表页面后发现修改没有应用。但是设计器中已经修改。
保存到后台后，可以发现page.yaml中label携带了i18n key，则前台得到的页面实际上会被国际化文本替换。
```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '@i18n:col.NopAuthDept.deptType,prop.label.NopAuthDept.deptType|改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```

`@i18n:key|defaultValue`格式中，字符|之后的部分是缺省值，只有当i18n key对应的国际化文本不存在的时候才会返回这个值。

在设计器里查看的时候，因为是在设计阶段所以没有替换i18n key。如果一定要以修改的值为准，则可以删除i18n key.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```
在设计器中修改，则需要在json视图中自己删除

![](remove-i18n-key.png)


## 2. 在GraphQL中如何构造Query条件
在前台可以使用`url："@query:NopAuthDept__findList/id?filter_deptName=a"`这种简化查询语法，执行对象的
findList或者findPage函数时，会识别`filter_`为前缀的参数，然后把它转换为QueryBean的filter树形结构查询条件。如果一定要手工构造QueryBean，则可以仿照如下调用

```graphql
query($query:QueryBeanInput,$q2:QueryBeaInput){
  NopAuthDept__findList(query:$query) {
    id,
    deptName
    parent {
      id
    }
  },
  
  NopAuthUser__findPage(query:$q2){
    page
    items{
      nickName
      userName
    }
  }
}

```
variables设置为:
```json
{
  "query": {
    "filter": {
      "$type":"and",
      "$body": [{
        "$type":"eq",
        "name": "deptName",
        "value": "a"
      }
      ]
    }
  },
  "q2":{
    "filter":{
      "$type":"eq",
      "name":"userName",
      "value":"a"
    }
  }
}

```

## 3. 单元测试如何不使用本地数据库
自动化测试介绍参见[autotest.md](../dev-guide/autotest.md)

1. 需要利用内存数据库，基于NopOrm底层的数据层录制回放机制，
2. 不需要内存数据库支持可以从JunitBaseTestCase继承，实现纯逻辑的测试，它只启动IoC容器。如果IoC也不需要，可以从BaseTestCase继承，它只提供一些帮助函数

## 4. 应用运行的时候，虚拟文件系统里面的模型文件更新了，会自动刷新吗？还是需要重启应用
Nop平台内部统一使用ResourceComponentManager来加载模型文件，加载的模型会缓存到内存中。ResourceLoadingCache内置了依赖追踪能力，即它会自动记录
解析模型的过程中所使用的所有依赖的模型文件，只要任何一个文件发生修改（时间戳发生变化），模型缓存都会自动失效。再次获取时会重新解析。

如果是新生成的文件，虚拟文件系统不会自动扫描得知新的文件，需要调用一下VirtualFileSystem.instance().refresh(true)。
虚拟文件系统不仅仅包括classpath下的_vfs目录，还会自动包括系统启动时当前目录下的_vfs目录。而且当前目录下的_vfs目录优先级更高，其中的文件会覆盖classpath下的文件，

在前台页面中，有一个【刷新缓存按钮】会清空后台全局模型缓存，并自动刷新虚拟文件系统。

## 4. 从IServiceContext中能否获取request, response等Web环境对象
NopGraphQL的设计是与Web环境无关，它可以用在消息队列、批处理等场景，作为一个通用的服务派发、结果汇总框架来使用，因此不提供任何与Web环境相关的方法。

在IServiceContext中可以通过setAttribute/getAttribute等方法保存自定义对象。IServiceContext.getCache()还提供了一个单次请求范围内有效的缓存对象，
可以用于缓存字典数据等。一般业务参数应该显式传递，IServiceContext基本等价于一个Map结构，主要用于在框架内部保存一些单次请求范围内的共享信息。

# 部署问题

h
# 设计问题

## 1. Nop平台的文档中提到的函数抽象和函数模板化是什么意思？
比如说工作流 执行到步骤A，需要增加一个判断条件，不管这个判断怎么写，它本质上都对应于一个判断函数。在工作流引擎的设计中，有些人可能会做如下设计

````xml
<step>
    <when class="xxx.MyCondition" />
</step>
````

由一个Java类负责提供判断条件。在这种情况下，工作流引擎甚至有可能提供一整套的插件机制，来动态加载判断插件。这些插件需要符合
工作流的接口标准，只能在当前这个工作流引擎中使用。

但是在Nop平台看来，这里所需要的仅仅是一个函数抽象接口，具体如何实现是另外一个独立的问题，**与工作流引擎的设计无关**。
也就是说，在工作流引擎的设计中，我们唯一需要的就是函数抽象，而并不需要进一步的插件加载抽象。

Nop平台的具体做法是统一使用xpl模板语言，通过将函数结构体模板化来解决函数实现的问题。

也就说，在Nop平台中解决问题，我们系统化的采用如下策略：
1. 在关键的地方定义函数，且使用通用的IEvalFunction或者IEvalAction接口
2. 使用模板语言提供函数实现。

因为模板语言是XML格式（结构化的表达格式），可以通过通用的TreeEditor自动实现可视化编辑。

根据可逆计算理论，一切信息结构都可以存在多种表示形式，这些表示形式之间可以进行可逆转换。可视化编辑不过是可视化表示与文本表示之间的一种双向映射。

这种双向映射的能力具有复合性。也就是说，如果a <--> A, b <--> B, 那么我们有可能自动得到 a + b <--> A + B

### 通过XPL模板语言实现函数结构的模板化

假设我们的判断条件在Java中具有如下实现代码

````
if(user.age < 18)
  return false
  
if(user.gender == 1)
  return false
  
return true    
````

我们可以把它转换为抽象语法树，然后再保存为XML格式，但实际看起来并不直观。
借助于Xpl模板语言中的标签库抽象机制，我们可以通过将具体的逻辑封装为更具有业务语义的标签函数，然后再提供可视化编辑器。
（这类似于我们在表单编辑的过程中配置的是自定义组件，而不是最底层的div/span节点）。

````xml
<and>
    <app:已经成年/>
    <app:性别为男/>
</and>
````

## 2. 什么情况下我们会自己定义自己的所需的.xlib文件，道xlib要放到固定的位置下吗？

xlib就是xpl模板语言的函数库，一般写在自己模块的xlib目录下。它可以写在任何地方，只是普通的函数库而已，在xpl段中调用时通过import语句导入
标签库就可以使用。常用的内置标签库有：
1. web.xlib是用于根据xview模型生成amis页面
2. control.xlib根据字段的domain和type等信息推导得到显示控件。
