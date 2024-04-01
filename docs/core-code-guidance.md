# Nop平台核心代码阅读导引

Nop平台核心引擎的实现代码都很简短，一般模块的核心代码量都是5000行左右的量级，只有ORM比较复杂一些，1万多行。
虽然代码很短，实际实现的功能特性却很多，要把所有细节设计都介绍到，文档量还是不小。建议有问题可以先查看源码，核心特性都有单元测试支持，
可以通过调试单元测试来学习源码（直接在关键类中加断点，然后沿着堆栈向上看）。

Nop平台所有的设计都非常简单直接，一般核心代码集中在少数几个类中。

## 一. 可逆计算核心原理

1. ResourceComponentManager
   平台统一的DSL模型加载入口
2. DefaultVirtualFileSystem
   自动扫描classpath下所有`_vfs`目录中的文件，构成一个虚拟文件系统，并自动识别delta定制路径
3. ResourceLoadingCache
   根据虚拟文件路径缓存解析结果
4. ResourceDependsManager
   跟踪模型解析过程中发现的模型文件依赖关系，实现方式类似vue组件的动态依赖追踪，当文件被修改时，自动使得所有依赖该文件的解析结果失效
5. DslModelParser
   根据XDef元模型配置，使用DslBeanModelParser将XML解析为DSL模型对象
6. DslNodeLoader和XDslExtender
   在DslModelParser内部使用，加载资源文件，执行可逆计算理论中的x-extends算法，动态合成XNode节点对象
7. DeltaMerger和DeltaDiffer
   实现可逆计算理论中的x-extends算法和x-diff算法
8. DslModelToXNodeTransformer
   根据XDef元模型，将DSL模型对象转换为XNode节点

## 二. NopXLang

1. XLang
   XLang语言编译器的使用入口
2. XplCompiler
   XML格式的Xpl模板语言的编译器
3. XLangParseTreeParser/XLangASTBuildVisitor
   根据antlr自动生成的XScript脚本语言解析器，并自动将Antlr的ParseTree转换为XLangAST抽象语法树节点
4. XplLibTagCompiler
   Xpl模板语言中的标签库编译器
5. SimpleExprParser
   简单的嵌入表达式的解析器，不支持函数定义和复杂语句
6. LexicalScopeAnalysis
   对抽象语法树进行词法作用域分析，并执行宏函数
7. BuildExecutableProcessor
   将抽象语法树节点转换为可执行的IExecutableExpression可执行解释函数接口
8. XDefinitionParser
   XDef元模型解析器
9. DslNodeLoader
   解析XML文件，执行差量合并，合成得到最终的XNode
10. XDslValidator
    根据XDef元模型，验证XNode的合法性
11. DslModelParser
    根据XDef元模型，解析XNode得到DSL模型对象
12. ObjMetaToXDef/XDefToObjMeta
    实现XDefinition元模型和ObjMeta元数据模型之间的相互转换
13. DslModelToXNodeTransformer
    将DSL模型对象反向序列化为XNode
14. DslXNodeToJsonTransformer
    根据XDef元模型，将DSL对应的XNode节点转换为JSON对象
15. CompactXNodeToJsonTransformer/CompactJsonToXNodeTransformer
    在没有XDef元模型的情况下，通过约定type和body等少量属性，实现紧凑格式的XNode与JSON之间的相互转换

## 三. NopCodeGen

1. XCodeGenerator
   代码生成器的核心入口
2. CodeGenTask
   mvn install时通过exec-maven-plugin插件调用的代码生成器入口
3. CliGenCommand
   nop-cli命令行工具调用代码生成器的入口函数，内部使用XCodeGenerator实现
4. AstGrammarBuilder
   分析g4语法定义，转换为AstGrammar模型结构。根据ParseTree自动构造AST语法树时会使用AstGrammar所提供的信息
