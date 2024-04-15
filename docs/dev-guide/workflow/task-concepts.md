# 堆栈式任务流

TaskFlow是采用堆栈结构的轻量级工作流。它具有如下功能：

1. 一个Task分解为多个Step。
2. Step允许异步执行或者同步执行
3. 缺省情况下执行完步骤A后会继续执行步骤的下一个兄弟节点

* 堆栈结构基本可以看作是线性执行，每个步骤有唯一的父步骤。
* 每个步骤具有一个IEvalScope，子步骤通过input从父scope中获取变量，通过output将数据返回到父scope中。相当于是函数调用

```
 var { outVar } = child( {inputVar: expr_eval_in_parent_scope })
```

**每个Step就相当于是一个可配置的、异步执行、可中断重启、可插入interceptor的函数**

## 任务步骤接口

```java
interface ITaskStep {
    String getStepType();

    List<? extends ITaskInputModel> getInputs();

    List<? extends ITaskOutputModel> getOutputs();

    TaskStepResult execute(ITaskStepRuntime stepRt);
}
```

* runId: 步骤每次执行都产生一个新的runId。例如循环执行10次，则产生10个不同的runId，通过runId可以区分步骤多次执行产生的不同实例。id=stepId+runId
* parentState: 步骤的执行实例记录了父子关系，从而可以根据这些状态记录恢复出调用堆栈结构。
* taskRt: 任务执行过程中的全局上下文。每个步骤的input都从taskRt中读取数据，步骤执行完毕后再通过output修改taskRt中的全局变量

步骤之间通过全局的taskRt来交换信息，相当于是一种黑板模式。

## 步骤通用能力

所有的任务步骤都具有一些通用的属性，例如执行条件检查、失败重试、触发次数限制、超时时间等。对这些通用属性的处理逻辑如下：

1. 在parentScope中检查when条件是否满足，不满足条件则直接跳过步骤
2. 在parentScope中执行input表达式得到输入变量，然后将它们设置到当前步骤的scope中
3. 注册超时时间，超时时间到达的时候取消整个step的执行，step的状态转换为TIMEOUT
4. 注册重试策略，如果后续执行失败，则按照重试策略重试
5. 注册错误处理，如果失败，则执行catch处理。如果catch，则认为步骤状态恢复到正常，步骤相当于是成功结束。
6. 检查throttle和rate-limit配置，对请求进行限速
7. 执行decorator
8. 执行步骤body
9. 根据output设置将步骤scope中的变量保存到parentScope或者全局scope中。抛出异常的时候不会执行output处理。

```javascript
  if(task.cancelled) stop;

  stepRt = taskRt.newStepRunTime(stepName);

  if(第一次执行){
    if(不满足条件) return SUCCESS;
    根据parentScope初始化inputs
    执行输入验证
  }else{
    从持久化存储中恢复stepState
  }

  registerTimeout()

  retry{
    try{
       rateLimit()
       result = execute(stepRt)
    }catch(e){
       onException(e)
    }finally{
       onFinally()
    }
  }

  将result中的变量按照输出配置拷贝到parentScope中
```

## 状态管理

* 无法直接看见parentScope中的变量，所有可用变量都是通过input传入
* catch和finally中的代码都是立刻执行的脚本函数，不涉及到异步状态保持。可以通过返回变量或者跳转步骤来到达一个新的异步步骤实现异步执行。
* 如果step设置了useParentScope=true，则本步骤直接使用parentScope，而不是新建自己的一个scope
* 为了支持状态恢复，每个步骤内部使用的状态也保存在自己的stepState上
* 主线程上会开启orm session，但是orm session本质上只能单线程访问。所有如果子步骤异步执行，则子步骤的input不能传递实体对象，只能是普通的POJO对象。
  原则上应该通过子步骤中重新获取实体数据。
* scope对应于当前step， taskRt.scope对应于整个任务共享的全局scope
* Step的scope中标记为persist的变量会被持久化。restore运行的时候会注入一个额外的\_recoveryMode变量

## 执行

* 如果没有指定next，则next为下一个兄弟节点。
* 标签库可以作为接口的实现。
* 任务执行有明确的生命周期概念。生命周期结束时自动回收相关资源。

错误恢复：

* 实现类似于continuation机制，基本做法是将内部执行状态明确保存在taskState中，这样只需要恢复taskState就恢复了内部执行状态
* `inputs + internalStates ==> change internalStates ==> outputs when finished`

```javascript
  internalStates = initState(inputs);
  while(!internalStates.finished){
     doSomething();
     update(internalStates,delta);
  }
  outputs = buildOutputs(scope)
```


## 数据驱动模式

在非流式处理的情况下，数据之间的依赖关系本质上是由步骤之间的依赖关系所衍生得到的。因为步骤的输出是整体性输出，必须整个步骤结束之后才能得到步骤的输出。

考虑到副作用的存在，不是所有依赖在直观上都会变现为输入输出依赖。步骤本身的依赖很重要。

* 在数据驱动模式中，每个步骤定义几个输出变量，然后引用到这些输出变量的节点才形成依赖关系。
* 数据驱动自动引入一个异步等待语义，当所有前置步骤都成功输出变量之后，才会执行本步骤。
* 数据驱动相当于是只使用空间坐标，而不显式使用时间坐标

## 输入输出和引用

* Step类似于函数，不允许跨层架跳转，因此只需要使用局部的stepName即可。
* Step具有全局path，它由各层级的stepName拼接而成: stepPath={stepName}/{stepName}
* SubTask提供基本的复用机制。可以构建独立的taskRt，也可以直接嵌入到当前taskRt中执行
* 返回值类型规范化为Map类型，从而自动支持多输出。如果只返回单一结果值，则变量名为RESULT

## 设计细节

1. 运行时模型与定义DSL分离。描述式模型经过编译转换得到运行时模型，并不直接使用运行时模型
2. 运行时模型为静态可缓存模型，并不包含运行时状态信息。运行时状态信息分离到独立的TaskStepRuntime中
3. 缺省不使用任务队列来调度，在简单情况下不使用复杂的调度技术。
4. 缺省情况下不涉及到线程操作。异步描述已经可以屏蔽所有线程概念。
5. 并不是平铺式的对步骤进行扩展。分层级抽象，通过extType + xpl实现扩展。
