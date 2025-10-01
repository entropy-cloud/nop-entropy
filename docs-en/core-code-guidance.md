# Nop Platform Core Code Reading Guide

The implementation code of the Nop platform core engine is quite concise. The core code size of a typical module is around 5,000 lines; only the ORM is somewhat more complex, with over 10,000 lines.
Despite the brevity of the code, it implements many features. Fully documenting all design details still takes substantial space. If you have questions, it’s recommended to first check the source code; all core features are covered by unit tests.
You can learn the source by debugging unit tests (set breakpoints in key classes and walk up the stack).

All designs in the Nop platform are very simple and straightforward, and the core code is generally concentrated in a small number of classes.

## I. Core Principles of Reversible Computation

1. ResourceComponentManager
   Unified entry point for loading DSL models across the platform
2. DefaultVirtualFileSystem
   Automatically scans all `_vfs` directories on the classpath to form a virtual file system, and automatically recognizes Delta customization paths
3. ResourceLoadingCache
   Caches parsing results based on virtual file paths
4. ResourceDependsManager
   Tracks model file dependencies discovered during model parsing; implemented similarly to dynamic dependency tracking in Vue components. When a file changes, all parsing results that depend on it are automatically invalidated
5. DslModelParser
   Based on the XDef meta-model configuration, uses DslBeanModelParser to parse XML into a DSL model object
6. DslNodeLoader and XDslExtender
   Used inside DslModelParser; load resource files, execute the x-extends algorithm from Reversible Computation theory, and dynamically synthesize XNode nodes
7. DeltaMerger and DeltaDiffer
   Implement the x-extends and x-diff algorithms from Reversible Computation theory
8. DslModelToXNodeTransformer
   Converts DSL model objects to XNode nodes according to the XDef meta-model

## II. NopXLang

1. XLang
   Entry point for using the XLang language compiler
2. XplCompiler
   Compiler for the XML-based Xpl template language
3. XLangParseTreeParser/XLangASTBuildVisitor
   Based on the ANTLR auto-generated XScript language parser; automatically converts ANTLR’s ParseTree into XLangAST abstract syntax tree nodes
4. XplLibTagCompiler
   Tag library compiler for the Xpl template language
5. SimpleExprParser
   A simple embedded expression parser; does not support function definitions or complex statements
6. LexicalScopeAnalysis
   Performs lexical scope analysis on the abstract syntax tree and executes macro functions
7. BuildExecutableProcessor
   Converts AST nodes into executable IExecutableExpression interpreter functions
8. XDefinitionParser
   XDef meta-model parser
9. DslNodeLoader
   Parses XML files, performs Delta merges, and synthesizes the final XNode
10. XDslValidator
    Validates XNode legality according to the XDef meta-model
11. DslModelParser
    Parses XNode into a DSL model object according to the XDef meta-model
12. ObjMetaToXDef/XDefToObjMeta
    Implements mutual conversion between the XDefinition meta-model and the ObjMeta metadata model
13. DslModelToXNodeTransformer
    Serializes a DSL model object back to XNode
14. DslXNodeToJsonTransformer
    Converts the XNode corresponding to a DSL into a JSON object according to the XDef meta-model
15. CompactXNodeToJsonTransformer/CompactJsonToXNodeTransformer
    Without an XDef meta-model, achieves conversion between compact-form XNode and JSON via conventions on a small set of attributes such as type and body

## III. NopCodeGen

1. XCodeGenerator
   Core entry point for the code generator
2. CodeGenTask
   Entry used by exec-maven-plugin during mvn install to invoke the code generator
3. CliGenCommand
   Entry function for invoking the code generator from the nop-cli command-line tool; internally uses XCodeGenerator
4. AstGrammarBuilder
   Analyzes g4 grammar definitions and converts them into the AstGrammar model. AstGrammar provides information used when automatically constructing the AST from a ParseTree
5. JdkJavaCompiler
   Uses the JDK’s built-in Java compiler to compile Java files into bytecode during AOP code generation
6. JavaObjMetaParser
   Uses the Janino parser to parse files with the .xjava suffix into ObjMeta objects. EqlAST, XLangAST, and others use this mechanism to generate abstract syntax tree structures.

## IV. NopIoC

1. BeanContainerImpl
   Core interface for external access to the IoC container; manages all beans
2. AppBeanContainerLoader
   Performs automatic bean discovery. Scans autoconfig beans across all modules and bean files matching the `beans/app-*.beans.xml` pattern
3. BeanContainerBuilder
   Builds the BeanContainer object from all collected bean configurations
4. BeanConditionEvaluator
   Checks dynamic conditions such as ioc:condition, processes alias mappings, and prunes all beans that do not meet the conditions
5. BeanDefinitionBuilder
   Handles auto-wiring properties such as the `@Inject` annotation and initializes valueResolvers for all properties
6. AopBeanProcessor
   Replaces the bean’s creation class with an AOP-enhanced derived class and injects interceptor configurations
7. ConfigExpressionProcessor
   Recognizes configuration expressions such as `@cfg:nop.server.port` and `${nop.server.port}` in bean property configurations and converts them into IValueResolver

