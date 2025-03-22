  # From Reversible Computation to Scalable Backend Service Function Design

Many low-code platforms are essentially built around a CRUD model, typically offering limited customization through built-in extension points (e.g., before insert, after insert). In the Nop platform, the CRUD model lacks any special characteristics. The CrudBizModel is just an ordinary BizModel in the Nop core and does not involve any special handling for CRUD extension points. In this article, I will use CrudBizModel as an example to explain common backend service implementation strategies in the Nop platform.

## 1. Using Callback Functions for Custom Timing

Most service functions in CrudBizModel use a two-layer abstraction. When an upper layer function calls the underlying implementation function, it uses parameters and callback functions to enable customization.

```javascript
    public PageBean<T> findPage(@Optional @Name("query") QueryBean query,
                                FieldSelectionBean selection, 
                                IServiceContext context) {
        return doFindPage0(query, getBizObjName(), prepareQuery, 
                   selection, context);
    }

    @BizAction
    public PageBean<T> doFindPage0(@Name("query") QueryBean query,
                                   @Name("authObjName") String authObjName,
                                   @Name("prepareQuery") BiConsumer<QueryBean, 
                                   IServiceContext> prepareQuery, 
                                   FieldSelectionBean selection,
                                   IServiceContext context) {
         query = prepareFindPageQuery(query, authObjName, 
                     METHOD_FIND_PAGE, prepareQuery, context);                      
        ...
    }
```

The findPage function leverages the `doFindPage0` flexible function. The `doFindPage0` provides two configurable parameters:

1. **authObjName**: By default, its value corresponds to the current business object name (`bizObjName`), but specifying a different value allows for applying different data access control conditions in various business scenarios.
2. **prepareQuery**: This parameter corresponds to the query conditions passed from the front end. The backend uses XMeta models to validate all query fields and operators within allowed ranges. The `prepareQuery` callback function can add additional query conditions to the front-end-provided query, without requiring validation for these added conditions.

Using callback functions provides a flexible extension mechanism that is more versatile than class inheritance and method overloading.

## 2. Leveraging XMeta Metadata Model for Unified Dynamic Processing

General function reuse involves duplicating processing logic, which is typically extended through a limited number of callbacks. However, many times the processing logic is not entirely similar, **we can abstract only a handling pattern**. For example, the basic logic of the save function is as follows:

1. Validate submitted field information.
2. For entities supporting logical deletion, check if any marked-for-deletion entities exist.
3. Check for duplicate records in the database, such as ensuring no two users have the same identity card.
4. Create entity objects based on request data, with special handling required for complex parent-child table structures.

The overall structure of save logic across different entities is consistent, but specifics vary, such as field types and validation rules, along with conversion logic that transforms front-end input values into formats required by the backend. Unique identifier fields also differ between entities.

In the Nop platform, each business object can be associated with an XMeta file, which defines metadata for the business object.

  
> XMeta metadata is more flexible and powerful than Java annotations. It supports meta programming and custom extensions, enabling automatic structural validation through the XDef metadata model. For details about XMeta, see [xmeta.md](../xlang/xmeta.md).
  

