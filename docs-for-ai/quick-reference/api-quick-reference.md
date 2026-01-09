# Nop Platform API Quick Reference

## Data Access (IEntityDao)

### CRUD Operations
```java
// Save
dao().saveEntity(entity);
dao().saveEntityDirectly(entity);
dao().saveOrUpdateEntity(entity);

// Update
dao().updateEntity(entity);
dao().updateEntityDirectly(entity);
dao().updateEntitiesDirectly(entities);

// Delete
dao().deleteEntity(entity);
dao().deleteEntityDirectly(entity);
dao().deleteAllByIds(ids);
```

### Query Operations
```java
// By ID
T entity = dao().getEntityById(id);        // Returns null if not found
T entity = dao().loadEntityById(id);        // Always returns object (may be proxy)
T entity = dao().requireEntityById(id);      // Throws if not found

// Batch by ID
List<T> entities = dao().batchGetEntitiesByIds(ids);
List<T> entities = dao().batchRequireEntitiesByIds(ids);
Map<Object, T> entityMap = dao().batchGetEntityMapByIds(ids);

// By Example
T entity = dao().findFirstByExample(example);
List<T> entities = dao().findAllByExample(example);
List<T> entities = dao().findAllByExample(example, orderBy);
List<T> entities = dao().findPageByExample(example, orderBy, offset, limit);
long count = dao().countByExample(example);

// By QueryBean
T entity = dao().findFirstByQuery(query);
List<T> entities = dao().findPageByQuery(query);
List<T> entities = dao().findAllByQuery(query);
long count = dao().countByQuery(query);
List<T> entities = dao().findNext(query);
List<T> entities = dao().findPrev(query);
```

### Batch Operations
```java
// Batch save/update/delete
dao().batchSaveEntities(entities);
dao().batchUpdateEntities(entities);
dao().batchDeleteEntities(entities);

// Batch flush
dao().batchFlush(entities);
dao().batchGetEntities(entities);
```

### Property Loading
```java
// Batch load properties
dao().batchLoadProps(entities, Arrays.asList("prop1", "prop2"));
dao().batchLoadPropsForEntity(entity, "prop1", "prop2");

// Load by selection
dao().batchLoadSelection(entities, selectionBean);
```

## Query Builder (FilterBeans)

### Comparison Operators
```java
FilterBeans.eq("name", "value");         // Equal
FilterBeans.ne("name", "value");         // Not equal
FilterBeans.gt("age", 18);              // Greater than
FilterBeans.ge("score", 60);            // Greater than or equal
FilterBeans.lt("age", 60);              // Less than
FilterBeans.le("score", 100);           // Less than or equal
```

### Collection Operators
```java
FilterBeans.in("id", Arrays.asList(1, 2, 3));
FilterBeans.notIn("id", Arrays.asList(1, 2, 3));
```

### Range Operators
```java
FilterBeans.between("age", 18, 30);
FilterBeans.betweenOp(FILTER_OP_BETWEEN, "date", start, end);
```

### String Matching
```java
FilterBeans.contains("name", "test");
FilterBeans.startsWith("name", "A");
FilterBeans.endsWith("email", "@gmail.com");
FilterBeans.likeOp(FILTER_OP_LIKE, "name", "te%t");
FilterBeans.regex("email", "^[a-z]+@[a-z]+\\.[a-z]+$");
```

### Null Operators
```java
FilterBeans.isNull("deletedAt");
FilterBeans.notNull("createTime");
FilterBeans.isEmpty("name");
FilterBeans.isNotEmpty("name");
FilterBeans.isBlank("name");
FilterBeans.notBlank("name");
```

### Logical Operators
```java
FilterBeans.and(filter1, filter2, filter3);
FilterBeans.or(filter1, filter2);
FilterBeans.not(filter1);
```

### Complex Conditions
```java
FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.or(
        FilterBeans.gt("score", 60),
        FilterBeans.like("name", "test%")
    )
);
```

## Query Structure (QueryBean)

### Basic Setup
```java
QueryBean query = new QueryBean();
query.setSourceName("NopAuthUser");
```

### Filter
```java
query.setFilter(FilterBeans.eq("status", 1));
```

### Order By
```java
query.addOrderField(OrderFieldBean.forField("createTime", true));
query.setOrderBy(Arrays.asList(
    OrderFieldBean.forField("status", false),
    OrderFieldBean.forField("createTime", true)
));
```

### Pagination
```java
query.setOffset(0);
query.setLimit(20);
```

### Cursor Pagination
```java
query.setCursor("cursor_value");
query.setFindPrev(false);
```

### Field Selection
```java
query.addField(QueryFieldBean.forField("id"));
query.addField(QueryFieldBean.forField("name"));
```

### Left Join Properties
```java
query.setLeftJoinProps(Arrays.asList("roles", "departments"));
```

### Aggregation
```java
QueryAggregateFieldBean agg = new QueryAggregateFieldBean();
agg.setName("id");
agg.setAggFunc("COUNT");
agg.setAlias("total");
query.setAggregates(Arrays.asList(agg));
```

## Business Model (CrudBizModel)

### Built-in Methods
```java
// Query methods
long count = findCount();
T entity = findFirst(query);
List<T> list = findList(query);
PageBean<T> page = findPage(query, pageNo, pageSize);

// CRUD methods
T entity = save(data);
T entity = update(data);
void delete(String id);
```

