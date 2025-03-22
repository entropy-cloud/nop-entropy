# Distributed Task Scheduling


## Glossary of Terms

- **Process Definition**: Created by dragging and linking task nodes to form a visual DAG (Directed Acyclic Graph).
- **Process Instance**: An instance of a Process Definition that can be manually started or scheduled at a specific time. Each execution of the Process Definition generates one Process Instance.
- **Task Instance**: The instantiation of a task node within a Process Definition, identifying a specific task.
- **Task Type**: Currently supports SHELL, SQL, SUB PROCESS (Subprocess), PROCEDURE, MR (MapReduce), SPARK, PYTHON, and DEPENDENT. Additionally, dynamic plugin expansion is planned.
  - Note: SUB PROCESS type tasks require linking another Process Definition, which can be independently started.
- **Scheduling Mode**: Supports cron-based scheduling and manual scheduling. The command types include:
  - Starting the workflow,
  - Resuming a failed task,
  - Recovering a suspended workflow,
  - Restarting a failed workflow,
  - Suspending a running workflow,
  - Stopping a running workflow,
  - Resuming a waiting thread.
  - Among these, RESUME_FAILED and SUSPEND are controlled internally; external calls cannot directly invoke them.

- **Cron Scheduling**: Supports cron expression visualization.
- **Dependency**: The system supports not only simple DAG dependencies (predecessor-successor relationships) but also custom task dependencies. It allows defining dependencies between tasks and processes.
- **Priority**: Supports prioritizing Process Instances and Task Instances. If priority is not set, the default behavior is FIFO (First-In-First-Out).
- **Email Alerts**: Supports sending email notifications for task results and exceptions, including both successful and failed executions.
- **Failure Strategy**: For parallel tasks, two failure handling strategies are available:
  - Continue with other running tasks until the workflow completes.
  - Terminate all running tasks upon encountering a failure.

- **Retries**: Supports retries based on time intervals or specific conditions. The system includes both serial and parallel retry modes.
- **Time Zones**: Supports time zone-aware scheduling via cron expressions.


## Execution Strategies

- **Parallel Execution**: If multiple instances of the same Process Definition are running, tasks are executed in parallel.
- **Serial Execution with Wait**: If multiple instances of the same Process Definition are running, tasks are executed one after another, waiting for each to complete before moving to the next.
- **Abandon on Failure**: If a task fails, it is abandoned, and execution continues with the next task or workflow.
- **Priority-Based Serial Execution**: Tasks are executed in priority order; higher-priority tasks start first.


## Workflow Parameters


## Workflow Execution Parameters

- **Failure Strategy**: 
  - If a single task fails, other running parallel tasks continue until the workflow completes.
  - If all tasks fail, the workflow terminates.
- **Notification Strategy**: Notifications can be sent for any state change (success, failure, suspension, etc.).
- **Priority**: Can be set at both Process and Task levels. Default is FIFO.


## Worker Parameters

- **Worker Group**: A workflow can only run in a specific worker group. Default behavior allows execution on any worker.
- **Notification Group**: Used to send notifications to all members of the selected group when certain events occur (e.g., cron expiration, task failure).


## Start Parameters

- **Job Parameters**: These include parameters that affect how tasks are started, such as:
  - Task parameters,
  - Time zones,
  - Maximum retries,
  - Recovery options.



- **Cron Expression**: Customizable via the UI or API.
- **Time Window**: Define a start and end time for task execution.



MasterServer is responsible for managing DAG tasks, submitting them to WorkerServers, and monitoring their health status. It also listens for health updates from other MasterServers in the system.



1. JobScheduler: Triggers jobs at specified times.
2. JobWorkflowRunner: Manages task execution and workflow orchestration.



WorkerServer is responsible for executing tasks and providing logging services.



1. JobTaskBizModel: Handles task execution, stopping, and status queries.
2. LoggerBizModel: Manages log data collection, refreshing, and retrieval.

1. **Serial Numbering**: Within the specified time range, from the start date to the end date, serially execute numberings, generating only one process instance;
2. **Parallel Numbering**: Within the specified time range, perform parallel numberings on multiple days, generating N process instances
