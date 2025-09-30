
# Delta Merge Algorithm in Reversible Computation Theory

Reversible Computation theory clearly states that a full model is a special case of Delta. We can reconstruct the entire software generation process based on the concept of Delta. To maximize the value of the Delta concept, operations between deltas should be automatically performed by software, without human intervention. In the Nop platform, we define an automated delta merge algorithm that preserves node order, and all tree-structured objects use this algorithm to implement delta merging.

## Merge Operator `x:override`

By default, node merging uses the `merge` mode:

1. Node attributes are overridden by name
2. If child nodes are non-repeatable, they are overridden by name
3. If child nodes are repeatable, they are overridden by a unique key attribute

For example:

```xml
<entity name="test.MyEntity" tableName="MY_ENTITY">
    <comment>Comment content</comment>
    <columns>
        <column name="phone3" label="xx" />
    </columns>
</entity>
```

Under the `entity` node, only one `comment` child node and one `columns` child node are allowed, so these two child nodes are merged by name. Then, when recursively handling the merge of these two child nodes, according to the XDef metamodel definition, the child nodes of `columns` are repeatable, so they will be merged based on the unique identifier attribute `name`. In the example above, the `label` attribute of the `phone3` field will be automatically overridden by customization.

All values of `x:override` are defined in the [XDefOverride](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java) class.

Commonly used merge modes include:

* `remove`: Delete the node
* `merge`: Merge attributes and child nodes
* `replace`: Replace entirely
* `merge-replace`: Merge attributes, replace child nodes or content
* `append`: Merge attributes, append child nodes or content
* `prepend`: Merge attributes, prepend child nodes or content
* `bounded-merge`: Basically similar to `merge`, but it additionally constrains the scope of the merge result, retaining only the child nodes present in both the base model and the derived model.

For example:

```xml
<grid>
    <cols x:override="bounded-merge">
        <col id="a" width="30"/>
        <col id="b" width="20"/>
        <col id="c" width="50"/>
    </cols>
</grid>
```

If the merge mode of `cols` is set to `bounded-merge`, then the merged result will contain only three nodes; all columns that exist only in the base model but not in the derived model will be deleted. If `x:override` is not configured or is configured as `x:override=merge`, it indicates adding or overriding child nodes; the final number of child nodes of `cols` may not be 3 but greater than 3.

## Order-Preserving Algorithm

When merging list-type child nodes, the algorithm will try to preserve the node order as defined in both the derived model and the base model. For example, if we want to append a node `b` right after node `a`, we can use the following configuration:

```xml
<cols>
    <col id="a" />
    <col id="b" width="30" label="test" />
</cols>
```

The merge algorithm stipulates that if node `a` exists in the base model and node `b` is a new node, then it will be inserted immediately after node `a` in the result set. The merge algorithm strictly preserves the node order specified in the derived model while best-effort preserving the order in the base model. Specifically, when merging lists, it first concatenates the two lists in order, then splits the list into several contiguous blocks based on overlapping nodes, and finally reorders the blocks.

Concrete examples are as follows:

1. Merge `a=[a1,a2,a3,a4,a5]` with `b=[b1, a2, b3]`, first obtain `all = [a1,a2,a3,a4,a5, b1,a2,b3]`, then find `a2` duplicated. Using `a2` as the pivot to move elements in `a`, we get `[a1,b1,a2,b3,a3,a4,a5]`. Since `b3` is immediately after `a2` in `b`, it is also placed right after `a2` in the rearranged sequence.

2. If `a` is merged with `b=[a1, b1, a3, b3]`, we first obtain `[a1,a2,a3,a4,a5, a1,b1,a3,b3]`, find `a1` and `a3` duplicated, and after moving we get \[a1,b1,a2,a3,b3,a4,a5\].

