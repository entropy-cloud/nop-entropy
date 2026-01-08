# XNode 使用指南

## 概述

XNode是Nop平台提供的**通用树形结构模型**，用于统一处理XML节点的创建、访问、修改等操作，替代传统的DOM或JDOM库。XNode设计简洁易用，提供了丰富的节点操作API，同时支持选择器查询和节点遍历，是Nop平台中处理XML数据的核心组件。

## 核心功能

### 1. 节点创建

- `XNode.make(tagName)`：创建指定标签名的新节点
- `XNode.makeDocNode(tagName)`：创建文档节点
- `XNode.makeTextNode()`：创建文本节点
- `XNode.makeDummyNode()`：创建占位节点
- `XNode.parse(xmlStr)`：从XML字符串解析并创建XNode对象
- `XNode.parseFragments(xmlStr)`：解析XML片段并创建XNode对象
- `XNode.parse(SourceLocation loc, String text)`：从XML字符串解析并创建XNode对象（指定位置）
- `XNode.load(String resourcePath)`：从资源路径加载XML
- `XNode.fromTreeBean(treeBean)`：从TreeBean转换为XNode
- `XNode.fromValue(text)`：从文本值创建XNode

### 2. 属性管理

- `setAttr(String name, Object value)`：设置节点属性，value支持各种类型自动转换为字符串
- `setAttrIfAbsent(String name, Object value)`：仅当属性不存在时设置
- `setAttr(SourceLocation loc, String name, Object value)`：设置属性并指定源位置
- `setAttrs(Map<String, Object> attrs)`：批量设置属性
- `attrText(String name)`：获取指定名称的属性文本值
- `attrText(String name, String defaultValue)`：获取属性值，不存在时返回默认值
- `attrTextOrEmpty(String name)`：获取属性文本值，不存在时返回空字符串
- `requireAttrText(String name)`：获取属性文本值，不存在时抛出异常
- `attrBoolean(String name)`：获取布尔类型属性值
- `attrBoolean(String name, Boolean defaultValue)`：获取布尔类型属性值，带默认值
- `attrInt(String name)`：获取整数类型属性值
- `attrInt(String name, Integer defaultValue)`：获取整数类型属性值，带默认值
- `requireAttrInt(String name)`：获取整数类型属性值，不存在时抛出异常
- `attrLong(String name)`：获取长整数类型属性值
- `attrLong(String name, Long defaultValue)`：获取长整数类型属性值，带默认值
- `requireAttrLong(String name)`：获取长整数类型属性值，不存在时抛出异常
- `attrDouble(String name)`：获取双精度类型属性值
- `attrDouble(String name, Double defaultValue)`：获取双精度类型属性值，带默认值
- `requireAttrDouble(String name)`：获取双精度类型属性值，不存在时抛出异常
- `attrCsvSet(String name)`：获取CSV格式的集合属性值
- `attrCsvList(String name)`：获取CSV格式的列表属性值
- `requireAttrCsvSet(String name)`：获取CSV格式的集合属性值，不存在时抛出异常
- `addAttrCsvSet(String attrName, String newSet)`：向CSV集合属性添加值
- `removeAttrCsvSet(String attrName, String set)`：从CSV集合属性移除值
- `removeAttr(String name)`：移除指定名称的属性
- `removeAttrs(Collection<String> names)`：批量移除属性
- `removeAttrsWithPrefix(String prefix)`：移除指定前缀的属性
- `removeAttrsIf(BiPredicate<String, ValueWithLocation> predicate)`：根据条件批量移除属性
- `removeNoneAttributes()`：移除值为"none"的属性
- `renameAttr(String srcName, String targetName)`：重命名属性
- `getAttr(String name)`：获取属性值（Object类型）
- `getAttrs()`：获取节点的所有属性，返回Map<String, Object>类型
- `hasAttr()`：检查节点是否有属性
- `hasAttr(String name)`：检查节点是否有指定名称的属性
- `getAttrNames()`：获取所有属性名称
- `attrLoc(String name)`：获取属性的源位置
- `attrValueLoc(String name)`：获取属性值（包含源位置）
- `attrValueLoc(String name, ValueWithLocation vl)`：设置属性值（包含源位置）
- `attrValueLocs(Map<String, ValueWithLocation> attrs)`：批量设置属性值（包含源位置）
- `mergeAttrs(XNode node)`：合并另一个节点的属性
- `transformAttr(BiFunction<String, ValueWithLocation, ValueWithLocation> fn)`：转换属性
- `forEachAttr(BiConsumer<String, ValueWithLocation> consumer)`：遍历所有属性

