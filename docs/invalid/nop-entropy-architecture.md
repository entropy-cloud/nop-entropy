# Nop Entropy Architecture

## Project Overview

Nop Entropy is a next-generation low-code development platform built from scratch based on **reversible computation theory**, adopting a language-oriented programming paradigm. It includes a suite of fully designed engines such as a GraphQL engine, ORM engine, workflow engine, reporting engine, rule engine, and batch processing engine, all developed from scratch based on new principles. It automatically generates GraphQL/REST/gRPC services according to Excel data models, allowing for customized development without modifying the source code of the base product. It supports native compilation with GraalVM and is free for commercial use by small and medium-sized enterprises.

**Key Design Philosophy:**
- **Reversible Computation**: Next-generation software construction theory that enables system-level reuse through Delta customization
- **Language-Oriented Programming**: Creates DSLs for specific domains with automatic parser, validator, and IDE support
- **Zero Framework Dependencies**: Does not use Spring or other third-party frameworks, completely redesigned based on new principles
- **Model-Driven Development**: All code can be generated from models, with automatic updates when models change
- **Cloud-Native Design**: Built-in distributed transaction and multi-tenant support, can run as single instance or distributed cluster

## Key Features

### Core Engines
- **GraphQL Engine**: Auto-generates GraphQL services from Excel models with full CRUD support
- **ORM Engine**: Next-generation ORM with delta-based customization, not relying on Hibernate
- **Workflow Engine**: Supports human approval workflows and distributed DAG task flows
- **Report Engine**: Chinese-style report generation using Excel/Word as design tools
- **Rule Engine**: Decision tables and decision matrices for complex business rules
- **Batch Processing Engine**: Distributed batch tasks with file parsing/generation configuration
- **Job Scheduler**: Distributed task scheduling with cron expression support

### Platform Features
- **Reversible Computation**: Delta customization allows product modification without touching source code
- **XLang DSL Platform**: Domain-specific language workbench for creating custom DSLs
- **Virtual File System**: Layered resource loading with Delta overlay support
- **IoC Container**: Lightweight dependency injection with no third-party dependencies
- **Code Generation**: Model-driven code generation with template system
- **IDEA Plugin**: Syntax highlighting, file navigation, and breakpoint debugging for custom DSLs
- **Multi-Tenancy**: Built-in multi-tenant support
- **Distributed Transaction**: TCC transaction support for cross-service operations

### Integration Support
- **Quarkus Integration**: Native compilation support, fast startup (10x faster)
- **Spring Integration**: Can be integrated with Spring applications
- **Solon Integration**: Lightweight Java framework support, 10MB smaller than Spring/Quarkus

## Architecture Overview

```
nop-entropy/
├── nop-kernel/                    # Core kernel modules
│   ├── nop-api-core/              # API core definitions
│   ├── nop-commons/               # Common utilities
│   ├── nop-core/                  # Core functionality
│   └── nop-xlang/                 # XLang language implementation
│
├── nop-core-framework/            # Core framework
│   ├── nop-ioc/                   # IoC container
│   ├── nop-config/                # Configuration management
│   └── nop-xapi/                  # XLang API
│
├── nop-service-framework/         # Service framework
│   └── nop-biz/                   # Business model framework (CrudBizModel)
│
├── nop-persistence/               # Persistence layer
│   ├── nop-dao/                   # Data access interfaces
│   └── nop-orm/                   # ORM engine
│
├── nop-auth/                      # Authentication and authorization
├── nop-sys/                       # System configuration
├── nop-graphql/                   # GraphQL engine
├── nop-wf/                        # Workflow engine
├── nop-report/                    # Report engine
├── nop-rule/                      # Rule engine
├── nop-batch/                     # Batch processing engine
├── nop-task/                      # Task orchestration
├── nop-job/                       # Distributed job scheduler
├── nop-tcc/                       # Distributed transaction
├── nop-cluster/                   # Distributed cluster support
├── nop-message/                   # Message queue integration
├── nop-codegen/                   # Code generator
├── nop-autotest/                  # Automated testing framework
├── nop-ooxml/                     # Office file parsing/generation
├── nop-spring/                    # Spring integration
├── nop-quarkus/                   # Quarkus integration
├── nop-dyn/                       # Online designer
├── nop-ai/                        # AI model integration
├── nop-search/                    # Search functionality
├── nop-datav/                     # Data visualization
├── nop-integration/               # External service integration
├── nop-dev-tools/                 # Development tools
├── nop-demo/                      # Demo applications
└── nop-benchmark/                 # Performance benchmarks
```

