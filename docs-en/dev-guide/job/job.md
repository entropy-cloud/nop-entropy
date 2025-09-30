# Overall Design

1. TaskFlow is the core engine for one-time execution.
2. Job adds scheduling capabilities to Task; one-time direct triggers do not need to go through Job.
3. BatchTask is a step within TaskFlow; it effectively extends the storage information of a specific Task step. It can run independently of TaskFlow.
4. BatchTask has a dedicated deduplication record table.
5. The frontend provides a unified mechanism to monitor the asynchronous execution status of TaskFlow.
<!-- SOURCE_MD5:fa7c7de363a2b50669dc57cce3045985-->