### 3. 内容管理

- `contentText()`：获取节点的纯文本内容
- `getContentValue()`：获取节点的内容值（Object类型）
- `content()`：获取节点的内容（ValueWithLocation类型）
- `setContentValue(Object value)`：设置节点的内容值，并清空子节点
- `content(Object value)`：设置节点的内容值
- `content(SourceLocation loc, Object value)`：设置节点的内容值并指定源位置
- `hasContent()`：检查节点是否有内容
- `contentAsInt(int defaultValue)`：将内容转换为整数
- `isCDataText()`：检查内容是否为CDATA文本
- `normalizeContent()`：将文本内容转换为子节点
- `prependContent(Object value)`：在内容前追加文本
- `appendContent(Object value)`：在内容后追加文本
- `appendScript(SourceLocation loc, String script)`：追加脚本节点
- `appendBodyXml(String xml)`：追加XML内容到body
- `prependBodyXml(String xml)`：在body前插入XML内容
- `normalizeScriptContent()`：规范化脚本内容

### 4. 子节点管理

- `appendChild(XNode child)`：在子节点列表末尾添加新子节点
- `appendChildren(Collection<XNode> children)`：添加多个子节点到末尾
- `prependChild(XNode child)`：在子节点列表头部添加新子节点
- `prependChildren(Collection<XNode> children)`：添加多个子节点到头部
- `makeChild(String tagName)`：创建并添加子节点
- `makeChildWithAttr(String tagName, String attrName, Object attrValue)`：创建带属性的子节点并添加
- `insertChild(int index, XNode child)`：在指定位置插入单个子节点
- `insertChildren(int index, Collection<XNode> children)`：在指定位置插入多个子节点
- `removeChild(XNode child)`：移除指定的子节点
- `removeChildByIndex(int index)`：按索引移除子节点
- `removeChildByTag(String tagName)`：按标签名移除第一个匹配的子节点
- `removeChildrenByTag(String tagName)`：按标签名移除所有匹配的子节点
- `replaceChildrenByTag(String tagName, Collection<XNode> children)`：替换指定标签的子节点
- `replaceChildren(int index, Collection<XNode> children)`：替换指定位置的子节点
- `replaceChild(int index, XNode child)`：替换指定位置的子节点
- `clearChildren()`：清空所有子节点
- `detach()`：从父节点中分离当前节点
- `remove()`：从父节点中移除当前节点
- `detachChildren()`：分离所有子节点并返回
- `clearBody()`：清空节点的内容和子节点
- `getChildren()`：获取所有子节点，返回List<XNode>类型
- `childIterator()`：获取子节点迭代器
- `child(int i)`：按索引获取子节点
- `childByTag(String tagName)`：按标签名获取第一个匹配的子节点
- `childByAttr(String attrName, Object attrValue)`：按属性值获取第一个匹配的子节点
- `childrenByTag(String tagName)`：按标签名获取所有匹配的子节点，返回List<XNode>类型
- `childrenByAttr(String attrName, Object attrValue)`：按属性值获取所有匹配的子节点
- `countChildByTag(String tagName)`：统计指定标签的子节点数量
- `countChildByAttr(String attrName, Object attrValue)`：统计指定属性值的子节点数量
- `childValue(String name)`：获取子节点的内容值
- `childContentText(String name)`：获取子节点的文本内容
- `childAttr(String name, String attrName)`：获取子节点的属性值
- `nodeWithAttr(String attrName, Object attrValue)`：在当前节点或子节点中查找匹配属性的节点
- `childWithAttr(String attrName, Object attrValue)`：在子节点中查找匹配属性的节点
- `uniqueChild(String tagName)`：获取唯一的子节点（有多个时抛异常）
- `uniqueChild(String tagName, Function<ErrorCode, NopException> errorFactory)`：获取唯一子节点，可自定义异常工厂
- `makeChild(String tagName)`：创建或返回指定标签的子节点
- `makeChildWithAttr(String tagName, String attrName, Object attrValue)`：创建或返回带属性的子节点
- `addChild(String tagName)`：创建并添加子节点（返回子节点）
- `childByIndex(int i)`：按索引获取子节点
- `childIndex()`：获取当前节点在父节点中的索引
- `firstChild()`：获取第一个子节点
- `lastChild()`：获取最后一个子节点
- `forEachChild(Consumer<XNode> action)`：遍历所有子节点

### 5. 节点遍历与查找

