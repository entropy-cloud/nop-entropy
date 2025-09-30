
# Why Is XML Schema a Poor Design?

1. xsd and the xml it defines are not isomorphic, making it highly unintuitive and directly violating the assertion in the theory of Reversible Computation that the full form is a special case of Delta.
2. xsd by default forcibly requires child nodes to be ordered, which is an unnecessary constraint.
3. Neither xsd nor json schema introduces the concept of keyAttr for list structures, making unique positioning within lists impossible, and thus preventing the introduction of Delta computation.
4. xsd and xml are defined for text structures and cannot store Number, Boolean, or more complex data types at runtime.
5. xsd lacks metaprogramming capabilities such as x:post-extends, x:post-parse, and x:gen-extends.
6. xsd has no Delta customization capability. Placing a file with the same name under the delta directory will automatically override the file with the same name under the standard path, and merge the two via x:extends.
7. xdef-defined structures allow the presence of extended attributes and extended nodes, and these attributes are automatically parsed during processing. All attributes with namespaces are, by default, not constrained by the xdef meta-model unless explicitly specified by a strict requirement like xdef:check-ns="c".

Compare beans.xdef and spring 1.0 xsd

The definition of Delta depends on the existence of a coordinate system, and a coordinate system depends on the stable existence of uniquely locatable coordinates. Since xsd does not assign a keyAttr to elements in lists, there are no unique, stable coordinates.

Defining a complete conceptual entity in one shot is indeed appealing; however, sometimes after defining it, you need to extract parts of it for separate use...?

In xdef, when defining a child structure you can reference existing definitions, but it still denotes continued nesting; coordinates extend downward.

Object orientation and Lisp are essentially short-range relations—object–property, list–element—whereas Tree is a long-range association: define a complete conceptual entity in one go while introducing the concept of domain coordinates in the process.

Java POJO = attributes, types, annotations, methods
extension, inheritance, deletion

At the structural level, object orientation is essentially a Map structure: Map = Map extends Map<Map>, whereas the software-structure space studied in Reversible Computation generalizes the underlying Map to a Tree: Tree = Tree x-extends Tree<Tree>. If you have studied some abstract mathematics, you will understand why this generalization is fundamentally different.

It is akin to generalizing vector structures to tensor structures:
mathematics on vectors is generalized to mathematics on tensors,
and many fundamental principles in modern mathematics essentially require tensor language for expression.

> How do you store an object inside an attribute? Can you give an example?

Map<String, ValueWithLocation> attributes;  class ValueWIthLocation{  Object value; SourceLocation loc; }  Attributes hold the ValueWithLocation type, which records the Location; this is critical for tracking the Delta merge process and ultimately clarifying the original source of each attribute. A large number of designs currently based on JSON structures fundamentally fail to understand this point.

xsd+xmlpatch

jsonpatch is an incorrect design within the theory of Reversible Computation. A = 0 + A; the full form is a special case of Delta. Delta should adopt the same form as the full form so that the Delta of a Delta remains a simple Delta of the same form, enabling more complex metaprogramming. These involve a series of constructions grounded in basic mathematical principles; relying on mere experience, some link in the reasoning chain will break, making it impossible to achieve the broad structural transformations of Reversible Computation.

The JSON structure currently has a problem: list structures cannot be augmented with metadata. <children x:key-attr="id"> can be easily achieved via node form. But with children: [] this cannot be handled.

The full form is a special case of Delta. If we want to express both full and Delta using the same JSON, extended metadata becomes indispensable. In fact, modern programming languages have introduced ubiquitous annotation mechanisms, allowing every syntactic structure to introduce local metadata. In the YAML format, there are indeed extended designs attempting to provide metadata for lists.

<!-- SOURCE_MD5:e453369af27e15365f6a0a73bb26ed6c-->
