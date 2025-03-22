# Delta Merge Algorithm in Reversible Computation Theory

Reversible computation theory explicitly states that the entire set is a special case of the subset, allowing us to reconstruct the entire software generation process based on deltas. To maximize the value of delta, operations between deltas should be automated without manual intervention. In the Nop platform, we have defined an automated Delta merge algorithm that maintains node order, and all tree structures adopt this algorithm for Delta merging.

## Merge Operator `x:override`

Node merging defaults to using the `merge` mode:

1. Node attributes are covered by name.
2. If child nodes are not allowed to repeat, they are covered by name.
3. If child nodes are allowed to repeat, they are merged based on unique key attributes.

Example:

```xml
<entity name="test.MyEntity" tableName="MY_ENTITY">
    <comment>注释内容</comment>
    <columns>
        <column name="phone3" label="xx" />
    </columns>
</entity>
```

The `entity` node allows only one `<comment>` child node and one `<columns>` child node, so during merging, both child nodes are merged by name. Then, recursively handle the merging of these child nodes:
- The `<columns>` child node is allowed to repeat, so it is merged based on the `name` attribute.
- In the example above, the `phone3` field's `label` attribute will be customized and covered.

The values of `x:override` are defined in the `[XDefOverride](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java)` class.

Common merge modes used include:

- `remove`: Delete nodes
- `merge`: Merge attributes and child nodes
- `replace`: Entirely replace nodes
- `merge-replace`: Merge attributes, replace child nodes or content
- `append`: Merge attributes, append child nodes or content
- `prepend`: Merge attributes, prepend child nodes or content
- `bounded-merge`: Similar to `merge`, but limits the merge scope to include only existing nodes in both models

Example:

```xml
<grid>
    <cols x:override="bounded-merge">
        <col id="a" width="30"/>
        <col id="b" width="20"/>
        <col id="c" width="50"/>
    </cols>
</grid>
```

If the `cols` element's merge mode is set to `bounded-merge`, only three nodes will be merged. Nodes present in the base model but not in the derived model will be removed.
If no `x:override` attribute is specified or it is set to `merge`, it implies a default behavior where new nodes are added or existing nodes are overwritten based on their identity.

## Order Preservation Algorithm

When merging list-type child nodes, the algorithm tries to preserve the order defined in both base and derived models. For example:
- If we want to append a new node `b` after node `a`, we can use the following configuration:

```xml
<cols>
    <col id="a" />
    <col id="b" width="30" label="test" />
</cols>
```

The merge algorithm enforces that if node `a` exists in the base model and node `b` is a new node, it will be appended right after `a`. The order preservation algorithm strictly maintains the node order as defined in the derived model while also attempting to preserve the order defined in the base model. Specifically:
- The algorithm first concatenates the two lists.
- It then checks for overlapping nodes and splits the merged list into contiguous blocks based on these overlaps.

Detailed examples:

1. Merging `a=[a1,a2,a3,a4,a5]` with `b=[b1,a2,b3]` results in:
   - Concatenated: `[a1,a2,a3,a4,a5, b1,a2,b3]`
   - Duplicate `a2` is detected, so the merged list becomes:
     `[a1,b1,a2,a3,a4,a5, a2,b3]`

   In the final sequence, `a2` appears twice. `b3` follows `a2` as it was positioned right after `a2` in `b`.

2. Merging `a=[a1,a2,a3,a4,a5]` with `b=[a1,b1,a3,b3]` results in:
   - Concatenated: `[a1,a2,a3,a4,a5, a1,b1,a3,b3]`
   - Duplicate `a1` is detected. Moving forward, the merged list becomes:
     `[a1,b1,a2,a3,a4,a5, a1,a3,b3]`

   Here, duplicate `a1` and `a3` are handled.

3. Baseline elements are used for identification and can be understood to represent themselves and their subsequent elements (up until another baseline element is encountered). If `a` is merged with `b=[a3, b1, a1]`, the result is `[a1, a2, a3, a4, a5, a3, b1, a1]`. After this, since `a3` and `a1` in `b` have their order swapped, we first move `a1` to the front, resulting in `[a3, a4, a5, a3, b1, a1, a2]`. Then, we move `a3` to its position, yielding `[a3, b1, a4, a5, a1, a2]`.