- `selectNode(IXSelector<XNode> selector)`：使用选择器查找单个节点
- `selectMany(IXSelector<XNode> selector)`：使用选择器查找多个节点
- `selectOne(IXSelector<XNode> selector)`：使用选择器查找单个值
- `updateSelected(IXSelector<XNode> selector, Object value)`：更新选中的节点
- `findByTag(String tagName)`：查找指定标签的节点
- `findAllByTag(String tagName)`：查找所有指定标签的节点
- `find(Predicate<XNode> filter)`：递归查找满足条件的节点
- `findAll(Predicate<XNode> filter)`：递归查找所有满足条件的节点
- `findChild(Predicate<XNode> filter)`：在子节点中查找满足条件的节点
- `findChildren(Predicate<XNode> filter)`：在所有子节点中查找满足条件的节点
- `countChild(Predicate<XNode> filter)`：统计满足条件的子节点数量
- `forEachNode(Consumer<XNode> action)`：递归遍历所有节点
- `closest(String tagName)`：查找最近的祖先节点
- `contains(XNode node)`：检查是否包含指定节点
- `visit(ITreeVisitor<XNode> visitor)`：访问所有节点（支持深度优先或广度优先遍历）
- `parents()`：获取所有父节点列表

### 6. 节点操作

- `getTagName()`：获取节点标签名
- `setTagName(String tagName)`：设置节点标签名
- `withTagName(String tagName)`：设置标签名并返回当前节点（链式调用）
- `getParent()`：获取父节点
- `getParent(int parentLevel)`：获取指定层级的父节点
- `getParentParent()`：获取父节点的父节点
- `root()`：获取根节点
- `getTreeLevel()`：获取节点在树中的层级
- `cloneWithoutChildren()`：浅复制节点，只复制节点本身和属性，不复制子节点
- `cloneInstance()`：深度复制节点，包括所有子节点
- `cloneChildren()`：克隆所有子节点
- `cloneParent(int upLevel)`：克隆祖先节点
- `clearAttrs()`：清空所有属性
- `clearComment()`：清空注释
- `clearLocation()`：清空源位置信息
- `singleNode()`：获取单一节点（非dummy节点时返回自身）
- `freeze(boolean cascade)`：冻结节点，防止修改
- `frozen()`：检查节点是否已冻结
- `checkNotReadOnly()`：检查节点是否只读
- `replaceByXml(SourceLocation loc, String xml, boolean forHtml)`：用XML内容替换当前节点
- `replaceBy(XNode node)`：用另一个节点替换当前节点
- `replaceByList(Collection<XNode> list)`：用节点列表替换当前节点
- `renameNames(Map<String, String> nameMap)`：递归重命名tagName和属性名
- `renameAttr(String srcName, String targetName)`：重命名属性
- `renameChild(String srcName, String targetName)`：重命名子节点

### 7. 节点查询与导航

- `nextSibling()`：获取下一个兄弟节点
- `prevSibling()`：获取上一个兄弟节点
- `nextElementSibling()`：获取下一个元素兄弟节点
- `prevElementSibling()`：获取上一个元素兄弟节点
- `nextLeaf()`：获取下一个叶子节点
- `prevLeaf()`：获取上一个叶子节点
- `firstLeaf()`：获取第一个叶子节点
- `lastLeaf()`：获取最后一个叶子节点
- `depth()`：获取节点深度
- `childIndexByTag(String tagName)`：获取子节点在同名节点中的索引
- `childIndexOfSameTag()`：获取在同名节点中的索引
- `commonAncestor(XNode node)`：查找最近公共祖先
- `parent(int level)`：获取指定层级的父节点
- `attrVPath(String name)`：获取属性值并解析为虚拟文件路径
- `attrValueLoc(String name)`：获取属性值及位置
- `attrValueLocs(Map<String, ValueWithLocation> attrs)`：批量设置属性值及位置
- `getAllNamespaces()`：获取所有命名空间
- `getXmlnsForUrl(String url)`：根据URL获取命名空间前缀
- `getUrlForXmlns(String ns)`：根据命名空间前缀获取URL

### 8. 序列化

- `xml()`：转换为XML字符串
- `outerXml(boolean withXmlDecl, boolean withIndent)`：转换为外部XML字符串（包含根元素）
- `innerXml()`：转换为内部XML字符串（不包含根元素）
- `serializeToString()`：转换为XML字符串（包含XML声明）
- `toString()`：转换为XPath字符串（包含源位置）
- `dump()`：打印节点结构到日志（包含源位置注释）
- `collectXPath(StringBuilder sb)`：收集XPath路径到StringBuilder

### 9. 扩展与元数据

