# Metadata

The Nop platform provides a standardized object metadata model called **XMeta**. All places where object structure needs to be defined should use the **XMeta** model.

* **XMeta and XDef**: These can be converted into each other. **XDef** corresponds to XML structure, while **XMeta** corresponds to object attribute structure.
* **NopGraphQL**: Generates exposed GraphQL types based on **XMeta** configuration.


## Entity Metadata

The Nop platform's code generator automatically generates an **XMeta** file for each entity based on the ORM model. Each entity attribute will correspond to a `prop` configuration, and additional properties are generated based on relationships like one-to-many and many-to-many.

> By default, configurations are placed in `/nop/template/meta/` and copied into `{appName}-meta` module.


## Object Configuration

Object-level configurations are as follows:

```xml
<meta>
  <entityName xdef:value="string"/>
  
  <primaryKey xdef:value="word-set"/>
  
  <!-- Display properties -->
  <displayProp xdef:value="string"/>
</meta>

<keys xdef:key-attr="name" xdef:body-type="list">
  <!-- Additional unique keys -->
  <key name="!string" props="!word-set" displayName="string" xdef:name="ObjKeyModel"/>
</keys>

<!-- Filter conditions are appended to GraphQL queries. Simple equality filters are used by default, while more complex conditions should be placed in Biz or sql-lib files -->
<filter xdef:value="filter-bean"/>

<!-- Sorting conditions are appended to GraphQL queries -->
<orderBy xdef:ref="query/order-by.xdef"/>

<!-- Tree structure -->
@parentProp corresponds to the `parentId` field
@rootParentValue is the initial value for root node search, e.g., `__null` indicates a null root node, while `0` indicates a root node with ID 0.
@childrenProp corresponds to child objects' collection property, e.g., `children`
@levelProp represents tree level structure, e.g., `level=1` indicates top-level nodes, `2` indicates second-level nodes, etc.
@rootLevelValue is the value of the `level` field for root nodes

<!-- Determine parentId -->
<tree isLeafProp="string" parentProp="!string" childrenProp="string"
      levelProp="string" rootLevelValue="string" xdef:name="ObjTreeModel"/>
</meta>
```

* **entityName**: Specifies the entity name. If there's no corresponding ORM entity, this can be empty.
* **primaryKey**: Specifies primary key fields. If there's no primary key, this can be empty.
* **displayProp**: Used for display properties, especially when used as dropdown or radio options.
* **key**: Specifies additional unique keys. Checks for conflicts during creation or updates.
* **filter**: Can specify object-level filters, e.g., `<eq name="status" value="1"/>` for active entities.
* **orderBy**: Specifies default sorting conditions.
* **tree**: Configures tree structure, with properties like `parentProp`, `childrenProp`, and level-based configurations.


## Prop Configuration

The `prop` node supports the following property configurations:


| Parameter | Default Value | Description                                                                                   |
|-----------|--------------|--------------------------------------------------------------------------------------------|
| tagSet   |              | Comma-separated extended tags, used in code generation                                                   |
| published | true         | Whether to publish as GraphQL attribute, accessible via service                                         |
| insertable | true        | Whether to include this attribute in the save operation's parameters                                   |
| updatable  | true         | Whether to allow modification of this attribute via update operation                               |
| queryable  | false        | Whether to include this attribute in the query conditions                                       |
| sortable   | false        | Whether to sort by this attribute                                                                 |
| lazy      | false        | Whether to load this entity using REST protocol without eager loading                           |
| allowFilterOp |           | Allow which operations to perform on this field, e.g., gt, ge, contains, like; default only in, eq |
| ui:filterOp | eq          | Generate frontend query form when this operation is selected                                     |
| ui:control  |             | Specify the UI control to display for this attribute; control.xlib will find the actual control based on configuration |
| ui:labelProp |           | If specified, generate forms and grids using labelProp; label field and GraphQL requests will return label field for display |
| ui:maskPattern |        | If specified, mask data in GraphQL responses using StringHelper.maskPattern function               |
| biz:codeRule |          | If specified, when creating entities without submitted code values, find code rules based on rule name and generate code automatically |
| ui:maxUploadSize |       | Maximum allowed upload size during file uploads; e.g., 20M                                         |
| ui:editGrid | sub-grid-edit | For subgrid properties: if tagSet contains grid tags, use embedded grids for creating and editing subgrids; submit both main and subgrids simultaneously by specifying which grid configuration to use |
| ui:viewGrid | sub-grid-view | For subgrid properties: specify which grid configuration to use for displaying data                 |

# Configuration Properties and Dynamic Calculations

The following document describes the configuration properties and dynamic calculation logic for a system component.

## Property Configuration

A property can be configured with specific settings that affect its behavior. Below are some common property types:

### Authorization Settings
```xml
<prop>
  <!-- Field-level permission constraints -->
  
  <auth for="!xml-name" xdef:unique-attr="for" xdef:name="ObjPropAuthModel" roles="csv-set"
        permissions="multi-csv-set"/>
</prop>

<!-- Corresponding to GraphQL argument -->
<arg xdef:name="ObjPropArgModel" name="!var-name" mandatory="!boolean=false" display-name="string"
     xdef:unique-attr="name">
  <description xdef:value="string"/>
  <schema xdef:ref="schema.xdef"/>
</arg>

<!-- Dynamic value calculation when field value is not provided -->
<autoExpr when="!csv-set" xdef:bean-body-prop="source" xdef:name="ObjConditionExpr" xdef:value="xpl"/>
```