## Detailed Architecture

### 1. Reversible Computation Core Theory

Reversible computation is the foundational principle of Nop Platform. It enables:

**Delta Customization**: Instead of modifying source code, you can create Delta files that describe changes to the base product.

```xml
<!-- Delta file: /_delta/default/bank/orm/app.orm.xml -->
<orm x:extends="super">
  <entity name="bank.BankAccount" className="mybank.BankAccountEx">
    <columns>
      <!-- Add new field -->
      <column name="refAccountId" code="REF_ACCOUNT_ID" sqlType="VARCHAR" length="20" />
      <!-- Remove field from base -->
      <column name="phone3" code="PHONE3" x:override="remove" />
    </columns>
  </entity>
</orm>
```

**Key Concepts**:
- **Delta Space**: Domain model space where deltas are defined with business semantics
- **Domain Coordinates**: XPath-based addressing system for locating values in models
- **Delta Merging**: Tree structure merging with x:extends operator (satisfies associativity)
- **Independent Deltas**: Deltas exist independently and can be applied to any base

**Benefits**:
- **System-Level Reuse**: Reuse entire software systems without decomposition
- **Localized Changes**: Changes only affect the modified paths, no abstraction leakage
- **Incremental Evolution**: Base product upgrades can be merged with customizations
- **No Forking**: No need to maintain separate branches for different customers

### 2. XLang DSL Platform

XLang is a language-oriented programming platform that enables creation of domain-specific languages.

#### 2.1 XDef Meta-Model

XDef defines the structure and validation rules for custom DSLs:

```xml
<!-- User entity meta-model -->
<entity name="User" xdef:bean-class="io.nop.xlang.xdef.XDefNode">
  <prop name="id" xdef:mandatory="true" />
  <prop name="name" xdef:min-length="1" xdef:max-length="50" />
  <prop name="email" xdef:pattern="^[A-Za-z0-9+_.-]+@(.+)$" />
  <prop name="roles" xdef:unique-item="true" />
</entity>
```

**Features**:
- Automatic parser generation from meta-model
- Syntax validation at parse time
- IDE integration with syntax highlighting and navigation
- Breakpoint debugging support

#### 2.2 XLang Template Language

XLang provides a powerful template language for code generation:

```xml
<template xmlns:xpl="urn:nop:xpl">
  <c:if test="${entity.hasProp('name')}">
    public String get${entity.prop('name').propName}() {
        return this.${entity.prop('name').propName};
    }
  </c:if>
</template>
```

**Features**:
- XML-based template syntax
- Control flow (if, for, switch)
- XPath-based value access
- Delta inheritance for template customization

### 3. Virtual File System (VFS)

Nop Platform implements a layered virtual file system:

```
┌─────────────────────────────────────────┐
│         Delta Layer (_delta/)           │  ← Customizations
├─────────────────────────────────────────┤
│         Application Layer (/)            │  ← Application code
├─────────────────────────────────────────┤
│         Platform Layer (/nop/)          │  ← Platform code
├─────────────────────────────────────────┤
│         System Layer (system/)          │  ← System resources
└─────────────────────────────────────────┘
```

**Key Features**:
- **DeltaResourceStore**: Merges multiple resource stores with delta overlay
- **Whiteout Files**: Special files to delete resources from lower layers
- **Path Merging**: Automatic merging of files from multiple layers
- **Resource Caching**: Built-in caching for performance

**Implementation**:
```java
IVirtualFileSystem vfs = new DeltaResourceStore(
    new ClasspathResourceStore(),  // Base layer
    new FileSystemResourceStore("/_delta/default/")  // Delta layer
);
IResource resource = vfs.getResource("/bank/orm/app.orm.xml");
```

### 4. IoC Container

Nop provides a lightweight IoC container without third-party dependencies.

#### 4.1 Bean Configuration

Beans can be configured via XML or annotations:

```xml
<beans xmlns="http://www.nopplatform.com/schema/beans">
  <bean id="userService" class="com.myapp.UserService">
    <property name="userDao" ref-ref="userDao" />
    <property name="maxRetry" value="3" />
  </bean>
</beans>
```

#### 4.2 Dependency Injection