- `getExtension(String name)`：获取扩展对象
- `setExtension(String name, IXNodeExtension value)`：设置扩展对象
- `removeExtension(String name)`：移除扩展对象
- `syncAllExtensionToNode()`：同步所有扩展到节点
- `syncAllExtensionFromNode()`：从节点同步所有扩展
- `uniqueAttr()`：获取或设置唯一标识属性名
- `getComment()`：获取注释
- `setComment(String comment)`：设置注释
- `getDocType()`：获取文档类型
- `setDocType(String docType)`：设置文档类型
- `getInstruction()`：获取指令
- `setInstruction(String instruction)`：设置指令
- `getSourceLocation()`：获取源位置
- `setLocation(SourceLocation loc)`：设置源位置
- `loc(SourceLocation loc)`：设置源位置并返回当前节点（链式调用）

### 10. 节点类型判断

- `isTextNode()`：判断是否为文本节点
- `isDummyNode()`：判断是否为占位节点
- `isElementNode()`：判断是否为元素节点
- `hasChild()`：判断是否有子节点
- `hasBody()`：判断是否有内容或子节点
- `hasChild(String childName)`：判断是否有指定标签的子节点

### 11. 其他操作

- `withAttr(String name, Object value)`：设置属性并返回当前节点（链式调用）
- `withTagName(String tagName)`：设置标签名并返回当前节点（链式调用）
- `getAttrCount()`：获取属性数量
- `getChildCount()`：获取子节点数量
- `getBody()`：获取节点体（内容或子节点）
- `normalizeText(boolean cascade)`：规范化文本节点
- `normalizeExpr()`：规范化表达式
- `normalizeExprInContent()`：规范化内容中的表达式
- `insertBeforeXml(SourceLocation loc, String xml, boolean forHtml)`：在节点前插入XML
- `insertAfterXml(SourceLocation loc, String xml, boolean forHtml)`：在节点后插入XML
- `assureAtMostOneChild()`：确保最多只有一个子节点
- `removeNextSiblings()`：移除后续所有兄弟节点

## 示例代码

```java
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import java.util.List;
import java.util.Map;

// 创建节点
XNode userNode = XNode.make("user");

// 设置属性
userNode.setAttr("id", "1");
userNode.setAttr("name", "test");
userNode.setAttrIfAbsent("status", "active"); // 仅当属性不存在时设置

// 获取属性
String id = userNode.attrText("id"); // "1"
String name = userNode.attrText("name"); // "test"
String email = userNode.attrText("email", "default@example.com"); // "default@example.com"
Boolean active = userNode.attrBoolean("active"); // true
Integer age = userNode.attrInt("age"); // null
Double height = userNode.attrDouble("height", 1.75); // 1.75
List<String> tags = userNode.attrCsvList("tags"); // ["tag1", "tag2"]

// 设置内容
userNode.content("用户信息");
userNode.setContentValue("新的内容");

// 添加子节点
XNode addressNode = XNode.make("address");
addressNode.setAttr("city", "Beijing");
userNode.appendChild(addressNode);

// 创建并添加子节点
XNode contactNode = userNode.makeChild("contact");
contactNode.setAttr("type", "email");
contactNode.content("test@example.com");

// 获取子节点
XNode addr = userNode.childByTag("address"); // address节点
String city = addr.attrText("city"); // "Beijing"
XNode emailContact = userNode.childByAttr("type", "email"); // email类型的contact节点
List<XNode> allContacts = userNode.childrenByTag("contact"); // 所有contact节点

// 查找节点
XNode phoneNode = userNode.find(n -> n.getTagName().equals("phone")); // 查找phone节点
List<XNode> allPhones = userNode.findAll(n -> n.getTagName().equals("phone")); // 所有phone节点

// 遍历节点
userNode.forEachNode(n -> {
    System.out.println("节点: " + n.getTagName());
});

userNode.forEachChild(n -> {
    System.out.println("子节点: " + n.getTagName());
});

userNode.forEachAttr((name, valueWithLoc) -> {
    System.out.println("属性: " + name + " = " + valueWithLoc.getValue());
});

// 复制节点
XNode shallowCopy = userNode.cloneWithoutChildren(); // 浅复制
XNode deepCopy = userNode.cloneInstance(); // 深度复制

// 解析XML字符串
XNode parsedNode = XNode.parse("<user><name>test</name></user>");

// 从资源加载
XNode fromResource = XNode.load("/path/to/file.xml");

// 序列化
String xml = userNode.xml(); // <user id="1" name="test"><address city="Beijing"/></user>
String outerXml = userNode.outerXml(true, true); // 包含XML声明和缩进
String innerXml = userNode.innerXml(); // 仅内容部分

// 导航
XNode parent = userNode.getParent();
XNode root = userNode.root();
int depth = userNode.depth();

// 链式调用
XNode node = XNode.make("div")
    .withAttr("class", "container")
    .withAttr("id", "main")
    .setAttr("data-value", "123");

// 冻结节点
userNode.freeze(true);
boolean isFrozen = userNode.frozen();

// 转换为TreeBean
TreeBean bean = userNode.toTreeBean();
```