5. JdkJavaCompiler
   AOP代码生成时使用JDK内置的java编译器来编译Java文件为字节码
6. JavaObjMetaParser
   利用janino解析器，将后缀名为xjava的文件解析为ObjMeta对象。EqlAST和XLangAST等生成抽象语法树结构时都使用了这一机制。

## 四. NopIoC

1. BeanContainerImpl
   外部访问IoC容器的核心接口，负责管理所有的bean
2. AppBeanContainerLoader
   执行bean的自动发现功能。扫描所有模块下的autoconfig bean，以及满足`beans/app-*.beans.xml`模式的bean文件
3. BeanContainerBuilder
   根据收集到的所有bean配置，构建BeanContainer对象
4. BeanConditionEvaluator
   检查ioc:condition等动态条件，处理alias映射，剔除所有不满足条件的bean
5. BeanDefinitionBuilder
   处理`@Inject`注解等自动编配属性，初始化所有property的valueResolver
6. AopBeanProcessor
   将bean的创建类替换为Aop增强后的派生类，并注入interceptor配置
7. ConfigExpressionProcessor
   识别bean属性配置中的`@cfg:nop.server.port`和`${nop.server.port}`这种配置表达式，将它们转换为IValueResolver

## 五. NopConfig

1. DefaultConfigProvider
   外部使用配置管理引擎的核心入口
2. ConfigStarter
   配置管理引擎的核心逻辑，在这里详细定义了配置项加载顺序和覆盖关系
3. RouterConfigSource
   实现灰度配置发布
4. NacosConfigService
   基于Nacos实现从远程配置中心获取配置，并实现配置实时更新

## 六. NopDao

1. DaoProvider
   外部使用的获取IEntityDao的统一入口
2. DialectManager
   统一管理数据库方言模型
3. JdbcTemplateImpl
   类似于Spring中的JdbcTemplate，对JDBC访问请求的模板模式封装。内置数据缓存和多数据源支持
4. JdbcTransactionFactory
   对数据库事务的封装，支持柔性事务和异步处理
5. JdbcDataSet
   通用的DataSet结构封装，凡是表格类型的数据全部封装为统一的IDataSet接口

## 七. NopORM

1. OrmSessionImpl
   ORM引擎的核心功能入口，负责所有实体状态切换和一级缓存管理
2. OrmSessionEntityCache
   一级缓存的实现。从数据库中获取的所有数据都存放在一级缓存中。
3. OrmEntity
   实体对象的基类，记录实体和属性的修改状态
4. EntityPersisterImpl
   单实体对象的增删改查处理，识别全局缓存和多租户，内部使用EntityPersisterDriver来最终访问外部存储
5. CollectionPersisterImpl
   一对多集合对象的增删改查处理，识别全局缓存和多租户
6. JdbcEntityPersistDriver
   单实体对象的JDBC持久化实现
7. JdbcCollectionPersistDriver
   一对多集合对象的JDBC持久化实现
8. OrmBatchLoadQueue
   批量加载优化，解决JPA长期存在的N+1问题
9. JdbcQueryExecutor
   利用EqlCompiler将EQL对象查询语法转换为SQL语句并执行
10. EqlCompiler
    将EQL语法编译为SQL语句，使用EqlTransformVisitor将对象属性关联转换为表关联，AstToSqlGenerator根据转换后的AST语法树生成SQL语句
11. CascadeFlusher
    session.flush的时候会递归检查所有实体是否被修改，并自动生成对应的增删改查操作，放入BatchActionQueue执行队列，后者最终会调用EntityPersister
12. OrmTemplate
    类似于Spring中HibernateTemplate的模板模式封装
13. EntityDao
    在OrmTemplate的基础上，针对单个实体类的封装，功能类似于JpaRepository的加强版。一般业务开发中优先使用这个接口
14. SqlLibManager
    类似MyBatis的EQL和SQL语句管理器。SqlLibInvoker负责将Mapper接口方法调用转化为对SqlLibManager方法的调用

