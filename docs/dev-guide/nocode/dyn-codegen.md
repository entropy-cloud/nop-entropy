# 动态模型代码生成

## 动态模型
nop-dyn模块提供在线模型定义。在DynCodeGen类中，初始化的时候会自动读取dyn配置，并在内存中生成meta和biz定义。

如果启动的时候希望跳过模型生成，可以配置`nop.dyn.gen-code-when-init`为false。
