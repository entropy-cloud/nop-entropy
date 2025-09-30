
# XDef Meta-Model Definition Language

All DSLs in the Nop platform uniformly adopt the XML format rather than custom surface syntaxes, which simplifies DSL design and provides a unified IDE development toolset. Specifically, every DSL uses a unified XDef meta-model to define the concrete syntax of the DSL (the XML structure), and then leverages a series of built-in mechanisms in the Nop platform to automatically generate code, implementing DSL parsing, validation, and other functions.

The role of an XDef meta-model file is similar to an XSD (XML Schema Definition) file: both add syntactic constraints to XML. However, XDef is simpler and easier to use compared to XSD, while also offering more powerful constraint capabilities.

## XDef Syntax Example

Let's look at a simple workflow DSL definition: a workflow contains multiple steps, and each step, upon completion, specifies the next executable step.

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

The corresponding meta-model is

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

First, we can see that the XDef meta-model and the model it describes are in a homomorphic relationship; put simply, by replacing values in the model's XML with type descriptors, you obtain the XDef meta-model.

* `name="!string"` indicates that the `name` attribute is of type `string`; the character `!` means the attribute value cannot be null.
* `xdef:body-type="list"` means the node, after parsing, corresponds to a list type; `xdef:key-attr="id"` means each element in the list must have an `id` attribute, which distinguishes different elements.
* `internal="!boolean=false"` means the `internal` attribute is non-null, of type `boolean`, with a default value of `false`.
* `joinType="enum:io.nop.wf.core.model.WfJoinType"` means the value of the `joinType` attribute is of type `WfJoinType`, which is an enum.
* `xdef:value="xpl"` indicates the content of the `source` node (including its direct text content and all child nodes) is a snippet of Xpl template language code, which after parsing yields an `IEvalAction` object (similar to a `Function` object in JavaScript).

All attributes in xdef files (excluding built-in attributes in the `xdef` and `x` namespaces) have values of type `def-type`. Its format is `(!~#)?{stdDomain}:{options}={defaultValue}`.

* `!` indicates a required attribute, `~` indicates an internal or deprecated attribute, and `#` indicates a compile-time expression can be used.
* `stdDomain` is a constraint stricter than a data type, e.g., `stdDomain=email`. See the dictionary definition [core/std-domain](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/dict/core/std-domain.dict.yaml).
* Some `def-type` definitions require `options` parameters, e.g., `enum:xxx.yyy`, where `options` sets the specific dictionary name.
* A default value can be specified for attributes.

## Common XDSL Syntax

The meta-model must be introduced via the `x:schema` attribute on the XML root node. For example, `x:schema="/nop/schema/my-wf.xdef"` indicates the model is constrained by the `my-wf.xdef` meta-model.

All DSL languages in the Nop platform share some common attributes and child nodes, effectively introducing a common syntax for all DSLs; the `x:schema` attribute is part of this common syntax. These common syntaxes are defined in the `xdsl.xdef` meta-model, so we specify on the root node `xmlns:x="/nop/schema/xdsl.xdef"` to indicate that the `x` namespace corresponds to the DSL common syntax space. For details, see
[xdsl.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef) and
[XDSL: General-Purpose Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300).

The XDef meta-model definition language is powerful enough to describe the XDef meta-model itself; see [xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef).

In the meta-meta model definition file `xdef.xdef`, the `xdef` namespace must be treated as an ordinary attribute namespace and cannot be interpreted as XDef meta attributes, so we add the attribute definition `xmlns:meta="/nop/schema/xdef.xdef"` on the root node and use the `meta` namespace to express meta attributes.

```xml
<workflow xmlns:meta="/nop/schema/xdef.xdef">
    <steps meta:body-type="list" meta:key-attr="id">
        ...
    </steps>
</workflow>
```

Equivalent to

```xml
<workflow xmlns:xdef="/nop/schema/xdef.xdef">
    <steps xdef:body-type="list" xdef:key-attr="id">
        ...
    </steps>
</workflow>
```

## Reusing Node Definitions

In xdef files, you can reference existing meta-model definitions via `xdef:ref`.