3. The pivot element used for positioning can be understood as representing itself and its subsequent elements (until another pivot element is encountered). If `a` is merged with `b=[a3,b1,a1]`, we first obtain `[a1,a2,a3,a4,a5, a3,b1,a1]`. Now `a3` and `a1` are in reverse order in `b`. First move `a1` to get `[a3,a4,a5, a3,b1,a1,a2]`, then move `a3` to get `[a3,b1,a4,a5,a1,a2]`.

See test cases in [TestMerge.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/test/java/io/nop/core/lang/json/TestMerge.java)

## Prototype Inheritance Between Sibling Nodes

The `x:override` configuration specifies the merge operator between the derived model and the base model introduced by `x:extends`. However, beyond inheritance from external base models, in many cases, the child nodes of a list may also exhibit similarities. For example, the layout of the edit form may be identical to the add form and the delta form, but it may gradually diverge as requirements change. Some fields may not be allowed to be created or modified, but can be viewed on the view page. Using `x:prototype` and `x:prototype-override`, we can specify from which sibling node to inherit and which merge operator to use during inheritance.

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

    <!-- Merge with the sibling node whose id=view -->
    <form id="add" x:prototype="view" >
        <layout x:override="remove" />
    </form>

</forms>
```

Prototype merging is performed after normal Delta merging. In the example above, `ext.forms.xml` inherits the `layout` configuration of the `add` form from the base model. We first delete the inherited `layout` using `x:override="remove"`, then specify `x:prototype="view"` to merge with the sibling node `view`. Ultimately, the `layout` in the `add` form is inherited from the `view` form.

## `x:extends` on Child Nodes

In addition to writing `x:extends` on the root node of the DSL file to inherit from a specified base model, `x:extends` can also be used on child nodes.

> You need to configure `xdef:support-extends=true` on the child's XDef metamodel to allow the child node to use the `x:extends` mechanism.

```xml
<forms x:extends="base.forms.xml">
    <form id="add" x:extends="default.form.xml" />
</forms>
```

From the base model `base.forms.xml` at the root node, we may inherit an `add` form, and we also specify via `x:extends` that the `add` form inherits from `default.form.xml`. In `default.form.xml`, it may continue to use the `x:extends` mechanism to inherit from other files. Considering all inherited nodes in full would make the merge algorithm implementation quite complex, so the Delta merge algorithm in the Nop platform makes a simplification: If a node explicitly sets `x:extends`, the content inherited from the root node’s base model is automatically ignored. For example, in the case above, the `add` form inherited from `base.forms.xml` will be automatically ignored.

## Post-processing of Merge Results

In Java, we can use the `final` keyword to specify that methods cannot be inherited, and use the `abstract` keyword to specify that a method is a virtual placeholder that cannot be called unless overridden in the derived class. Similarly, in Delta merging we define some keywords for finer control over the merge result.

* `x:final`: Nodes marked with `x:final` cannot be customized via Delta; they must remain as is
* `x:abstract`: Nodes marked with `x:abstract` will be deleted from the final output if they are not customized, effectively making them nonexistent. With this mechanism, we can provide default values for a complex DSL node. For example, we can treat a node as a template node and mark it as `abstract`; all other sibling nodes can use `x:prototype` to inherit configuration from this template node
* `x:virtual`: Nodes marked with `x:virtual` must override some node in the base model. If they do not override anything, the node’s configuration may be incomplete (e.g., missing required attributes), and it will be automatically deleted from the final output. With this mechanism, we can implement simultaneous modification of the same DSL model by automatic generation and a visual designer. If the visual designer modifies on top of auto-generated code, and later the generator stops generating a certain node, then the fine-tuning done by the visual designer on that node will also be discarded.

Additionally, note that all nodes marked with `x:override="remove"` will ultimately be deleted from the output.

## JsonMerger
When merging JSON, unique identifier attributes such as `v-id`, `id`, and `name` are automatically attempted for locating. If both `id` and `name` exist, `id` is used by default. However, if you want to override this, you can use `"x:unique-attr":"name"` to specify the unique locating attribute.

<!-- SOURCE_MD5:6bbe170e58dc534c6a0839d2e787202d-->
