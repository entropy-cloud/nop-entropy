# Workflow Design

## Basic Concepts

### Workflow Definition (NopWfDefinition)
The workflow designer is responsible for designing the workflow definition. After analysis, it generates an IWorkflowModel model object.

The workflow definition consists of multiple components such as Step, Action, Assignment, and Transition.

* Each Step has an Assignment configuration, which specifies which Actors will participate in handling that step.
* Each Step has an Action configuration, indicating which Actions can be executed when the Step is reached.
* After executing an Action, it may cause a change in the Step's state. When the state meets certain conditions, a Transition is triggered.
* Transitions have multiple branches, each of which can be configured with a condition (Condition). Only when the condition is met will the branch be executed.
* Each branch can configure the next Step.

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
            <eq name="wfVars.argA" value="1"/>
          </when>
        </to-step>
      </transition>
    </step>
  </steps>
</workflow>
```

### Workflow Instance (NopWfInstance)
The workflow instance is an execution instance of the workflow model. The workflow instance is represented by the IWorkflow object.

## Step Instance (NopWfStepInstance)
A step instance is a step within a workflow instance. The step instance is represented by the IWorkflowStep object. When transitioning to a certain workflow step, it retrieves the assignment configuration from the workflow step model, and for each specified Actor, creates a corresponding step instance.
