# Feature Toggles

At any node in XDSL, you can use feature expressions to implement compile-time toggle selection.

```
<root>
  <child feature:on="my.flag and !my.disabled-flag">
  </child>
</root>
```

* `feature:on` means the node exists only when the expression evaluates to true; otherwise, it will be automatically removed during post-processing after loading.
* `feature:off` means the node exists only when the expression evaluates to false, i.e., the opposite semantics of feature:on.
* A node can have both `feature:on` and `feature:off` set; the node is retained only if both evaluations pass.

## Feature Expressions

1. Feature expressions support complex and/or syntax and simple comparison operations such as `>=`, `=`, etc.
2. Negation is expressed with `!`.
3. Parentheses are supported.

## Virtual Nodes

For easier control, you can sometimes add a virtual node. When the feature toggle condition is not satisfied, all content under the virtual node will be automatically removed.

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

When `my.a1=true` is configured in application.yaml, the loaded XNode is:

```xml

<domain>
  <options>
    <option>1</option>
    <option>2</option>
    <option>3</option>
  </options>
</domain>
```

## Meta Configuration Variables

After setting `feature:enable-meta-cfg` to true on the root node, the `@meta-cfg:` prefix will be recognized and configuration variables will be automatically substituted. For example:

```xml

<task feature:enable-meta-cfg="true">
  <step fetchSize="@meta-cfg:my.fetch-size|10">

  </step>
</task>
```

If `my.fetch-size` is configured in application.yaml, the configured value takes precedence; otherwise, fetchSize will be set to the default value 10.

<!-- SOURCE_MD5:07c259dccd98d539ae8025504560ac14-->
