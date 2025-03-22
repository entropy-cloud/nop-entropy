# Through NopTaskFlow Logic Orchestration Implement Backend Service Functionality

[Video Introduction](https://www.bilibili.com/video/BV19J4m1J78t/)

In the microservices architecture, services are coarse-grained and reusable units. In cross-domain collaboration, we can introduce a service orchestration engine to flexibly combine microservice calls. However, when focusing on the internal logic structure of a single service, we need more efficient and concise implementations, which standard service orchestration engines often struggle to provide.

1. Service orchestration often embeds REST calls or assumes some remote call mechanisms. When invoking local functions, it can become overly complex.
2. Service calls typically involve input and output that are serializable (e.g., JSON), but sharing complex domain model objects directly is not straightforward.
3. Standard service orchestration engines rarely include native environment abstraction for local execution, making it difficult to specify certain steps within a database transaction or using the same OrmSession.
4. Service orchestration often enforces the inclusion of heavy infrastructure components like message queues (e.g., Redis), which complicates lightweight operation without third-party dependencies.

NopTaskFlow adopted the principle of minimizing information expression, abstracting the core logic in logic orchestration, and supporting both heavy distributed service orchestration and lightweight function-level granularity in service internals. It also embeds built-in meta-programming mechanisms via the [XLang language](../xlang/index.md), enabling persistence, transaction handling, and distributed RPC calls to be added as needed.

> For minimizing information expression, refer to [Business Development Freedom: Breaking Framework Constraints, Achieving True Framework Independence](https://zhuanlan.zhihu.com/p/682910525)

On the Nop platform, NopTaskFlow essentially provides a generic mechanism for decomposing and organizing functions. Any function that requires function replacement can be implemented by calling NopTaskFlow.

> For more information about NopTaskFlow, refer to [Next Generation Logic Orchestration Engine from Scratch: NopTaskFlow](https://zhuanlan.zhihu.com/p/691166138)

A typical use case for a logic orchestration framework is implementing backend service functions. Originally, we manually wrote backend service functions, but now we can implement them using the NopTask service orchestration model.

On the Nop platform, you can define service functions in xbiz model files.

```xml
<!-- /nop/demo/model/Demo/Demo.xbiz -->
<biz>
  <actions>
    <mutation name="callTask">
      <arg name="a" type="java.lang.Integer" mandatory="false"/>
      <arg name="b" type="java.lang.Integer" mandatory="false"/>
      <arg name="_selection" type="io.nop.api.core.beans.FieldSelectionBean" kind="FieldSelection"/>
      <arg name="svcCtx" type="io.nop.core.context.IServiceContext" kind="ServiceContext"/>
      <return>
        <schema>
          <props>
            <prop name="sum" type="java.lang.Integer"/>
          </props>
        </schema>
      </return>
      <source>
        const taskFlowManager = inject('nopTaskFlowManager');
        const task = taskFlowManager.getTask('test/DemoTask', 1);
        const taskRt = taskFlowManager.newTaskRuntime(task, false, svcCtx);
        taskRt.setInput('a', a);
        taskRt.setInput('b', b);
        return task.executeAsync(taskRt, _selection?.sourceFields);
      </source>
    </mutation>
  </actions>
</biz>
```

> In the `Demo.xbiz` business object model, adding the `callTask` method will expose the `/r/Demo__callTask` REST endpoint.

When manually writing the integration code for `NopTaskFlow`, you will encounter a significant amount of repetitive information. For instance, if you define input variables `a` and `b` within a task, these definitions will still need to be declared separately in the `xbiz` model.

The Nop platform emphasizes minimal information expression, which means that all information that can be derived automatically should remain unexpressed. Imagine that if the **Web framework** and **logic orchestration engine** are designed as a unified entity, we only need to express the following information:

```xml
<mutation name="callTask" task:name="test/DemoTask"/>
```

Once you locate `task:name` in `TaskFlowModel`, you can automatically derive the output parameters of the REST service function, including its parameter types and specific implementation code.

The **Web framework** and **logic orchestration framework** are designed separately within the Nop platform. While they may not share knowledge about each other, we can seamlessly integrate them using a compile-time meta-programming mechanism.

```xml
<biz>
  <x:post-extends>
    <biz-gen:TaskFlowSupport xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
  </x:post-extends>

  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

The `<x:post-extends>` mechanism is a compile-time post-processing mechanism used across all Domain Specific Languages (DSLs) within the Nop platform. It allows us to execute code that modifies the current model.

```xml
<biz-gen:TaskFlowSupport>
  <x:property name="libPath">/nop/core/xlib/biz-gen.xlib</x:property>
</biz-gen:TaskFlowSupport>
```

The `<biz-gen:TaskFlowSupport>` tag identifies the `task:name` property and automatically defines the `action` in full detail on a mathematical level. This process is entirely separate from the runtime environment.

We can further abstract the `<x:post-extends>` mechanism into a base model.

```xml
<biz x:extends="/nop/biz/lib/common.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

All `xbiz` models generated from the data model automatically include `<biz-gen:TaskFlowSupport>`, allowing direct configuration using `task:name`.

```xml
<biz x:extends="_NopAuthUser.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```



The Nop platform employs meta-programming to automatically derive new models from existing ones. This process can be viewed as a **reactive derivation**: when dependent models change, the cached model automatically invalidates.

For example, if the `test/DemoTask` logic orchestration task is modified, the `callTask` action definition will be regenerated, ensuring that the action and task definitions remain synchronized indefinitely.

> The specific mechanism resembles Vue's reactive data tracking. When a referenced model changes, the dependency relationship between models is automatically recorded.