```java
@Component
public class OrderService {
    @Inject
    private IUserService userService;

    @Inject
    @Named("prodDataSource")
    private IDataSource dataSource;

    @Inject
    private List<IOrderValidator> validators;  // Inject all beans
}
```

**Features**:
- Constructor injection
- Field injection
- Setter injection
- Optional injection
- Collection injection
- Qualifiers and names

#### 4.3 Lifecycle Management

```java
@Component
public class MyBean implements IBeanWithLifecycle {
    @PostConstruct
    public void init() {
        // Initialization
    }

    @PreDestroy
    public void destroy() {
        // Cleanup
    }
}
```

### 5. Configuration Management

Nop provides a dynamic configuration system:

```yaml
# application.yaml
nop:
  datasource:
    default:
      jdbc-url: jdbc:mysql://localhost:3306/nop
      username: root
      password: password
  cache:
    default:
      timeout: 3600
```

**Features**:
- Multiple configuration sources (YAML, XML, JSON, properties)
- Environment variable support
- Profile-based configuration (dev, test, prod)
- Hot reload support
- Delta-based configuration customization

### 6. Service Layer (BizModel)

The service layer uses BizModel pattern with built-in CRUD support:

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @Inject
    private IRoleService roleService;

    // Built-in CRUD methods
    // findPage(), findList(), get(), save(), delete()

    @BizQuery
    public PageBean<NopAuthUser> findUsers(
        @Name("keyword") String keyword,
        @Name("pageNo") Integer pageNo,
        @Name("pageSize") Integer pageSize
    ) {
        QueryBean query = new QueryBean();
        if (StringHelper.isNotEmpty(keyword)) {
            query.setFilter(FilterBeans.contains("userName", keyword));
        }
        return findPage(query, pageNo, pageSize);
    }

    @BizMutation
    @Transactional
    public User createUser(@Name("user") UserData data) {
        // Validation
        if (emailExists(data.getEmail())) {
            throw new NopException(ERR_EMAIL_EXISTS)
                .param("email", data.getEmail());
        }

        // Create user
        User user = new User();
        BeanTool.copyProperties(user, data);
        dao().saveEntity(user);

        // Assign default role
        roleService.assignDefaultRole(user);

        return user;
    }

    @Override
    protected void beforeSave(NopAuthUser entity, boolean isNew) {
        if (isNew) {
            entity.setStatus(NopAuthConstants.USER_STATUS_ACTIVE);
            entity.setCreateTime(new Date());
        }
    }
}
```

**Key Features**:
- Built-in CRUD operations
- Automatic GraphQL/REST generation
- Transaction management
- Permission control
- Data validation
- Extension points (beforeSave, afterSave, etc.)

### 7. ORM Engine

Nop ORM is a model-driven, high-performance ORM engine:

#### 7.1 Entity Definition

```java
@Entity(table = "nop_auth_user")
@Cacheable(cacheName = "userCache", timeout = 3600)
public class NopAuthUser implements IOrmEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @ManyToOne
    @JoinColumn(name = "dept_id")
    private NopAuthDept dept;

    @OneToMany(mappedBy = "user")
    private List<NopAuthRole> roles;
}
```

#### 7.2 Query Operations

```java
// Find by ID
User user = dao().getEntityById("1");

// Find by example
User example = new User();
example.setStatus(1);
List<User> users = dao().findAllByExample(example);

// Complex query with QueryBean
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.contains("userName", keyword)
));
query.setOrderBy("createTime DESC");
query.setOffset(0);
query.setLimit(20);

PageBean<User> page = dao().findPageByQuery(query);
```

#### 7.3 Delta-Based Customization

```xml
<!-- Delta ORM model -->
<orm x:extends="super">
  <entity name="io.nop.auth.entity.NopAuthUser" className="myapp.CustomUser">
    <columns>
      <!-- Add column -->
      <column name="customField" sqlType="VARCHAR" length="100" />
      <!-- Remove column -->
      <column name="oldField" x:override="remove" />
    </columns>
    <relations>
      <!-- Add relation -->
      <relation name="extProfile" x:override="add" ... />
    </relations>
  </entity>
