# 如何用Nop平台提升第三方框架的可扩展性
Nop平台的底层可以很容易的和任何设计良好的第三方框架集成在一起使用，只需要极少量的整合代码就可以极大提升第三方框架的可扩展性。
正好最近solon框架引入了一个流处理的插件，它的示例代码中有一个明显的可以集成使用Nop平台的扩展点。

![](images/solon-chain.png)

在这个示例中，Chain是一种可以通过json文件定义的模型对象，它的解析和装载可以通过`Chain.parseByUrl`函数进行。

如果和Nop平台集成，我们可以直接将`Chain.parseByUrl`替换为`ResourceComponentManager.loadComponentModel(path)`，然后把json文件移动到`resources/_vfs`目录下即可。

通过这一修改之后，我们可以立刻得到如下功能：

1. JSON文件解析的结果会被自动缓存，只有到缓存中未找到的时候才会真正读取模型文件。
2. 如果要定制一个模型文件，不需要修改原始文件，只需要在`resources/_delta/default`目录下创建一个同名文件即可。Nop平台会优先加载Delta目录下的文件。
3. 如果希望将模型文件保存到数据库中，而不是放在classpath下，只需要引入一个dao文件协议，例如`dao:Chain/my-task/v1`
4. 如果一个文件过大，在任意子节点上都可以使用`x:extends`语法来引入基础文件，当前节点和引入的节点会自动合并，从而实现大模型文件的拆分和重组。
5. ResourceComponentManager会自动跟踪模型文件的依赖关系，任何依赖文件发生变化的时候都会自动设置模型缓存失效。
6. 可以通过`x:gen-extends`和`x:post-extends`等机制通过元编程来动态生成模型节点，提供更加灵活的二次抽象机制。
7. 使用Nop平台之后，在模型文件内部完全不需要再设计自己特定的`import/plugin`等机制。

```json
{
  "x:gen-extends": "<chain-gen:GenBaseFlow xpl:lib='/xlib/chain-gen.xlib'/>",
  "steps": [
    {
      "x:extends": "my-step.chain.json",
      "name": "step1",
      "ui:hidden": false
    }
  ]
}
```

如果自行实现以上内容也很简单，只需要两三千行代码。

```java
interface IDeltaModelLoader{
  <T> T loadDeltaModel(String path, Class<T> clazz);
}
```
比如增加一个上面定义的IDeltaModelLoader接口，然后通过DeltaFileSystem来查找path对应的文件，在JSON层面实现Delta合并后再转型为指定Java模型类。

**无需修改solon框架的任何运行时代码，只需要将模型加载器的调用这一行代替替换，就可以获得Nop平台独有的Delta定制能力和强大的元编程抽象能力**。使用Delta定制，可以实现当所有json都打包到jar包中以后，无需修改jar包中的文件，通过额外增加delta配置就可以实现对任何模型文件的深度定制调整。可以选择替换整个模型文件，也可以选择只定制模型文件中的某个属性。

## 元模型增强
如果为json文件补充一个xdef元模型，则可以立刻得到如下功能：

1. 可以使用XML、JSON、YAML等多种格式来定义模型文件
2. 可以自动生成模型文件对应的Java类，方便在Java代码中直接使用
3. 在IDEA开发工具中自动实现语法校验、自动补全、断点调试等功能
4. 可以通过Excel来定义该模型内容，无需编写Excel解析代码，就可以自动实现复杂嵌套结构的模型对象与Excel文件之间的双向转换。
5. 模型节点自动支持扩展属性。XDef元模型格式检查会自动跳过所有带名字空间的属性。
