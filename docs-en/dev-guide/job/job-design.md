# Distributed Task Scheduling

## Terminology

- **Workflow Definition**: A visual DAG formed by dragging task nodes and establishing associations between them.

- **Workflow Instance**: An instantiation of a workflow definition, which can be created by manual start or scheduled trigger. Each run of a workflow definition produces one workflow instance.

- **Task Instance**: An instantiation of a task node within a workflow definition, representing a specific task.

- **Task Types**: Currently supports SHELL, SQL, SUB_PROCESS (sub-process), PROCEDURE, MR, SPARK, PYTHON, DEPENDENT (dependency), and plans to support dynamic plugin extensions. Note: SUB_PROCESS-type tasks must be associated with another workflow definition; the associated workflow definition can be started and executed independently.

- **Scheduling Methods**: The system supports scheduled execution based on cron expressions and manual scheduling. Supported command types include: Start workflow, Start from current node, Resume a fault-tolerant workflow, Resume a paused workflow, Start from failed node, Backfill, Schedule, Re-run, Pause, Stop, Resume waiting threads. Among these, Resume a fault-tolerant workflow and Resume waiting threads are controlled internally by the scheduler and cannot be invoked externally.

- **Scheduled Execution**: The system supports visual generation of cron expressions.

- **Dependencies**: The system not only supports simple predecessor/successor dependencies in a DAG, but also provides task dependency nodes, enabling custom task dependencies across workflows.

- **Priority**: Supports priority for workflow instances and task instances. If not set, FIFO is used by default.

- **Email Alerts**: Supports emailing SQL task query results, workflow instance result alerts, and fault-tolerance alert notifications.

- **Failure Strategy**: For tasks running in parallel, if any task fails, two strategies are provided. Continue means regardless of the status of parallel tasks, processing continues until the workflow ends in failure. End means that once a failed task is detected, all running parallel tasks are killed and the workflow ends in failure.

- **Backfill**: Backfill historical data, supporting two modes—parallel and serial backfill within a range. Date selection includes date ranges and date enumeration.

## Execution Strategies

* Parallel: If multiple workflow instances exist for the same workflow definition at the same time, execute the instances in parallel.

* Serial Wait: If multiple workflow instances exist for the same workflow definition at the same time, execute the instances serially.

* Serial Drop: If multiple workflow instances exist for the same workflow definition at the same time, discard the later-created workflow instances and kill the instance currently running.

* Serial Priority: If multiple workflow instances exist for the same workflow definition at the same time, execute the instances serially according to priority.

## Workflow Parameters

Description of workflow runtime parameters:

* Failure Strategy: When a task node fails, the strategy for other parallel task nodes. “Continue” means other task nodes continue normally after one task fails; “End” means terminate all running tasks and end the entire workflow.

* Notification Strategy: When the workflow ends, send notification emails based on the workflow status: send none; send on success; send on failure; send on success or failure.

* Workflow Priority: The runtime priority of the workflow, with five levels: HIGHEST, HIGH, MEDIUM, LOW, LOWEST. When master thread count is insufficient, higher-priority workflows are executed first in the queue; workflows with the same priority execute in FIFO order.

* Worker Grouping: This workflow can only run on the specified group of worker machines. The default is Default, meaning it can execute on any worker.

* Notification Group: When the chosen notification strategy triggers, on timeout alerts, or when fault tolerance occurs, workflow information or email is sent to all members of the notification group.

* Startup Parameters: Set or override global parameter values when starting a new workflow instance.

* Backfill: Run the workflow definition over a specified date range, generating corresponding workflow instances according to the backfill strategy. Backfill strategies include Serial Backfill and Parallel Backfill.
  Dates can be selected via the UI or entered manually. The date range is a closed interval (startDate <= N <= endDate).
  Serial Backfill: Within the specified time range, execute backfill sequentially from the start date to the end date, generating multiple workflow instances in order. Click Run Workflow, select Serial Backfill mode: for example, execute sequentially from July 9 to July 10, generating two workflow instances on the workflow instance page.
  Degree of Parallelism: In parallel backfill mode, the maximum number of instances to run concurrently.

## MasterServer

MasterServer is mainly responsible for DAG task partitioning, task submission monitoring, and also monitors the health of other MasterServers and WorkerServers.

Internal functionalities of MasterServer:

1. JobScheduler triggers tasks on schedule
2. JobWorkflowRunner is responsible for DAG task partitioning and task submission monitoring

## WorkerServer

WorkerServer is mainly responsible for task execution and providing logging services.

1. JobTaskBizModel provides capabilities to start, stop, and view the status of tasks
2. LoggerBizModel provides viewing, refreshing, and downloading of log segments

## Backfill Logic

1. Serial Backfill: Within the specified time range, execute backfill sequentially from the start date to the end date, generating only one workflow instance;
2. Parallel Backfill: Within the specified time range, backfill multiple days concurrently, generating N workflow instances

<!-- SOURCE_MD5:124dc48aa656ab0c6942158fb6d6c2b8-->
