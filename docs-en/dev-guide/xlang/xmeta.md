# Metadata

The Nop platform provides a standardized object metadata model, XMeta. All places that need to define object structures uniformly use the XMeta model.

* XMeta and XDef can be converted to each other. XDef corresponds to an XML structure, while XMeta corresponds to an object property structure.
* NopGraphQL generates externally exposed GraphQL types based on the XMeta configuration.

## Entity Metadata

The Nop platform’s code generator automatically generates a corresponding XMeta file for each entity based on the ORM model. Each entity property will have a corresponding object prop configuration. On this basis, some auxiliary properties will be generated according to one-to-many, many-to-many, and other association configurations.

> By default, the /nop/template/meta template is used to generate into the {appName}-meta module.

## Object Configuration

The following configurations exist at the object level:

```xml
<meta>
  <entityName xdef:value="string"/>

  <primaryKey xdef:value="word-set"/>

  <!--
  Fields used for display, such as displayName. Selection controls will use this field.
  -->
  <displayProp xdef:value="string"/>

  <keys xdef:key-attr="name" xdef:body-type="list">
    <!--
    Other unique keys besides the primary key
    -->
    <key name="!string" props="!word-set" displayName="string" xdef:name="ObjKeyModel"/>
  </keys>

  <!--
  Filter conditions. Will be appended to the GraphQL query conditions. Because these filters are also checked during update and view,
  they are generally simple equality filters. More complex, business-related query conditions should be written in Biz or sql-lib files.
  -->
  <filter xdef:value="filter-bean"/>

  <!-- Sorting conditions. Appended to the GraphQL query conditions -->
  <orderBy xdef:ref="query/order-by.xdef"/>

  <!--
  Tree structure
  @parentProp Corresponds to a field pointing to the parent node, such as parentId
  @rootParentValue The initial value used to find the root node for the parent field, e.g., __null means find roots with null, 0 means find roots with 0
  @childrenProp Corresponds to the collection property in the parent object that holds the children, e.g., children
  @levelProp The level property for the tree structure, e.g., level=1 indicates first-level nodes, 2 indicates second-level nodes, etc.
  @rootLevelValue The value of the level field that corresponds to root nodes
  -->
  <!-- Determine the parentId value for root nodes in the following order: levelProp -> parentProp. If neither is configured, use parentId=__null to filter and obtain root nodes. -->
  <tree isLeafProp="string" parentProp="!string" childrenProp="string"
        levelProp="string" rootLevelValue="string" xdef:name="ObjTreeModel"/>
</meta>
```

* entityName specifies the corresponding entity name. If there is no entity object in the ORM layer, this can be empty. Meta metadata is not limited to database entities.
* primaryKey specifies the list of primary key fields. If there is no primary key, this can be empty.
* displayProp specifies the display field. When used as a dropdown option or selected option, this field is used to display the object.
* key specifies other unique keys besides the primary key. When creating or modifying an entity, a uniqueness check is automatically performed to avoid conflicts.
* filter specifies object-level auto-assigned filter conditions, for example `<eq name="status" value="1" />`, which can be used to filter active entities. During create or modify operations, property values in filter are automatically set to ensure the filter constraints are not bypassed.
* orderBy specifies the default object-level sorting conditions.
* tree supplements tree-structure-related field information.

## prop Configuration

The prop node supports the following attribute configurations:

| Name             | Default        | Description                                                                                               |
|------------------|----------------|-----------------------------------------------------------------------------------------------------------|
| tagSet           |                | Comma-separated extension tags used during code generation                                                |
| published        | true           | Whether to expose this property via GraphQL so it can be accessed through services                        |
| insertable       | true           | Whether this property is allowed in the parameters of the save operation                                  |
| updatable        | true           | Whether this property can be modified via the update operation                                            |
| queryable        | false          | Whether this property can be included in query conditions                                                 |
| sortable         | false          | Whether sorting by this property is allowed                                                               |
| lazy             | false          | Whether this property is not returned by default when accessing entity objects via REST                   |
| allowFilterOp    |                | Which query operations are allowed for this field, e.g., gt, ge, contains, like, etc.; default is only in, eq |
| ui:filterOp      | eq             | The default query operation used when generating the frontend query form                                  |
| ui:control       |                | Directly specify which control to use to display this property; control.xlib will resolve the actual control |
| ui:labelProp     |                | If labelProp is specified, the label field is displayed in view mode when generating forms and tables; GraphQL also returns the label field for display on the frontend |
| ui:maskPattern   |                | If a mask pattern is specified, GraphQL will automatically call StringHelper.maskPattern to mask data     |
| biz:codeRule     |                | If a code rule is specified, when creating an entity, if the frontend does not submit a code value, one is automatically generated based on the code rule configuration |
| ui:maxUploadSize |                | Maximum allowed file size for uploads, e.g., 20M                                                          |
| ui:editGrid      | sub-grid-edit  | For subtable collection properties, if tagSet contains the grid tag, it indicates using an embedded grid to edit subtables during create and modify, with master and sub tables submitted together. Use this parameter to specify which subtable grid configuration to use for editing |
| ui:viewGrid      | sub-grid-view  | For subtable collection properties, specify which subtable grid configuration to use for displaying data  |
| depends          |                | If depends is specified, the GraphQL request will automatically load these associated properties. When view.xml generates the ajax call, these properties will also be returned to the frontend (if a property in depends has a ~ prefix, it is only loaded on the backend and not returned to the frontend). |

