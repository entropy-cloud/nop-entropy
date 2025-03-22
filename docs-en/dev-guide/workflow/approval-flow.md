# Approval Flow

The description of the approval flow is based on the introduction document of [FlowLong workflow engine](https://doc.flowlong.com/docs/preface).

## Conditional Branch
When the `splitType` of a transition is set to "or", only the first condition that is met will be selected. If no conditions are met, an exception will be thrown.

## Parallel Branch
When the `splitType` of a transition is set to "and", all conditions that are met will be selected. Parallel branches can be combined using a join node.

## Subflow
Entering a subflow node causes the parent process to enter a waiting state. When the subflow completes, the parent process resumes execution.

## Signatory
In the same approval node, multiple people can be set, such as A, B, and C. All three will handle the task simultaneously. Only after all have approved will the next step be triggered.

* New `stepInstance` is in `activated` state
* After completing `stepInstance`, check if all `execGroup` members' `stepInstance` have completed. If so, trigger the next move.

## Voting
In the same approval node, multiple people can be set, such as A, B, and C, each with different weights. If the voting weight percentage exceeds 50%, the task can proceed to the next node.

Other logic for signature and voting is similar.

## Or Signatory
An approval node can have multiple handlers. Any handler completing their task will allow the process to move forward.

* New `stepInstance` is in `activated` state
* After completing `stepInstance`, check if all `execGroup` members' `stepInstance` have completed. If so, trigger the next move.

## Sequential Signatory
In the same approval node, multiple people can be set, such as A, B, and C, to handle tasks in sequence. Only after A approves and submits, B can approve, and only then will the process proceed.

* New `stepInstance` is in `waiting` state
* After completing `stepInstance`, check if all `execGroup` members' `stepInstance` have completed in order. If so, trigger the next move.

## Assignment
The approval result is notified to the specified person.

## Rejection
The approval result is resent to the node for re-approval. A rejection can also be referred to as a refusal, which may involve rolling back to previous steps or any number of steps backward.

In the DingDing workflow pattern, all step nodes form a tree structure, and each time only one step is executed.

Thus, the concept of rollback is quite clear: find the parent node in the tree structure and cancel all currently executing `stepInstance`, then jump to a new step.

## Transition
A transition from A to B approval. After B approves, the process moves to the next node.

* Add a new `actor` to the same `execGroup`
* The current step enters `TRANSFERRED` state
* No calculation of the agreement ratio is performed for this example

## Delegation
A transitions to B approval, then to A again. After A approves, the process moves to the next node.

* `stepInstanceA` changes to `TRANSFERRED` state
* Wait for `stepInstanceB` to complete
* Then reactivate `stepInstanceA`

## Proxy
A delegates to B, allowing B to see A's pending tasks. If A completes a task actively, B will no longer see it.

## Jump
The current process instance can be jumped to any execution node.

## Recall
Before handling a file, allow the previous node to submit again.

## Wakeup
Wake up historical tasks and re-enter the approval flow.

## Abandonment
The initiator of the process can abandon the process. All currently executing `stepInstance` will be canceled. The entire process enters the `CANCELLED` state.

## Signature Addition
Allow the current handling node to add or modify the participants (predecessor, successor) as needed.

* Add a new `actor` corresponding to the `stepInstance`
* No calculation of the agreement ratio is performed in this example

## Signature Deletion
Remove participants before handling.

* Remove the specified `actor` from the same `execGroup`

## Addition
Add or modify participants for any step.

* `step.changeOwner` and `step.changeActor` can be used to modify participants

## Recognition
Public tasks are claimed by participants.

* Participants belonging to a specific `actor` group can see the task instance, e.g., actor A

## Readiness
Mark a task instance as read.

* Set `isRead=true`

## Initiation
Initiate tasks outside the workflow system using mechanisms.

## Communication
Communicate with task handlers outside the workflow system.

## Termination
Terminate any executing process instance at any node.

## Scheduling


## Trigger
Implement the business logic of the trigger. After execution, proceed to the next step. Supports two types of triggers: Immediate Trigger and Scheduled Trigger.


## Timeout Approval
Automatically approve or reject based on the configured timeout setting after the specified time has elapsed.


## Automatic Reminder
Send reminders at the configured intervals. The system will notify the approver via SMS, email, WeChat, DingDing, etc., with the remaining number of days for approval.


## Execution Group execGroup


- Each step corresponds to a group of actors.
- When entering a new step, a `stepInstance` is created for each actor.
- These `stepInstances` form an `execGroup`.
- The order of creating `stepInstances` determines the order of generating `execOrder`.
- Moving to other steps automatically cancels all `stepInstances` in the current `execGroup`.
- In step configuration, enable `useExecGroup=true`. If not enabled, the engine will treat each `stepInstance` as independent and ignore any group-specific logic.


There are two meanings when a stepInstance ends:
1. The current step instance is completed.
2. The current execution group may be terminated, depending on the context.