</orm>
```

**Key Features**:
- Model-driven metadata
- Automatic SQL generation
- Lazy loading and eager loading
- Dirty checking and snapshot mechanism
- Two-level cache (L1 session cache, L2 entity cache)
- Batch operations
- Database dialect support (MySQL, PostgreSQL, Oracle, SQL Server, H2)

### 8. GraphQL Engine

The GraphQL engine auto-generates schemas from BizModel:

#### 8.1 Schema Auto-Generation

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public User getUser(@Name("userId") String userId) {
        return dao().getEntityById(userId);
    }

    @BizMutation
    public User createUser(@Name("user") UserData data) {
        return save(data);
    }
}
```

Auto-generated GraphQL Schema:
```graphql
type User {
    userId: ID!
    userName: String
    email: String
    status: Int
    dept: Dept
    roles: [Role!]!
}

type UserBizModel {
    getUser(userId: String!): User
    findUsers(keyword: String, pageNo: Int, pageSize: Int): PageBean!
    createUser(user: UserInput!): User
    updateUser(user: UserInput!): User
    deleteUser(userId: String!): Boolean
}

type Query {
    User: UserBizModel
}

type Mutation {
    User: UserBizModel
}
```

#### 8.2 GraphQL Query

```graphql
query {
  User {
    findUsers(keyword: "test", pageNo: 1, pageSize: 20) {
      pageNo
      pageSize
      totalCount
      items {
        userId
        userName
        email
        dept {
          deptName
        }
      }
    }
  }
}
```

**Key Features**:
- Schema auto-generation from BizModel
- Type inference from Java types
- DataLoader for batch queries (N+1 prevention)
- Query validation
- Field selection
- Pagination support
- Subscription support (WebSocket)

### 9. Workflow Engine

The workflow engine supports both human approval workflows and DAG task flows:

#### 9.1 Workflow Definition

```xml
<wf-definition name="PurchaseOrderWorkflow" version="1.0">
  <actors>
    <actor id="manager" type="user" />
    <actor id="finance" type="user" />
  </actors>

  <steps>
    <step id="submit" type="start">
      <next-step id="managerApproval" />
    </step>

    <step id="managerApproval" type="user-task" actor-id="manager">
      <transition id="approve" to="financeApproval" />
      <transition id="reject" to="end" />
    </step>

    <step id="financeApproval" type="user-task" actor-id="finance">
      <transition id="approve" to="end" />
      <transition id="reject" to="end" />
    </step>

    <step id="end" type="end" />
  </steps>
</wf-definition>
```

#### 9.2 Workflow Execution

```java
@BizModel("Workflow")
public class WorkflowBizModel {

    @BizMutation
    public WfStartResponse startWorkflow(@Name("request") WfStartRequest request) {
        return wfEngine.start(request.getWfName(), request.getVars());
    }

    @BizMutation
    public void approveStep(@Name("request") WfActionRequest request) {
        wfEngine.executeStep(request.getWfInstanceId(),
                            request.getStepId(),
                            "approve",
                            request.getVars());
    }
}
```

**Key Features**:
- Human approval workflows
- DAG task workflows (Airflow-like)
- Parallel step execution
- Sub-workflow support
- Workflow variables
- Actor assignment (users, roles, expressions)
- Workflow versioning

### 10. Report Engine

The report engine generates Chinese-style reports using Excel/Word as design tools:

#### 10.1 Excel Report Template

Excel cells with special annotations:

```
+------------------+------------------+
| @title           | ${@now}          |
+------------------+------------------+
| Order ID         | Amount           |
+------------------+------------------+
| ${orderId}       | ${amount}        |
| @row:items       |                  |
|   ${item.name}   | ${item.price}    |
| @end             |                  |
+------------------+------------------+
| Total:           | ${@sum(amount)}  |
+------------------+------------------+
```

#### 10.2 Report Generation

```java
@BizModel("Report")
public class ReportBizModel {

    @BizQuery
    public byte[] generateOrderReport(@Name("orderId") String orderId) {
        Order order = orderDao.getEntityById(orderId);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("amount", order.getAmount());
        data.put("items", order.getItems());

        return reportEngine.render("order-report.xlsx", data);
    }
}
```

**Key Features**:
- Excel as report designer
- Word document generation
- Cross-table reports (pivot tables)
- Block reports with dynamic data
- Expression support (@sum, @avg, @count)
- Conditional formatting
- Chart support
- Print optimization

### 11. Rule Engine

The rule engine supports decision tables and decision matrices:

#### 11.1 Decision Table

Excel-based decision table:

