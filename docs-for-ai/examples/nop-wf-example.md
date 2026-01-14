# NopWf 项目示例

## 概述

本文档提供NopWf项目的完整实现示例，展示如何使用Nop Platform构建工作流管理系统，包括工作流定义、流程实例管理、任务管理等功能。

## 项目结构

```
nop-wf/
├── nop-wf.orm.xml              # 数据库模型定义（model目录使用模块名）
├── nop-wf.api.xml              # API模型定义
├── src/
│   ├── main/java/io/nop/wf/
│   │   ├── dao/                # DAO接口
│   │   ├── domain/             # 实体类
│   │   ├── service/            # 服务实现
│   │   │   ├── NopWfDefinitionBizModel.java     # 流程定义业务模型
│   │   │   ├── NopWfInstanceBizModel.java      # 流程实例业务模型
│   │   │   ├── NopWfTaskBizModel.java          # 任务业务模型
│   │   │   └── NopWfEngineBizModel.java         # 工作流引擎
│   │   └── web/                # Web控制器
│   └── main/resources/
│       └── _vfs/               # 虚拟文件系统（_vfs目录下使用app.orm.xml等命名）
│           ├── app/            # 应用配置
│           │   └── app.orm.xml # ORM模型定义
│           ├── biz/            # 业务模型
│           ├── wf/             # 工作流定义
│           └── xlib/           # 扩展库
└── pom.xml                      # Maven配置
```

## 数据库模型设计

### 1. 流程定义表 (nop_wf_definition)

```xml
<entity name="NopWfDefinition" table="nop_wf_definition">
  <field name="definitionId" type="string" primary="true" length="32" />
  <field name="processName" type="string" required="true" length="100" />
  <field name="processCode" type="string" required="true" length="50" unique="true" />
  <field name="processVersion" type="int" required="true" defaultValue="1" />
  <field name="processType" type="string" length="20" />
  <field name="processDef" type="clob" />
  <field name="description" type="string" length="500" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="deployTime" type="datetime" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />

  <relation name="instances" type="one-to-many" target="NopWfInstance" />
</entity>
```

### 2. 流程实例表 (nop_wf_instance)

```xml
<entity name="NopWfInstance" table="nop_wf_instance">
  <field name="instanceId" type="string" primary="true" length="32" />
  <field name="definitionId" type="string" length="32" />
  <field name="processName" type="string" length="100" />
  <field name="processCode" type="string" length="50" />
  <field name="processVersion" type="int" />
  <field name="businessKey" type="string" length="100" />
  <field name="starter" type="string" length="50" />
  <field name="status" type="string" length="20" />
  <field name="startTime" type="datetime" />
  <field name="endTime" type="datetime" />
  <field name="variables" type="clob" />

  <relation name="definition" type="many-to-one" target="NopWfDefinition" />
  <relation name="tasks" type="one-to-many" target="NopWfTask" />
</entity>
```

### 3. 任务表 (nop_wf_task)

```xml
<entity name="NopWfTask" table="nop_wf_task">
  <field name="taskId" type="string" primary="true" length="32" />
  <field name="instanceId" type="string" length="32" />
  <field name="taskName" type="string" required="true" length="100" />
  <field name="taskCode" type="string" length="50" />
  <field name="taskType" type="string" length="20" />
  <field name="assignee" type="string" length="50" />
  <field name="candidateUsers" type="string" length="500" />
  <field name="candidateGroups" type="string" length="500" />
  <field name="status" type="string" length="20" />
  <field name="priority" type="int" defaultValue="5" />
  <field name="createTime" type="datetime" />
  <field name="dueTime" type="datetime" />
  <field name="claimTime" type="datetime" />
  <field name="completeTime" type="datetime" />
  <field name="variables" type="clob" />

  <relation name="instance" type="many-to-one" target="NopWfInstance" />
</entity>
```

## 工作流引擎实现

### 1. 工作流引擎业务模型

