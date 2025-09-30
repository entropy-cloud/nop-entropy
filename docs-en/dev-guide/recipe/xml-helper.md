# 1. Parse XML text

```
XNode node = XNodeParser.instance().parseFromText(loc, text);
```

The XNode parsing result differs from DOM parsing. DOM always preserves whitespace nodes between elements. When XNode parses, if there is only whitespace text between two nodes, that whitespace text is ignored. For example

```xml

<root>
  <child1/>
  <child2/>
</root>
```

The root node parsed by XNode has two child nodes: child1 and child2.

## 2. Parse XML files

```
XNode node = XNodeParser.instance().parseFromReource(resource);
or

XNode node = ResourceHelper.readXml(resource);
```

## 3. Serialize XNode to XML and save it to a file

```
ResourceHelper.writeXml(resource,node);e
```

## 4. Parse an XML file into Java objects according to the XDef meta-model definition

```javascript
new DslModelParser(xdefPath).parseFromResource(resource);
```

## 5. Recursively traverse each node of an XNode

```
node.forEachNode(n-> process(n));
```

## 6. Common functions

```javascript

node.getTagName() // Read the tag name
node.getAttr(name) // Read an attribute
node.setAttr(name,value) // Set an attribute

node.attrText(name) // Get a text attribute; if the text value is empty, return null instead of an empty string
node.attrTextOrEmpty(name) // If the attribute value is empty, return null. If the attribute does not exist, return null
node.attrInt(name) // Get the attribute value and convert it to Integer
node.attrInt(name, defaultValue) // If the attribute value is empty, return the default value
node.attrBoolean(name) // Read the attribute value and convert it to Boolean
node.attrLong(name) // Read the attribute value and convert it to Long
node.attrCsvSet(name) // Read the attribute value; if it is a string, split by commas into a set of strings


node.getAttrs() // Get the attribute collection
node.getChildren() // Get the children collection
node.childByTag(tagName) // Find a child node by its tag name
node.childByAttr(attrName, attrValue) // Find a child node by attribute value
node.getContentValue() // Read the node value

node.hasChild() // Whether it has child nodes
node.hasAttr() // Whether it has attributes
node.hasContent() // Whether the direct content is not empty
node.hasBody()  // Whether it has children or direct content

node.getParent() // Get the parent node

node.cloneInstance() // Clone the node

list = node.cloneChildren() // Clone all child nodes

node.detach() // Detach from its parent-child relationship

node.remove() // Remove from the parent node

node.replaceBy(newNode) // Replace this node with newNode in the parent's children collection


node.xml() // Get the node's XML text
node.innerXml() // Get the XML text corresponding to the node's inner content

node.toTreeBean() // Convert to a TreeBean object

XNode.fromTreeBean(treeBean) // Convert from a TreeBean to XNode

```

## 7. Conversion between XML and JSON

```javascript

node.toXJson() // Convert to a JSON object in XJson format

node.toJsonObject() // Convert to JSON in the standard format
```

The standard format is defined as follows:

1. tagName corresponds to $type
2. children and content correspond to $body
3. Attributes map directly to object properties.

For example:

```xml

<div class='a'>
  <span/>
</div>
```

Converted to

```json
{
  "$type": "div",
  "class": "a",
  "$body": [
    {
      "$type": "span"
    }
  ]
}
```

XJson is defined as follows:

1. In general, both attributes and child nodes map to object properties.
2. If a node is marked with j:list='true', the current node corresponds to a list object.
3. If the node name is `_`, the node name is ignored.
4. If a node has no attributes and no child nodes, its text content is used as its value.
5. If a node has a `j:key` attribute, it replaces the node name as the property name.
6. The tagName of a child node under a list node corresponds to the type property. Exception: if the node name is `_`, the node name is ignored.

For example:

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

Corresponds to

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
  "options": [
    "A",
    "B"
  ]
}
```

### Frequently Asked Questions

1. How to output `{"&":"$$"}`
   Use `j:key` to specify a key that contains special characters

```xml
<_ j:key="&amp;">$$</_>
```
<!-- SOURCE_MD5:925458aa35891b279c5d9ebcaa27aded-->