```
| Rule | Age | Region | Discount |
|------|-----|--------|----------|
| R1   | <30 | CN     | 10%      |
| R2   | >=30| CN     | 15%      |
| R3   | <30 | US     | 5%       |
| R4   | >=30| US     | 10%      |
```

#### 11.2 Rule Execution

```java
@BizModel("Rule")
public class RuleBizModel {

    @BizQuery
    public BigDecimal calculateDiscount(
        @Name("customerAge") Integer age,
        @Name("region") String region
    ) {
        Map<String, Object> input = new HashMap<>();
        input.put("Age", age);
        input.put("Region", region);

        Map<String, Object> result = ruleEngine.execute("discount-rules", input);
        return (BigDecimal) result.get("Discount");
    }
}
```

**Key Features**:
- Decision tables
- Decision matrices
- Decision trees
- Rule versioning
- Rule testing
- Performance optimization (rule caching)

### 12. Batch Processing Engine

The batch processing engine processes files with configuration-based parsing:

#### 12.1 Batch Definition

```xml
<batch name="ImportOrderBatch" input-type="excel">
  <parser>
    <sheet name="Orders">
      <field name="orderId" column="A" type="String" />
      <field name="amount" column="B" type="BigDecimal" />
    </sheet>
  </parser>

  <process>
    <action bean-ref="orderBatchBizModel" method="importOrder" />
  </process>
</batch>
```

#### 12.2 Batch Execution

```java
@BizModel("Batch")
public class BatchBizModel {

    @BizMutation
    public BatchResult executeBatch(@Name("batchName") String batchName,
                                    @Name("inputFile") IFile inputFile) {
        return batchEngine.execute(batchName, inputFile);
    }
}
```

**Key Features**:
- Excel/CSV file parsing
- XML/JSON file parsing
- Batch processing configuration
- Distributed batch execution
- Progress tracking
- Error handling
- Rollback support

### 13. Distributed Transaction (TCC)

Nop provides TCC (Try-Confirm-Cancel) distributed transaction support:

```java
@Service
public class OrderService {

    @BizMutation
    @TccTransaction
    public void createOrder(@Name("order") OrderData data) {
        // Try phase
        orderDao.saveOrder(order);
    }

    @TccConfirm
    public void confirmCreateOrder(@Name("order") OrderData data) {
        // Confirm phase
        inventoryService.deductInventory(data.getItems());
    }

    @TccCancel
    public void cancelCreateOrder(@Name("order") OrderData data) {
        // Cancel phase
        orderDao.deleteOrder(data.getOrderId());
    }
}
```

**Key Features**:
- TCC transaction pattern
- Saga pattern support
- Transaction logging
- Retry mechanism
- Timeout handling
- Distributed locking

### 14. Code Generation

The code generator generates code from models:

#### 14.1 Model Definition

Excel model file:

| Entity | Prop Name | Column Name | Type | Length | Mandatory |
|--------|-----------|-------------|------|--------|-----------|
| User | userId | user_id | String | 32 | true |
| User | userName | user_name | String | 50 | true |
| User | email | email | String | 100 | false |

#### 14.2 Code Generation

```bash
# Generate code from Excel model
mvn exec:java -Dexec.mainClass="io.nop.codegen.task.CodeGenTask" \
  -Dexec.args="model/user.model.xlsx templates/"
```

**Generated Code**:
- Entity classes
- DAO interfaces and implementations
- BizModel classes with CRUD methods
- GraphQL schemas
- Frontend page definitions
- API documentation

**Key Features**:
- Model-driven generation
- Template-based
- Incremental generation (preserve custom code)
- Delta-based customization
- Multiple output formats (Java, XML, JSON, Markdown)

### 15. IDEA Plugin

Nop provides an IDEA plugin for custom DSL support:

**Features**:
- Syntax highlighting
- Code completion
- Go to definition
- Find usages
- Refactoring
- Breakpoint debugging
- Hover documentation

**Meta-Model to Editor**:
```xml
<!-- XDef meta-model -->
<entity name="MyEntity">
  <prop name="id" type="String" />
  <prop name="name" type="String" />
</entity>
```

Auto-generates:
- Parser
- Validator
- IDEA language support
- Documentation

## Key Design Decisions

### 1. Zero Third-Party Framework Dependencies

**Decision**: Nop Platform does not use Spring or other third-party frameworks.

**Rationale**:
- Complete control over framework design
- No framework upgrade breaking changes
- Smaller footprint and faster startup
- Better integration with reversible computation