## 八. NopGraphQL

1. GraphQLEngine
   外部使用的GraphQL引擎核心入口
2. GraphQLDocumentParser
   手工编写的GraphQL语言解析器
3. GraphQLSelectionResolver
   验证前端发过来的GraphQL查询语句中的元素都具有合法定义
4. RpcSelectionSetBuilder
   REST调用模式下，如果前端不传送`@selection`参数，则这里自动添加所有非lazy的字段作为selection
5. GraphQLExecutor
   任务派发和结果选择的具体执行逻辑
6. BizObjectManager
   使用BizObjectBuilder动态组装BizObject业务对象。将BizObjet上的方法映射为GraphQLOperation，将BizObject的属性结构映射为GraphQLObjectDefinition。
7. BizObjectBuilder
   将XMeta文件、XBiz文件中的信息，以及BizModel Java类中的信息合成在一起，构成有一个完整的BizObject业务对象。
8. ReflectionBizModelBuilder和ReflectionGraphQLTypeFactory
   使用反射机制，根据Java类上的`@BizModel/@BizQuery`等注解构造GraphQL操作定义和类型定义
9. ObjMetaToGraphQLDefinition
   将xmeta元数据定义转换为GraphQL类型定义
10. BizModelToGraphQLDefinition
    将xbiz模型定义转换为GraphQL操作定义
11. GraphQLActionAuthChecker
    精确到字段级别的权限约束

## 九. NopRPC

1. ClusterRpcClient
   分布式RPC客户端调用的核心逻辑
2. ClusterRpcProxyFactoryBean/RpcInvocationHandler
   将RPC接口适配到ClusterRpcClient对象上的方法
3. HttpRpcService
   基于http协议实现RPC调用
4. MessageRpcClient
   基于单向发送和接收的消息队列实现RPC调用
5. SentinelFlowControlRunner
   集成Sentinel实现熔断限流
6. LoadBalanceServerChooser
   通过负载均衡算法选择服务实例
7. RouteServiceInstanceFilter
   识别消息头中的nop-svc-route配置，实现服务的灰度发布

## 十. NopGrpc

1. GrpcServer
   启动gRPC服务器，将GraphQL服务转换为gRPC服务对外暴露
2. GraphQLServerCallHandler
   将gRPC的服务端处理器转换为对GraphQLEngine的调用
3. ServiceSchemaManager
   根据GraphQL类型定义，动态生成protobuf格式的编解码器
4. GraphQLToApiModel
   将GraphQL服务定义转换为api.xdef元模型定义的Api模型

## 十一. NopReport

1. ReportEngine
   外部访问报表引擎的核心入口
2. ExcelWorkbookParser
   将xlsx文件解析为ExcelWorkbook对象，直接解析xlsx文件中的XML，不使用Apache POI包。
3. ExcelToXptModelTransformer
   将ExcelWorkbook转换为报表模型对象，
4. XptModelInitializer
   分析XptModel中父子单元格的关联关系，产生的分析结果保存在XptCellModel中
5. ExcelFormulaParser
   解析Excel公式语法，将它转换为ReportExpr
6. ReportExpressionParser
   为普通的表达式引擎增加报表层次坐标语法支持
7. ExpandedSheetGenerator
   执行报表引擎的核心逻辑，将ExcelSheet模板动态展开为ExpandedSheet，内部使用TableExpander实现表格展开
8. TableExpander/CellRowExpander/CellColExpander
   执行中国式报表的行列对称展开算法，构造ExpandedTable对象
9. DynamicReportDataSet
   在报表展开过程中，提供数据集封装，简化层次坐标和数据集数据转换操作
10. CellCoordinateHelper
    层次坐标表达式的核心执行逻辑
11. ExcelTemplate
    将展开后的ExpandedSheet导出为Excel文件，直接生成xml和xlsx文件，不使用POI库
