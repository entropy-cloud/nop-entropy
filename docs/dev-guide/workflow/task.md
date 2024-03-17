# 堆栈式任务流

TaskFlow是采用堆栈结构的轻量级工作流。它具有如下功能：

1. 一个Task分解为多个Step。
2. Step允许异步执行或者同步执行
3. Step返回TaskStepResult，其中可以通过nextStepId指定跳转到指定步骤
4. 缺省情况下执行完步骤A后会继续执行步骤的下一个兄弟节点

* 堆栈结构基本可以看作是线性执行，每个步骤有唯一的父步骤。
* 每个步骤具有一个IEvalScope，子步骤通过input从父scope中获取变量，通过output将数据返回到父scope中。相当于是函数调用

````
 var { outVar } = child( {inputVar: expr_eval_in_parent_scope }) 
````

**每个Step就相当于是一个可配置的、异步执行、可中断重启、可插入interceptor的函数**

## 任务步骤接口

````java 
interface ITaskStep {
    String getStepId();

    TaskStepResult execute(int runId, ITaskStepState parentState, ITaskRuntime taskRt);
} 
````

* stepId: 每个步骤具有唯一的定义id，它在整个流程图定义中唯一
* runId: 步骤每次执行都产生一个新的runId。例如循环执行10次，则产生10个不同的runId，通过runId可以区分步骤多次执行产生的不同实例。id=stepId+runId
* parentState: 步骤的执行实例记录了父子关系，从而可以根据这些状态记录恢复出调用堆栈结构。
* taskRt: 任务执行过程中的全局上下文。每个步骤的input都从taskRt中读取数据，步骤执行完毕后再通过output修改taskRt中的全局变量

步骤之间通过全局的taskRt来交换信息，相当于是一种黑板模式。

## 步骤通用能力

所有的任务步骤都具有一些通用的属性，例如执行条件检查、失败重试、触发次数限制、超时时间等。对这些通用属性的处理逻辑如下：

1. 在parentScope中检查when条件是否满足，不满足条件则直接跳过步骤
2. 在parentScope中执行input表达式得到输入变量，然后将它们设置到当前步骤的scope中
3. 注册超时时间，超时时间到达的时候取消整个step的执行，step的状态转换为TIMEOUT
4. 注册错误处理，如果失败，则执行catch处理。如果catch，则认为步骤状态恢复到正常，步骤相当于是成功结束。
5. 注册重试策略，如果后续执行失败，则按照重试策略重试
6. 检查throttle和rate-limit配置，对请求进行限速
7. 执行decorator
8. 执行步骤body
9. body执行完毕后，根据output设置将步骤scope中的变量保存到parentScope或者全局scope中。抛出异常的时候不会执行output处理。

````javascript
  if(task.cancelled) stop;
  
  retry{
      if(第一次执行){
        if(不满足条件) return SUCCESS;
        根据parentScope初始化inputs
        执行输入验证
        初始化stepState
        startTimeoutMonitor()
      }else{
        从持久化存储中恢复stepState
      }

      try{
         rateLimit()
         result = execute()
         return processResult(result)
      }catch(e){
         onException(e)
      }finally{
         onFinally()
      }
  }
````

## 状态管理

* 无法直接看见parentScope中的变量，所有可用变量都是通过input传入
* catch和finally中的代码都是立刻执行的脚本函数，不涉及到异步状态保持。可以通过返回变量或者跳转步骤来到达一个新的异步步骤实现异步执行。
* 如果step设置了useParentScope=true，则本步骤直接使用parentScope，而不是新建自己的一个scope
* 为了支持状态恢复，每个步骤内部使用的状态也保存在自己的stepState上
* 主线程上会开启orm session，但是orm session本质上只能单线程访问。所有如果子步骤异步执行，则子步骤的input不能传递实体对象，只能是普通的POJO对象。
  原则上应该通过子步骤中重新获取实体数据。
* scope对应于当前step， taskRt.scope对应于整个任务共享的全局scope
* Step的scope中标记为persist的变量会被持久化。restore运行的时候会注入一个额外的_recoveryMode变量

## 执行

* 如果没有指定next，则next为下一个兄弟节点。
* 标签库可以作为接口的实现。
* 任务执行有明确的生命周期概念。生命周期结束时自动回收相关资源。

错误恢复：

* 实现类似于continuation机制，基本做法是将内部执行状态明确保存在taskState中，这样只需要恢复taskState就恢复了内部执行状态
* inputs + internalStates ==> change internalStates ==> outputs when finished

````javascript
  internalStates = initState(inputs);
  while(!internalStates.finished){
     doSomething();
     update(internalStates,delta);
  }
  outputs = buildOutputs(scope)
````

## 结构

chain/pipeline是一个特别常用的模式，应该可以直接通过约定来构建

## 数据驱动模式

TaskFlow并不直接支持数据驱动模式。

* 在数据驱动模式中，每个步骤定义几个输出变量，然后引用到这些输出变量的节点才形成依赖关系。
* 数据驱动自动引入一个异步等待语义，当所有前置步骤都成功输出变量之后，才会执行本步骤。