```java
public T save(@Name("data") Map<String, Object> data, IServiceContext context) {
    return doSave(data, null, this::defaultPrepareSave, context);
}

@BizAction
public T doSave(@Name("data") Map<String, Object> data,
                @Name("inputSelection") FieldSelectionBean inputSelection,
                @Name("prepareSave") BiConsumer<EntityData<T>, IServiceContext> prepareSave,
                IServiceContext context) {
    if (CollectionHelper.isEmptyMap(data))
        throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_SAVE)
             .param(ARG_BIZ_OBJ_NAME, getBizObjName());

    // 1. Perform input validation and transformation based on XMeta configuration
    ObjMetaBasedValidator validator = 
        new ObjMetaBasedValidator(bizObjectManager, bizObj.getBizObjName(),
            objMeta, context, true);

    Map<String, Object> validated = 
          validator.validateForSave(data, inputSelection);

    // 2. Determine whether to enable logical deletion based on ORM entity model parameters
    T entity = recoverLogicalDeleted(data, objMeta);
    boolean recover = true;
    if (entity == null) {
        recover = false;
        entity = dao().newEntity();
    }

    EntityData entityData = new EntityData<>(data, validated, entity, objMeta); 

    // 3. Check for duplicate records based on XMeta configuration
    checkUniqueForSave(entityData);

    // 4. Copy main and subordinate table data to the new entity object as per XMeta configuration
    new OrmEntityCopier(daoProvider, bizObjectManager)
            .copyToEntity(entityData.getValidatedData(),
                entityData.getEntity(), null, entityData.getObjMeta(), 
                getBizObjName(), BizConstants.METHOD_SAVE, 
                context.getEvalScope());

    // 5. Verify data permissions after setting properties for current user visibility
    checkDataAuth(BizConstants.METHOD_SAVE, entityData.getEntity(), context);

    // 6. Execute additional custom logic
    if (prepareSave != null)
        prepareSave.accept(entityData, context);

    doSaveEntity(entityData, context);

    return entityData.getEntity();
}

The Nop platform provides a general-purpose `ObjMetaBasedValidator` and `OrmEntityCopier`, which leverage XMeta metadata models to **unify input validation** and entity object construction.

Similar patterns are frequently used in various generic processing functions, such as when `findTreeEntityPage` utilizes XMeta's `TreeModel` configuration to generate attribute structure query statements.

Using XMeta also offers the benefit of Delta customization. Different applications can use distinct XMeta models for the same business object, allowing tailored handling of actual processing logic without modifying the base product source code.

**The combination of GraphQL's object composition ability and XMeta's object structure abstraction ability, along with reversible computing's delta capabilities, enables most CRUD-related logic to be solidified. Generally, no CRUD-specific codes need to be written, nor do大量针对不同场景的代码需要生成**, as a unified implementation suffices for primary requirements. The only additional step is to supplement the standard CRUD processing flow with information about deviations from it.

## Three. Leveraging Prefix Syntax in Local Extended Model Expansion

The Nop platform employs a language-oriented programming paradigm (Language Oriented Programming), meaning that to address current business challenges, we first establish a domain-specific language (DSL) tailored to the problem before using this DSL to express business logic.

Do not view DSL as overly mysterious or complex. Its essence is modelization; once an abstract model is established for a business issue and a text representation format is selected for it, the model naturally becomes a DSL.

In the context of front-end/back-end separation and microservices, all interactions between front-end and back-end, as well as between back-end services, must be conducted using serialized object data. By merging related functionalities on the semantic level and exposing only limited, coarse-grained service interfaces externally, the parameters of these service interfaces can function as a DSL.

**Service functions can be regarded as a kind of virtual machine executing the DSL. The input parameters guide the virtual machine to execute different processing logics**. For example, the `findPage`/`findList` generic query functions provided by `CrudBizModel` accept query condition objects like `QueryBean`, which can be viewed as a DSL describing composite query conditions for complex object structures.

```
POST /r/NopAuthDept__findPage

{
   "query": {
      "filter": {
          "$type": "or",
          "$body": [
             {
                "$type": "eq",
                "name": "status",
                "value": 1
             },
             {
                "$type": "gt",
                "name": "parent.status",
                "value": 2
             }
          ]
      },
      "orderBy":[
         {
           "name": "status",
           "desc" : false
         }
      ]
   }
}
```

The `query` object corresponds to the `QueryBean` structure in the backend, serving as a generic query model that can seamlessly switch between XML and JSON formats.

```xml
<query>
  <filter>
     <or>
       <eq name="status" value="1" />
       <gt name="parent.status" value="2" />
     </or>
  </filter>
  <orderBy>
     <field name="status" desc="false" />
  </orderBy>