prop also supports the following node-level configurations:

```xml
<prop>
  <!--
      Configure field-level access control

      @for If set to all, this access control applies to all operations.
      If set to read, the constraint is used when reading. If write-related auth is not configured,
      modification is effectively not allowed.
      If set to write, the constraint is used for both reading and modifying.
  -->
  <auth for="!xml-name" xdef:unique-attr="for" xdef:name="ObjPropAuthModel" roles="csv-set"
        permissions="multi-csv-set"/>

  <!-- Corresponds to a GraphQL argument -->
  <arg xdef:name="ObjPropArgModel" name="!var-name" mandatory="!boolean=false" displayName="string"
       xdef:unique-attr="name">
    <description xdef:value="string"/>
    <schema xdef:ref="schema.xdef"/>
  </arg>

  <!-- When creating or modifying, if the frontend does not send a value for this field, it can be computed automatically via autoExpr -->
  <autoExpr when="!csv-set" xdef:bean-body-prop="source" xdef:name="ObjConditionExpr" xdef:value="xpl"/>

  <!-- Adapt/convert the value input from the frontend -->
  <transformIn xdef:value="xpl"/>

  <!-- The value returned from the backend may need format conversion -->
  <transformOut xdef:value="xpl"/>

  <!-- Generate dynamic properties based on the current entity. Getter and setter are backend entity-layer functions, similar to get/set methods on a Java object -->
  <getter xdef:value="xpl"/>

  <!-- Process externally provided values, potentially setting properties on the entity object -->
  <setter xdef:value="xpl"/>
</prop>
```

* Use auth to specify field-level access control rules. read and write can have different permissions.
* Use arg to set request parameters supported by the GraphQL protocol. In other words, a prop may correspond to a dynamic property function that supports request parameters.
* autoExpr is used to automatically compute property values during create or modify, e.g., automatically computing the total price based on item prices in a subtable. The execution context contains an entity object corresponding to the current entity.
* transformIn converts parameter values submitted by the frontend to values used by the backend. The execution context contains variables such as value and data; data corresponds to the request parameters sent by the frontend, and value corresponds to the property value.
* transformOut converts an entity’s property value to the result value returned to the frontend. transformOut processes based on context variables such as value and data, and returns the result value.
* getter and setter act as replacements for setXX and getXX methods on the entity. The context contains parameters such as entity and value.

ui:filterOp corresponds to the frontend-generated control name format: `filter_{name}__{filterOp}`, for example, `filter_userStatus__in`.

## Dependent Data Loading

```xml
<prop name="myProp" depends="~a.bMappings,otherProp">
  <getter>
    return entity.a.bMappings.size() + entity.otherProp;
  </getter>
</prop>
```

In the example above, myProp is a computed property. It is dynamically computed based on associated properties on the entity, with the computation expression defined in the getter block.

Assume otherProp is a lazy property (lazy=true on prop), and `a.bMappings` is an associated collection from an association table. If you directly execute
`entity.a.bMappings.size()+entity.otherProp`, it will trigger three lazy-loading calls.
Returning this property in a list would cause a large number of database accesses. By specifying the associated properties used in the depends attribute, the backend ORM engine will automatically batch load these properties, and then invoke the getter computation expression after loading.

* `~a.bMappings` has a special `~` prefix indicating that this associated property is read only on the backend and not returned to the frontend.
* `otherProp` is a normal property that will be loaded on the backend and, when `view.xml` translates an ajax call, included as part of the GraphQL selection.

The N+1 problem that plagues JPA frameworks is thoroughly solved in the Nop platform. The specific solution is to provide an independent data batch loading channel that does not affect direct data retrieval. There is no need to write a lot of special code just to optimize retrieval performance.

