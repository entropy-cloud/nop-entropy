# 1. Parsing XML Text

```xml
XNode node = XNodeParser.instance().parseFromText(loc, text);
```

XNode parsing results in a different structure compared to DOM parsing. While DOM always retains whitespace nodes, XNode ignores whitespace between nodes. For example:

```xml
<root>
  <child1/>
  <child2/>
</root>    
```

The root node parsed by XNode contains two child nodes: child1 and child2.

## 2. Parsing XML Files

```xml
XNode node = XNodeParser.instance().parseFromReource(resource);
或者

XNode node = ResourceHelper.readXml(resource);
```

## 3. Serializing XNode to XML and Saving to File

```xml
ResourceHelper.writeXml(resource, node);e
```

## 4. Parsing XML into Java Objects Based on XDef Schema

```java
new DslModelParser(xdefPath).parseFromResource(resource);
```

## 5. Recursively Traversing Each Node in XNode

```java
node.forEachNode(n -> process(n));
```

## 6. Common Functions

```java

node.getTagName() // Get tag name
node.getAttr(name) // Get attribute
node.setAttr(name, value) // Set attribute

node.attrText(name) // Get text attribute; returns null if text is empty
node.attrTextOrEmpty(name) // Returns null if attribute is missing
node.attrInt(name) // Get integer attribute; returns default value if not present
node.attrInt(name, defaultValue) // Get integer attribute with default value
node.attrBoolean(name) // Get boolean attribute
node.attrLong(name) // Get long attribute
node.attrCsvSet(name) // Get comma-separated string

node.getAttrs() // Get all attributes
node.getChildren() // Get child nodes
node.childByTag(tagName) // Find child node by tag name
node.childByAttr(attrName, attrValue) // Find child node by attribute value
node.getContentValue() // Get content value

node.hasChild() // Check if node has children
node.hasAttr() // Check if node has attributes
node.hasContent() // Check if node has direct content
node.hasBody() // Check if node has children or direct content

node.getParent() // Get parent node
node.cloneInstance() // Clone node
list = node.cloneChildren() // Clone all child nodes
node.detach() // Detach node from parent
node.remove() // Remove node from parent
node.replaceBy(newNode) // Replace node in parent's children with newNode
node.xml() // Get XML text content
node.innerXml() // Get inner XML content

node.toTreeBean() // Convert to TreeBean object
XNode.fromTreeBean(treeBean) // Convert TreeBean to XNode

## 7. Conversion Between XML and JSON

```java

node.toXJson() // Convert to XJson format
node.toJsonObject() // Convert to standard JSON object
```

Standard format specifies:

1. tagName corresponds to $type
2. children and content correspond to $body
3. Attributes directly correspond to object attributes

Example:

```xml
<div class='a'>
  <span />
</div>    
```


```markdown
# XJson Specification

1. **General Rules**:
   - Properties and child nodes are typically mapped to object properties.
   - If a node is marked with `j:list='true'`, it corresponds to a list object.
   - If a node's name is `_`, it is ignored.
   - If a node has no properties and no children, its content is treated as its value.
   - If a node has the `j:key` property, it replaces the node's name with the property's name.

2. **List Nodes**:
   - Child nodes' tag names are mapped to the `type` property of the list object.
   - However, if a child node's name is `_`, it is ignored.

## Example

### XML Example:

```xml
<root a="1">
  <buttons j:list="true">
    <button id="a">
      <description>aa</description>
    </button>
  </buttons>

  <options j:list="true">
    <_>A</_>
    <_>B</_>
  </options>
</root>
```

### Corresponding JSON:

```json
{
  "type": "root",
  "a": "1",
  "buttons": [
    {
      "type": "button",
      "id": "a",
      "description": "aa"
    }
  ],
  "options": ["A", "B"]
}
```
