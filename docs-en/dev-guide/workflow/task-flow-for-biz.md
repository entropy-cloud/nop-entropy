
# Implementing Backend Service Functions via NopTaskFlow Logic Orchestration

[Video Introduction](https://www.bilibili.com/video/BV19J4m1J78t/)

Under a microservices architecture, a service is a coarse-grained, reusable unit. For cross-domain collaboration, we can introduce a service orchestration engine to flexibly compose microservice invocations. However, when we zoom in to the internal logic of a single service, we need a more efficient and concise implementation—something typical orchestration engines struggle with.

1. Many orchestration tools bake in assumptions about REST or other forms of remote invocation, making local function calls less straightforward.
2. Service calls usually take and return serializable value objects (e.g., JSON), which prevents sharing complex domain model objects by reference.
3. Orchestration engines typically lack a local environment abstraction, so you can’t specify that certain steps run within the same database transaction or share the same OrmSession.
4. Many orchestration frameworks mandate heavyweight infrastructure—such as message queues, Redis, or persistent databases—making it impossible to run in a lightweight mode with no third‑party dependencies and no persistence.

NopTaskFlow adopts a design principle of minimal information expression, abstracting the core pure-logic portion of orchestration. It can support both heavyweight distributed service orchestration and lightweight, fine-grained orchestration at the function level within a service. Through the [XLang language](../xlang/index.md)’s built-in metaprogramming mechanisms, we can introduce persistence, transaction handling, distributed RPC calls, and more on demand.

> For minimal information expression, see [The Road to Freedom in Business Development: How to Break Framework Constraints and Achieve True Framework Neutrality](https://zhuanlan.zhihu.com/p/682910525)

In the Nop platform, NopTaskFlow provides a general mechanism to structurally decompose and organize functions; anywhere a function is needed, it can be replaced by invoking NopTaskFlow.

> For an introduction to NopTaskFlow, see [A From-Scratch Next-Generation Logic Orchestration Engine: NopTaskFlow](https://zhuanlan.zhihu.com/p/691166138)

A typical use case for a logic orchestration framework is implementing backend service functions: instead of hand-writing a backend service function, we switch to invoking a NopTask service orchestration model.

In the Nop platform, we can define service functions in xbiz model files.

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
        const task = taskFlowManager.getTask('test/DemoTask',1);
        const taskRt = taskFlowManager.newTaskRuntime(task,false,svcCtx);
        taskRt.setInput('a',a);
        taskRt.setInput('b',b);
        return task.executeAsync(taskRt,_selection?.sourceFields);
      </source>
    </mutation>
  </actions>
</biz>
```

> By adding the `callTask` method to the `Demo.xbiz` business object model, it is exposed externally as the `/r/Demo__callTask` REST endpoint.

If we integrate NopTaskFlow manually, we end up with large swaths of boilerplate like the above, inevitably causing duplicated information. For example, even though the task already defines input variables `a` and `b`, we still have to redundantly declare the parameters in the xbiz model.

The Nop platform emphasizes minimal information expression, meaning anything that can be inferred automatically should be inferred rather than explicitly stated. Imagine that the web framework and the orchestration engine were designed as one: we would only need to express the following:

```xml
<mutation name="callTask" task:name="test/DemoTask"/>
```

After locating the TaskFlowModel via `task:name`, we can automatically infer the REST service function’s input and output parameter types, as well as the function’s concrete implementation, from the orchestration model.

In the Nop platform, the web framework and the orchestration framework are designed independently and are unaware of each other, but we can seamlessly glue them together via compile-time metaprogramming.

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

`x:post-extends` is a compile-time post-processing mechanism available to all DSLs in the Nop platform, where we can execute code to amend the current model. The [`<biz-gen:TaskFlowSupport>`](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task-gen.xlib) tag recognizes the `task:name` attribute and automatically derives the action’s complete definition.

> Applying `<biz-gen:TaskFlowSupport>` is akin to introducing a mathematical theorem: using it automatically carries out the derivation. This is a purely formal transformation at the mathematical level and has nothing to do with the framework’s runtime.

We can further simplify the invocation by abstracting `<x:post-extends>` into a base model.

```xml
<biz x:extends="/nop/biz/lib/common.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

Currently, all xbiz models generated from data models automatically include `<biz-gen:TaskFlowSupport>`, so you can use the `task:name` configuration directly.

```xml
<biz x:extends="_NopAuthUser.xbiz">
  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```

**Dynamic Updates:**

The Nop platform’s process of automatically deriving new models via metaprogramming can be viewed as a form of **Reactive Derivation**: when a dependent model changes (is modified), the cache of already-derived models is automatically invalidated. For example, in the case above, when the `test/DemoTask` orchestration task is modified, the action definition of `callTask` is regenerated to ensure it always stays in sync with the task definition.

> The implementation is similar to Vue’s reactive data tracking: when a model is referenced, dependencies between model files are automatically recorded.

<!-- SOURCE_MD5:cb9ee98deeb017d8b768ae712a2951fa-->