```javascript
Set<MyEntity> entities = ...;
// Insert an extra batch-loading invocation
dao.batchLoadProps(entities, Arrays.asList("prop2", "prop3.prop4"));

for(MyEntity entity: entities){
   // At this point all data has been loaded into memory. Accessing associated properties
   // of the entity will not trigger lazy loading calls and will not hit the database.
   entity.getProp3().getProp4();
}
```

## Object Metadata

An xmeta file defines metadata for backend service objects, describing which properties an object has, whether these properties can be modified, whether they can be queried, etc.
The object information returned by the NopGraphQL engine is entirely defined by XMeta. If a property is not defined in XMeta, then even if the entity has this field, frontend GraphQL and REST requests cannot access it.

## Defining Associated Properties

Associated objects in the entity model are reflected in the XMeta model with the following configuration:

```xml
<props>
  <prop name="parent">
    <schema bizObjName="NopAuthDepartment"/>
  </prop>

  <prop name="children">
    <schema>
      <item bizObjName="NopAuthDepartment"/>
    </schema>
  </prop>
</props>
```

* If schema has an item, it indicates a collection property. The type of collection elements is specified by the bizObjName attribute on the item node.
* If it is an associated object, specify the association type via the bizObjName attribute on schema.

## Querying Associated Properties

When querying via the GraphQL findPage method, you can directly query fields on associated objects, but you must set queryable in xmeta. This is for security reasons to avoid exposing arbitrary fields that could lead to security vulnerabilities.

If not defined correctly, the console may show the error: `desc=Undefined query field:parent.name`

```xml
<prop name="parent.name" queryable="true">

</prop>
```

You can then query like:

```json
{
  "query": [
    {
      "$type": "eq",
      "name": "parent.name",
      "value": "aaa"
    }
  ]
}
```

Note that setting queryable on the parent property does not automatically open all of parent’s properties to queries. You must specify each property individually.

## Property Mapping mapToProp

If the meta contains `<prop name="xyz" mapToProp="abc.xyz">`, then the frontend sees the property name `xyz` instead of `abc.xyz`.
mapToProp means that during backend execution, it translates the field name from the GraphQL request to the property access path specified by mapToProp to get the data.
The view model also uses the prop’s name, not mapToProp. If the frontend needs to use `abc.xyz` directly, you must also configure `<prop name="abc.xyz">` in meta. This design is for security reasons and does not allow direct access to associated objects.

## Automatically Inferring prop Configuration Based on domain

The DefaultMetaPostExtends tag in `meta-gen.xlib` adds post-extends processing for all meta generated by model-driven logic.

```xml
<DefaultMetaPostExtends outputMode="node">
  <attr name="_dsl_root" implicit="true"/>

  <source>
    <thisLib:GenDictLabelFields/>
    <thisLib:GenConnectionFields/>
    <thisLib:GenCodeRuleAutoExpr/>
    <thisLib:GenMaskingExpr/>
    <thisLib:GenFilterOp/>
    <thisLib:GenPropForDomain/>
  </source>
</DefaultMetaPostExtends>
```

* GenDictLabelFields Automatically generate a corresponding label field for each value field based on dict configuration.
* GenConnectionFields Automatically generate Relay Connection fields for associated properties with the connection tag.
* GenCodeRuleAutoExpr Automatically add autoExpr configuration for fields that have the `biz:codeRule` attribute.
* GenMaskingExpr Automatically add transformOut configuration for fields that have the `ui:maskPattern` attribute.
* GenFilterOp Automatically add the `ui:filterOp` configuration for fields with the like tag.
* GenPropForDomain Automatically generate prop configurations based on the attributes of domain objects.

Among these, GenPropForDomain executes tags in `meta-prop.xlib` to perform transformations.

```xml
<domain-csv-list outputMode="node">
  <attr name="propNode"/>

  <source>
    <prop name="${propNode.getAttr('name')}">
      <schema type="List&lt;String>"/>

      <transformIn>
        return value?.$toCsvListString();
      </transformIn>

      <transformOut>
        return value?.$toCsvList();
      </transformOut>
    </prop>
  </source>
</domain-csv-list>
```

For example, the above tag adds schema and transformIn/transformOut configurations for fields with domain=csv-list.

## FAQ

### 1. How to automatically mark fields as non-queryable based on domain

You can customize the DefaultMetaPostExtends tag in `meta-gen.xlib` to add your own inference logic. You can also directly generate specified properties for a given domain in `meta-prop.xlib`.

```xml
<domain-my-domain outputMode="node">
  <attr name="propNode"/>
  <source>
    <prop name="${propNode.getAttr('name')}" queryable="false">
    </prop>
  </source>
</domain-my-domain>
```
<!-- SOURCE_MD5:26fc116cf8653443723a3b67106f0484-->