1. Import an external xdef file

```xml
<form id="!string" xdef:ref="form.xdef" />
```

2. Reference an internal node  
   You can add the `xdef:name` attribute to any node to mark it as a named node, then reference it via `xdef:ref`.

```xml
<steps>
    <step id="!string" xdef:name="WorkflowStepModel">
        ...
    </step>

    <join id="!string" xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel">
    </join>
</steps>
```

> Note: Due to current implementation reasons, attributes such as `id` that uniquely distinguish collection elements need to be repeated; other attributes can be directly referenced from other nodes without redefining them.

During code generation, `xdef:name` is treated as the Java class name corresponding to the node, and `xdef:ref` is treated as the base class of the current node class.

`xdef:ref="WorkflowStepModel" xdef:name="WfJoinStepModel"` corresponds to generated code `class WfJoinStepModel extends WorkflowStepModel`.

To simplify node reuse, XDef also defines a special node solely for reuse, `xdef:define`, for example

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

`xdef:define` defines a reusable part, equivalent to defining a base class, and nodes can inherit this base class via `xdef:ref`. `xdef:name` serves as the base class's class name.

## Collection Node Definitions

In addition to using `xdef:body-type="list"` to denote a collection node, xdef also provides a simplified way to define collection nodes: use `xdef:unique-attr` to denote the unique identifying attribute of collection elements.

```xml
<arg name="!string" xdef:unique-attr="name" value="any" />
```

A node with `xdef:unique-attr` will be parsed as a collection property; the property name is generally the camelCase form of the node name plus 's', e.g., `<task-step xdef:unique-attr="id">` corresponds to `taskSteps`. You can also specify the property name via `xdef:bean-prop`, e.g., `xdef:bean-prop="taskStepList"`.

```xml
<!--
  The following DSL definition is equivalent to the code:
  bp.taskSteps.add({id: 'a', displayName: 'A'})
  bp.taskSteps.add({id: 'b', displayName: 'B'})
-->
<task-step id="a" displayName="A"></task-step>
<task-step id="b" displayName="B"></task-step>
```

The advantage of defining collection properties via `xdef:body-type="list"` is that it allows the collection to contain different types of child nodes, for example

```xml

<steps xdef:body-type="list" xdef:key-attr="name" xdef:bean-sub-type-prop="type" xdef:bean-child-name="step"
       xdef:bean-body-type="List&lt;io.nop.wf.core.model.WfStepModel>">
    <step name="!string" xdef:bean-tag-prop="type" />
    <join name="!string" xdef:bean-tag-prop="type" />
</steps>
```

* `xdef:bean-body-type` specifies the type name of the generated collection property.
* `xdef:bean-child-name="step"` means the model object will automatically have a `getStep(String name)` method to retrieve a child node by its unique identifier attribute.
* `xdef:bean-tag-prop="type"` means the node's tag name (`step`, `join`) will be parsed as the value of the `type` attribute during JSON serialization.
* `xdef:bean-sub-type-prop="type"` indicates that during JSON deserialization, the `type` attribute determines the child node type.

```xml
<!--
  After conversion to JSON, the structure of the following DSL definition is:
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

## Frequently Asked Questions

1. What's the difference between defining with `<parent> <item xdef:value="string"> </parent>` and defining with `<parent item="string">`? There's no difference in the generated Java; it's only a difference in the XML form.

2. If `xdef:body-type="list"` is specified, must its child nodes be object types? How should basic types like int or string be written? I haven't handled this case; if it must be handled, the current approach is something like `<list xdef:body-type="list"> <_ xdef:value="int" />`. Additionally, for common comma-separated list values, you can directly write `<list xdef:value="csv-list" />`.

3. If a node has already set `xdef:body-type="list"` but also has other attributes, e.g., `<row name="string" xdef:body-type="list" height="string">`, what structure will be generated? By default the body content corresponds to the `body` property. This aligns with AMIS's conventions: tagName corresponds to `type`, and the body content corresponds to `body`.

<!-- SOURCE_MD5:dc7688c36d2caedc1e2064e377e42dfb-->
