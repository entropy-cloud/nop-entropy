# 逻辑编排

单元测试参见 [TestTaskManager.java]()
，测试文件参见[/nop/task/test](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test)

设计原理的介绍，参见[lowcode-task-flow.md](../../theory/lowcode-task-flow.md)

使用NopTaskFlow来实现后台服务函数的原理说明，参见[task-flow-for-biz.md](task-flow-for-biz.md)

## 基本概念

1. TaskFlow: 逻辑流模型，存放在task.xml模型文件中，另外也可以存放在task-lib.xml中作为可复用的步骤库
2. Step: 逻辑流分解为多个子步骤，每个步骤可以嵌套调用其他子步骤。
3. Input/Output: 步骤类似于一个多输入、多输出的函数，通过Input获取数据，并通过output输出数据
4. Scope: 每个步骤内部有一个独立的变量作用域，Input从父步骤的scope中获取变量值，而Output则负责更新父步骤的scope
5. Async: 步骤可以同步执行或者异步执行，一般情况下框架会自动等待前一个步骤异步执行完毕，然后才执行下一个步骤
6. TaskRuntime/TaskStepRuntime: 任务和任务步骤执行的上下文对象，支持异步cancel

基本执行结构如下：

```javascript
parentScope = parentStepRuntime.scope

for each inputModel
   inputs[inputModel.name] = inputModel.source.evaluate(parentScope)

outputs = await step.execute(inputs);

for each outputModel
   parentScope[outputModel.exportAs] = outputs[outputModel.name]
```

在概念层面上非常类似于一般程序语言中的函数调用：

```javascript
var { a: aName, b: bName} = await fn( {x: exprInput1, y: exprInput1} )
```

TaskFlow的Step相当于是对于传统的函数对象进行了增强，自动支持异步执行、超时处理、自动重试等功能。

## 步骤的通用配置

TaskFlow的元模型定义参见[task.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

TaskFlow内置了sequential/parallel/loop/choose等通用语法步骤，所有这些步骤具有一些通用的配置。

```xml

<xdef:define xdef:name="TaskStepModel" executor="bean-name" timeout="!long=0"
             name="var-name" runOnContext="!boolean=false" ignoreResult="!boolean=false"
             next="string" nextOnError="string">
  <input name="!var-name" xdef:name="TaskInputModel" type="generic-type" mandatory="!boolean=false"
         fromTaskScope="!boolean=false" xdef:unique-attr="name">
    <source xdef:value="xpl"/>
  </input>

  <output name="!var-name" xdef:name="TaskOutputModel" toTaskScope="!boolean=false" type="generic-type"
          xdef:unique-attr="name" exportAs="var-name">
    <source xdef:value="xpl"/>
  </output>

  <when/>
  <validator/>
  <catch/>
  <finally/>

  <retry/>

  <throttle/>

  <rate-limit/>
</xdef:define>
```

*
input缺省情况下从parentScope获取变量。如果指定了source，则动态执行表达式获取，否则按照name名称获取。如果设置了`fromTaskScope=true`
表示从全局task上下文获取
* 当步骤成功执行之后，会根据output配置更新parentScope。如果指定了source，则根据表达式动态计算返回值。如果不指定source，则根据name从步骤的TaskStepReturn.outputs集合中获取。
  如果设置了`toTaskScope=true`，则表示更新全局task上下文，而不是更新parentScope
* 通过output中的exportAs配置可以改变更新scope时的变量名。例如

```xml

<output name="result" exportAs="a"/>
```

表示将返回数据中的result变量更新到父scope中，变量名修改为a

## 内置步骤

## Xpl脚本

```xml

<xpl name="test">
  <input name="sum"/>
  <source>return sum + 1</source>
  <output name="sum"/>
</xpl>
```

xpl步骤用于执行xpl模板语言。上面的例子相当于是

```
sum = function(sum){
  return sum + 1
}(sum)
```

## 顺序执行

```xml

<sequential name="test">
  <steps>
    <xpl name="step1">
      <source>
        return 1
      </source>
    </xpl>

    <xpl name="step2">
      <input name="RESULT"/>
      <source>
        return RESULT + 2
      </source>
    </xpl>
  </steps>
</sequential>
```

sequential步骤执行时，会特殊识别每个子步骤返回的名称为RESULT的变量，并自动更新到scope中。以上代码相当于

```
RESULT = function(){
  return 1
}();

RESULT = function(RESULT){
  return RESULT + 2
}(RESULT)
```

## 步骤装饰器

通过步骤装饰器可以引入transaction、ormSession等依赖更多的类AOP支持，参见[task-step-decorator.md](task-step-decorator.md)

## output变量重命名

通过以下方式可以改变输出变量的名称

### 1. 使用exportAs变量会改变返回到父scope中的变量名

```xml

<output name="RESULT" exportAs="value"/>
```

### 2. 执行表达式生成新的返回变量

```xml

<output name="value">
  <source>RESULT</source>
</output>
```

根据当前环境中的值执行某个source表达式动态计算得到一个返回值

## 扩展节点类型

NopTaskFlow内置了一个特殊的步骤类型custom，可以通过customType属性和xmlns来指定一个xpl标签，它会被自动翻译为标签实现。

```xml

<task xmlns:test="/nop/test/test.xlib" x:extends="/nop/task/lib/common.task.xml">
  <steps>
    <custom name="step1" customType="test:MyFunc" test:a="${1}">
      <input name="b"/>
      <test:exec>
        <c:script>
          return x + y;
        </c:script>
      </test:exec>
    </custom>
  </steps>
</task>
```

* `common.task.xml`通过`<x:post-extends>`引入了customType的翻译支持，实际会调用`<task-gen:TransformCustomType>`来实现翻译
  翻译后实际编译的内容为

```xml

<task xmlns:test="/nop/test/test.xlib" x:extends="/nop/task/lib/common.task.xml">
  <steps>
    <xpl name="step1">
      <input name="b"/>
      <source>
        <test:MyFunc xpl:lib="/nop/test/test.xlib" a="${1}" b="${b}">
          <exec>
            <c:script>
              return x + y;
            </c:script>
          </exec>
        </test:MyFunc>
      </source>
    </xpl>
  </steps>
</task>
```

也就是说customType会被翻译为xpl标签，然后通过xpl标签来实现自定义的逻辑。customType会指定一个名字空间，所有具有此名字空间的属性和子节点会成为标签函数的属性和子节点。
另外，所有input输入的参数，也会自动成为标签的属性。

通过这种转换机制可以尽量减小自定义扩展步骤和内置步骤之间的形式差异。除了多出一个名字空间之外，基本上与内置标签形式完全一致。