```java
package io.nop.wf.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.support.OrmEntity;
import io.nop.wf.domain.NopWfDefinition;
import io.nop.wf.domain.NopWfInstance;
import io.nop.wf.domain.NopWfTask;
import io.nop.wf.engine.WorkflowEngine;
import io.nop.wf.engine.ProcessInstance;
import io.nop.wf.engine.Task;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.nop.wf.WfErrors.*;

@BizModel("NopWfEngine")
public class NopWfEngineBizModel {

    @Inject
    private IOrmEntityDao<NopWfDefinition> definitionDao;

    @Inject
    private IOrmEntityDao<NopWfInstance> instanceDao;

    @Inject
    private IOrmEntityDao<NopWfTask> taskDao;

    @Inject
    private WorkflowEngine workflowEngine;

    /**
     * 部署流程定义
     */
    @BizMutation
    @Transactional
    public NopWfDefinition deployDefinition(@Name("processCode") String processCode,
                                              @Name("processDef") String processDef) {
        // 检查流程定义是否已存在
        NopWfDefinition existing = definitionDao.findFirst(
            FilterBean.eq(NopWfDefinition.PROP_NAME_processCode, processCode)
        );

        if (existing != null) {
            // 更新版本
            existing.setProcessVersion(existing.getProcessVersion() + 1);
            existing.setProcessDef(processDef);
            existing.setDeployTime(new Date());
            existing.setUpdateTime(new Date());
            return definitionDao.updateEntity(existing);
        } else {
            // 创建新流程定义
            NopWfDefinition definition = new NopWfDefinition();
            definition.setDefinitionId(StringHelper.generateUUID());
            definition.setProcessCode(processCode);
            definition.setProcessName(processCode); // 默认使用code作为name
            definition.setProcessVersion(1);
            definition.setProcessDef(processDef);
            definition.setStatus(1);
            definition.setDeployTime(new Date());
            definition.setCreateTime(new Date());
            definition.setUpdateTime(new Date());
            return definitionDao.saveEntity(definition);
        }
    }

    /**
     * 启动流程实例
     */
    @BizMutation
    @Transactional
    public NopWfInstance startProcess(@Name("processCode") String processCode,
                                       @Name("businessKey") String businessKey,
                                       @Name("starter") String starter,
                                       @Name("variables") Map<String, Object> variables) {
        // 获取流程定义
        NopWfDefinition definition = getLatestDefinition(processCode);
        if (definition == null) {
            throw new NopException(ERR_WF_DEFINITION_NOT_FOUND)
                .param(ARG_PROCESS_CODE, processCode);
        }

        // 创建流程实例
        NopWfInstance instance = new NopWfInstance();
        instance.setInstanceId(StringHelper.generateUUID());
        instance.setDefinitionId(definition.getDefinitionId());
        instance.setProcessName(definition.getProcessName());
        instance.setProcessCode(definition.getProcessCode());
        instance.setProcessVersion(definition.getProcessVersion());
        instance.setBusinessKey(businessKey);
        instance.setStarter(starter);
        instance.setStatus("running");
        instance.setStartTime(new Date());

        if (variables != null && !variables.isEmpty()) {
            instance.setVariables(JsonTool.instance().serialize(variables));
        }

        instance = instanceDao.saveEntity(instance);

        // 启动工作流引擎
        ProcessInstance processInstance = workflowEngine.startProcess(
            instance.getInstanceId(),
            definition.getProcessDef(),
            variables
        );

        // 创建初始任务
        createTasks(instance, processInstance);

        return instance;
    }

    /**
     * 完成任务
     */
    @BizMutation
    @Transactional
    public void completeTask(@Name("taskId") String taskId,
                              @Name("assignee") String assignee,
                              @Name("variables") Map<String, Object> variables) {
        // 获取任务
        NopWfTask task = taskDao.getEntityById(taskId);
        if (task == null) {
            throw new NopException(ERR_WF_TASK_NOT_FOUND)
                .param(ARG_TASK_ID, taskId);
        }

        // 验证任务状态
        if (!"pending".equals(task.getStatus())) {
            throw new NopException(ERR_WF_TASK_NOT_PENDING)
                .param(ARG_TASK_ID, taskId);
        }

        // 验证任务负责人
        if (!assignee.equals(task.getAssignee())) {
            throw new NopException(ERR_WF_TASK_NOT_ASSIGNEE)
                .param(ARG_TASK_ID, taskId)
                .param(ARG_ASSIGNEE, assignee);
        }

        // 更新任务状态
        task.setStatus("completed");
        task.setCompleteTime(new Date());

        if (variables != null && !variables.isEmpty()) {
            task.setVariables(JsonTool.instance().serialize(variables));
        }

        taskDao.updateEntity(task);

        // 获取流程实例
        NopWfInstance instance = instanceDao.getEntityById(task.getInstanceId());
        if (instance == null) {
            throw new NopException(ERR_WF_INSTANCE_NOT_FOUND)
                .param(ARG_INSTANCE_ID, task.getInstanceId());
        }

        // 完成任务
        Task wfTask = new Task();
        wfTask.setTaskId(task.getTaskId());
        wfTask.setTaskCode(task.getTaskCode());
        wfTask.setTaskName(task.getTaskName());
        wfTask.setAssignee(task.getAssignee());

        ProcessInstance processInstance = workflowEngine.completeTask(
            instance.getInstanceId(),
            wfTask,
            variables
        );

        // 创建下一节点任务
        createTasks(instance, processInstance);

        // 检查流程是否完成
        if (processInstance.isEnded()) {
            instance.setStatus("completed");
            instance.setEndTime(new Date());
            instanceDao.updateEntity(instance);
        }
    }

    /**
     * 认领任务
     */
    @BizMutation
    @Transactional
    public void claimTask(@Name("taskId") String taskId,
                           @Name("assignee") String assignee) {
        // 获取任务
        NopWfTask task = taskDao.getEntityById(taskId);
        if (task == null) {
            throw new NopException(ERR_WF_TASK_NOT_FOUND)
                .param(ARG_TASK_ID, taskId);
        }

        // 验证任务状态
        if (!"pending".equals(task.getStatus())) {
            throw new NopException(ERR_WF_TASK_NOT_PENDING)
                .param(ARG_TASK_ID, taskId);
        }

        // 验证任务候选人
        if (!isCandidate(task, assignee)) {
            throw new NopException(ERR_WF_TASK_NOT_CANDIDATE)
                .param(ARG_TASK_ID, taskId)
                .param(ARG_ASSIGNEE, assignee);
        }

        // 认领任务
        task.setAssignee(assignee);
        task.setClaimTime(new Date());
        taskDao.updateEntity(task);
    }

    /**
     * 委派任务
     */
    @BizMutation
    @Transactional
    public void delegateTask(@Name("taskId") String taskId,
                              @Name("fromAssignee") String fromAssignee,
                              @Name("toAssignee") String toAssignee) {
        // 获取任务
        NopWfTask task = taskDao.getEntityById(taskId);
        if (task == null) {
            throw new NopException(ERR_WF_TASK_NOT_FOUND)
                .param(ARG_TASK_ID, taskId);
        }

        // 验证任务负责人
        if (!fromAssignee.equals(task.getAssignee())) {
            throw new NopException(ERR_WF_TASK_NOT_ASSIGNEE)
                .param(ARG_TASK_ID, taskId)
                .param(ARG_ASSIGNEE, fromAssignee);
        }

        // 委派任务
        task.setAssignee(toAssignee);
        taskDao.updateEntity(task);
    }

    /**
     * 获取待办任务
     */
    @BizQuery
    public List<NopWfTask> getTodoTasks(@Name("assignee") String assignee) {
        return taskDao.findAll(
            FilterBean.and(
                FilterBean.eq(NopWfTask.PROP_NAME_assignee, assignee),
                FilterBean.eq(NopWfTask.PROP_NAME_status, "pending")
            )
        );
    }

    /**
     * 获取待认领任务
     */
    @BizQuery
    public List<NopWfTask> getCandidateTasks(@Name("userId") String userId) {
        // 查询候选人包含该用户的任务
        List<NopWfTask> tasks1 = taskDao.findAll(
            FilterBean.and(
                FilterBean.eq(NopWfTask.PROP_NAME_status, "pending"),
                FilterBean.like(NopWfTask.PROP_NAME_candidateUsers, "%" + userId + "%")
            )
        );

        // 查询候选组包含该用户组的任务
        // TODO: 实现组查询逻辑

        return tasks1;
    }

    /**
     * 获取流程实例
     */
    @BizQuery
    public NopWfInstance getInstance(@Name("instanceId") String instanceId) {
        return instanceDao.getEntityById(instanceId);
    }

    /**
     * 获取流程实例任务
     */
    @BizQuery
    public List<NopWfTask> getInstanceTasks(@Name("instanceId") String instanceId) {
        return taskDao.findAll(
            FilterBean.eq(NopWfTask.PROP_NAME_instanceId, instanceId)
        );
    }

    /**
     * 终止流程实例
     */
    @BizMutation
    @Transactional
    public void terminateInstance(@Name("instanceId") String instanceId) {
        NopWfInstance instance = instanceDao.getEntityById(instanceId);
        if (instance == null) {
            throw new NopException(ERR_WF_INSTANCE_NOT_FOUND)
                .param(ARG_INSTANCE_ID, instanceId);
        }

        if ("completed".equals(instance.getStatus())) {
            throw new NopException(ERR_WF_INSTANCE_COMPLETED)
                .param(ARG_INSTANCE_ID, instanceId);
        }

        // 终止流程
        workflowEngine.terminateInstance(instanceId);

        // 更新实例状态
        instance.setStatus("terminated");
        instance.setEndTime(new Date());
        instanceDao.updateEntity(instance);

        // 终止所有待办任务
        taskDao.updateByFilter(
            FilterBean.and(
                FilterBean.eq(NopWfTask.PROP_NAME_instanceId, instanceId),
                FilterBean.eq(NopWfTask.PROP_NAME_status, "pending")
            ),
            NopWfTask.PROP_NAME_status,
            "terminated"
        );
    }

    /**
     * 获取最新流程定义
     */
    private NopWfDefinition getLatestDefinition(String processCode) {
        return definitionDao.findFirst(
            FilterBean.eq(NopWfDefinition.PROP_NAME_processCode, processCode)
        );
    }

    /**
     * 创建任务
     */
    private void createTasks(NopWfInstance instance, ProcessInstance processInstance) {
        List<Task> tasks = processInstance.getCurrentTasks();
        for (Task task : tasks) {
            NopWfTask wfTask = new NopWfTask();
            wfTask.setTaskId(StringHelper.generateUUID());
            wfTask.setInstanceId(instance.getInstanceId());
            wfTask.setTaskName(task.getTaskName());
            wfTask.setTaskCode(task.getTaskCode());
            wfTask.setTaskType(task.getTaskType());
            wfTask.setAssignee(task.getAssignee());
            wfTask.setStatus("pending");
            wfTask.setPriority(task.getPriority());
            wfTask.setCreateTime(new Date());

            if (task.getVariables() != null && !task.getVariables().isEmpty()) {
                wfTask.setVariables(JsonTool.instance().serialize(task.getVariables()));
            }

            taskDao.saveEntity(wfTask);
        }
    }

    /**
     * 检查是否为候选人
     */
    private boolean isCandidate(NopWfTask task, String userId) {
        if (task.getCandidateUsers() != null) {
            String[] candidates = task.getCandidateUsers().split(",");
            for (String candidate : candidates) {
                if (userId.equals(candidate.trim())) {
                    return true;
                }
            }
        }

        // TODO: 检查候选组

        return false;
    }
}
```

