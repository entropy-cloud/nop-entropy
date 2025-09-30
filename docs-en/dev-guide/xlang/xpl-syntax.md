# **XPL Template Tag Specification**

In Markdown, you can use xpl-syntax to concisely represent the syntax structure of XPL tags, for example:

```xpl-syntax
<!--
@bizObjName Target business object name
@limit Maximum number of records to return (optional)
-->
<bo:DoFindList bizObjName="entity-name" limit="int">
  <!-- @filter Query criteria -->
  <filter>
    <!--
    @name Field name
    @value Field value (expression)
    @op Operator (defaults to eq)
    -->
    <condition name="field" value="t-expr" op="eq|gt|lt..."/>
  </filter>

  <!-- @orderBy Sorting rules -->
  <orderBy>
    <!-- @name Sort field @desc Whether descending -->
    <field name="field-name" desc="boolean"/>
  </orderBy>
</bo:DoFindList>
```

## **1. Syntax Format**
- Use **`xpl-syntax`** as the root marker to describe the syntax rules of XPL template tags.
- The parameter description for each tag appears as a `<!-- -->` comment immediately above the tag, in the following format:
  ```xpl-syntax
  <!--
    @paramName1 Parameter description (optional additional constraints)
    @paramName2 Parameter description
    ...
  -->
  <tagName attr1="value" attr2="value"/>
  ```
- Sub-tag comments (such as `<filter>`) are on their own line, with indentation aligned to the tag's hierarchy.

---

## **2. Comment Rules**
- Parameter description: starts with `@paramName`, followed by a concise description (e.g., type, behavioral constraints).
  - Example: `@bizObjName Entity name (e.g., User)`
  - Optional parameters are marked as `(optional)`, such as `@limit Return count limit (optional)`.
- Sub-tag description: comment separately above the sub-tag to describe its purpose.
  - Example: `<!-- @filter Filter criteria -->`
<!-- SOURCE_MD5:a5c06b0b115ba05c3667c5f18958460a-->
