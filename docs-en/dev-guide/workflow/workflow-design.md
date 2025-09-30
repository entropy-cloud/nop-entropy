# Workflow Design

## Basic Concepts

### Workflow Model (NopWfDefinition)
The workflow designer is responsible for designing the workflow model. After the workflow model is parsed, it yields an IWorkflowModel model object.

The workflow model is composed of multiple parts, including steps (Step), actions (Action), task assignment (Assignment), and transitions (Transition).

* Each step (Step) has an assignment (Assignment) configuration that specifies which participants (Actors) will take part in processing that step.
* Each step (Step) has an action (Action) configuration that specifies which actions (Action) can be executed when the workflow reaches that step.
* Executing an action results in a change in the step's state. When the state satisfies certain conditions, a transition (Transition) is triggered.
* A transition can have multiple "to" branches. Each branch can be configured with a condition (Condition), and the branch is taken only when its condition is satisfied.
* A "to" branch can specify the next step (Step).

```xml

<workflow>
  <actions>
    <action name="xx"/>
  </actions>

  <steps>
    <step name="yy">
      <assignment>
        <actors>
          <actor actorType="role" actorId="manager"/>
        </actors>
      </assignment>

      <transition onAppStates="complete">
        <to-step stepName="stepB">
          <when>
            <eq name="wfVars.argA" value="1" />
          </when>
        </to-step>
      </transition>
    </step>
  </steps>
</workflow>
```

### Workflow Instance (NopWfInstance)
A workflow instance is a runtime instance of the workflow model and is represented by an IWorkflow object.

## Step Instance (NopWfStepInstance)
A step instance is a step within a workflow instance and is represented by an IWorkflowStep object. When transitioning to a particular workflow step, the workflow retrieves the assignment (Assignment) configuration from the step model and creates a step instance for each Actor specified therein.

<!-- SOURCE_MD5:7dbc3c1ea00a4dc13079b41d905c93d0-->