For reference, see [TestMerge.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/test/java/io/nop/core/lang/json/TestMerge.java).

## Brother nodes between prototype inheritance

The `x:override` attribute is used to configure derived models, while the `x:extends` attribute introduces the base model. However, aside from inheriting from external base models, sibling nodes may also exhibit similarities.

For example, the layout of an edit form might be identical across add, new, and diff forms, but this consistency may degrade over time as requirements change. Some fields may not allow creation or modification, but they can still be viewed on the page. Using `x:prototype` and `x:prototype-override`, we can specify from which sibling node to inherit and which merge operator to use.

```xml
<!-- base.forms.xml -->
<forms>
    <form id="add">
        <layout>
            c d
        </layout>
    </form>
</forms>

<!-- ext.forms.xml -->
<forms x:extends="base.forms.xml">
    <form id="view">
        <layout>
            a b
        </layout>
    </form>

    <!-- Merging based on id=view -->
    <form id="add" x:prototype="view">
        <layout x:override="remove" />
    </form>

</forms>
```

Prototype merging in DeltaMerge works after regular Delta merge operations. In the example above, `ext.forms.xml` inherits the "add" form's layout from `base.forms.xml`. We first remove the inherited layout using `<layout x:override="remove"/>`, then set the prototype to "view" using `<form id="add" x:prototype="view">`.

## Sibling nodes' `x:extends`

In addition to inheriting from a base model at the root level, sibling nodes can also use `x:extends`. However, this requires setting `xdef:support-extends` to `true` in the XDef metadata.

```xml
<forms x:extends="base.forms.xml">
    <form id="add" x:extends="default.form.xml"/>
</forms>
```

This allows inheriting from both `base.forms.xml` and `default.form.xml`. However, if a child node explicitly sets `x:extends`, it will override any inheritance from the base model. For instance, in `default.form.xml`, you might have:

```xml
<forms x:extends="base.forms.xml">
    <form id="add" x:extends="default.form.xml"/>
</forms>
```

Here, "add" form inherits layout and behavior from both its immediate parent (`base.forms.xml`) and the grandparent (`default.form.xml`). However, if a node explicitly sets `x:extends`, it takes precedence over inherited values.

## Merge results after processing

In Java, you can use `final` to prevent inheritance and `abstract` to mark methods as abstract. In DeltaMerge, we define similar keywords for controlling merge behavior:

* `x:final`: Disables Delta inheritance for a node, leaving its properties unchanged during further merges.

For example:
```xml
<forms x:extends="base.forms.xml">
    <form id="add" x:prototype="view">
        <layout x:override="remove"/>
    </form>
</forms>
```

After merging, the "add" form's layout is removed using `x:override`, and its prototype is set to "view". The final merge result will not include any elements inherited from `base.forms.xml` if they are marked as `final`.

## Abstract Handling
- `x:abstract`: Nodes marked with the `x:abstract` attribute will be removed from the final output if they have not been customized. This is equivalent to them not existing.
- By using this mechanism, we can provide default values for complex DSL nodes.

Example:
- We can create a template node and mark it as `x:abstract`.
- All other sibling nodes can then inherit configurations from this template node via `x:prototype`.


- Nodes marked with the `x:virtual` attribute must cover some node in the base model. If they do not cover, their configuration may be incomplete (e.g., missing required attributes), and they will be automatically removed from the final output.
- Using this mechanism, we can simultaneously modify a single DSL model with both a generator and a visualization tool.
- However, if the visualization tool is modified after the automatic generation and no longer generates a particular node, its fine-tuning in the visualization tool will also be discarded.

Additionally, all nodes marked with `x:override="remove"` will be removed from the final output.

---


During JSON merging, `v-id`, `id`, `name`, etc., are automatically used to locate unique identifiers. If both `id` and `name` attributes exist, `id` is used by default. However, if you want to override this behavior, you can use `"x:unique-attr":"name"` to specify a unique identifier attribute.