## 最佳实践

1. **优先使用XNode**：所有XML操作优先使用XNode，避免直接使用DOM或JDOM
2. **选择合适的属性获取方法**：根据属性类型选择对应的获取方法，如`attrInt`、`attrBoolean`、`attrDouble`等，而不是统一使用`getAttr`然后手动转换
3. **批量操作**：使用`setAttrs`、`appendChildren`等方法进行批量操作，提高效率
4. **节点创建**：优先使用`makeChild`和`makeChildWithAttr`等便捷方法创建子节点
5. **性能考虑**：频繁操作时考虑使用`cloneWithoutChildren`或`cloneInstance`进行复制，避免意外修改原节点
6. **节点遍历**：使用`getChildren()`获取所有子节点，或使用`forEachChild`进行迭代
7. **XML转换**：使用`xml()`或`serializeToString()`方法将节点转换为XML字符串
8. **使用链式调用**：利用`withAttr`、`withTagName`等方法提高代码可读性
9. **正确处理空值**：使用`attrText(name, defaultValue)`而不是先`getAttr`再判断null
10. **异常处理**：使用`requireAttrText`、`requireAttrInt`等方法在属性缺失时抛出有意义的异常
11. **查找优先级**：使用`childByTag`获取单个节点比使用`childrenByTag`然后遍历更高效
12. **注意冻结状态**：节点冻结后修改会抛出异常，使用`frozen()`方法检查后再修改
13. **使用选择器**：对于复杂查询，使用`selectNode`、`selectMany`配合选择器接口，提高灵活性

## 注意事项

- 所有方法都是实例方法，通过XNode对象调用
- 线程安全：节点对象本身不是线程安全的，多线程环境下需加锁保护
- 属性值类型转换：`setAttr`方法会自动将各种类型转换为字符串
- 文本内容：`contentText()`方法会返回合并后的文本内容（如果节点同时有文本内容和子节点）
- XML片段解析：`parseFragments`方法返回的节点以`_`为标签名
- 节点复制：`cloneWithoutChildren()`只复制节点本身和属性，不复制子节点；`cloneInstance()`深度复制整个节点树
- 命名空间：XNode简化了XML命名空间处理，只在根节点处理xmlns配置
- 源位置追踪：节点和属性都支持`SourceLocation`追踪，便于调试和错误定位
- 冻结机制：`freeze`方法会冻结节点及其所有子节点，冻结后不可修改
- 选择器查询：`selectNode`、`selectMany`等方法需要实现`IXSelector`接口，提供了灵活的查询方式
- 遍历访问：`visit`方法支持深度优先或广度优先遍历，可控制遍历行为
- 内容与子节点互斥：设置`content`会清空子节点；添加子节点时会将`content`转为子节点
- 虚拟文件系统：支持`attrVPath`方法将属性值解析为虚拟文件路径
- 扩展机制：支持通过`getExtension`和`setExtension`方法绑定扩展对象，实现节点功能的扩展

## 扩展功能

XNode还支持：

- 节点比较与差异计算（通过TreeVisitors工具类）
- 节点合并与差量化更新（通过XLang的Delta合并机制）
- 节点序列化与反序列化（支持JSON转换）
- 与JSON的互转（通过JsonTool工具类）
- 支持标签集管理（通过attrCsvList等方法）
- 支持源位置追踪（所有操作都记录SourceLocation）
- 支持XPath路径收集（通过collectXPath方法）
- 支持CDATA文本处理（通过isCDataText方法判断）
- 支持命名空间处理（通过getAllNamespaces、getXmlnsForUrl等方法）

## 替代方案

避免使用以下XML库：

- DOM：API复杂，性能较差，方法冗长
- JDOM：第三方依赖，与平台集成度低
- SAX：事件驱动，使用复杂

## 在XLang中作为扩展方法

XNode的方法可在Nop平台的XLang表达式语言（XScript/XPL）中作为扩展方法调用：

**映射规则：**
```
node.$method(args) → 调用XNode的method方法
```

**示例：**
```xpl
<c:script>
  XNode user = $user.makeChild('contact');
  $user.appendChild(user);
</c:script>
```
