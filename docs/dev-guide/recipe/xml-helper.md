# 1. 解析XML文本

```
XNode node = XNodeParser.instance().parseFromText(loc, text);
```

XNode解析结果与DOM解析不同。DOM总是保留节点之间的空白节点。而XNode解析时如果两个节点之间只有空白文本，则该空白文本会被忽略。例如

```xml
<root>
	<child1/>
	<child2/>
</root>	
```

XNode解析得到的root节点，它具有两个子节点child1和child2。

## 2. 解析XML文件

```
XNode node = XNodeParser.instance().parseFromReource(resource);
或者

XNode node = ResourceHelper.readXml(resource);
```

## 3. 将XNode序列化为XML保存到文件中

```
ResourceHelper.writeXml(resource,node);e
```

## 4. 根据XDef元模型定义将XML文件解析为Java对象

```javascript
new DslModelParser(xdefPath).parseFromResource(resource);
```

## 5. 递归遍历XNode的每一个节点

```
node.forEachNode(n-> process(n));
```

## 6. 常用函数

```javascript

node.getTagName() // 读取标签名
node.getAttr(name) // 读取属性
node.setAttr(name,value) // 设置属性

node.attrText(name) // 得到文本属性，如果文本值为空，则返回null而不是空串
node.attrTextOrEmpty(name) // 如果属性值为空，则返回null。属性值不存在时返回null
node.attrInt(name) //  得到属性值并转换为Integer类型
node.attrInt(name, defaultValue) // 如果属性值为空，则返回缺省值
node.attrBoolean(name) // 读取属性值并转换为Boolean类型
node.attrLong(name) // 读取属性值并转换为Long类型
node.attrCsvSet(name) // 读取属性值，如果是字符串，则按照逗号分隔转换字符串集合


node.getAttrs() // 得到属性集合
node.getChildren() // 得到子节点集合
node.childByTag(tagName) // 根据子节点名查找得到对应子节点
node.childByAttr(attrName, attrValue) // 根据属性值查找得到子节点
node.getContentValue() // 读取节点值

node.hasChild() // 是否有子节点
node.hasAttr() // 是否有属性
node.hasContent() // 直接内容是否不为空
node.hasBody()  // 是否有子节点或者直接内容

node.getParent() // 得到父节点

node.cloneInstance() // 复制节点

list = node.cloneChildren() // 复制所有子节点

node.detach() // 解除父子关系

node.remove() // 从父节点中删除

node.replaceBy(newNode) // 在父节点的children集合中将本节点替换为newNode


node.xml() // 得到节点的XML文本
node.innerXml() // 得到节点内部对应的XML文本

node.toTreeBean() // 转换为TreeBean对象

XNode.fromTreeBean(treeBean) // 从TreeBean转换为XNode

```

## 7. XML和JSON之间的转换

```javascript

node.toXJson() // 按照XJson格式转换为JSON对象

node.toJsonObject() // 按照标准格式转换为json
```

标准格式规定如下：

1. tagName 对应于  $type
2. children和content 对应于  $body
3. 属性直接对应于对象属性。

例如:

```xml
<div class='a'>
	<span />
</div>	
```

转换为

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

XJson规定如下：

1. 一般情况下属性和子节点都对应于对象属性
2. 如果节点上标记了j:list='true'表示当前节点对应于一个列表对象
3. 如果节点名为 `_`，则表示节点名被忽略
4. 如果节点没有属性，且没有子节点，则它的内容作为它的值
5. 如果节点具有j:key属性，则它替代节点名作为属性名
6. 列表节点的子节点的tagName对应于type属性。但有个例外，如果节点名为 `_`，则表示节点名被忽略

例如:

```xml
<root a="1">
	<buttons j:list="true">
		<button id="a" >
			<description>aa</description>
		</button>
	</buttons>

	<options j:list="true">
		 <_>A</_>
		 <_>B</_>
	</options>	
</root>
```

对应于

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
	"options": ["A","B"]
}
```
