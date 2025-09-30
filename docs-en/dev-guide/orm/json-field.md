
# JSON Field Support

In NopORM, JSON fields provide native support for JSON data. Through standard domain (stdDomain) configuration and auto-generated component classes, they enable convenient manipulation of JSON data.

## Basic Configuration

In the ORM model, set the column’s `stdDomain` to `json`. During the `<orm-gen:DefaultPostExtends>` processing, the system will automatically execute `JsonComponentSupport`, generating a corresponding Component field for each JSON column.

**Example Configuration**:

```xml
<!-- NopRuleNode.orm.xml -->
<entity name="NopRuleNode">
  <column name="outputs" stdDomain="json" length="2000"/>
</entity>
```

## Auto-Generated Component Field

The above configuration will automatically generate the `outputsComponent` field, providing the following features:

- Automatic JSON parsing and serialization
- Structured access methods
- Support for direct manipulation of the internal JSON structure

### Metadata Mapping Configuration

In the `xmeta` file, you can configure an alias mapped to the internal properties of the Component:

```xml
<!-- NopRuleNode.xmeta -->
<prop name="outputsMap"
      mapToProp="outputsComponent._jsonMap"
      displayName="Output Values"
      lazy="true"/>
```

**Configuration Notes**:

- When the frontend submits the `outputsMap` field, it actually invokes `entity.getOutputsComponent().set_JsonMap(data)`
- Read operations are performed via the `outputsComponent` object
- `lazy="true"` indicates lazy loading of JSON data

## Detailed Explanation of the Component Concept

Here, Component is a backend concept, similar to Hibernate’s Embedded component:

- One or more fields are combined into a Component object
- Provides enhanced composition methods
- JsonComponent encapsulates JSON parsing/serialization logic
- FileComponent encapsulates attachment file storage functionality

<!-- SOURCE_MD5:25a5ad2211d32d4a7a2703ea8f1c9ad9-->