</query>
```

Through the `QueryBean` query model, complex conditions with nested `and/or` relationships can be expressed. Leveraging NopORM's association query capabilities, properties like `parent.status` are utilized to automatically implement parent-child table association queries.

> Generally, a single unified `findPage` function is sufficient for handling various business queries, without the need to write numerous individual query functions. The XMeta model can control which fields support which query operators and how many fields can be queried at once, preventing complex query construction that could lead to denial of service attacks.

  **Each model can be considered as a Domain-specific language (DSL), and the same DSL can be interpreted using different interpreters in various application scenarios**. For example, taking the QueryBean model, it can be converted into an SQL statement to be executed against the database or compiled into a Predicate function that runs in memory. The rule engine can use the QueryBean model to express complex judgment conditions, while the frontend business rule designer can automatically generate visualization and editing tools based on the DSL content.

![](../rule/images/rule-model.png)

The Nop platform ships with numerous built-in DSLs and provides various data transformation capabilities (e.g., each DSL automatically includes Excel data modeling, enabling Excel-based configuration without programming). This allows seamless integration between different DSLs. As a result, during general business development, we do not need to create new DSLs.

However, this does not mean that we are limited to using the built-in models in the Nop platform. All models undergo multiple transformations before execution within the Nop platform. During this process, we can introduce our own DSL translation rules to extend the existing DSL models with additional semantics.

A commonly used technique in the Nop platform is prefix-based syntax. Specifically, it introduces a special prefix like `@filter:` **to enhance a value into a domain structure that can be interpreted by the interpreter**.

> For detailed information on prefix-based syntax, please refer to my article [DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138).

This approach ensures that the overall form of existing DSL models remains unchanged while allowing for localized extensions. This enables it to coexist with other syntax structures.

For example, if a user proposes a specific query requirement, they may wish to filter already selected records in a concise manner. This can be represented using prefix-based syntax as:

```xml
<notIn name="id" value="@filter:selectedItemIds" />
```

All query conditions in CrudBizModel will apply the global IQueryTransformer transformation.

```java
public interface IQueryTransformer {
    void transform(QueryBean filter, String authObjName, String action,
                   IBizObject bizObj, IServiceContext context);
}
```

Combining this with the capabilities from the previous section (XMeta), we can retrieve Xpl template labels via bizObj and determine how `@filter:` prefixes should be interpreted. A feasible approach is to map it directly to an Xpl template label and generate subqueries or dynamically fetch the corresponding data collection.

## Four. Extending Service Functions with XBiz Models

The Nop platform uses a layered structure similar to Docker image slicing on the global logical organization layer. For backend services, the Nop platform decomposes BizObject into multiple slices with different priorities.

![](gather-and-scatter.png)

For example, a Java-developed CrudBizModel can be considered as a foundational behavior slice developed using the ProCode pattern. Each business object has an associated XBiz model file (in XML format), which acts as an extended BizModel DSL language. In this file, we can define business methods using XML syntax. The XBiz model is akin to a higher-priority behavior slice that overlays the underlying CrudBizModel. If a service method with the same name is defined in the XBiz model, it will directly override the Java implementation. If not, it will add new business methods to the BizObject. At an even higher level, dynamic behavior slices can be introduced using no-code programming and stored in a dynamic model definition table within the database. Upon startup, these are automatically loaded as virtual files in the virtual file system and then used with Delta customization to override existing XBiz files.


```xml
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
    x:extends="_NopAuthUser.xbiz" xmlns:bo="bo" xmlns:c="c">
    
    <actions>
        <query name="active_findPage" x:prototype="findPage">
            <source>
                <c:import class="io.nop.auth.api.AuthApiConstants" />
                
                <bo:DoFindPage query="${query}" selection="${selection}" 
                    xpl:lib="/nop/biz/xlib/bo.xlib">
                    <filter>
                        <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}" />
                    </filter>
                </bo:DoFindPage>
            </source>
        </query>
    </actions>
</biz>
```

* The XBiz model can inherit existing model files or auto-generated files using the generic `x:extends` syntax.
* Within the `<source>` section, we can utilize custom tags in Xpl Template Language to implement encapsulation. **Xpl Template Language provides implicit parameters and compile-time transformations, enabling a more concise domain-specific expression compared to general programming languages.**

For example, the `bo.xlib` tag set provides encapsulations for functions like `doFindPage/doFindList` within `CrudBizModel`.

```xml
<source>
    <bo:DoFindPage bizObjName="NopAuthUser" 
        xpl:lib="/nop/biz/xlib/bo.xlib" selection="items{name,status}">
        <filter>
            <c:if test="${xxx}">
                <eq name="status" value="1" />
            </c:if>  

            <!--Can use a more concise expression -->
            <eq name="status" value="1" xpl:if="xxx" />
        </filter>
    </bo:DoFindPage>