**Impact**:
- Custom IoC container
- Custom transaction management
- Custom AOP implementation
- Better performance characteristics

### 2. Model-Driven Architecture

**Decision**: All code should be generatable from models.

**Rationale**:
- Single source of truth
- Automatic consistency
- Faster development
- Easier refactoring

**Impact**:
- Excel models for business entities
- ORM models for database mapping
- BizModel definitions for services
- Template-based code generation

### 3. Delta-Based Customization

**Decision**: Customization through delta files, not source code modification.

**Rationale**:
- Enables system-level reuse
- Isolates customizations
- Simplifies upgrades
- No forking needed

**Impact**:
- Delta resource overlay
- X:extends operator
- Remove semantics
- Independent delta management

### 4. Language-Oriented Programming

**Decision**: Create DSLs for specific domains instead of using general-purpose languages.

**Rationale**:
- Domain-specific expressiveness
- Automatic tooling (parsers, validators, IDE)
- Better abstraction
- Easier business logic

**Impact**:
- XDef meta-model system
- XLang template language
- Custom DSL support
- IDEA plugin integration

### 5. Cloud-Native Design

**Decision**: Built-in support for distributed systems from day one.

**Rationale**:
- Modern deployment models
- Scalability requirements
- Multi-tenancy needs
- Microservice architecture

**Impact**:
- Distributed transaction support
- Multi-tenant architecture
- Cluster support
- GraalVM native compilation

### 6. Virtual File System

**Decision**: Layered virtual file system with delta overlay.

**Rationale**:
- Enforces delta customization
- Supports multiple deployment models
- Enables hot reload
- Facilitates testing

**Impact**:
- DeltaResourceStore
- Resource caching
- Path merging
- Layer separation

### 7. Separate Engine Layers

**Decision**: Each engine (ORM, GraphQL, Report, etc.) is independent and can be used standalone.

**Rationale**:
- Modular architecture
- Selective dependency inclusion
- Independent evolution
- Better testability

**Impact**:
- Clear module boundaries
- Minimal dependencies
- Flexible usage patterns

## Configuration

### Installation

```bash
# Clone repository
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy

# Build (requires JDK 17+)
mvn install -DskipTests -Dquarkus.package.type=uber-jar
```

### Configuration Files

**application.yaml**:
```yaml
nop:
  datasource:
    default:
      jdbc-url: jdbc:mysql://localhost:3306/nop
      username: root
      password: password
      hikari:
        maximum-pool-size: 20

  orm:
    default:
      cache:
        enabled: true
        timeout: 3600

  graphql:
    enabled: true
    path: /graphql

  auth:
    session-timeout: 7200

  cluster:
    enabled: false
    node-id: ${HOSTNAME}
```

### Maven Dependencies

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-commons</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-xlang</artifactId>
    </dependency>

    <!-- Framework -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-ioc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-config</artifactId>
    </dependency>

    <!-- Persistence -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-dao</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-orm</artifactId>
    </dependency>

    <!-- Service -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-biz</artifactId>
    </dependency>

    <!-- GraphQL -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-graphql</artifactId>
    </dependency>

    <!-- Quarkus Integration (optional) -->
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-quarkus-core</artifactId>
    </dependency>
</dependencies>
```

### Quarkus Integration

```java
@ApplicationScoped
public class MyApp {

    @Inject
    private IUserBizModel userBizModel;

    @GET
    @Path("/users/{id}")
    public User getUser(@PathParam("id") String userId) {
        return userBizModel.getUser(userId);
    }
}
```

### Spring Integration

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}

@RestController
public class UserController {
    @Inject
    private IUserBizModel userBizModel;

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable String id) {
        return userBizModel.getUser(id);
    }
}
```

## Use Cases

### 1. Low-Code Development Platform

**Scenario**: Build a CRM system with minimal code

**Solution**:
1. Define data models in Excel
2. Auto-generate entities, DAOs, and BizModels
3. Use GraphQL for API layer
4. Use AMIS (frontend low-code framework) for UI
5. Customize business logic with BizModel extensions

**Benefits**:
- 80% less code
- Automatic CRUD
- Built-in validation
- Easy customization

### 2. Multi-Tenant SaaS Platform

**Scenario**: Provide CRM as SaaS to multiple customers

