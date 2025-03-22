# XDef Model Definition Language

In the Nop platform, all DSL languages use a unified XML format instead of custom syntax. This simplifies DSL design and provides a unified IDE development tool. Specifically, all DSLs use a single XDef model to define their specific syntax (XML structure), and then built-in mechanisms in the Nop platform are used to generate code, enabling the parsing and validation of DSLs.

XDef model files function similarly to XSD (XML Schema Definition) files, as both add syntactic constraints to XML formats. However, XDef is more straightforward and offers greater constraint capabilities compared to XSD.

## XDef Syntax Example

Let's examine a simple workflow DSL definition: a workflow consists of multiple steps, each defining the next executable step.

```xml
<workflow name="Test" x:schema="/nop/schema/my-wf.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps>
        <step id="a" displayName="StepA" next="b">
            <source>
                <c:script>
                    import app.MyHelper;
                    MyHelper.doSomething();
                </c:script>
            </source>
        </step>

        <step id="b" displayName="StepB" joinType="and" />
    </steps>
</workflow>
```

The corresponding model is:

```xml
<workflow name="!string" x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <steps xdef:body-type="list" xdef:key-attr="id">
        <step id="!string" displayName="string" internal="!boolean=false"
              joinType="enum:io.nop.wf.core.model.WfJoinType" next="string">
            <source xdef:value="xpl"/>
        </step>
    </steps>
</workflow>
```

From this, we can see that the XDef model has a special relationship with the model it describes. Essentially, by replacing values in the XML model with descriptors, we obtain the XDef model.

