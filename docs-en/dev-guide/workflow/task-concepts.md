# Stack-Based Task Flow

TaskFlow is a lightweight flow that uses a stack structure. It has the following features:

1. A Task is decomposed into multiple Steps.
2. Each Step can be executed asynchronously or synchronously.
3. In the default case, after executing Step A, it will proceed to execute the next sibling node in the sequence.

* The stack structure essentially functions as a linear execution flow, where each Step has a unique parent Step.
* Each Step possesses an `IEvalScope`. Child Steps obtain input variables from the parent scope and return output values back to the parent scope. This is akin to function calls.

```
var { outVar } = child({inputVar: expr_eval_in_parent_scope});
```

**Each Step essentially functions as a configurable, asynchronous, restartable, and interceptable function.**

## Task Step Interface

```java
interface ITaskStep {
    String getStepType();

    List<? extends ITaskInputModel> getInputs();

    List<? extends ITaskOutputModel> getOutputs();

    TaskStepResult execute(ITaskStepRuntime stepRt);
}
```

* `runId`: A unique identifier is generated for each execution. For example, if a Step is executed 10 times, it will generate 10 different runIds, allowing us to distinguish between multiple instances of the same Step.
* `parentState`: The execution instance records the parent-child relationship, enabling reconstruction of the call stack based on these state records.
* `taskRt`: The global context of the task execution. Each Step's input is read from this context, and after execution, the output modifies the global variables in `taskRt`.

Steps communicate via the global `taskRt`, mimicking a blackboard pattern.

## Step Common Capabilities

All Steps possess some common attributes, such as execution conditions, failure handling, retry limits, timeouts, etc. The processing logic for these common attributes is as follows:

1. Check if the `when` condition in the parent scope is met. If not, skip the Step.
2. Evaluate the `when` expression in the parent scope to obtain input variables and set them in the current Step's scope.
3. Register a timeout; if exceeded, cancel the Step's execution and transition its state to TIMEOUT.
4. Implement retry logic: if subsequent executions fail, retry according to the defined strategy.
5. Define error handling: if an exception occurs, handle it with the associated catch block. Upon catching an error, restore the Step's state to a normal state, effectively treating the Step as successfully completed.
6. Enforce throttling and rate limiting based on configuration.
7. Apply decorators to the Step.
8. Execute the Step's logic in the `body`.

Upon execution completion, variables from the current Step's scope are copied to either the parent scope or the global scope. If an exception occurs during this process, it will not be handled by the `output` processing.

## State Management

* The parentScope variables cannot be directly accessed; they must be retrieved via the input mechanism.
* `catch` and `finally` blocks in the code are executed immediately upon encountering an error, without considering asynchronous state maintenance. Asynchronous execution can only be achieved through variable substitution or step transitions.
* If a Step is configured with `useParentScope=true`, it will directly utilize the parentScope instead of creating its own new scope.
* Each Step maintains its own `stepState` for internal use, which is stored within the Step's context to support state recovery.

## State Recovery

- The main thread initializes an `orm session`. However, due to the limitations of the `orm session`, only synchronous operations can be performed. Asynchronous Steps cannot transfer entity data through their input parameters; they must operate on plain POJOs. In principle, asynchronous Steps should obtain entity data from child Steps.


* If `next` is not specified, `next` will be the next sibling node.
* The library can be used as an implementation of the interface.


## Execution Flow

* The execution has a clear lifecycle concept. When the lifecycle ends automatically releases related resources.


## Error Recovery

* The implementation resembles the continuation mechanism. The basic approach is to store the internal execution state explicitly in `taskState`. Thus, only the taskState needs to be recovered to restore the internal execution state.
* `inputs + internalStates ==> change internalStates ==> outputs when finished`

```javascript
  internalStates = initState(inputs);
  while(!internalStates.finished){
     doSomething();
     update(internalStates,delta);
  }
  outputs = buildOutputs(scope)
```


## Data-Driven Mode

In non-streaming processing scenarios, the dependencies between data are essentially derived from the dependencies between steps. Because the output of a step is an overall output, the entire step must complete before its output can be obtained.

Considering side effects, not all dependencies evident in the data flow will manifest as input-output dependencies. The dependencies of the steps themselves are important.

* In the data-driven mode, each step defines a few output variables, and only nodes referencing these output variables form dependencies.
* The data-driven mode introduces an implicit asynchronous semantic. A step will only execute once all preceding steps have successfully produced their output variables.
* Data-driven mode is similar to using space coordinates instead of explicitly using time coordinates.


## Input-Output and References

* Steps are similar to functions, which do not allow cross-layer jumps. Thus, only the local `stepName` needs to be used.
* The step has a global `path`, which is composed by concatenating the `stepName` of each layer: `stepPath={stepName}/{stepName}`
* SubTask provides a basic reuse mechanism. It can build an independent `taskRt` or directly embed in the current `taskRt`.
* The return type is normalized into a Map type, which supports multiple outputs. If only a single result value is returned, the variable name is RESULT.



1. Runtime model and DSL definition are separated. The descriptive model undergoes compilation to generate the runtime model and does not directly use it.
2. The runtime model is static and cacheable. It does not contain runtime state information. Runtime state information is separated into an independent `TaskStepRuntime`.
3. If no scheduler is used for simple cases, complex scheduling techniques are not employed.
4. Asynchronous descriptions can hide all thread-related concepts.
5. It's not a flat expansion of steps. Layered abstraction through `extType + xpl` is used for extension.

