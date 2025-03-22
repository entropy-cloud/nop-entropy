# Nop Platform Core Code Reading Guide

The implementation code of the Nop platform's core engine is very concise. The core code of typical modules is around 5,000 lines long, with ORM being somewhat more complex at approximately 10,000 lines.

Although the code is brief, its functionality encompasses numerous details that must be thoroughly documented. If you encounter issues or need clarification, it is recommended to examine the source code directly, as unit tests and debugging will provide valuable insights into its internal workings.

The Nop platform's design is straightforward, with core functionalities concentrated within a limited number of key classes.

## 1. Reverse Computing Core Principles

### 1.1 ResourceComponentManager
- **Platform**: A unified DSL model loading entry point.
- **Description**: Manages the loading and processing of resource components based on the DSL model.

### 1.2 DefaultVirtualFileSystem
- **Description**: Constructs a virtual file system by scanning all `_vfs` directories, automatically identifying custom paths (`delta` paths) and incorporating them into the structure.

### 1.3 ResourceLoadingCache
- **Functionality**: Caches parsing results based on virtual file paths for efficient reuse.

### 1.4 ResourceDependsManager
- **Functionality**: Tracks dependencies between model files during parsing, automatically invalidating cached results when dependent files are modified (similar to Vue's dynamic dependency tracking).

### 1.5 DslModelParser
- **Description**: Parses XDef metadata configurations and converts XML into DSL model objects.

### 1.6 DslNodeLoader 和 XDslExtender
- **Functionality**: Loads resource files within the DslModelParser, applying reverse computing algorithms (e.g., x-extends) to dynamically construct XNode nodes.

### 1.7 DeltaMerger 和 DeltaDiffer
- **Functionality**: Implements delta computation algorithms for reverse computing (`x-extends` and `x-diff`).

### 1.8 DslModelToXNodeTransformer
- **Description**: Transforms DSL model objects into XNode nodes based on XDef metadata.

## 2. NopXLang

### 2.1 XLang
- **Functionality**: Entry point for the XLang language compiler.
- **Description**: Processes templates written in XLang, a markup language that integrates XML and template logic.

### 2.2 XplCompiler
- **Functionality**: Compiles Xpl templates into executable code.

### 2.3 XLangParseTreeParser / XLangASTBuildVisitor
- **Functionality**: Automatically generates the XScript parser from ANTLR-generated trees, converting Antlr's ParseTree into XLangAST nodes.

### 2.4 XplLibTagCompiler
- **Functionality**: Compiles Xpl template logic, including custom tags defined in libraries.

### 2.5 SimpleExprParser
- **Functionality**: Parses simple expressions without support for function definitions or complex statements.

### 2.6 LexicalScopeAnalysis
- **Functionality**: Analyzes the abstract syntax tree (AST) to perform lexical and scope analysis, also supporting macro functions.

### 2.7 BuildExecutableProcessor
- **Functionality**: Converts AST nodes into executable expressions, implementing the IExecutableExpression interface.

### 2.8 XDefinitionParser
- **Functionality**: Parses definitions defined in XLang templates.

### 2.9 DslNodeLoader
- **Functionality**: Loads XML files and merges deltas to construct final XNodes.

### 2.10 XDslValidator
- **Functionality**: Validates the legality of XNodes based on XDef metadata.

### 2.11 DslModelParser
- **Functionality**: Parses XDef metadata to generate DSL model objects from XNodes.

### 2.12 ObjMetaToXDef / XDefToObjMeta
- **Functionality**: Converts between XDefinition and ObjMeta (metadata) formats, enabling bidirectional transformation.

### 2.13 DslModelToXNodeTransformer
- **Functionality**: Transforms DSL model objects into XNodes.

### 2.14 DslXNodeToJsonTransformer
- **Functionality**: Converts XNodes into JSON format based on XDef metadata.

### 2.15 CompactXNodeToJsonTransformer / CompactJsonToXNodeTransformer
- **Functionality**: Handles compact formats for converting between XNodes and JSON, especially when XDef metadata is unavailable.

## 3. NopCodeGen

### 3.1 XCodeGenerator
- **Functionality**: Core entry point for code generation.

### 3.2 CodeGenTask
- **Functionality**: Facilitates code generation via Maven plugins (e.g., `exec-maven-plugin`).

### 3.3 CliGenCommand
- **Functionality**: Command-line interface for code generation, utilizing the XCodeGenerator core.

### 3.4 AstGrammarBuilder
- **Functionality**: Parses g4 grammar definitions and constructs AST structures based on them.

### 3.5 JdkJavaCompiler
- **Functionality**: Uses the JDK's built-in Java compiler to compile Java source files into bytecode, suitable for AOP use cases.

6. JavaObjMetaParser  
   Utilizes the Janino parser to convert suffixes like "xjava" into `ObjMeta` objects. Both EqlAST and XLangAST generate abstract syntax trees using this mechanism.

## Four. NopIoC  

1. BeanContainerImpl  
   Acts as the core interface for accessing the IoC container, managing all beans.  
2. AppBeanContainerLoader  
   Handles the discovery of beans. Scans modules for autoconfig beans and those matching `beans/app-*.beans.xml`.  
3. BeanContainerBuilder  
   Constructs the `BeanContainer` object based on collected bean configurations.  
4. BeanConditionEvaluator  
   Checks dynamic conditions like `ioc:condition`, handles alias mappings, and removes beans that don't meet criteria.  
5. BeanDefinitionBuilder  
   Processes `@Inject` annotations for automatic wiring, initializing all `valueResolver`s.  
6. AopBeanProcessor  
   Replaces the bean's creation class with an enhanced AOP class, injecting interceptor configurations.  
7. ConfigExpressionProcessor  
   Identifies configuration expressions like `@cfg:nop.server.port` and `${nop.server.port}`, converting them into `IValueResolver`.  

## Five. NopConfig  

1. DefaultConfigProvider  
   Serves as the entry point for the configuration management engine.  
2. ConfigStarter  
   Implements the core logic of the configuration engine, defining the order and precedence of configuration items.  
3. RouterConfigSource  
   Manages gray-scale deployments.  
4. NacosConfigService  
   Retrieves configurations from a remote configuration center using Nacos, supporting real-time updates.  

## Six. NopDao  

1. DaoProvider  
   Provides a unified entry point for `IEntityDao`.  
2. DialectManager  
   Manages database dialects.  
3. JdbcTemplateImpl  
   Mimics Spring's `JdbcTemplate`, encapsulating JDBC operations and supporting data caching and multiple data sources.  
4. JdbcTransactionFactory  
   Wraps database transactions, supporting flexible transaction management and asynchronous processing.  
5. JdbcDataSet  
   Encapsulates data from various sources into a standardized `IDataSet` interface.  

## Seven. NopORM  

1. OrmSessionImpl  
   Handles ORM functionality as the core entry point for state management and level 1 caching.  
2. OrmSessionEntityCache  
   Implements level 1 caching. All queried data is stored in this cache.  
3. OrmEntity  
   Serves as the base class for entities, tracking their modified states.  
4. EntityPersisterImpl  
   Manages CRUD operations for single entities, identifying global and multi-tenant caches, and using `EntityPersisterDriver` to access external storage.  
5. CollectionPersisterImpl  
   Handles one-to-many relationships, managing CRUD operations and recognizing global and multi-tenant caching.  
6. JdbcEntityPersistDriver  
   Implements JDBC persistence for single entities.  
7. JdbcCollectionPersistDriver  
   Manages persistence for one-to-many relationships using JDBC.  
8. OrmBatchLoadQueue  
   Optimizes batch loading to address the N+1 problem inherent in JPA.  
9. JdbcQueryExecutor  
   Utilizes `EqlCompiler` to convert EQL queries into SQL statements and execute them.  
10. EqlCompiler  
    Compiles EQL into SQL statements, using `EqlTransformVisitor` to translate object relationships into table relationships and `AstToSqlGenerator` to generate SQL from the AST.  
11. CascadeFlusher  
    Recursively checks for modified entities during `session.flush()`, generating corresponding CRUD operations and adding them to `BatchActionQueue`. The queue is then processed by `EntityPersister`.  
12. OrmTemplate  
    Mimics Spring's HibernateTemplate, encapsulating ORM operations.  
13. EntityDao  
    Builds on `OrmTemplate` to provide entity-specific operations, acting as an enhanced version of `JpaRepository`. It is preferred for general business development.  
14. SqlLibManager  


## Eight. NopGraphQL

1. GraphQLEngine
   External usage of the GraphQL engine's core entry point.
2. GraphQLDocumentParser
   Handwritten GraphQL language parser.
3. GraphQLSelectionResolver
   Verifies that all elements in the incoming GraphQL query are legally defined.
4. RpcSelectionSetBuilder
   In REST call mode, if the frontend does not send the `@selection` parameter, this automatically adds all non-lazy fields as selection.
5. GraphQLExecutor
   Specific logic for task dispatching and result selection.
6. BizObjectManager
   Uses BizObjectBuilder to dynamically assemble BizObject business objects. Maps BizObject methods to GraphQLOperation and object structures to GraphQLObjectDefinition.
7. BizObjectBuilder
   Combines information from XMeta files, XBiz files, and Java BizModel classes into a single BizObject.
8. ReflectionBizModelBuilder and ReflectionGraphQLTypeFactory
   Uses reflection to construct GraphQL operations and definitions based on `@BizModel` or `@BizQuery` annotations in Java classes.
9. ObjMetaToGraphQLDefinition
   Converts xmeta metadata into GraphQL type definitions.
10. BizModelToGraphQLDefinition
    Converts xbiz model definitions into GraphQL operation definitions.
11. GraphQLActionAuthChecker
    Enforces field-level access restrictions.


## Nine. NopRPC

1. ClusterRpcClient
   Core logic for distributed RPC client calls.
2. ClusterRpcProxyFactoryBean/RpcInvocationHandler
   Adapts the RPC interface to methods in ClusterRpcClient.
3. HttpRpcService
   Implements RPC calls using HTTP protocol.
4. MessageRpcClient
   Implements RPC communication using a one-way message queue.
5. SentinelFlowControlRunner
   Integrates Sentinel for circuit breakers.
6. LoadBalanceServerChooser
   Selects service instances using load balancing algorithms.
7. RouteServiceInstanceFilter
   Identifies configuration like nop-svc-route and implements gradual roll-out.


## Ten. NopGrpc

1. GrpcServer
   Starts a gRPC server and exposes the GraphQL service as a gRPC service.
2. GraphQLServerCallHandler
   Converts gRPC endpoint handlers to GraphQLEngine calls.
3. ServiceSchemaManager
   Dynamically generates Protobuf schemas based on GraphQL definitions.
4. GraphQLToApiModel
   Converts GraphQL service definitions into api.xdef models.


## Eleven. NopReport

1. ReportEngine
   Core entry point for report engine access.
2. ExcelWorkbookParser
   Parses xlsx files into ExcelWorkbook objects without using Apache POI.
3. ExcelToXptModelTransformer
   Converts ExcelWorkbook to XptModel business objects.
4. XptModelInitializer
   Analyzes parent-child cell relationships in XptModel and saves results in XptCellModel.
5. ExcelFormulaParser
   Parses Excel formula syntax into ReportExpr.
6. ReportExpressionParser
   Enhances expression engines with reporting-level hierarchical coordinate support.
7. ExpandedSheetGenerator
   Implements the core logic for generating dynamic reports from Excel templates.
8. TableExpander/CelRowExpander/CelColExpander
   Expands rows, columns, and cells in tables.

## Twelve. NopRule

1. **RuleManager**  
   The core entry point for using the rule engine externally.

2. **ExecutableRule and ExecutableMatrixRule**  
   The specific execution logic for decision trees and decision matrices.

3. **RuleExprParser**  
   A manually written parser similar to FEEL (Friendly Enough Expression Language).

4. **DaoRuleModelLoader and DaoRuleModelSaver**  
   Save rule models to the `nop_rule_definition` table.

5. **RuleExcelModelParser**  
   Parse rule models from Excel files, supporting decision trees and decision matrices.

6. **RuleServiceImpl**  
   Expose rule engine functionality as GraphQL and REST services.

---

## Thirteen. NopWorkflow

1. **WorkflowManagerImpl**  
   The core entry point for accessing the workflow engine externally.

2. **WorkflowImpl**  
   Create or load workflow objects from the WorkflowManager. It internally calls the WorkflowEngine to implement functionality.

3. **WorkflowEngineImpl**  
   The core implementation logic of the workflow engine.

4. **DaoWorkflowStore**  
   Persistent storage for workflows.

5. **WfModelAnalyzer**  
   Analyze the validity of workflow model configurations and remove redundant branches, constructing a DAG (Directed Acyclic Graph).

6. **DaoWfActorResolver**  
   Adapt to external users, roles, department management, and collaboration mechanisms.

7. **WorkflowServiceImpl**  
   Provide external GraphQL and REST services for workflows.

---

## Fourteen. NopBatch

1. **BatchTaskBuilder**  
   A factory class responsible for creating BatchTask instances. It organizes the order of processing `skip`, `retry`, `transaction`, `process`, and `listener`.

2. **BatchTask**  
   The core logic of batch processing, executed in threads with multiple batches, each thread handling a chunk.

3. **RetryBatchConsumer**  
   Add failure retry logic for chunk processing.

4. **BatchProcessorConsumer**  
   Process data row by row, collect all output data into a list structure, and then call the downstream consumer once.

5. **PartitionDispatchLoader**  
   Distribute data to different data partitions based on business characteristics.

6. **ResourceRecordLoader**  
   A generic data loader for resource records.

7. **ResourceRecordConsumer**  
   Generic data file output mechanism.

8. **BatchGenLoader**  
   A test data generator used in performance testing, dynamically generating test data based on configuration.

---

## Fifteen. NopJob

1. **DefaultJobScheduler**  
   The core entry point for using the job scheduling engine externally.

2. **TriggerBuilder**  
   Logic for building scheduled triggers.

3. **CalendarBuilder**  
   Build the Calendar component, supporting specific holidays and intervals.

4. **TriggerExecutorImpl**  
   The core logic of the job scheduling engine.

---

## Sixteen. NopAutoTest

1. **JunitAutoTestCase**  
   A base class for automatic testing that supports recording and replaying actions. Inherits from `NopJunitExtension`, which provides integration support with JUnit.

2. **JunitBaseTestCase**  
   A base class without specific test functionality, but requires `NopIoC` injection for certain tests. Inherits from this class.

3. **BaseTestCase**  
   The simplest base class without `NopIoC` injection. It provides convenient methods for resource file operations.

4. **AutoTestCaseDataBaseInitializer**  
   Initialize the database based on data in the input directory.

5. **AutoTestCaseDataSaver**  
   Save test data to the database automatically.

1. To save the database modification contents collected via AutoTestOrmHook into the output directory.
6. **AutoTestCaseResultChecker**  
   After running the unit tests, automatically run the checker to verify if the execution results match the recorded data.

7. **AutoTestMatchChecker**  
   Using the nop-match module's prefix-based matching syntax to validate data patterns.

8. **AutoTestVars**  
   Random numbers generated during unit test runs, which change each time, need to be registered in this collection. This way, they will be replaced with variable names during recording.