### Value Transformation
```xml
<prop>
  <!-- Front-end input value adaptation -->
  <transformIn xdef:value="xpl"/>

  <!-- Back-end result formatting -->
  <transformOut xdef:value="xpl"/>
</prop>

<!-- Dynamic attribute generation based on current entity -->
<getter xdef:value="xpl"/>
```

### Dynamic Attribute Generation
```xml
<prop>
  <!-- Getter and setter methods for the backend entity -->
  <getter xdef:value="xpl"/>
  
  <!-- Setter method for modifying entity properties -->
  <setter xdef:value="xpl"/>
</prop>
```

## Example Configuration

Below is an example configuration that demonstrates dynamic property behavior:

```xml
<prop name="myProp" depends="~a.bMappings, otherProp">
  <getter>
    return entity.a.bMappings.size() + entity.otherProp;
  </getter>
</prop>
```

### Explanation of the Example
- **myProp**: A computed property that dynamically calculates its value based on the current state of related entities.
- **otherProp**: A dependent property that influences the behavior of myProp.
- **~a.bMappings**: A special prefix indicating that this is a reverse relationship, used for lazy loading.

When accessing `myProp` in the UI, if `otherProp` has not been set, the system will automatically calculate its value using the `autoExpr` mechanism. This ensures efficient data handling without unnecessary database queries.

# Technical Documentation Translation

The following English translation preserves the original Markdown format, including headers, lists, and code blocks. Technical terms and code snippets are accurately translated while maintaining their structure and indentation.

[EndOfData]

---

## Troublesome N+1 Problem in JPA

The N+1 problem, which has long plagued the JPA framework, has been completely resolved within the Nop platform. The solution involves providing an independent bulk loading channel that does not interfere with direct data retrieval. This approach eliminates the need for writing special code for performance optimization.

---

## Object Metadata

The `xmeta` file defines the metadata for backend service objects, detailing which attributes are available and whether they can be modified or queried. The information returned by the NopGraphQL engine is entirely determined by XMeta. If an attribute is not defined in XMeta, even if it exists in the entity, neither GraphQL nor REST queries will access it.

---

## Defining Related Attributes

In the entity model, related objects are mapped to XMeta and reflected in the configuration as follows:

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

- If a schema contains an `item`, it indicates that the attribute is a collection.
- The type of the collection elements is determined by the `bizObjName` attribute of the `item`.

---

## Querying Related Attributes

Using the `findPage` method in GraphQL queries allows direct querying of related objects' fields. However, this requires enabling `queryable` in XMeta for security reasons to prevent unintended queries that could expose sensitive data.

If `queryable` is not properly defined, errors such as `desc=undefined query field:parent.name` may occur:

```xml
<prop name="parent.name" queryable="true"/>
```

This allows querying like this:

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

Note that enabling `queryable` for a parent attribute does not automatically expose all child attributes. Each must be explicitly defined.

---

## Property Mapping (`mapToProp`)

In the meta configuration, if `<prop name="xyz" mapToProp="abc.xyz">` is defined, the frontend will display "xyz" instead of "abc.xyz". The `mapToProp` attribute translates GraphQL field names to the corresponding property access paths for data retrieval.

- In the view model, properties are accessed using `name`, not `mapToProp`.
- To use "abc.xyz", the meta configuration must include `<prop name="abc.xyz">`.

This design is implemented for security reasons to prevent unintended direct accesses to related objects.

---

## Automatic Configuration (`meta-gen.xlib`)

The `DefaultMetaPostExtends` tag in `meta-gen.xlib` automatically generates meta configurations for all models. This feature adds post-extend handling to the metadata, ensuring that all necessary properties are defined without manual intervention.

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

* GenDictLabelFields Automatically generates a corresponding label field for each value based on the dictionary configuration.
* GenConnectionFields Automatically generates a connection field with the appropriate tag for relational attributes.
* GenCodeRuleAutoExpr Automatically adds an auto-explanation rule for fields with `biz:codeRule` attribute.
* GenMaskingExpr Automatically adds masking configurations for fields with `ui:maskPattern` attribute.
* GenFilterOp Automatically adds filtering configurations for fields with `like` tags, using the `ui:filterOp` configuration.
* GenPropForDomain Automatically generates property configurations based on the domain object's attributes, using `meta-prop.xlib` transformations.

Example of the `<domain-csv-list>` component:

```xml
<domain-csv-list outputMode="node">
  <attr name="propNode"/>

  <source>
    <prop name="${propNode.getAttr('name')}>
      <schema type="List<String />"/>
    
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

For example, the `<domain-my-domain>` component:

```xml
<domain-my-domain outputMode="node">
  <attr name="propNode"/>
  <source>
    <prop name="${propNode.getAttr('name')}" queryable="false">
    </prop>
  </source>
</domain-my-domain>
```

## Common Issues

### 1. How to set automatic settings for fields that do not support querying?

You can customize the `DefaultMetaPostExtends` tag in `meta-gen.xlib` by adding custom inference logic. You can also directly use the `meta-prop.xlib` transformations for specific domains.