- `name="!string"` indicates that the `name` property is of type `string`, and the `!` symbol denotes that this property cannot be empty.
- `xdef:body-type="list"` means that the node resolves to a list type in the XML structure, and `xdef:key-attr="id"` means each element in the list must have an `id` attribute for differentiation.
- `internal="!boolean=false"` indicates that the `internal` property is non-empty and of type `boolean`, with a default value of `false`.
- `joinType="enum:io.nop.wf.core.model.WfJoinType"` means the `joinType` property resolves to an enum value from the `WfJoinType` type.
- `xdef:value="xpl"` indicates that the `source` node contains a code snippet in Xpl template language, which can be directly evaluated to an `IEvalAction` object (similar to JavaScript's `Function` object).

In Xdef files, all attributes (excluding those in the `xdef` and `x` namespaces) are of type `def-type`, following the format `(!~#)?{stdDomain}:{options}={defaultValue}`.

- `!` denotes required attributes.
- `~` indicates internal properties or deprecated attributes.
- `#` allows for compile-time expressions.
- `stdDomain` represents a stricter data type with more specific constraints, such as `stdDomain=email`.

For detailed information on the values and definitions of these types, refer to the dictionary definition at [core/std-domain](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/dict/core/std-domain.dict.yaml).


* Some `def-type` definitions require an `options` parameter, e.g., `enum:xxx.yyy`, which sets the specific dictionary name using `options`.  
* Properties can have default values.


## XDSL Public Schema

In the XML root node, the `x:schema` attribute is used to introduce the meta-model definition. For example, `x:schema="/nop/schema/my-wf.xdef"` indicates that the model is constrained by the `my-wf.xdef` meta-model.  
Nop platform's DSL languages have common properties and child nodes, similar to introducing some public syntax for all DSLs. The `x:schema` attribute is a part of this public syntax. These public syntactic elements are defined in the `xdsl.xdef` meta-model, so we need to declare `xmlns:x="/nop/schema/xdsl.xdef"` at the root node to represent the correspondence between the `x` namespace and the DSL public syntax space. For detailed explanations, refer to  
[ xDSL: Common Domain Specific Language Design](https://zhuanlan.zhihu.com/p/612512300) and [ xdsl.xdef ](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef).

The XDef meta-model definition language is very powerful. It can be used to describe the XDef meta-model itself. For detailed explanations, refer to [xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef).

In the `xdef.xdef` meta-meta-model definition file, the `xdef` namespace is treated as a regular namespace and should not be interpreted as an XDef meta-property. Therefore, we add the `xmlns:meta="/nop/schema/xdef.xdef"` attribute to the root node to express meta-properties using the `meta` namespace.

```xml
<workflow xmlns:meta="/nop/schema/xdef.xdef">
    <steps meta:body-type="list" meta:key-attr="id">
        ...
    </steps>
</workflow>
```

This is equivalent to:

```xml
<workflow xmlns:x="/nop/schema/xdef.xdef">
    <steps xdef:body-type="list" xdef:key-attr="id">
        ...
    </steps>
</workflow>
```


## Reusable Node Definitions

In the `xdef` file, nodes can reference existing meta-model definitions using the `xdef:ref` attribute.

1. Importing external `xdef` files  
   Example:  
   ```xml
   <form id="!string" xdef:ref="form.xdef" />
   ```

2. Referencing internal nodes  
   Nodes can have a `xdef:name` attribute to mark them as named nodes. Then, `xdef:ref` can be used to reference them.  
   Example:  
   ```xml
   <steps>
       <step id="!string" xdef:name="WorkflowStepModel">
           ...
       </step>

       <join id="!string" xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel">
       </join>
   </steps>
   ```

> Note: Currently, due to implementation reasons, `id` attributes need to be duplicated as unique identifiers for collection elements, while other attributes can be directly referenced from other nodes without being duplicated.

During code generation, `xdef:name` is interpreted as the Java class name corresponding to the node, and `xdef:ref` is interpreted as the base class of the current node class.  
For example,  
`xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel"` corresponds to  
`class WfJoinStepModel extends WorkflowStepModel`.

To simplify node reuse, the XDef language defines a special `xdef:define` attribute for reusable nodes, which is used only for reuse purposes. For example,

```xml
<workflow>
    <xdef:define xdef:name="WorkflowStepModel" id="!string">
        ....
    </xdef:define>

    <steps xdef:body-type="list" xdef:key-attr="id">
        <step xdef:ref="WorkflowStepModel" id="!string"/>
    </steps>
</workflow>
```

`xdef:define` is used to define a reusable component, equivalent to defining a base class. Child nodes can inherit this base class using `xdef:ref`. `xdef:name` serves as the class name of the base class.

## Collection Node Definition

In addition to the `xdef:body-type="list"` for defining collection nodes, xdef language provides an alternative simplified way to define collection nodes: using `xdef:unique-attr` to represent the uniqueness attribute of collection elements.

```xml
<arg name="!string" xdef:unique-attr="name" value="any" />
```

Nodes with the `xdef:unique-attr` attribute will be parsed as unique attributes. The attribute names typically follow camelCase convention, such as `task-step` corresponding to `taskSteps`. The `xdef:bean-prop` attribute can also be used to specify the property name, for example `xdef:bean-prop="taskStepList"`.

```xml
<!--
  Below DSL is equivalent to the code:
  bp.taskSteps.add({id: 'a', displayName: 'A'})
  bp.taskSteps.add({id: 'b', displayName: 'B'})
-->
<task-step id="a" displayName="A"></task-step>
<task-step id="b" displayName="B"></task-step>
```

Using `xdef:body-type="list"` allows the collection to contain nodes of different types, such as:

```xml
<steps xdef:body-type="list" xdef:key-attr="name" xdef:bean-sub-type-prop="type" xdef:bean-child-name="step"
       xdef:bean-body-type="List&lt;io.nop.wf.core.model.WfStepModel>">
    <step name="!string" xdef:bean-tag-prop="type" />
    <join name="!string" xdef:bean-tag-prop="type" />
</steps>
```

* `xdef:bean-body-type` is used to specify the type of the collection's child elements.
* `xdef:bean-child-name="step"` automatically adds a method `getStep(String name)` to retrieve child nodes based on their unique identifier.
* `xdef:bean-tag-prop="type"` translates the tag names (`step`, `join`) into the `type` property during JSON serialization.
* `xdef:bean-sub-type-prop="type"` determines the type of child nodes during JSON deserialization based on the `type` attribute.

```xml
<!--
  Below DSL is converted to JSON as:
  {
    "steps": [{
      "type": "step",
      "name": "a"
    }, {
      "type": "join",
      "name": "b"
    }]
  }
-->
<steps>
    <step name="a"/>
    <join name="b"/>
</steps>
```

## Common Questions

1. What is the difference between `<parent> <item xdef:value="string"> </parent>` and `<parent item="string">` in terms of XML structure?  
   There is no difference in the XML structure; only the DSL representation differs.

2. If you specify `xdef:body-type="list"`, does it mean that its child nodes must be object types? How should basic data types like int or string be handled?  
   Child nodes can be of any type, including primitive types like int or string.
# Handling List Data

## Common Cases

I have not dealt with this situation before, but if I must handle it. The current approach is similar to the following:

```markdown
- For `<list xdef:body-type="list">`, the value can be set as `<_ xdef:value="int" />`
```

## CSV List Handling

For commonly used comma-separated list values, you can directly use:

```markdown
<list xdef:value="csv-list" />
```

## Node with Body Type Set

3. If a node already has `xdef:body-type="list"`, but also has other attributes, for example:

```markdown
<row name="string" xdef:body-type="list" height="string">
```

What structure will this generate?

In the default case, body content will correspond to the body attribute. This follows AMIS conventions, where `tagName` corresponds to `type`, and `bodyContent` corresponds to `body`.

