# Why XML Schema Is a Bad Design?

1. xsd and the xml it defines are not in a schema-conformant relationship, making it very non-intuitive and directly violating the assertion in the theory of reversible computing that total is delta is a special case of this principle.
2. xsd implicitly enforces an ordered hierarchy on its child elements, which is unnecessary and restrictive.
3. Both xsd and json schema fail to introduce the concept of keyAttr, making it impossible to establish unique identification for elements in a list, thus preventing the introduction of delta computation.
4. xsd and xml are both designed for text content only, which makes them incapable of storing Number, Boolean, or more complex data types at runtime.
5. xsd lacks support for x:post-extends, x:post-parse, and x:gen-extends, which would enable meta-programming capabilities.
6. xsd also lacks delta customization capabilities. Placing files with the same name in a delta directory automatically overwrites the standard path's files, merging them via x:extends.
7. The structure defined by xdef allows for the extension of attributes and nodes, but during parsing, these extended attributes are automatically resolved. All attributes with namespaces are not subject to xdef model constraints unless explicitly specified with xdef:check-ns="c".

Comparison between beans.xdef and Spring 1.0 xsd:

The definition of delta relies on the existence of a coordinate system, which in turn depends on the stable existence of unique coordinates. Since xsd does not provide keyAttr for list elements, there is no stable coordinate.

While defining an entire concept can be very attractive, it often leads to the need to split and extract parts of the definition after implementation.

In xdef, you can reference existing definitions when defining sub-structures, but this still represents a continuation of nesting, with coordinates extending downward.

Both object-oriented programming and Lisp fundamentally operate on short-term relationships. Object-attribute, list-element, and Tree-long-term relationship. While Tree represents a long-term relationship, the definition of delta requires maintaining these relationships over time.

Java's pojo=属性 类型 标签 方法

Extensions, inheritance, and exclusion.

Object-oriented programming is fundamentally about Map structures at the structural level. Map = Map extends Map<Map>, while the software structure space is expanded to Tree by pushing Map down to Tree. If you've studied some abstract mathematics, you'll understand why this expansion is fundamentally different.

Just as vector structures are extended to tensor structures in mathematical terms:
- Vector mathematics is extended to tensor mathematics.
- Vector-based math is expanded to tensor-based math.
- Modern mathematics relies heavily on tensor mathematics for many fundamental principles, which are best expressed using tensor language.

For example:
属性里怎么保存object的？能举个例子吗？

Map<String, ValueWithLocation> attributes;  
class ValueWIthLocation {  
    Object value;  
    SourceLocation loc;  
}  

This Map<String, ValueWithLocation> type holds the ValueWithLocation type, which records the Location information. This is crucial for tracking delta merging processes over time.

### xdef与xsd的对比

- xsd+xmlpatch:  
  In the context of reversible computing theory, this combination is incorrect because it represents A = 0 + A, where total is delta is a special case. To correctly represent delta as a difference that maintains the same form as total, we must have A = A + delta, which is not the case here.

- jsonpatch:  
  JSON Patch (jsonpatch) also fails in this context because it does not support the necessary operations to maintain the reversible relationship between total and delta. The current implementation only allows additive changes, not the subtraction required for true delta computation.

### json结构的局限性

The current JSON structure lacks the ability to add metadata to lists, which is essential for implementing delta computation:
- <children x:key-attr="id">  
  Through this mechanism, each child element can be uniquely identified by an "id" attribute. However, children: [] syntax cannot handle this because it doesn't support individual identification.

### YAML的扩展设计

Modern programming languages have introduced metadata annotations in YAML format to address these limitations. This allows for the addition of metadata to lists and structures during serialization, enabling proper delta computation.