### 2. 工作流定义示例

```xml
<process name="leaveApproval" xmlns="http://nop-xlang.github.io/schema/wf.xdef">
  <startEvent id="start">
    <outgoing>toManagerApproval</outgoing>
  </startEvent>

  <userTask id="managerApproval" name="经理审批">
    <incoming>fromStart</incoming>
    <outgoing>toHrApproval</outgoing>
    <assignee>${manager}</assignee>
    <candidateGroups>${managerGroups}</candidateGroups>
  </userTask>

  <userTask id="hrApproval" name="HR审批">
    <incoming>fromManagerApproval</incoming>
    <outgoing>toEnd</outgoing>
    <assignee>${hr}</assignee>
  </userTask>

  <endEvent id="end">
    <incoming>fromHrApproval</incoming>
  </endEvent>

  <sequenceFlow id="toManagerApproval" sourceRef="start" targetRef="managerApproval" />
  <sequenceFlow id="toHrApproval" sourceRef="managerApproval" targetRef="hrApproval" />
  <sequenceFlow id="toEnd" sourceRef="hrApproval" targetRef="end" />
</process>
```

## 测试用例

### 1. 工作流引擎测试

```java
package io.nop.wf.service;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.wf.domain.NopWfDefinition;
import io.nop.wf.domain.NopWfInstance;
import io.nop.wf.domain.NopWfTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NopWfEngineBizModelTest extends JunitBaseTestCase {

    @Inject
    private NopWfEngineBizModel wfEngine;

    @Test
    public void testDeployDefinition() {
        String processDef = "<process name=\"testProcess\" xmlns=\"http://nop-xlang.github.io/schema/wf.xdef\">" +
                           "</process>";

        NopWfDefinition definition = wfEngine.deployDefinition("testProcess", processDef);

        assertNotNull(definition);
        assertNotNull(definition.getDefinitionId());
        assertEquals("testProcess", definition.getProcessCode());
        assertEquals(1, definition.getProcessVersion());
    }

    @Test
    public void testStartProcess() {
        // 部署流程定义
        String processDef = "<process name=\"leaveApproval\" xmlns=\"http://nop-xlang.github.io/schema/wf.xdef\">" +
                           "</process>";
        wfEngine.deployDefinition("leaveApproval", processDef);

        // 启动流程实例
        Map<String, Object> variables = new HashMap<>();
        variables.put("manager", "user001");
        variables.put("hr", "user002");

        NopWfInstance instance = wfEngine.startProcess("leaveApproval", "biz001", "user003", variables);

        assertNotNull(instance);
        assertNotNull(instance.getInstanceId());
        assertEquals("leaveApproval", instance.getProcessCode());
        assertEquals("running", instance.getStatus());
    }

    @Test
    public void testCompleteTask() {
        // 部署并启动流程
        String processDef = "<process name=\"testProcess\" xmlns=\"http://nop-xlang.github.io/schema/wf.xdef\">" +
                           "</process>";
        wfEngine.deployDefinition("testProcess", processDef);

        Map<String, Object> variables = new HashMap<>();
        NopWfInstance instance = wfEngine.startProcess("testProcess", "biz001", "user001", variables);

        // 获取待办任务
        List<NopWfTask> tasks = wfEngine.getInstanceTasks(instance.getInstanceId());
        assertTrue(tasks.size() > 0);

        // 完成任务
        NopWfTask task = tasks.get(0);
        wfEngine.completeTask(task.getTaskId(), task.getAssignee(), null);
    }
}
```

## 最佳实践

1. **流程定义**: 使用BPMN 2.0标准定义流程
2. **任务分配**: 支持单人、多人、组等任务分配方式
3. **流程监控**: 提供流程实例和任务的监控功能
4. **异常处理**: 处理流程执行过程中的异常
5. **事务管理**: 使用@Transactional保证数据一致性
6. **性能优化**: 合理使用缓存提高性能
7. **审计日志**: 记录流程操作的审计日志

## 总结

NopWf项目展示了如何使用Nop Platform构建工作流管理系统：

1. **流程定义**: 定义和管理流程定义
2. **流程实例**: 管理流程实例的生命周期
3. **任务管理**: 处理任务的创建、认领、完成等操作
4. **工作流引擎**: 实现工作流执行引擎
5. **GraphQL API**: 自动生成GraphQL查询和变更

遵循这些模式，可以快速构建灵活、可靠的工作流管理系统。

## 相关文档

- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [GraphQL服务开发指南](../getting-started/api/graphql-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
