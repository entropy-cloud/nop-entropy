# 整体设计

1. TaskFlow是单次执行的核心引擎
2. Job为Task增加定时调度功能，单次直接触发不需要从Job进入
3. BatchTask是TaskFlow的一个步骤，相当于是扩展Task的某个Step存储信息。可以独立于TaskFlow运行。
4. BatchTask有专用的去重记录表。
5. 前端对于TaskFlow异步执行状态有一个统一的监控手段。
