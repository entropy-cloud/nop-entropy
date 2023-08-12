# Nop平台与SpringCloud的功能对比

Nop平台是根据可逆计算原理从零开始设计并实现的新一代的低代码平台，它的目标并不是针对少数固化的场景提供预置的开发脚手架和可视化设计工具，
而是打破描述式编程和传统命令式编程之间人为制造的藩篱，建立两者无缝相容的一种新的编程范式。不断扩大描述式编程所覆盖的语义空间。为了以最低的技术成本达到这一目标，
Nop平台没有采用目前业内主流的基础开源框架，而是选择了基于可逆计算原理重塑整个技术体系。本文将简单列举一下Nop平台所造的轮子，并与SpringCloud技术体系中现有的轮子做个对比。

| 轮子         | Nop体系         | Spring体系            |
|------------|---------------|---------------------|
| Web框架      | NopGraphQL    | SpringMVC           |
| 表达式引擎      | XLang         | SpringEL            |
| 模板引擎       | XLang Xpl     | Velocity/Freemarker |
| ORM引擎      | NopORM        | JPA/Mybatis         |
| IoC容器      | NopIoC        | SpringIoC           |
| 动态配置       | NopConfig     | SpringConfig        |
| 分布式事务      | NopTcc        | Alibaba Seata       |
| 自动化测试      | NopAutoTest   | SpringBootTest      |
| 分布式RPC     | NopRPC        | Feign RPC           |
| 报表引擎       | NopReport     | JasperReport        |
| 规则引擎       | NopRule       | Drools              | 
| 批处理引擎      | NopBatch      | SpringBatch         |
| 工作流引擎      | NopWorkflow   | Flowable/BPM        |
| 任务调度       | NopJob        | Quartz              |
| XML/JSON解析 | NopCore       | Jaxb/Jackson        |
| 资源抽象       | NopResource   | Spring Resource     |
| 代码生成器      | NopCodeGen    | 各类自制生成器             |
| IDE插件      | NopIdeaPlugin | Mybatis插件/Spring插件等 |


