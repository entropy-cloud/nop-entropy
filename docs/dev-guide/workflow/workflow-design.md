# 工作流设计

## 基本概念

### 工作流模型(NopWfDefinition)
工作流设计器负责设计工作流模型，工作流模型解析后得到IWorkflowModel模型对象。

工作流模型有步骤（Step），动作(Action)，任务分配(Assignment)，迁移(Transition)等多个部分组成。

* 每个步骤(Step)具有分配(Assignment)配置，它指定哪些参与者(Actor)会参与该步骤的处理
* 每个步骤(Step)具有动作(Action)配置，它指定执行到该步骤时可以执行哪些动作（Action)
* 执行动作后导致步骤状态变化，状态满足一定的条件后会触发迁移(Transition)
* 迁移具有多个to分支，每个分支上可以配置判断条件(Condition)，满足条件才会执行此分支。
* to分支上可以配置下一步骤(Step)

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

### 工作流实例(NopWfInstance)
工作流实例是工作流模型的一个运行实例，工作流实例由IWorkflow对象表示。

## 步骤实例(NopWfStepInstance)
步骤实例是工作流实例中的一个步骤，步骤实例由IWorkflowStep对象表示。迁移到某个工作流步骤时，会获取工作流步骤模型上的工作分配配置(Assignment)，为其中指定的每一个Actor创建一个步骤实例。


