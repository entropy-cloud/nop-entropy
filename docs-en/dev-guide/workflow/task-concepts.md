# Stack-based Task Flow

TaskFlow is a lightweight workflow that adopts a stack structure. It provides the following capabilities:

1. A Task is decomposed into multiple Steps.
2. Steps can execute asynchronously or synchronously.
3. By default, after finishing Step A, execution proceeds to the next sibling node.

* The stack structure can essentially be regarded as linear execution; each step has a unique parent step.
* Each step has an IEvalScope. Child steps obtain variables from the parent scope via input and return data to the parent scope via output—akin to a function call.

```
 var { outVar } = child( {inputVar: expr_eval_in_parent_scope })
```

**Each Step is essentially a configurable, asynchronously executed, interruptible-and-restartable function into which interceptors can be inserted.**

## Task Step Interface

```java
interface ITaskStep {
    String getStepType();

    List<? extends ITaskInputModel> getInputs();

    List<? extends ITaskOutputModel> getOutputs();

    TaskStepResult execute(ITaskStepRuntime stepRt);
}
```

* runId: Each execution of the step produces a new runId. For example, if it runs in a loop 10 times, 10 different runIds are generated. You can distinguish different instances of multiple executions via runId. id = stepId + runId.
* parentState: The execution instance of a step records the parent-child relationship, so the call stack structure can be reconstructed from these state records.
* taskRt: The global context during task execution. Each step reads its inputs from taskRt; after the step finishes, it modifies global variables in taskRt via its outputs.

Steps exchange information through the global taskRt, which is essentially a blackboard pattern.

## Common Capabilities of Steps

All task steps share common attributes such as execution condition checks, failure retries, trigger limits, timeouts, and more. The handling logic for these common attributes is as follows:

1. Check the when condition within parentScope; if not satisfied, skip the step directly.
2. Evaluate input expressions in parentScope to obtain input variables, then set them into the current step’s scope.
3. Register a timeout; when the timeout is reached, cancel the entire step execution and set the step’s state to TIMEOUT.
4. Register the retry policy; if subsequent execution fails, retry according to the policy.
5. Register error handling; on failure, execute catch handling. If caught, the step is considered recovered, effectively completing successfully.
6. Check throttle and rate-limit configurations to rate-limit requests.
7. Execute decorators.
8. Execute the step body.
9. Persist variables from the step scope to parentScope or the global scope according to the output settings. When an exception is thrown, output processing is not performed.

```javascript
  if(task.cancelled) stop;

  stepRt = taskRt.newStepRunTime(stepName);

  if(first run){
    if(condition not met) return SUCCESS;
    initialize inputs from parentScope
    perform input validation
  }else{
    restore stepState from persistent storage
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

  copy variables from result to parentScope according to output configuration
```

## State Management

* Variables in parentScope are not directly visible; all available variables are passed in via input.
* The code in catch and finally is an immediately executed script function and does not involve asynchronous state retention. You can return variables or jump to another step to transition to a new asynchronous step for asynchronous execution.
* If the step sets useParentScope=true, this step uses parentScope directly instead of creating its own scope.
* To support state recovery, each step’s internal state is stored on its own stepState.
* An ORM session is opened on the main thread, but an ORM session is inherently single-threaded. Therefore, if a child step executes asynchronously, the child step’s input must not pass entity objects—only plain POJOs. In principle, entity data should be re-fetched within the child step.
* The scope corresponds to the current step; taskRt.scope corresponds to the global scope shared by the entire task.
* Variables in the Step’s scope marked as persist will be persisted. When restore runs, an additional _recoveryMode variable is injected.

## Execution

* If next is not specified, next defaults to the next sibling node.
* Tag libraries can serve as implementations of the interface.
* Task execution has a clear lifecycle concept. When the lifecycle ends, related resources are automatically reclaimed.

Error Recovery:

* Implement a mechanism similar to continuations. The basic approach is to explicitly store internal execution state in taskState, so restoring taskState restores internal execution state.
* `inputs + internalStates ==> change internalStates ==> outputs when finished`

```javascript
  internalStates = initState(inputs);
  while(!internalStates.finished){
     doSomething();
     update(internalStates,delta);
  }
  outputs = buildOutputs(scope)
```

## Data-Driven Pattern

In non-streaming scenarios, dependencies among data essentially derive from dependencies among steps, because step outputs are holistic and only available after the step completes.

Considering side effects, not all dependencies will intuitively manifest as input/output dependencies. Dependencies among steps themselves are important.

* In the data-driven mode, each step defines several output variables, and only nodes that reference these outputs form dependencies.
* Data-driven introduces an implicit asynchronous waiting semantics: this step executes only after all predecessor steps have successfully produced their outputs.
* Data-driven is akin to using only spatial coordinates, without explicitly using temporal coordinates.

## Inputs, Outputs, and References

* A Step is similar to a function; cross-level jumps are not allowed, so using the local stepName suffices.
* A Step has a global path formed by concatenating stepNames across levels: stepPath={stepName}/{stepName}
* SubTask provides basic reuse. It can build an independent taskRt or be embedded directly into the current taskRt for execution.
* The return type is normalized to Map, thereby automatically supporting multiple outputs. If only a single result value is returned, the variable name is RESULT.

## Design Details

1. The runtime model is separated from the definition DSL. The declarative model is compiled/transformed into the runtime model; the definition is not used directly as the runtime model.
2. The runtime model is a static, cacheable model and does not contain runtime state. Runtime state is separated into a dedicated TaskStepRuntime.
3. By default, no task queue is used for scheduling; complex scheduling techniques are not used in simple cases.
4. By default, no thread operations are involved. The asynchronous description already abstracts away all thread concepts.
5. Extensions are not done as a flat list of steps. We use layered abstractions and implement extensions via extType + xpl.
<!-- SOURCE_MD5:b7164d1edd49b8fd704c511f772309b3-->
