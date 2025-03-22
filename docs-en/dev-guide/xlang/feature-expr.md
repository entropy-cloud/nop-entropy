# Feature Toggle

In any node of XDSL, the feature switch can be implemented using a feature expression at compile time.

```xml
<root>
  <child feature:on="my.flag and !my.disabled-flag">
  </child>
</root>
```

* `feature:on` indicates that the node exists only when the expression evaluates to true; otherwise, it will be automatically deleted during loading.
* `feature:off` means the node exists only when the expression evaluates to false; its meaning is the opposite of `feature:on`.
* A node can simultaneously have both `feature:on` and `feature:off` settings, and both conditions must be satisfied for the node to remain.

## Feature Expression

1. Feature expressions support complex `and/or` logic, simple comparison operations (e.g., `>=`, `=`), etc.
2. `!` can be used to indicate negation.
3. Parentheses are supported.

## Virtual Node

Sometimes, adding a virtual node can make control easier. When the feature switch does not satisfy, all content within the virtual node will be automatically deleted.

```xml
<domain>
  <options>
    <x:div feature:on="my.a1">
      <option>1</option>
      <option>2</option>
    </x:div>
    <option>3</option>
  </options>
</domain>
```

When `my.a1` is set to true in `application.yaml`, the resulting XNode node will be:

```xml
<domain>
  <options>
    <option>1</option>
    <option>2</option>
    <option>3</option>
  </options>
</domain>
```

## Meta Configuration Variable

On the root node, after setting `feature:enable-meta-cfg` to true, it will recognize the `@meta-cfg:` prefix and automatically switch configuration variables. For example:

```xml
<task feature:enable-meta-cfg="true">
  <step fetchSize="@meta-cfg:my.fetch-size|10">
  </step>
</task>
```

If `my.fetch-size` is configured in `application.yaml`, then the fetchSize will be set accordingly; otherwise, it will default to 10.