12. HtmlTemplate
    将展开后的ExpandedSheet导出为Html文件
13. WordTemplate
    基于docx格式的Word模板生成Word文件，可以在Word中进行可视化编辑

## 十二. NopRule

1. RuleManager
   外部使用规则引擎的核心入口
2. ExecutableRule和ExecutableMatrixRule
   决策树和决策矩阵的具体执行逻辑
3. RuleExprParser
   手工编写的类似于FEEL（Friendly Enough Expression Language）的表达式语法解析器
4. DaoRuleModelLoader和DaoRuleModelSaver
   将规则模型保存到nop\_rule\_definition表中
5. RuleExcelModelParser
   从xlsx文件中解析规则模型，支持决策树和决策矩阵
6. RuleServiceImpl
   将规则引擎功能对外暴露为GraphQL和REST服务

## 十三. NopWorkflow

1. WorkflowManagerImpl
   外部访问工作流引擎的核心入口
2. WorkflowImpl
   从WorkflowManager中创建或者加载的工作流对象，它内部调用WorkflowEngine实现功能
3. WorkflowEngineImpl
   工作流引擎的核心实现逻辑
4. DaoWorkflowStore
   工作流的持久化存储
5. WfModelAnalyzer
   分析工作流模型配置的有效性，并剔除回退分支，构建出DAG有向无环图模型
6. DaoWfActorResolver
   适配外部用户、角色、部门管理和工作委托机制
7. WorkflowServiceImpl
   提供对外的GraphQL服务和REST服务

## 十四. NopBatch

1. BatchTaskBuilder
   负责创建BatchTask的工厂类。它负责组织skip/retry/transaction/process/listener的处理顺序
2. BatchTask
   批处理任务的核心逻辑，多线程并行执行，每个线程上分批次执行，每次处理一个chunk
3. RetryBatchConsumer
   为chunk处理增加失败重试逻辑
4. BatchProcessorConsumer
   逐条处理输入数据，并收集所有的输出数据到列表结构中，然后统一调用一次下游的consumer
5. PartitionDispatchLoader
   根据业务数据特征，将数据派发到不同的数据分区队列中进行处理
6. ResourceRecordLoader
   通用的数据文件加载器
7. ResourceRecordConsumer
   通用的数据文件输出机制
8. BatchGenLoader
   压力测试中使用的测试数据生成器，根据配置动态生成具有指定数据分布的测试数据

## 十五. NopJob

1. DefaultJobScheduler
   外部使用Job调度引擎的核心入口
2. TriggerBuilder
   定时触发器的构造逻辑
3. CalendarBuilder
   Calendar表达需要被的假期和指定时间区间
4. TriggerExecutorImpl
   Job定时调度的核心执行逻辑

## 十六. NopAutoTest

1. JunitAutoTestCase
   需要录制回放支持的单元测试从这个基类继承。注解中标注的NopJunitExtension提供了具体的与Junit集成支持
2. JunitBaseTestCase
   不需要录制回放功能，但是需要使用NopIoC注入bean的单元测试从这个基类继承
3. BaseTestCase
   最简单的，不需要NopIoC自动注入的测试用例可以从这个基类继承，它提供给了获取资源文件数据的一些便捷方法
4. AutoTestCaseDataBaseInitializer
   根据input目录下录制的数据自动初始化内存数据库
5. AutoTestCaseDataSaver
   将通过AutoTestOrmHook收集到的对数据库修改的内容保存到output目录下
6. AutoTestCaseResultChecker
   单元测试执行完之后，自动运行checker来检查执行结果与录制的数据内容是否匹配
7. AutoTestMatchChecker
   使用nop-match模块提供的前缀引导匹配语法来校验数据模式
8. AutoTestVars
   单元测试运行过程中产生的随机数等每次运行都变化的量需要在这个集合中注册，这样在录制的时候会把它们替换为变量名
