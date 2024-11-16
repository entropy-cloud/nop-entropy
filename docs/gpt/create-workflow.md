你是计算机专家，精通元模型、元数据等概念，你需要分析下面的需求描述信息，从中得到流程相关的工作流模型定义，要求返回结果必须满足以下元模型约束

* name和stepName使用英文，displayName使用中文

```xml

<workflow displayName="chinese">

    <description/>

    <start startStepName="english" >

    </start>

    <end>
    </end>


    <actions>
        <!--
          @local 是否局部操作，不导致本步骤结束
          @common 是否每个步骤都具有的公共操作
          @forActivated 是否在步骤处于活动状态时可调用
          @forHistory 是否在步骤处于历史状态时可调用
          @forWaiting 是否在工作流步骤处于等待状态时可调用
          @forFlowEnded 是否在工作流结束之后可调用
          @forReject 是否退回操作，退回操作可以没有配置步骤迁移
          @forWithdraw 是否是撤回操作, 撤回操作可以没有配置步骤迁移
        -->
        <action name="!english" displayName="chinese" local="!boolean=false" common="!boolean=false"
                internal="!boolean=false" group="string" sortOrder="!int=0"
                forActivated="!boolean=true" forHistory="!boolean=false" saveActionRecord="!boolean=true"
                forWaiting="!boolean=false" forReject="!boolean=false" forWithdraw="!boolean=false"
                forFlowEnded="!boolean=false">

            <description/>

            <when>condition-expr</when>

            <source>javascript-code</source>

            <!--
              @splitType 分支类型，and表示每个分支都执行，or表示从上至下执行，只执行第一个满足条件的迁移目标。缺省为and
            -->
            <transition splitType="and|or" onAppStates="csv-set">

                <to-step stepName="!string">
                      <when>conditon-expr</when>
                </to-step>

            </transition>

        </action>
    </actions>

    <steps>
        <!--
          @internal 标记为internal的步骤不会在界面中显示
          @optional 本步骤是否可选步骤，如果不是，则步骤出现异常时将导致异常向父节点传播，最终可能导致整个流程终止

        -->
        <step name="!english" displayName="chinese" waitSignals="csv-set"
              internal="!boolean=false"
              optional="!boolean=false"
              tagSet="csv-set" allowWithdraw="!boolean=false"
              allowReject="!boolean=false" dueAction="string">

            <!-- 如果不自动迁移，则必须有assignment -->
            <assignment>
               <actor actorName="string" actorType="role|user|group" actorId="string" />
            </assignment>

            <ref-actions>
                <ref-action name="!english"/>
            </ref-actions>

        </step>

    </steps>

</workflow>
```

需求描述如下：

```
 请设计一个员工转正申请流程，要求经过组长和经理审批。如果员工级别大于10级，还需要经过总经理审批。
```

流程设计需要满足上面的元模型要求，要求仅以XML格式返回。
