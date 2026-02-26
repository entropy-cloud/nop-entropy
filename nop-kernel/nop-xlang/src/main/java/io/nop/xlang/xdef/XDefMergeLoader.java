package io.nop.xlang.xdef;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslParseHelper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XDef合并加载器，用于加载xdef文件并将所有外部xdef:ref引用合并为统一的元模型。
 * 
 * <p>主要用于向AI传递完整的xdef元模型信息，避免分散到多个文件中。
 * 支持两种模式：
 * <ul>
 *   <li>内联模式(inlineXDefRef=true)：直接将引用内容合并到当前节点</li>
 *   <li>定义模式(inlineXDefRef=false)：将外部引用转换为xdef:define定义</li>
 * </ul>
 * 
 * <p>只保留必要的xdef属性：name, ref, body-type, key-attr, unique-attr
 */
public class XDefMergeLoader {

    private static final Set<String> KEEP_XDEF_ATTRS = Set.of(
            "xdef:name", "xdef:ref", "xdef:body-type", "xdef:key-attr", "xdef:unique-attr"
    );

    private final XDefMergeOptions options;
    private final ICache<Object, Object> cache;
    private final Set<String> loadedPaths = new LinkedHashSet<>();
    private final Map<String, XNode> collectedDefines = new LinkedHashMap<>();
    private final Map<String, String> collectedNsDecls = new LinkedHashMap<>();
    private final AtomicInteger defineCounter = new AtomicInteger(0);

    public XDefMergeLoader() {
        this(XDefMergeOptions.defaults(), new MapCache<>());
    }

    public XDefMergeLoader(XDefMergeOptions options) {
        this(options, new MapCache<>());
    }

    public XDefMergeLoader(XDefMergeOptions options, ICache<Object, Object> cache) {
        this.options = options != null ? options : XDefMergeOptions.defaults();
        this.cache = cache != null ? cache : new MapCache<>();
    }