### Entity Data Access
```java
IEntityDao<T> dao = dao();
```

### Transaction Management
```java
txn(() -> {
    // Transactional code
});
```

## Common Annotations

### Business Model
```java
@BizModel("MyBizModel")       // Mark as business model
@BizQuery                       // Mark as query method
@BizMutation                    // Mark as mutation method
@BizAction                      // Mark as action method
```

### Dependency Injection
```java
@Inject                          // Inject bean
@Named("beanName")              // Bean with specific name
```

### Transaction
```java
@Transactional                   // Transactional method
```

### Validation
```java
@Optional                       // Optional parameter
@Name("paramName")               // Parameter name
@Description("description")        // Description
@Locale("zh-CN")               // Locale
```

## Error Handling

### Throw Exception
```java
throw new NopException(ErrorCode.ERR_ERROR_NAME)
    .param("param1", value1)
    .param("param2", value2);
```

### Common Error Codes
```java
ERR_BIZ_ENTITY_NOT_FOUND
ERR_BIZ_EMPTY_DATA_FOR_SAVE
ERR_BIZ_ENTITY_ALREADY_EXISTS
ERR_BIZ_NO_BIZ_MODEL_ANNOTATION
```

## Helper Classes

### StringHelper
```java
StringHelper.isEmpty(str);
StringHelper.isNotEmpty(str);
StringHelper.isBlank(str);
StringHelper.isNotBlank(str);
StringHelper.equals(str1, str2);
StringHelper.equalsIgnoreCase(str1, str2);
```

### ConvertHelper
```java
ConvertHelper.toInteger(value);
ConvertHelper.toLong(value);
ConvertHelper.toString(value);
ConvertHelper.toDate(value);
```

### BeanTool
```java
BeanTool.getSimpleProperty(obj, "propertyName");
BeanTool.setSimpleProperty(obj, "propertyName", value);
BeanTool.cloneBean(obj);
BeanTool.buildBean(beanClass, map);
```

### JsonTool
```java
JsonTool.instance().parseToObject(json, clazz);
JsonTool.instance().beanToJson(obj);
JsonTool.instance().stringToBean(jsonStr, clazz);
```

### DateHelper
```java
DateHelper.now();
DateHelper.parseDate(dateStr);
DateHelper.formatDate(date);
DateHelper.addDays(date, days);
```

## IoC Container

### Get Bean
```java
IBeanContainer container = BeanContainerProvider.getContainer();
MyBean bean = container.getBeanByName("myBean");
MyBean bean = container.getBeanByType(MyBean.class);
```

### Auto-wire
```java
container.autowireBean(obj);
```

## Transaction Template

### Execute in Transaction
```java
ITransactionTemplate txnTemplate = BeanContainerProvider.getBeanByType(ITransactionTemplate.class);
txnTemplate.runInTransaction(() -> {
    // Transactional code
});
```

## Common Patterns

### Service Method with Transaction
```java
@BizMutation
public void myMethod(String id) {
    txn(() -> {
        MyEntity entity = dao().requireEntityById(id);
        entity.setStatus(1);
        dao().saveEntity(entity);
    });
}
```

### Query with Dynamic Conditions
```java
@BizQuery
public List<MyEntity> search(String keyword, Integer status) {
    QueryBean query = new QueryBean();

    List<TreeBean> filters = new ArrayList<>();
    if (StringHelper.isNotEmpty(keyword)) {
        filters.add(FilterBeans.contains("name", keyword));
    }
    if (status != null) {
        filters.add(FilterBeans.eq("status", status));
    }

    if (!filters.isEmpty()) {
        query.setFilter(FilterBeans.and(filters));
    }

    return dao().findAllByQuery(query);
}
```

### Batch Operation
```java
@BizMutation
public void batchUpdate(List<String> ids, Integer newStatus) {
    List<MyEntity> entities = dao().batchGetEntitiesByIds(ids);
    for (MyEntity entity : entities) {
        entity.setStatus(newStatus);
    }
    dao().batchSaveEntities(entities);
}
```

## Quick Tips

1. **Use Example for simple queries**: `dao().findFirstByExample(example)`
2. **Use QueryBean for complex queries**: `dao().findAllByQuery(query)`
3. **Use batch methods for performance**: `dao().batchSaveEntities(entities)`
4. **Check null before using**: Always check return value of `getEntityById()`
5. **Use transaction for multi-step operations**: Wrap in `txn(() -> { ... })`
6. **Use FilterBeans for building conditions**: Don't build filter strings manually
7. **Use proper exception handling**: Throw `NopException` with error codes

## Common Pitfalls

1. ❌ `dao().findById(id)` → ✅ `dao().getEntityById(id)` or `dao().requireEntityById(id)`
2. ❌ `dao().save(entity)` → ✅ `dao().saveEntity(entity)`
3. ❌ `dao().findByIds(ids)` → ✅ `dao().batchGetEntitiesByIds(ids)`
4. ❌ `FilterBeans.eq("field", null)` → ✅ `FilterBeans.isNull("field")`
5. ❌ Query without limit → ✅ Always set limit for list queries
6. ❌ N+1 query problem → ✅ Use `batchLoadProps()` to load associations
