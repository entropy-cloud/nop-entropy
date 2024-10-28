# 审批流

审批流的功能描述参考了[FlowLong工作流引擎](https://doc.flowlong.com/docs/preface)的介绍文档。

## 条件分支
配置transition的splitType为or，则转移步骤时只有第一个满足条件的to节点会被选择。如果没有任何一个to节点满足条件，则会抛出异常

## 并行分支
配置transition的splitType为and，则转移步骤时所有满足条件的to节点都会被选择。并行分支后可以通过join节点将多个分支汇聚在一起。

## 子流程
进入子流程节点，父流程步骤会进入waiting状态。子流程结束，父流程会恢复执行。

## 会签
指同一个审批节点设置多个人，如A、B、C三人，三人会同时处理，需全部同意之后，审批才可到下一审批节点。

* 新建stepInstance处于activated状态
* 完成stepInstance后检查同一execGroup的其他stepInstance是否都已经完成，如果都完成，则触发到下一步的迁移

## 票签
指同一个审批节点设置多个人，如A、B、C三人，分别定义不同的权重，当投票权重比例大于 50% 就能进入下一个节点。

其他逻辑和会签基本相同

## 或签
一个审批节点里有多个处理人，任意一个人处理后就能进入下一个节点

* 新建stepInstance处于activated状态
* 完成stepInstance后自动取消同一execGroup中的其他步骤，然后触发到下一步的迁移

## 串签
指同一个审批节点设置多个人，如A、B、C三人，三人按顺序处理，即A先审批，A提交后B才能审批，需全部同意之后，审批才可到下一审批节点

* 新建stepInstance处于waiting状态，然后检查同一execGroup中所有execOrder小于自己的stepInstance是否已经结束，如果是，则转变为activated状态。、
* 完成stepInstance后，自动激活下一个execOrder的stepInstance。如果是execGroup中的最后一个stepInstance，则触发迁移。

## 抄送
将审批结果通知给指定人员

## 驳回
将审批重置发送给某节点，重新审批。驳回也叫退回，也可以分退回申请人、退回上一步、任意退回等
在钉钉工作流模式下，所有的步骤节点构成一个树结构，而且每次只会有一个步骤在执行。一个步骤具有多个actor，多个actor可以并行执行。
所以回退的概念比较明确，就是找到Tree结构上的父节点，然后取消所有当前执行的stepInstance，跳转到新的步骤，并建立步骤实例即可。

## 转办
A转给B审批，B审批后，进入下一节点

* 同一个execGroup分组增加一个新的actor，当前步骤进入TRANSFERRED状态，计算同意比例时并不计算此实例

## 委派
A转给B审批，B审批后，转给A，A审批后进入下一节点

* stepInstanceA的状态转换为TRANSFERRED，然后等待stepInstanceB完成，stepInstanceB完成后状态为COMPLETED，然后重新激活stepInstanceA。

## 代理
A指定代理人B之后，B就可以看到A的待办任务。如果是A主动完成了任务，则B就看不到了。如果是B完成的任务，则B应该可以看到。

## 跳转
可以将当前流程实例跳转到任意办理节点

## 拿回
在当前办理人尚未处理文件前，允许上一节点提交人员执行拿回

## 唤醒
历史任务唤醒，重新进入审批流程

## 撤销
流程发起者可以对流程进行撤销处理，取消所有当前正在执行的stepInstance。整个流程也进入CANCELLED状态。

## 加签
允许当前办理人根据需要自行增加当前办理节点的办理人员（前置节点，后置节点）
* 在当前execGroup中增加新的actor对应的stepInstance。应该需要检查活跃stepInstance中没有这个actor。

## 减签
在当前办理人操作之前减少办理人
* 在当前execGroup中取消指定actor对应的stepInstance

## 追加
为任意步骤增加、修改节点处理人

* `step.changeOwner`和`step.changeActor`可以改变处理人

## 认领
公共任务认领

* 属于指定actor的所有user都可以看到步骤实例，比如说actor为角色A。认领是将owner设置为自己

## 已阅
将步骤实例标记为已阅状态

* 设置`isRead=true`

## 催办
通知当前活动任务处理人办理任务，调用工作流系统之外的机制实现。

## 沟通
与当前活动任务处理人沟通，在工作流系统之外执行。

## 终止
在任意节点终止流程实例

## 定时
设置时间节点定时执行任务进入下一步

## 触发
执行流程触发器业务逻辑实现，结束执行进入下一步，支持【立即触发】【定时触发】两种实现

## 超时审批
根据设置的超时审批时间，超时后自动审批【自动通过或拒绝】

## 自动提醒
根据设置的提醒时间，提醒审批人审批【可设定提醒次数】实现接口任意方式提醒【短信，邮件，微信，钉钉等】、


## 执行分组 execGroup

* 每个步骤step对应一组actor，每次进入一个新步骤step，会为每个actor都建立一个stepInstance。
* 这些stepInstance构成一个execGroup。
* 按照stepInstance的创建顺序，会自动生成一个execOrder。在串签的时候会用到这个execOrder。
* 迁移到其他步骤时，引擎会自动取消execGroup中的所有stepInstance。
* step配置中需要明确启用`useExecGroup=true`。否则引擎内部不会考虑execGroup特定的处理逻辑，只会把每个stepInstance单独看待
* stepInstance结束的时候存在两个含义：结束当前步骤实例，以及有可能结束当前执行分组。


