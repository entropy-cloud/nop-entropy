
# Approval Flow

The functional description of the approval flow refers to the introduction document of the [FlowLong Workflow Engine](https://doc.flowlong.com/docs/preface).

## Conditional Branch
If the transition’s splitType is configured as or, then when transitioning steps only the first to node that satisfies the condition will be selected. If none of the to nodes satisfy the condition, an exception will be thrown.

## Parallel Branch
If the transition’s splitType is configured as and, then when transitioning steps all to nodes that satisfy the condition will be selected. After parallel branches, multiple branches can be joined together via a join node.

## Subprocess
When entering a subprocess node, the parent process step enters the waiting state. When the subprocess ends, the parent process resumes execution.

## Joint Sign-off
This means setting multiple people for the same approval node, such as A, B, and C. The three will handle simultaneously, and only after all agree can the approval proceed to the next node.

* A newly created stepInstance is in the activated state.
* After completing a stepInstance, check whether the other stepInstances in the same execGroup have all been completed. If so, trigger the transition to the next step.

## Voting Sign-off
This means setting multiple people for the same approval node, such as A, B, and C, and defining different weights for them respectively. When the voting weight ratio exceeds 50%, the process can enter the next node.

Other logic is basically the same as Joint Sign-off.

## Any-one Sign-off
In an approval node with multiple handlers, as long as any one person processes it, the process can enter the next node.

* A newly created stepInstance is in the activated state.
* After completing the stepInstance, automatically cancel the other steps in the same execGroup, then trigger the transition to the next step.

## Serial Sign-off
This means setting multiple people for the same approval node, such as A, B, and C. The three handle in order, i.e., A approves first; only after A submits can B approve; only after all agree can the approval proceed to the next node.

* A newly created stepInstance is in the waiting state, then it checks whether all stepInstances in the same execGroup with an execOrder less than its own have already finished. If yes, it changes to the activated state.
* After completing the stepInstance, automatically activate the stepInstance with the next execOrder. If it is the last stepInstance in the execGroup, trigger the transition.

## CC
Notify the approval result to designated personnel.

## Reject/Return
Reset the approval and send it to a certain node for re-approval. Rejection is also called return, and can be categorized as return to the applicant, return to the previous step, arbitrary return, etc.
In DingTalk workflow mode, all step nodes form a tree structure, and only one step will be executing at a time. A single step has multiple actors, and multiple actors can execute in parallel.
Therefore, the concept of rollback is quite clear: find the parent node on the tree structure, cancel all currently executing stepInstances, jump to the new step, and create the step instances.

## Forward
A forwards to B for approval; after B approves, enter the next node.

* Add a new actor within the same execGroup. The current step enters the TRANSFERRED state. When calculating the approval ratio, do not count this instance.

## Delegate
A delegates to B for approval; after B approves, it is transferred back to A; after A approves, enter the next node.

* The state of stepInstanceA changes to TRANSFERRED, then it waits for stepInstanceB to complete. After stepInstanceB completes, its state is COMPLETED, then stepInstanceA is reactivated.

## Proxy
After A designates B as the proxy, B can see A’s pending tasks. If A actively completes the task, B will no longer see it. If B completes the task, B should be able to see it.

## Jump
You can jump the current process instance to any handling node.

## Recall
Before the current handler has processed the document, allow the submitter of the previous step to perform a recall.

## Wake Up
Wake up historical tasks and re-enter the approval flow.

## Cancel
The process initiator can cancel the process, cancel all currently executing stepInstances. The entire process also enters the CANCELLED state.

## Add Signers
Allow the current handler to add handling personnel to the current handling node (pre-node, post-node).
* Add a new actor’s corresponding stepInstance within the current execGroup. You should check that there is no such actor among the active stepInstances.

## Remove Signers
Reduce handlers before the current handler operates.
* Cancel the stepInstance corresponding to the specified actor within the current execGroup.

## Append
Add or modify node handlers for any step.

* `step.changeOwner` and `step.changeActor` can change handlers.

## Claim
Claim public tasks.

* All users belonging to the specified actor can see the step instance, e.g., the actor is Role A. Claiming sets the owner to oneself.

## Mark as Read
Mark the step instance as read.

* Set `isRead=true`.

## Expedite
Notify the handlers of the current active task to process the task, using mechanisms outside the workflow system.

## Communicate
Communicate with the handlers of the current active task, executed outside the workflow system.

## Terminate
Terminate the process instance at any node.

## Schedule
Set a time point to execute the task and proceed to the next step.

## Trigger
Execute the business logic of the process trigger. Upon completion, proceed to the next step. Supports two implementations: [Immediate Trigger] and [Scheduled Trigger].

## Timeout Approval
According to the configured timeout approval time, automatically approve after timeout [auto approve or reject].

## Auto Reminder
According to the configured reminder time, remind approvers to approve [the number of reminders can be set]. Implement reminders via any interface/method [SMS, email, WeChat, DingTalk, etc].

## Execution Group execGroup

* Each step corresponds to a set of actors. Each time entering a new step, a stepInstance is created for each actor.
* These stepInstances form an execGroup.
* According to the creation order of stepInstances, an execOrder is automatically generated. This execOrder is used in Serial Sign-off.
* When transitioning to other steps, the engine automatically cancels all stepInstances in the execGroup.
* The step configuration needs to explicitly enable `useExecGroup=true`. Otherwise, the engine will not consider execGroup-specific handling logic internally and will treat each stepInstance individually.
* Ending a stepInstance has two meanings: ending the current step instance, and possibly ending the current execution group.

<!-- SOURCE_MD5:c4b4c25152b47f1332270fab34605af5-->