</source>
```

* The `<bo:DoFindPage>` tag will call the specified business object's method if `bizObjName` is provided, otherwise it calls the method on the current context's `thisObj`.
* If the `selection` parameter is specified, it will automatically invoke `dao.batchLoadSelection(entityList, selection)` after retrieving the entity objects, batch-loading all specified properties to avoid subsequent lazy loading triggers and improve performance.
* The `<bo:DoFindPage>` tag's `<filter>` child node essentially provides the `prepareQuery` callback function for the `doFindPage` method discussed earlier. Here, you can use Xpl Template Language to dynamically generate query conditions.

The implementation of the `bizObjName` and `selection` properties within this tag is particularly interesting as they leverage Xpl custom tag compile-time transformation mechanisms.

```xml
<DoFindPage>
    <attr name="query" optional="true" type="io.nop.api.core.beans.query.QueryBean"/>
    <attr name="authObjName" optional="true" type="String" />
    <attr name="selection" optional="true" type="io.nop.api.core.beans.FieldSelectionBean"/>
    <attr name="bizObjName" optional="true" />
    <attr name="thisObj" implicit="true" type="io.nop.biz.api.IBizObject"/>
    <attr name="svcCtx" implicit="true" type="io.nop.core.context.IServiceContext"/>

    <transform>
         <c:script><![CDATA[
            const bizObjName = node.attrText('bizObjName');
            if(bizObjName != null){
               $.checkArgument(bizObjName.$isValidSimpleVarName(),"bizObjName");
               node.setAttr(node.attrLoc('bizObjName'),'thisObj', "${inject('nopBizObjectManager').getBizObject('" +bizObjName+"')}");
            }
            const selection = node.attrText('selection');
            if(selection and !selection.contains('${')){
                node.setAttr(node.attrLoc('selection'),'selection', "${selection('"+selection+"')}");
            }
        ]]></c:script>
    </transform>
    <source>
      ...
    </source>
</DoFindPage>        
```

When compiling, parameters `bizObjName` or `selection` being non-empty will automatically be converted into expressions

```xml
<bo:DoFindPage thisObj="${inject('nopBizObjectManager').getBizObject('NopAuthUser')}"
    selection="${selection('items{name,status}')}">
  ...
</bo:DoFindPage>
```

* The `selection` function is a global macro function that will resolve its parameters at compile time and convert it into a `FieldSelectionBean` object. At runtime, the pre-resolved result can be used directly without re-parsing.

* When comparing this to implementing the same functionality using Java, it becomes evident that Xpl tags provide a more concise way of calling, allowing for automatic derivation of information that would otherwise need to be explicitly stated.

* Imagine how one would express the logic "if bizObjName is specified, call the specified object; otherwise, call thisObj"? How can one completely hide the concept of thisObj when it's not needed in the DSL description?

## Five. Enhancing XBiz Models with Meta-Programming

Once an XBiz DSL model file is introduced, standardized meta-programming patterns can be immediately applied to enhance the DSL model with custom extensions. For example, in an XBiz file, you can add logic support as follows:

```xml
<biz>
  <x:post-extends>
    <biz-gen:TaskFlowSupport xpl:lib="/nop/core/xlib/biz-gen.xlib"/>
  </x:post-extends>

  <actions>
    <mutation name="callTask" task:name="test/DemoTask"/>
  </actions>
</biz>
```


* The `x:post-extends` transformation is automatically executed during the compilation phase. It transforms function nodes with the `task:name` attribute via the `<biz-gen:TaskFlowSupport>` tag and generates automatic calls to the TaskFlowManager.

* Tasks can be designed using a visualization-based logic arrangement designer. Simply specify the associated `task:name` in the XBiz model for service functions.

For detailed information, please refer to:
  
* [Implementation of Backend Service Functions Using NopTaskFlow Logic Arrangement](../workflow/task-flow-for-biz.md)

* [XDSL: A General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)