## V. NopConfig

1. DefaultConfigProvider
   Core entry point for external use of the configuration management engine
2. ConfigStarter
   Core logic of the configuration management engine; defines the load order and override precedence of configuration items in detail
3. RouterConfigSource
   Implements canary configuration rollout
4. NacosConfigService
   Based on Nacos; retrieves configuration from a remote configuration center and supports real-time updates

## VI. NopDao

1. DaoProvider
   Unified entry point for external retrieval of IEntityDao
2. DialectManager
   Centralized management of database dialect models
3. JdbcTemplateImpl
   Similar to Spring’s JdbcTemplate; template wrapper for JDBC access with built-in data caching and multi-datasource support
4. JdbcTransactionFactory
   Encapsulation of database transactions; supports flexible transactions and asynchronous processing
5. JdbcDataSet
   Generic DataSet structure encapsulation: all tabular data is wrapped under a unified IDataSet interface

## VII. NopORM

1. OrmSessionImpl
   Core entry point of the ORM engine; responsible for all entity state transitions and first-level cache management
2. OrmSessionEntityCache
   Implementation of the first-level cache. All data fetched from the database is stored in the first-level cache.
3. OrmEntity
   Base class for entity objects; records modification state of entities and their properties
4. EntityPersisterImpl
   CRUD handling for single-entity objects; aware of global cache and multi-tenancy. Internally uses EntityPersisterDriver to access external storage
5. CollectionPersisterImpl
   CRUD handling for one-to-many collection objects; aware of global cache and multi-tenancy
6. JdbcEntityPersistDriver
   JDBC persistence implementation for single-entity objects
7. JdbcCollectionPersistDriver
   JDBC persistence implementation for one-to-many collection objects
8. OrmBatchLoadQueue
   Batch loading optimization to solve the longstanding N+1 problem in JPA
9. JdbcQueryExecutor
   Uses EqlCompiler to transform EQL object query syntax into SQL and execute it
10. EqlCompiler
    Compiles EQL syntax into SQL; uses EqlTransformVisitor to convert object property associations into table joins; AstToSqlGenerator generates SQL from the transformed AST
11. CascadeFlusher
    On session.flush, recursively checks all entities for modifications, automatically generates the corresponding CRUD operations, enqueues them into BatchActionQueue for execution, which ultimately calls EntityPersister
12. OrmTemplate
    Template wrapper similar to Spring’s HibernateTemplate
13. EntityDao
    Built atop OrmTemplate for a single entity class; an enhanced equivalent of JpaRepository. This is the preferred interface in typical business development
14. SqlLibManager
    Manager for EQL and SQL statements similar to MyBatis. SqlLibInvoker maps Mapper interface method calls to SqlLibManager method calls

## VIII. NopGraphQL

1. GraphQLEngine
   Core entry point for the GraphQL engine used externally
2. GraphQLDocumentParser
   A hand-written parser for the GraphQL language
3. GraphQLSelectionResolver
   Validates that all elements in the client-submitted GraphQL query have valid definitions
4. RpcSelectionSetBuilder
   In REST invocation mode, if the client does not pass the `@selection` parameter, automatically adds all non-lazy fields as the selection here
5. GraphQLExecutor
   Concrete execution logic for task dispatch and result selection
6. BizObjectManager
   Uses BizObjectBuilder to dynamically assemble BizObject business objects. Maps methods on BizObject to GraphQLOperation and the BizObject property structure to GraphQLObjectDefinition.
7. BizObjectBuilder
   Merges information from XMeta files, XBiz files, and BizModel Java classes to form a complete BizObject business object.
8. ReflectionBizModelBuilder and ReflectionGraphQLTypeFactory
   Uses reflection to construct GraphQL operation and type definitions based on annotations such as `@BizModel/@BizQuery` on Java classes
9. ObjMetaToGraphQLDefinition
   Converts xmeta metadata definitions to GraphQL type definitions
10. BizModelToGraphQLDefinition
    Converts xbiz model definitions to GraphQL operation definitions
11. GraphQLActionAuthChecker
    Field-level authorization constraints

## IX. NopRPC

1. ClusterRpcClient
   Core logic of the distributed RPC client
2. ClusterRpcProxyFactoryBean/RpcInvocationHandler
   Adapts RPC interfaces to methods on the ClusterRpcClient object
3. HttpRpcService
   Implements RPC calls over HTTP
4. MessageRpcClient
   Implements RPC calls using message queues based on one-way send and receive
5. SentinelFlowControlRunner
   Integrates Sentinel to implement circuit breaking and rate limiting
6. LoadBalanceServerChooser
   Selects service instances via load balancing algorithms
7. RouteServiceInstanceFilter
   Detects the nop-svc-route configuration in message headers to enable canary release of services

## X. NopGrpc

1. GrpcServer
   Starts the gRPC server and exposes GraphQL services as gRPC services
2. GraphQLServerCallHandler
   Converts gRPC server handlers into calls to the GraphQLEngine
3. ServiceSchemaManager
   Dynamically generates protobuf encoders/decoders based on GraphQL type definitions
4. GraphQLToApiModel
   Converts GraphQL service definitions to the Api model defined by the api.xdef meta-model

