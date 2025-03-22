# Overall Design

1. **TaskFlow is a single-execution core engine**  
2. **Jobs add scheduled execution functionality to Tasks, and single executions are triggered directly without needing to enter a Job**  
3. **BatchTask is a step in TaskFlow, equivalent to extending Task's Step for storing information. It can run independently of TaskFlow**  
4. **BatchTask has its own dedicated deduplication table**  
5. **The front-end for TaskFlow uses an asynchronous execution status with a unified monitoring mechanism**  
