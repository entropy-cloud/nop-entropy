# Nop Platform AI Documentation Index

## Core Principle

Nop platform is a low-code platform based on Reversible Computation: `App = Delta x-extends Generator<DSL>`.
- **Model-driven**: DSL models define business structure → generate code (entities, APIs, etc.)
- **Delta customization**: modify/extend WITHOUT changing base source code
- **Framework-agnostic**: runs on Spring/Quarkus/Solon

**Key**: Before coding, check if code can be derived from models. Extend generated code via Delta/inheritance.

## Directory Mapping (WHEN TO READ WHAT)

### Quick Start
- New to Nop? → `00-quick-start/10-min-quickstart.md`
- Need task reference? → `00-quick-start/common-tasks.md`

### Development Tasks (Primary)

| Task | Reference |
|------|-----------|
| Create CRUD functionality | `03-development-guide/service-layer.md`, `08-examples/crud-example.md` |
| Handle complex queries | `03-development-guide/data-access.md`, `08-examples/query-example.md` |
| Manage transactions | `04-core-components/transaction.md`, `12-tasks/transaction-boundaries.md` |
| Handle errors | `04-core-components/exception-handling.md` |
| Develop GraphQL APIs | `03-development-guide/api-development.md` |
| Batch processing | `03-development-guide/batch-engine.md` |
| Project structure & code gen | `03-development-guide/project-structure.md` |

### Advanced Features

| Feature | Reference |
|---------|-----------|
| Delta customization | `05-xlang/xdsl-delta.md`, `01-core-concepts/delta-basics.md`, `12-tasks/README.md` |
| ORM advanced (sharding, encryption, masking, hooks) | `03-development-guide/orm-advanced-features.md` |
| SQLLib SQL management | `03-development-guide/orm-sqllib.md` |
| XDef & XMeta | `05-xlang/xdef-core.md`, `05-xlang/meta-programming.md` |
| XScript/Xpl templates | `05-xlang/xscript.md`, `05-xlang/xpl.md` |

### Extend CRUD

| Extension | Reference |
|-----------|-----------|
| Extend CRUD hooks | `12-tasks/extend-crud-with-hooks.md` |
| Custom queries with QueryBean | `12-tasks/custom-query-with-querybean.md` |
| Extend API fields with Delta + BizLoader | `12-tasks/extend-api-with-delta-bizloader.md` |

### Core Components

| Component | Reference |
|-----------|-----------|
| IoC container | `04-core-components/ioc-container.md` |
| Config management (@InjectValue) | `04-core-components/config-management.md` |
| Error codes | `04-core-components/error-codes.md` |
| DTO/Enum standards | `04-core-components/enum-dto-standards.md` |

### Testing & Debugging

| Task | Reference |
|------|-----------|
| Write tests | `07-best-practices/testing.md`, `11-test-and-debug/autotest-guide.md` |
| Debug/diagnose | `11-test-and-debug/nop-debug-and-diagnosis-guide.md` |
| Troubleshoot issues | `09-quick-reference/troubleshooting.md` |

### Best Practices

| Topic | Reference |
|-------|-----------|
| Code style | `07-best-practices/code-style.md` |
| Performance | `07-best-practices/performance.md` |
| Security | `07-best-practices/security.md` |

### Reference

| Topic | Reference |
|-------|-----------|
| Quick API reference | `09-quick-reference/api-reference.md` |
| Source code anchors | `13-reference/source-anchors.md` |
| Utility classes | `06-utilities/*.md` |

## Quick API Reference

### CrudBizModel (Service Layer)

**IMPORTANT**: Extending `CrudBizModel` provides built-in CRUD. DO NOT implement simple CRUD methods!

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {
    // Built-in methods available:
    // - User__findPage(request: {...}, pageNo, pageSize)
    // - User__get(data: {id})
    // - User__save(data: {...})
    // - User__update(data: {...})
    // - User__delete(data: {id})

    // Customize via overrides:
    @Override
    protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // Custom logic
    }
}
```

**PREFERRED**: `getEntity()`, `requireEntity()`, `doFindList()`, `doFindPage()`, `doSave()`, `doUpdate()`, `doDelete()`
**AVOID**: Direct `dao().getEntityById()`, `dao().saveEntity()`, `dao().deleteEntity()` - they bypass data permissions and callbacks

### Custom Query

```java
@BizQuery
public PageBean<User> searchUsers(@Name("request") Map<String, Object> request,
                                  FieldSelectionBean selection, IServiceContext context) {
    QueryBean query = new QueryBean();
    List<TreeBean> filters = new ArrayList<>();

    if (request.containsKey("keyword")) {
        filters.add(FilterBeans.contains("name", request.get("keyword")));
    }
    if (request.containsKey("status")) {
        filters.add(FilterBeans.eq("status", request.get("status")));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }
    return doFindPage(query, selection, context);
}
```

### Transaction Management

**BizModel**: Use `@BizMutation` (auto transaction boundary)
**Non-BizModel/Fine control**: Use `ITransactionTemplate`

```java
@BizMutation  // Auto-transaction, no need for txn()
public void transferOrder(@Name("request") Map<String, Object> request, IServiceContext context) {
    Order from = requireEntity((String) request.get("fromId"));
    Order to = requireEntity((String) request.get("toId"));

    from.setStatus("TRANSFERRED");
    to.setStatus("PENDING");

    updateEntity(from,context);
    updateEntity(to,context);
}
```

### Exception Handling

```java
throw new NopException(MyErrors.ERR_NAME_REQUIRED)
    .param("field", "name");
```

### FilterBeans

- Compare: `eq`, `ne`, `gt`, `ge`, `lt`, `le`
- Collection: `in`, `notIn`
- Range: `between`
- String: `contains`, `startsWith`, `endsWith`, `like`, `regex`
- Null: `isNull`, `notNull`, `isEmpty`, `isNotEmpty`, `isBlank`, `notBlank`
- Logic: `and`, `or`, `not`