## XI. NopReport

1. ReportEngine
   Core entry point for external access to the reporting engine
2. ExcelWorkbookParser
   Parses xlsx files into ExcelWorkbook objects by reading the XML inside xlsx directly; does not use Apache POI.
3. ExcelToXptModelTransformer
   Converts ExcelWorkbook into report model objects
4. XptModelInitializer
   Analyzes parent-child cell relationships in XptModel; the analysis results are stored in XptCellModel
5. ExcelFormulaParser
   Parses Excel formula syntax and converts it to ReportExpr
6. ReportExpressionParser
   Adds hierarchical coordinate syntax for reports to a standard expression engine
7. ExpandedSheetGenerator
   Executes the core logic of the reporting engine, dynamically expands an ExcelSheet template into an ExpandedSheet; internally uses TableExpander for table expansion
8. TableExpander/CellRowExpander/CellColExpander
   Performs the row/column symmetric expansion algorithm common in Chinese-style reports to construct an ExpandedTable
9. DynamicReportDataSet
   Provides dataset encapsulation during report expansion to simplify transformations between hierarchical coordinates and dataset data
10. CellCoordinateHelper
    Core execution logic for hierarchical coordinate expressions
11. ExcelTemplate
    Exports the expanded ExpandedSheet to an Excel file; directly generates XML and XLSX without using the POI library
12. HtmlTemplate
    Exports the expanded ExpandedSheet to an HTML file
13. WordTemplate
    Generates Word files based on docx-format templates, allowing visual editing in Word

## XII. NopRule

1. RuleManager
   Core entry point for external use of the rule engine
2. ExecutableRule and ExecutableMatrixRule
   Execution logic for decision trees and decision matrices
3. RuleExprParser
   A hand-written expression syntax parser similar to FEEL (Friendly Enough Expression Language)
4. DaoRuleModelLoader and DaoRuleModelSaver
   Persist rule models to the nop_rule_definition table
5. RuleExcelModelParser
   Parses rule models from xlsx files; supports decision trees and decision matrices
6. RuleServiceImpl
   Exposes the rule engine as GraphQL and REST services

## XIII. NopWorkflow

1. WorkflowManagerImpl
   Core entry point for external access to the workflow engine
2. WorkflowImpl
   Workflow objects created or loaded from WorkflowManager; internally delegates to WorkflowEngine for functionality
3. WorkflowEngineImpl
   Core implementation logic of the workflow engine
4. DaoWorkflowStore
   Persistent storage for workflows
5. WfModelAnalyzer
   Analyzes the validity of workflow model configurations, prunes rollback branches, and constructs a DAG (Directed Acyclic Graph) model
6. DaoWfActorResolver
   Adapts to external user, role, department management, and delegation mechanisms
7. WorkflowServiceImpl
   Provides external GraphQL and REST services

## XIV. NopBatch

1. BatchTaskBuilder
   Factory responsible for creating BatchTask. Orchestrates the processing order of skip/retry/transaction/process/listener
2. BatchTask
   Core logic of batch jobs; multi-threaded parallel execution; on each thread, runs in batches, processing one chunk at a time
3. RetryBatchConsumer
   Adds retry-on-failure logic to chunk processing
4. BatchProcessorConsumer
   Processes input records one by one, collects all outputs into a list, then invokes the downstream consumer once
5. PartitionDispatchLoader
   Dispatches data to different partition queues for processing based on business data characteristics
6. ResourceRecordLoader
   Generic data file loader
7. ResourceRecordConsumer
   Generic data file output mechanism
8. BatchGenLoader
   Test data generator used in stress testing; dynamically generates test data with specified distributions based on configuration

## XV. NopJob

1. DefaultJobScheduler
   Core entry point for external use of the Job scheduling engine
2. TriggerBuilder
   Construction logic for scheduled triggers
3. CalendarBuilder
   Builds calendars to express holidays and designated time intervals
4. TriggerExecutorImpl
   Core execution logic for job scheduling

## XVI. NopAutoTest

1. JunitAutoTestCase
   Unit tests that need record/replay support inherit from this base class. The NopJunitExtension specified in the annotation provides concrete integration with JUnit
2. JunitBaseTestCase
   Unit tests that do not need record/replay but do require NopIoC bean injection inherit from this base class
3. BaseTestCase
   The simplest base class for tests that do not require NopIoC auto-injection; provides convenient methods for accessing resource file data
4. AutoTestCaseDataBaseInitializer
   Automatically initializes the in-memory database from recorded data under the input directory
5. AutoTestCaseDataSaver
   Saves database modifications collected by AutoTestOrmHook into the output directory
6. AutoTestCaseResultChecker
   After unit tests complete, automatically runs a checker to verify that execution results match the recorded data
7. AutoTestMatchChecker
   Uses the prefix-guided matching syntax provided by the nop-match module to validate data patterns
8. AutoTestVars
   Values that change on every run (such as random numbers) must be registered in this collection so they can be replaced with variable names during recording

<!-- SOURCE_MD5:a1c7c931fa2b9f8bdc0ddcee6219b74f-->
