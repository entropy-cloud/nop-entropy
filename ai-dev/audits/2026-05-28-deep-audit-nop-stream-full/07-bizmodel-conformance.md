# 维度 07：BizModel 规范遵循

## 零发现说明

**检查范围**: 搜索 @BizModel, @BizQuery, @BizMutation, @BizLoader 注解和 CrudBizModel 继承。

**结论**: nop-stream 模块无 BizModel 类。该模块不使用 Nop 平台的 BizModel 服务层模式，而是提供自有的流处理 API（DataStream, Pattern, CEP 等）。服务层功能通过 StreamExecutionEnvironment 和算子接口暴露。

此维度不适用于 nop-stream 模块。