**Solution**:
1. Build base product with standard features
2. Use delta customization for customer-specific features
3. Multi-tenant isolation at data and configuration level
4. Online designer for runtime customization

**Benefits**:
- Single code base
- No forking
- Easy upgrades
- Customer self-service

### 3. Enterprise Report Platform

**Scenario**: Generate complex Chinese-style reports

**Solution**:
1. Design reports in Excel
2. Use report engine with cross-table support
3. Batch generation for multiple reports
4. Export to Excel, Word, PDF

**Benefits**:
- Excel as design tool (non-technical users)
- Complex reports (cross-table, grouping, aggregation)
- High performance
- Print-optimized output

### 4. Workflow Automation

**Scenario**: Approval workflows for purchase orders

**Solution**:
1. Define workflow in XML or online designer
2. Human tasks for approval
3. Parallel approval steps
4. Conditional transitions
5. Sub-workflows for complex processes

**Benefits**:
- Visual workflow designer
- Flexible actor assignment
- Workflow versioning
- Audit trail

### 5. Rule Engine for Business Rules

**Scenario**: Complex discount calculation rules

**Solution**:
1. Define rules in Excel decision tables
2. Easy modification without code changes
3. Rule versioning
4. Performance optimization

**Benefits**:
- Business logic separated from code
- Easy maintenance
- Non-technical users can modify rules
- Rule testing

### 6. Batch Data Processing

**Scenario**: Import large Excel files with order data

**Solution**:
1. Define batch configuration in XML
2. Parse Excel with configuration
3. Process records with BizModel
4. Handle errors with rollback
5. Distributed execution for large files

**Benefits**:
- Configuration-driven
- No parsing code
- Distributed execution
- Progress tracking

### 7. Microservice Architecture

**Scenario**: Build microservices with GraphQL APIs

**Solution**:
1. Use Nop GraphQL engine for service communication
2. Distributed transaction with TCC
3. Service discovery with nop-cluster
4. Message queue integration with nop-message

**Benefits**:
- Type-safe inter-service communication
- Flexible queries
- Transaction consistency
- Scalability

### 8. Legacy System Integration

**Scenario**: Integrate Nop Platform with existing Spring application

**Solution**:
1. Use nop-spring integration
2. Gradually migrate modules
3. Coexist with existing Spring code
4. Shared database or data synchronization

**Benefits**:
- Gradual migration
- Low risk
- Spring compatibility
- Phased adoption

### 9. High-Performance API

**Scenario**: Build high-performance GraphQL API

**Solution**:
1. Use GraalVM native compilation
2. Enable entity cache
3. DataLoader for batch queries
4. Database read/write separation

**Benefits**:
- Fast startup (100ms)
- Low memory footprint
- High throughput
- Efficient queries

### 10. Custom DSL Development

**Scenario**: Create DSL for specific business domain (e.g., insurance calculations)

**Solution**:
1. Define XDef meta-model
2. Use code generator to create parser and validator
3. Install IDEA plugin for IDE support
4. Create BizModel to execute DSL

**Benefits**:
- Domain-specific language
- Business experts can write rules
- IDE support
- Type safety

## Summary

Nop Entropy is a revolutionary low-code platform based on reversible computation theory. Key takeaways:

### Core Innovation
- **Reversible Computation**: Enables system-level reuse through delta customization
- **Language-Oriented Programming**: Creates DSLs with automatic tooling
- **Model-Driven Architecture**: Everything is generatable from models
- **Zero Framework Dependencies**: Complete control and better performance

### Technical Excellence
- **Independent Engines**: ORM, GraphQL, Report, Workflow, Rule, Batch engines built from scratch
- **Cloud-Native**: Built-in distributed support, multi-tenancy, and native compilation
- **High Performance**: Optimized for speed and efficiency
- **Developer Experience**: IDEA plugin, hot reload, powerful debugging

### Business Value
- **Reduced Development Time**: 80% less code with model-driven development
- **Easy Customization**: Delta customization without touching source code
- **Future-Proof**: Upgrades without conflicts
- **Scalability**: From single instance to distributed cluster

### Unique Capabilities
- **Product Reuse**: Reuse entire software systems, not just components
- **No Forking**: Maintain single code base for all customers
- **Business Logic Separation**: Rules in Excel, workflows in XML
- **Online Customization**: Runtime modifications without deployment

Nop Entropy represents the next generation of software construction technology, providing a solid foundation for AI-era software production.
