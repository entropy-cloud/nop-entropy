# Logic Arrangement

Unit tests can be found in [TestTaskManager.java](), and test files are located at [/nop/task/test](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test).

The explanation of the design principles is provided in [lowcode-task-flow.md](../../theory/lowcode-task-flow.md).

The rationale behind implementing backend service functions using NopTaskFlow is explained in [task-flow-for-biz.md](task-flow-for-biz.md).

## Basic Concepts

1. **TaskFlow**: The logic flow model is stored in the `task.xml` or `task-lib.xml` files, where it can be reused as a reusable step library.
2. **Step**: The logic flow is decomposed into multiple sub-steps, each of which can call and nest other sub-steps.
3. **Input/Output**: Each step behaves like a multi-input, multi-output function, obtaining data via Input and outputting data via Output.
4. **Scope**: Each step has an independent variable scope. The Input retrieves variable values from the parent step's scope, while the Output updates the parent step's scope.
5. **Async**: Steps can be executed synchronously or asynchronously. Generally, the framework automatically waits for the previous asynchronous step to complete before executing the next one.
6. **TaskRuntime/TaskStepRuntime**: The context object for tasks and sub-steps, supporting asynchronous cancellation.

The basic execution structure is as follows:

```javascript
parentScope = parentStepRuntime.scope;
```

For each `inputModel`:
```javascript
inputs[inputModel.name] = inputModel.source.evaluate(parentScope);
```

Then, execute the step asynchronously:
```javascript
outputs = await step.execute(inputs);
```

For each `outputModel`:
```javascript
parentScope[outputModel.exportAs] = outputs[outputModel.name];
```

The conceptual structure is very similar to function calls in general programming languages:

```javascript
var { a: aName, b: bName } = await fn({ x: exprInput1, y: exprInput2 });
```

The `Step` in TaskFlow is an enhanced version of traditional functions, supporting asynchronous execution, timeout handling, and retry functionality.

## Step Configuration

The meta-model for TaskFlow is defined in [task.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef).

TaskFlow supports built-in sequential, parallel, loop, and choose configurations. All these configurations come with some common settings.


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

*If input is not specified, variables are retrieved from the parent scope by default. If a source is specified, expressions are dynamically evaluated to retrieve variables; otherwise, variables are retrieved based on their name. If `fromTaskScope=true` is set, variables are retrieved from the global task context instead of the parent scope.*

*When the step successfully executes, it updates the parent scope based on the output configuration. If a source is specified, the return value is dynamically calculated using expressions; otherwise, it is obtained based on the step's TaskStepReturn.outputs collection. If `toTaskScope=true` is set, the global task context is updated instead of the parent scope.*

*Through the output's exportAs configuration, you can change the variable name during updates to the scope. For example:*

```xml
<output name="result" exportAs="a"/>
```

*this indicates that the result variable from the data is updated to the parent scope with the variable name changed to "a".*

## Built-in Steps

## Xpl Script

```xml
<xpl name="test">
  <input name="sum"/>
  <source>return sum + 1</source>
  <output name="sum"/>
</xpl>
```

The xpl step is used to execute the Xpl template language. In the example above, it is equivalent to:

```xml
sum = function(sum){
  return sum + 1
}(sum)
```

## Sequential Execution

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

In sequential steps, the system specially identifies each sub-step's return value by its name (e.g., RESULT) and automatically updates the scope. The example above is equivalent to:

```xml
RESULT = function(){
  return 1
}();

RESULT = function(RESULT){
  return RESULT + 2
}(RESULT)
```

## Step Decorator

Using step decorators allows you to introduce transaction, ORM session, etc., which depend on more advanced AOP support. For details, see [task-step-decorator.md](task-step-decorator.md).

## Output Variable Renaming

You can change the name of the output variable by using the exportAs property.

### 1. Using exportAs will change the variable name when returned to the parent scope

```xml
<output name="RESULT" exportAs="value"/>
```

### 2. Execute Expression to Generate New Return Variable

```xml
<output name="value">
  <source>RESULT</source>
</output>
```

Based on the current environment values, execute a `source` expression dynamically to calculate a return value.

## Expand Node Types

NopTaskFlow has built-in support for a special step type called `custom`. This can be specified using the `customType` attribute and `xmlns` namespace. It will automatically be translated into a tag implementation.

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

* `common.task.xml` introduces custom type translation support via `<x:post-extends>`, which actually invokes `<task-gen:TransformCustomType>` for implementation
Translation results in:

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

This means `customType` is translated into an `XPL tag`, which then implements custom logic. The `customType` specifies a namespace, and all attributes and child nodes with this namespace become properties of the `XPL tag`.

Additionally, all input parameters are automatically converted into tag properties.

Through this conversion mechanism, we aim to minimize differences between custom extension steps and built-in steps. Except for the additional namespace, the structure is almost identical to built-in tags.

## `in/out` Namespace

The `task.xdef` metadata model introduces `<xdef:transformer-class`, which can automatically convert structures after loading XNode. This mechanism allows us to normalize different structures into a unified format.

`InOutNodeTransformer` automatically identifies `in:` and `out:` prefixes and converts them into `input` and `output` child nodes.

```xml
<xpl in:x="1" out:RESULT="x+y">
  <in:y mandatory="true">2</in:y>
</xpl>
```

This is equivalent to:

```xml
<xpl>
  <input name="x">
    <source>1</source>
  </input>

  <input name="y" mandatory="true">
    <source>2</source>
  </input>

  <output name="RESULT">
    <source>x + y</source>
  </output>
</xpl>
```