    public XNode loadFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadFromResource(resource);
    }

    public XNode loadFromResource(IResource resource) {
        loadedPaths.clear();
        collectedDefines.clear();
        collectedNsDecls.clear();
        defineCounter.set(0);

        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        XNode result = transformNode(node);

        if (!options.isInlineXDefRef() && options.isCollectDefinesToRoot()) {
            flattenNestedDefines(result);

            mergeNsDeclsToRoot(result);

            if (!collectedDefines.isEmpty()) {
                int insertIndex = 0;
                for (XNode defineNode : collectedDefines.values()) {
                    XNode cloned = defineNode.cloneInstance();
                    removeNsDecls(cloned);
                    result.getChildren().add(insertIndex++, cloned);
                }
            }
        }

        return result;
    }

    private void mergeNsDeclsToRoot(XNode root) {
        for (Map.Entry<String, String> entry : collectedNsDecls.entrySet()) {
            String attrName = entry.getKey();
            if (!root.hasAttr(attrName)) {
                root.setAttr(attrName, entry.getValue());
            }
        }
    }

    private void removeNsDecls(XNode node) {
        node.getAttrNames().removeIf(name -> 
            name.startsWith("xmlns:") || name.equals("xmlns") || name.startsWith("x:"));
        
        for (XNode child : node.getChildren()) {
            removeNsDecls(child);
        }
    }

    private void flattenNestedDefines(XNode node) {
        List<XNode> nestedDefines = node.getChildren().stream()
                .filter(child -> child.getTagName().equals("xdef:define"))
                .collect(java.util.stream.Collectors.toList());

        for (XNode defineNode : nestedDefines) {
            String defineName = defineNode.attrText("xdef:name");
            if (defineName != null && !collectedDefines.containsKey(defineName)) {
                collectedDefines.put(defineName, defineNode.cloneInstance());
            }
        }

        node.getChildren().removeIf(child -> child.getTagName().equals("xdef:define"));

        for (XNode child : node.getChildren()) {
            flattenNestedDefines(child);
        }
    }

    public Set<String> getLoadedPaths() {
        return loadedPaths;
    }

    public Map<String, XNode> getCollectedDefines() {
        return collectedDefines;
    }

    private XNode transformNode(XNode node) {
        processXDefRef(node);
        transformDefTypes(node);
        processAttributes(node);
        processChildren(node);
        return node;
    }

    private void processXDefRef(XNode node) {
        String refPath = node.attrText("xdef:ref");
        if (refPath == null || !refPath.endsWith(".xdef")) {
            return;
        }

        String resolvedPath = node.attrVPath("xdef:ref");
        if (resolvedPath == null) {
            resolvedPath = refPath;
        }

        if (options.isInlineXDefRef()) {
            inlineXDefRef(node, resolvedPath);
        } else {
            convertToDefine(node, resolvedPath);
        }
    }

    private void inlineXDefRef(XNode node, String refPath) {
        if (!loadedPaths.add(refPath)) {
            return;
        }

        XNode refNode = loadRefNode(refPath);
        if (refNode == null) {
            return;
        }

        node.mergeAttrs(refNode);

        List<XNode> children = refNode.detachChildren();
        if (node.hasChild()) {
            for (XNode child : children) {
                if (!node.hasChild(child.getTagName())) {
                    node.appendChild(child);
                }
            }
        } else {
            node.appendChildren(children);
        }

        node.removeAttr("xdef:ref");
    }

    private void convertToDefine(XNode node, String refPath) {
        String defineName = generateDefineName(refPath);

        if (!collectedDefines.containsKey(defineName)) {
            if (loadedPaths.add(refPath)) {
                XNode refNode = loadRefNode(refPath);
                if (refNode != null) {
                    collectNsDecls(refNode);

                    transformNode(refNode);

                    XNode defineNode = XNode.make("xdef:define");
                    defineNode.setAttr("xdef:name", defineName);
                    defineNode.setLocation(refNode.getLocation());

                    refNode.forEachAttr((name, value) -> {
                        if (KEEP_XDEF_ATTRS.contains(name)) {
                            defineNode.setAttr(name, value.asString());
                        }
                    });

                    for (XNode child : refNode.getChildren()) {
                        if (!child.getTagName().equals("xdef:define")) {
                            defineNode.appendChild(child.cloneInstance());
                        }
                    }

                    collectedDefines.put(defineName, defineNode);
                }
            }
        }

        node.setAttr("xdef:ref", defineName);
    }

    private void collectNsDecls(XNode node) {
        node.forEachAttr((name, value) -> {
            if ((name.startsWith("xmlns:") || name.equals("xmlns") || name.startsWith("x:")) 
                && !collectedNsDecls.containsKey(name)) {
                collectedNsDecls.put(name, value.asString());
            }
        });
    }

    private XNode loadRefNode(String refPath) {
        try {
            IResource refResource = VirtualFileSystem.instance().getResource(refPath);
            XNode refNode = DslNodeLoader.INSTANCE.loadFromResource(refResource).getNode();
            return transformNode(refNode);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateDefineName(String refPath) {
        String fileName = refPath;
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }
        if (fileName.endsWith(".xdef")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }

        String modelName = StringHelper.capitalize(StringHelper.xmlNameToVarName(fileName));

        String baseName = modelName;
        int counter = 1;
        while (collectedDefines.containsKey(modelName)) {
            modelName = baseName + counter++;
        }

        return modelName;
    }

    private void transformDefTypes(XNode node) {
        ValueWithLocation xdefValue = node.attrValueLoc("xdef:value");
        if (!xdefValue.isNull()) {
            String transformed = transformDefType(xdefValue.getLocation(), "xdef:value", xdefValue.asString());
            node.setContentValue(transformed);
        } else {
            ValueWithLocation content = node.content();
            if (!content.isNull()) {
                String transformed = transformDefType(content.getLocation(), "body", content.asString());
                node.setContentValue(transformed);
            }
        }
    }

    private void processAttributes(XNode node) {
        Iterator<Map.Entry<String, ValueWithLocation>> it = node.attrValueLocs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            String name = entry.getKey();

            if (name.startsWith("x:") || name.startsWith("xmlns:") || name.equals("xmlns")) {
                if (options.isRemoveNsDecls()) {
                    it.remove();
                }
            } else if (name.startsWith("xdef:")) {
                if (!KEEP_XDEF_ATTRS.contains(name)) {
                    it.remove();
                }
            } else {
                ValueWithLocation vl = entry.getValue();
                String defType = transformDefType(vl.getLocation(), name, vl.asString());
                entry.setValue(ValueWithLocation.of(vl.getLocation(), defType));
            }
        }
    }

    private void processChildren(XNode node) {
        if (options.isRemoveXDefChildren()) {
            node.getChildren().removeIf(child -> child.getTagName().startsWith("xdef:"));
        }

        for (XNode child : node.getChildren()) {
            transformNode(child);
        }
    }

    private String transformDefType(SourceLocation loc, String propName, String defType) {
        if (!options.isInlineEnumOptions()) {
            return defType;
        }

        XDefTypeDecl typeDecl = XDslParseHelper.parseDefType(loc, propName, defType);
        String stdDomain = typeDecl.getStdDomain();

        if (typeDecl.getOptions() != null) {
            String typeOptions = typeDecl.getOptions();

            if (stdDomain.equals(XDefConstants.STD_DOMAIN_ENUM)) {
                if (StringHelper.isValidClassName(typeOptions)) {
                    DictBean dict = DictProvider.instance().requireDict(null, typeOptions, cache, null);
                    return stdDomain + ':' + dict.getOptionsText();
                }
            } else if (stdDomain.equals(XDefConstants.STD_DOMAIN_DICT)) {
                DictBean dict = DictProvider.instance().requireDict(null, typeOptions, cache, null);
                return XDefConstants.STD_DOMAIN_ENUM + ':' + dict.getOptionsText();
            }
            return stdDomain + ":" + typeOptions;
        }
        return stdDomain;
    }
}
