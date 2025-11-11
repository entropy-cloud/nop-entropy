
# Logical Orchestration

For unit tests, see [TestTaskManager.java]()
, and for test files, see [/nop/task/test](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test)

For an introduction to the design principles, see [lowcode-task-flow.md](../../theory/lowcode-task-flow.md)

For an explanation of using NopTaskFlow to implement backend service functions, see [task-flow-for-biz.md](task-flow-for-biz.md)

## Basic Concepts

1. TaskFlow: A logical flow model stored in a task.xml model file; it can also be stored in task-lib.xml as a reusable step library.
2. Step: The logical flow is decomposed into multiple sub-steps, and each step can recursively invoke other sub-steps.
3. Input/Output: A step is like a multi-input, multi-output function. It obtains data via Input and outputs data via Output.
4. Scope: Each step has an independent variable scope. Input reads variable values from the parent step’s scope, while Output updates the parent step’s scope.
5. Async: Steps can run synchronously or asynchronously. In general, the framework automatically waits for the previous step to complete its asynchronous processing before executing the next step.
6. TaskRuntime/TaskStepRuntime: Context objects for task and task-step execution, supporting asynchronous cancellation.

The basic execution structure is as follows:

```javascript
parentScope = parentStepRuntime.scope

for each inputModel
   inputs[inputModel.name] = inputModel.source.evaluate(parentScope)

outputs = await step.execute(inputs);

for each outputModel
   parentScope[outputModel.exportAs] = outputs[outputModel.name]
```

Conceptually, this is very similar to function calls in general programming languages:

```javascript
var { a: aName, b: bName} = await fn( {x: exprInput1, y: exprInput1} )
```

A TaskFlow Step can be seen as an enhanced version of a traditional function object, with built-in support for asynchronous execution, timeout handling, automatic retries, and more.

## Common Step Configuration

For the TaskFlow meta-model definition, see [task.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

TaskFlow comes with generic syntactic steps such as sequential/parallel/loop/choose. All of these steps share some common configuration.

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
By default, input reads variables from parentScope. If source is specified, the expression is evaluated dynamically; otherwise, the variable is obtained by name. If you set fromTaskScope=true, it reads from the global task context.
* After a step executes successfully, parentScope is updated according to the output configuration. If source is specified, the return value is computed dynamically via the expression. If source is not specified, the value is retrieved by name from the TaskStepReturn.outputs collection of the step. If toTaskScope=true is set, it updates the global task context instead of parentScope.
* The exportAs attribute in output allows you to change the variable name used when updating the scope. For example:

```xml

<output name="result" exportAs="a"/>
```

This means the result variable in the return data is written back to the parent scope under the name a.

## Built-in Steps

## Xpl Script

```xml

<step name="test">
  <input name="sum"/>
  <source>return sum + 1</source>
  <output name="sum"/>
</step>
```

The xpl step is used to execute the XPL template language. The example above is equivalent to

```
sum = function(sum){
  return sum + 1
}(sum)
```

## Sequential Execution

```xml

<sequential name="test">
  <steps>
    <step name="step1">
      <source>
        return 1
      </source>
    </step>

    <step name="step2">
      <input name="RESULT"/>
      <source>
        return RESULT + 2
      </source>
    </step>
  </steps>
</sequential>
```

When a sequential step runs, it pays special attention to a variable named RESULT returned by each sub-step and automatically updates it in the scope. The code above is equivalent to

```
RESULT = function(){
  return 1
}();

RESULT = function(RESULT){
  return RESULT + 2
}(RESULT)
```

## Step Decorators

Step decorators can introduce transaction, ormSession and other AOP-like supports. See [task-step-decorator.md](task-step-decorator.md)

## Renaming Output Variables

You can change the name of the output variable as follows.

### 1. Using exportAs changes the variable name written back to the parent scope

```xml

<output name="RESULT" exportAs="value"/>
```

### 2. Evaluate an expression to produce a new return variable

```xml

<output name="value">
  <source>RESULT</source>
</output>
```

This dynamically computes a return value by evaluating a source expression against the current context.

## Extending Node Types

The step node in NopTaskFlow has a special customType attribute. Using it together with xmlns to specify an xpl tag will cause the step to be automatically translated into that tag implementation.

```xml

<task xmlns:test="/nop/test/test.xlib" x:extends="/nop/task/lib/common.task.xml">
  <steps>
    <step name="step1" customType="test:MyFunc" test:a="${1}">
      <input name="b"/>
      <test:exec>
        <c:script>
          return x + y;
        </c:script>
      </test:exec>
    </step>
  </steps>
</task>
```

* common.task.xml introduces translation support for customType via <x:post-extends>, which internally invokes <task-gen:TransformCustomType> to perform the translation. After translation, the content actually compiled is:

```xml

<task xmlns:test="/nop/test/test.xlib" x:extends="/nop/task/lib/common.task.xml">
  <steps>
    <step name="step1">
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
    </step>
  </steps>
</task>
```

That is, customType is translated into an xpl tag, and the custom logic is implemented through that xpl tag. customType specifies a namespace; all attributes and child nodes in that namespace become attributes and child nodes of the tag function. In addition, all input parameters are also automatically added as attributes of the tag.

This transformation mechanism minimizes the formal differences between custom extended steps and built-in steps. Other than having an extra namespace, it is essentially identical to the built-in tag form.

## `in/out` namespace
The meta-model definition in task.xdef introduces xdef:transformer-class, which can automatically transform the structure after loading an XNode. This mechanism can be used to normalize multiple different structures into a unified structure.

InOutNodeTransformer automatically recognizes the in: and out: prefixes and converts them into input and output child nodes.

```xml
<xpl in:x="1" out:RESULT="x+y">
   <in:y mandatory="true">2</in:y>
</xpl>
```

Equivalent to
```xml
<step>
  <input name="x">
     <source>1</source>
  </input>

  <input name="y" mandatory="true">
     <source>2</source>
  </input>

  <output name="RESULT">
     <source> x + y</source>
  </output>
</step>
```

<!-- SOURCE_MD5:a8d124a13d63f80eaea13c6d0643944c-->
