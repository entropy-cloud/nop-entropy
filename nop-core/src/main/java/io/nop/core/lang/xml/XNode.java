/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.graphql.GraphQLMap;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.WriterEvalOutput;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.lang.json.handler.CollectTextJsonHandler;
import io.nop.core.lang.xml.handler.CollectJObjectHandler;
import io.nop.core.lang.xml.handler.CollectTextHandler;
import io.nop.core.lang.xml.handler.CollectXmlHandler;
import io.nop.core.lang.xml.json.StdXNodeToJsonTransformer;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.tree.ITreeStructure;
import io.nop.core.model.tree.ITreeVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.IResource;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.nop.commons.util.objects.ValueWithLocation.NULL_VALUE;
import static io.nop.core.CoreConstants.ATTR_CLASS;
import static io.nop.core.CoreConstants.ATTR_ID;
import static io.nop.core.CoreConstants.ATTR_NAME;
import static io.nop.core.CoreConstants.ATTR_V_ID;
import static io.nop.core.CoreConstants.DUMMY_TAG_NAME;
import static io.nop.core.CoreConstants.TEXT_TAG_NAME;
import static io.nop.core.CoreErrors.ARG_ATTR_NAME;
import static io.nop.core.CoreErrors.ARG_CHILD;
import static io.nop.core.CoreErrors.ARG_INDEX;
import static io.nop.core.CoreErrors.ARG_NODE;
import static io.nop.core.CoreErrors.ARG_TAG_NAME;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_XML_ATTACH_CHILD_NOT_ALLOW_NULL;
import static io.nop.core.CoreErrors.ERR_XML_ATTACH_CHILD_SHOULD_NOT_HAS_PARENT;
import static io.nop.core.CoreErrors.ERR_XML_ATTR_IS_EMPTY;
import static io.nop.core.CoreErrors.ERR_XML_INVALID_CHILD_INDEX;
import static io.nop.core.CoreErrors.ERR_XML_MULTIPLE_CHILD_WITH_SAME_TAG_NAME;
import static io.nop.core.CoreErrors.ERR_XML_NOT_NODE_VALUE;
import static io.nop.core.CoreErrors.ERR_XNODE_IS_READONLY;

/**
 * 通用的树形结构模型，节点具有属性(attr)，内容(content)，以及子节点(child)。 XNode.equals是指针相等
 * <p>
 * 为了维持代码执行的精确语义，属性必须保持定义顺序，因此采用LinkedHashMap来保存。
 */
@GraphQLMap
public class XNode implements Serializable, ISourceLocationGetter, ISourceLocationSetter, ITreeBean, ITreeStructure,
        ICloneable, IJsonSerializable, IFreezable {
    private static final long serialVersionUID = -8460236455991070110L;

    static final Logger LOG = LoggerFactory.getLogger(XNode.class);

    private static final List<XNode> EMPTY_CHILDREN = Collections.emptyList();
    private static final Map<String, ValueWithLocation> EMPTY_ATTRS = Collections.emptyMap();

    private static final int FLAG_READ_ONLY = 0x1;

    private SourceLocation sourceLocation;
    private String tagName;
    private Map<String, ValueWithLocation> attributes = EMPTY_ATTRS;
    private List<XNode> children = EMPTY_CHILDREN;
    private ValueWithLocation content = NULL_VALUE;

    private XNode parent;
    private int flags;

    private String comment;
    private String uniqueAttr;

    private Map<String, IXNodeExtension> extensions;

    public XNode(String tagName) {
        this.tagName = Guard.notEmpty(tagName, "tagName");
    }

    public static XNode makeDocNode(String tagName) {
        return new XDocNode(tagName);
    }

    public static XNode make(String tagName) {
        return new XNode(tagName);
    }

    public static XNode makeTextNode() {
        return new XNode(TEXT_TAG_NAME);
    }

    public static XNode make(SourceLocation loc, String tagName) {
        XNode node = XNode.make(tagName);
        node.setLocation(loc);
        return node;
    }

    public IXNodeExtension getExtension(String name) {
        if (extensions == null)
            return null;
        return extensions.get(name);
    }

    public void setExtension(String name, IXNodeExtension value) {
        if (extensions == null)
            extensions = new HashMap<>();
        extensions.put(name, value);
    }

    public void removeExtension(String name) {
        if (extensions != null)
            extensions.remove(name);
    }

    public void syncAllExtensionToNode() {
        if (extensions != null) {
            for (IXNodeExtension extension : extensions.values()) {
                extension.syncToNode(this);
            }
        }
    }

    public void syncAllExtensionFromNode() {
        if (extensions != null) {
            for (IXNodeExtension extension : extensions.values()) {
                extension.syncFromNode(this);
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        collectXPath(sb);
        sb.append("@@").append(sourceLocation);
        return sb.toString();
    }

    void collectXPath(StringBuilder sb) {
        if (parent != null) {
            parent.collectXPath(sb);
            sb.append('/');
        }
        sb.append(tagName);
        String uidKey = uniqueAttr;
        if (uidKey == null) {
            if (hasAttr(ATTR_V_ID)) {
                uidKey = ATTR_V_ID;
            } else if (hasAttr(ATTR_ID)) {
                uidKey = ATTR_ID;
            } else if (hasAttr(ATTR_NAME)) {
                uidKey = ATTR_NAME;
            }
        }
        if (uidKey != null) {
            sb.append('[').append('@');
            sb.append(uidKey).append("=\"");
            String value = this.attrText(uidKey);
            sb.append(StringHelper.escapeXmlAttr(value));
            sb.append("\"]");
        } else if (parent != null) {
            int count = 0;
            int index = -1;
            for (int i = 0, n = parent.getChildCount(); i < n; i++) {
                XNode child = parent.child(i);
                if (child == this) {
                    count++;
                    index = i;
                } else if (child.getTagName().equals(tagName)) {
                    count++;
                }
            }
            // 如果存在多个同名节点，则补充index选择符
            if (count > 1) {
                sb.append("[").append(index).append(']');
            }
        }
    }

    public void _assignAttrs(Map<String, ValueWithLocation> attrs) {
        this.attributes = attrs;
    }

    void _assignParent(XNode parent) {
        this.parent = parent;
    }

    public Set<String> getAttrNames() {
        return attributes.keySet();
    }

    protected XNode makeNode() {
        return new XNode(tagName);
    }

    public String getComment() {
        return comment;
    }

    /**
     * 设置comment属性
     *
     * @return this
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * 节点的属性中可以作为唯一标识使用的属性名称，例如id, name等
     */
    public String uniqueAttr() {
        return uniqueAttr;
    }

    public void uniqueAttr(String value) {
        checkNotReadOnly();

        this.uniqueAttr = value;
    }

    @Override
    public boolean frozen() {
        return (flags & FLAG_READ_ONLY) != 0;
    }

    void checkNotReadOnly() {
        if (frozen())
            throw new NopException(ERR_XNODE_IS_READONLY).param(ARG_NODE, this);
    }

    public void freeze(boolean cascade) {
        boolean frozen = frozen();
        if (frozen)
            return;

        this.flags |= FLAG_READ_ONLY;

        if (cascade) {
            if (children != EMPTY_CHILDREN) {
                for (XNode child : children) {
                    child.freeze(true);
                }
            }
        }

        if (!frozen && children != EMPTY_CHILDREN) {
            children = Collections.unmodifiableList(children);
        }
    }

    public String getXmlnsForUrl(String url) {
        for (Map.Entry<String, ValueWithLocation> entry : this.attributes.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(CoreConstants.NS_XMLNS_PREFIX)) {
                if (url.equals(entry.getValue().asString())) {
                    return key.substring(CoreConstants.NS_XMLNS_PREFIX.length());
                }
            }
        }
        return null;
    }

    /**
     * 占位使用，只有XDocNode具有docType
     */
    public String getDocType() {
        return null;
    }

    public void setDocType(String docType) {
        checkNotReadOnly();
    }

    /**
     * 占位使用，只有XDocNode具有instruction
     */
    public String getInstruction() {
        return null;
    }

    public void setInstruction(String instruction) {
        checkNotReadOnly();
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        checkNotReadOnly();
        this.tagName = Guard.notEmpty(tagName, "tagName");
    }

    public SourceLocation getLocation() {
        return sourceLocation;
    }

    public void setLocation(SourceLocation loc) {
        checkNotReadOnly();
        this.sourceLocation = loc;
    }

    /**
     * 是否文本节点
     */
    public boolean isTextNode() {
        return tagName.equals(TEXT_TAG_NAME);
    }

    public boolean isDummyNode() {
        return tagName.equals(DUMMY_TAG_NAME);
    }

    public boolean isElementNode() {
        return !isTextNode();
    }

    public boolean hasContent() {
        return !content.isNull();
    }

    public void normalizeExprInContent() {
        if (hasContent()) {
            String text = contentText();
            // 没有闭合的表达式
            int pos = text.indexOf("${");
            if (pos >= 0 && text.indexOf('}', pos) < 0) {
                text = StringHelper.replace(text, "$", "${'$'}");
                content(content().getLocation(), text);
            }
        }
    }

    public void normalizeExpr() {
        forEachNode(child -> child.normalizeExprInContent());
    }

    public Object getContentValue() {
        return content.getValue();
    }

    public String contentText() {
        return content.asString();
    }

    public ValueWithLocation content() {
        return content;
    }

    public void setContentValue(Object value) {
        content(null, value);
        this.clearChildren();
    }

    public void content(Object value) {
        this.content(null, value);
    }

    public void content(SourceLocation loc, Object value) {
        checkNotReadOnly();
        ValueWithLocation content = ValueWithLocation.of(loc, value);
        this.content = content == null || content.isEmpty() ? NULL_VALUE : content;
    }

    public int contentAsInt(int defaultValue) {
        return ConvertHelper.toPrimitiveInt(getContentValue(), defaultValue, err -> new NopException(err).param(ARG_NODE, this));
    }

    public boolean isCDataText() {
        return content.isCDataText();
    }

    NopException newError(ErrorCode errorCode) {
        return new NopException(errorCode).param(ARG_NODE, this);
    }

    public Object getAttr(String name) {
        ValueWithLocation attr = attributes.get(name);
        return attr == null ? null : attr.getValue();
    }

    public String attrTextOrEmpty(String name) {
        Object value = getAttr(name);
        return StringHelper.toString(value, null);
    }

    public String attrText(String name) {
        Object value = getAttr(name);
        return StringHelper.emptyAsNull(StringHelper.toString(value, null));
    }

    public String attrText(String name, String defaultValue) {
        String value = attrText(name);
        if (value == null)
            value = defaultValue;
        return value;
    }

    public String requireAttrText(String name) {
        String value = attrText(name);
        if (value == null)
            throw newAttrError(ERR_XML_ATTR_IS_EMPTY, name);
        return value;
    }

    public boolean attrBoolean(String name) {
        return ConvertHelper.toPrimitiveBoolean(getAttr(name), err -> newAttrError(err, name));
    }

    public Boolean attrBoolean(String name, Boolean defaultValue) {
        Boolean value = ConvertHelper.toBoolean(getAttr(name), err -> newAttrError(err, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public Integer attrInt(String name) {
        Integer value = ConvertHelper.toInt(getAttr(name), err -> newAttrError(err, name));
        return value;
    }

    public Integer attrInt(String name, Integer defaultValue) {
        Integer value = ConvertHelper.toInt(getAttr(name), err -> newAttrError(err, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public Integer requireAttrInt(String name) {
        Integer value = attrInt(name);
        if (value == null)
            throw newAttrError(ERR_XML_ATTR_IS_EMPTY, name);
        return value;
    }

    public Long attrLong(String name) {
        Long value = ConvertHelper.toLong(getAttr(name), err -> newAttrError(err, name));
        return value;
    }

    public Long attrLong(String name, Long defaultValue) {
        Long value = ConvertHelper.toLong(getAttr(name), err -> newAttrError(err, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public Long requireAttrLong(String name) {
        Long value = attrLong(name);
        if (value == null)
            throw newAttrError(ERR_XML_ATTR_IS_EMPTY, name);
        return value;
    }

    public Double attrDouble(String name) {
        return attrDouble(name, null);
    }

    public Double attrDouble(String name, Double defaultValue) {
        Double value = ConvertHelper.toDouble(getAttr(name), err -> newAttrError(err, name));
        if (value == null)
            value = defaultValue;
        return value;
    }

    public Double requireAttrDouble(String name) {
        Double value = attrDouble(name);
        if (value == null)
            throw newAttrError(ERR_XML_ATTR_IS_EMPTY, name);
        return value;
    }

    public Set<String> attrCsvSet(String name) {
        Set<String> value = ConvertHelper.toCsvSet(getAttr(name), err -> newAttrError(err, name));
        return value;
    }

    public void addAttrCsvSet(String attrName, String newSet) {
        if (StringHelper.isEmpty(newSet))
            return;

        Set<String> set = attrCsvSet(attrName);
        if (set == null || set.isEmpty()) {
            setAttr(attrName, newSet);
        } else {
            set = CollectionHelper.mergeSet(set, ConvertHelper.toCsvSet(newSet));
            setAttr(attrName, StringHelper.join(set, ","));
        }
    }

    public void removeAttrCsvSet(String attrName, String set) {
        if (StringHelper.isEmpty(set))
            return;

        Set<String> curSet = attrCsvSet(attrName);
        if (curSet == null || curSet.isEmpty())
            return;

        Set<String> newSet = ConvertHelper.toCsvSet(set);
        if (newSet == null || newSet.isEmpty())
            return;

        Set<String> result = new LinkedHashSet<>(curSet);
        result.removeAll(newSet);
        setAttr(attrName, StringHelper.join(result, ","));
    }

    public Set<String> requireAttrCsvSet(String name) {
        Set<String> value = attrCsvSet(name);
        if (value == null || value.isEmpty())
            throw newAttrError(ERR_XML_ATTR_IS_EMPTY, name);
        return value;
    }

    protected NopException newAttrError(ErrorCode err, String attrName) {
        ValueWithLocation attr = this.attrValueLoc(attrName);
        return new NopException(err).source(attr).param(ARG_NODE, this).param(ARG_ATTR_NAME, attrName);
    }

    public SourceLocation attrLoc(String name) {
        ValueWithLocation attr = attributes.get(name);
        if (attr == null)
            return null;
        return attr.getLocation();
    }

    public ValueWithLocation attrValueLoc(String name) {
        ValueWithLocation attr = attributes.get(name);
        return attr == null ? NULL_VALUE : attr;
    }

    public void attrValueLoc(String name, ValueWithLocation vl) {
        checkNotReadOnly();
        if (vl.isNull()) {
            attributes.remove(name);
            return;
        }

        if (attributes == EMPTY_ATTRS)
            attributes = new LinkedHashMap<>();
        attributes.put(name, vl);
    }

    public XNode setAttr(String name, Object value) {
        return this.setAttr(null, name, value);
    }

    public XNode setAttr(SourceLocation loc, String name, Object value) {
        checkNotReadOnly();
        if (value == null || value == NULL_VALUE) {
            attributes.remove(name);
            return this;
        }

        if (attributes == EMPTY_ATTRS)
            attributes = new LinkedHashMap<>();
        attributes.put(name, ValueWithLocation.of(loc, value));
        return this;
    }

    public void attrValueLocs(Map<String, ValueWithLocation> attrs) {
        if (attrs != null) {
            checkNotReadOnly();

            if (attributes == EMPTY_ATTRS)
                attributes = new LinkedHashMap<>();
            attributes.putAll(attrs);
        }
    }

    public void mergeAttrs(XNode node) {
        attrValueLocs(node.attrValueLocs());
    }

    public Map<String, ValueWithLocation> attrValueLocs() {
        return attributes;
    }

    public XNode clearChildren() {
        checkNotReadOnly();

        this.children = EMPTY_CHILDREN;
        return this;
    }

    public XNode clearBody() {
        checkNotReadOnly();

        this.content = NULL_VALUE;
        this.children = EMPTY_CHILDREN;
        return this;
    }

    public XNode clearComment() {
        checkNotReadOnly();
        this.comment = null;
        for (XNode child : children) {
            child.clearComment();
        }
        return this;
    }

    public XNode clearLocation() {
        checkNotReadOnly();
        this.sourceLocation = null;
        for (XNode child : children) {
            child.clearLocation();
        }
        return this;
    }

    @Override
    public List<XNode> getChildren() {
        return children;
    }

    @Override
    public XNode getParent() {
        return parent;
    }

    public XNode getParentParent() {
        if (parent == null)
            return null;
        return parent.getParent();
    }

    public int getTreeLevel() {
        if (parent != null)
            return parent.getTreeLevel() + 1;
        return 0;
    }

    public XNode getParent(int parentLevel) {
        if (parentLevel == 0)
            return this;
        if (parent == null)
            return null;
        return parent.getParent(parentLevel - 1);
    }

    public Object getBody() {
        if (children.isEmpty())
            return content.getValue();
        return children;
    }

    public Map<String, Object> getAttrs() {
        if (attributes.isEmpty())
            return Collections.emptyMap();
        JObject obj = new JObject(this.getLocation(), attributes);
        obj.freeze(false);
        return obj;
    }

    public XNode setAttrs(Map<String, Object> attrs) {
        if (attrs != null && !attrs.isEmpty()) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                setAttr(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public boolean hasAttr() {
        return !attributes.isEmpty();
    }

    public boolean hasAttr(String name) {
        return attributes.containsKey(name);
    }

    public ValueWithLocation removeAttr(String name) {

        checkNotReadOnly();

        ValueWithLocation value = attributes.remove(name);
        if (value == null)
            return NULL_VALUE;
        return value;
    }

    public boolean removeAttrs(Collection<String> names) {

        checkNotReadOnly();

        if (names != null)
            return attributes.keySet().removeAll(names);
        return false;
    }

    public boolean removeAttrsWithPrefix(String prefix) {
        Guard.notNull(prefix, "prefix is null");
        return removeAttrsIf((name, vl) -> name.startsWith(prefix));
    }

    public boolean removeAttrsIf(BiPredicate<String, ValueWithLocation> predicate) {
        checkNotReadOnly();

        boolean b = false;
        Iterator<Map.Entry<String, ValueWithLocation>> it = attributes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            if (predicate.test(entry.getKey(), entry.getValue())) {
                it.remove();
                b = true;
            }
        }
        return b;
    }

    public void renameAttr(String srcName, String targetName) {
        checkNotReadOnly();

        ValueWithLocation attr = attributes.remove(srcName);
        if (attr != null) {
            attributes.put(targetName, attr);
        }
    }

    public void renameChild(String srcName, String targetName) {
        checkNotReadOnly();
        XNode child = childByTag(srcName);
        if (child != null)
            child.setTagName(targetName);
    }

    public final boolean hasChild() {
        return !children.isEmpty();
    }

    public final boolean hasBody() {
        return hasChild() || !content.isNull();
    }

    public boolean hasChild(String childName) {
        return childByTag(childName) != null;
    }

    public final int getChildCount() {
        return children.size();
    }

    public final int getAttrCount() {
        return attributes.size();
    }

    public final XNode child(int i) {
        return children.get(i);
    }

    public XNode uniqueChild(String tagName, Function<ErrorCode, NopException> errorFactory) {
        List<XNode> children = this.childrenByTag(tagName);
        if (children.isEmpty())
            return null;
        if (children.size() > 1)
            throw errorFactory.apply(ERR_XML_MULTIPLE_CHILD_WITH_SAME_TAG_NAME).param(ARG_TAG_NAME, tagName)
                    .param(ARG_NODE, this);
        return children.get(0);
    }

    public XNode uniqueChild(String tagName) {
        return uniqueChild(tagName, NopException::new);
    }

    public XNode childByTag(String tagName) {
        if (children.isEmpty())
            return null;

        for (XNode child : children) {
            if (child.getTagName().equals(tagName))
                return child;
        }
        return null;
    }

    public XNode childByAttr(String attrName, Object attrValue) {
        if (children.isEmpty())
            return null;

        if (ApiConstants.TREE_BEAN_PROP_TYPE.equals(attrName))
            return childByTag(String.valueOf(attrValue));

        for (XNode child : children) {
            if (child.matchAttr(attrName, attrValue))
                return child;
        }
        return null;
    }

    Object normalizeValue(Object v) {
        if (v instanceof String) {
            String s = v.toString();
            if (s.length() == 0)
                return null;
        }
        return v;
    }

    private boolean isAttrEq(Object v1, Object v2) {
        if (Objects.equals(v1, v2))
            return true;
        v1 = normalizeValue(v1);
        v2 = normalizeValue(v2);
        return Objects.equals(v1, v2);
    }

    public int countChildByAttr(String attrName, Object attrValue) {
        if (children.isEmpty())
            return 0;
        int ret = 0;
        for (XNode child : children) {
            if (child.matchAttr(attrName, attrValue))
                ret++;
        }
        return ret;
    }

    /**
     * 返回所有具有指定tagName的子节点。
     *
     * @param tagName 子节点标签名
     * @return 匹配的子节点集合。调用者不应修改返回的集合
     */
    public @Nonnull List<XNode> childrenByTag(String tagName) {
        if (children.isEmpty())
            return Collections.emptyList();
        List<XNode> ret = new ArrayList<>();
        for (XNode child : children) {
            if (child.getTagName().equals(tagName))
                ret.add(child);
        }
        return ret;
    }

    public List<XNode> childrenByAttr(String attrName, Object attrValue) {
        return TreeVisitors.findChildren(this, child -> child.matchAttr(attrName, attrValue));
    }

    public List<XNode> findChildren(Predicate<XNode> filter) {
        return TreeVisitors.findChildren(this, filter);
    }

    public int countChildByTag(String tagName) {
        if (children.isEmpty())
            return 0;
        int ret = 0;
        for (XNode child : children) {
            if (child.getTagName().equals(tagName))
                ret++;
        }
        return ret;
    }

    public XNode findChild(Predicate<XNode> filter) {
        for (XNode child : children) {
            if (filter.test(child))
                return child;
        }
        return null;
    }

    public int countChild(Predicate<XNode> filter) {
        if (children.isEmpty())
            return 0;
        int ret = 0;
        for (XNode child : children) {
            if (filter.test(child))
                ret++;
        }
        return ret;
    }

    public List<XNode> parents() {
        List<XNode> list = new ArrayList<>();
        XNode node = this;
        do {
            XNode parent = node.getParent();
            if (parent == null)
                break;
            list.add(parent);
            node = parent;
        } while (true);
        Collections.reverse(list);
        return list;
    }

    /**
     * 从节点自身开始向上查找，找到第一个标签名指定值的节点，可能是节点自身，也可能是节点的某个父节点。
     *
     * @param tagName 节点的标签名
     * @return 查找到的节点。如果未找到，则返回null
     */
    public XNode closest(String tagName) {
        XNode node = this;
        do {
            if (node.getTagName().equals(tagName))
                return node;
            node = node.getParent();
        } while (node != null);

        return null;
    }

    /**
     * 是否包含子节点
     *
     * @param node 待检查的子节点
     */
    public boolean contains(XNode node) {
        if (this == node)
            return true;

        int d1 = depth();
        int d2 = node.depth();
        if (d1 >= d2)
            return false;
        return node.parent(d2 - d1 - 1) == this;
    }

    /**
     * 向上查找，返回根节点
     */
    public XNode root() {
        XNode node = this;
        do {
            XNode parent = node.getParent();
            if (parent == null)
                return node;
            node = parent;
        } while (true);
    }

    /**
     * 返回第一个子节点
     */
    public XNode firstChild() {
        if (children.isEmpty())
            return null;
        return children.get(0);
    }

    public XNode lastChild() {
        if (children.isEmpty())
            return null;
        return children.get(children.size() - 1);
    }

    public int childIndex() {
        if (parent == null)
            return -1;
        return parent.getChildren().indexOf(this);
    }

    public int childIndexOfSameTag() {
        if (parent == null)
            return -1;
        List<XNode> children = parent.getChildren();
        int index = 0;
        for (XNode child : children) {
            if (child == this) {
                return index;
            }
            if (child.getTagName().equals(tagName)) {
                index++;
            }
        }
        return -1;
    }

    public int depth() {
        if (parent == null)
            return 1;
        int ret = 1;
        XNode node = parent;
        do {
            ret++;
            node = node.parent;
        } while (node != null);
        return ret;
    }

    /**
     * parent(0) 为 parent(), parent(1) 为parent().parent()
     *
     * @param level 向上查找第几层的父节点，从0开始
     * @return 查找到的父节点
     */
    public XNode parent(int level) {
        XNode p = parent;

        for (int i = 0; i < level; i++) {
            if (p == null)
                return null;
            p = p.parent;
        }
        return p;
    }

    public XNode nextSibling() {
        if (parent == null)
            return null;
        int idx = parent.getChildren().indexOf(this);
        if (idx < 0)
            return null;
        idx++;
        if (idx >= parent.getChildren().size())
            return null;
        return parent.getChildren().get(idx);
    }

    public XNode prevSibling() {
        if (parent == null)
            return null;
        int idx = parent.getChildren().indexOf(this);
        if (idx <= 0)
            return null;
        return parent.getChildren().get(idx - 1);
    }

    public XNode prevElementSibling() {
        int idx = childIndex();
        if (idx <= 0)
            return null;

        List<XNode> children = parent.getChildren();
        for (int i = idx - 1; i >= 0; i--) {
            XNode node = children.get(i);
            if (node.isElementNode())
                return node;
        }
        return null;
    }

    public XNode nextElementSibling() {
        int idx = childIndex();
        if (idx < 0)
            return null;

        List<XNode> children = parent.getChildren();
        for (int i = idx + 1, n = children.size(); i < n; i++) {
            XNode node = children.get(i);
            if (node.isElementNode())
                return node;
        }
        return null;
    }

    public XNode firstLeaf() {
        XNode node = this;
        do {
            if (!node.hasChild())
                return node;
            node = node.child(0);
        } while (true);
    }

    public XNode lastLeaf() {
        XNode node = this;
        do {
            if (!node.hasChild())
                return node;
            node = node.child(node.getChildCount() - 1);
        } while (true);
    }

    public XNode nextLeaf() {
        if (this.hasChild()) {
            return firstLeaf();
        }
        XNode node = this;
        do {
            XNode next = node.nextSibling();
            if (next != null) {
                return next.firstLeaf();
            }
            node = node.getParent();
        } while (node != null);
        return null;
    }

    public XNode prevLeaf() {
        if (this.hasChild()) {
            return lastLeaf();
        }
        XNode node = this;
        do {
            XNode next = node.prevSibling();
            if (next != null) {
                return next.lastLeaf();
            }
            node = node.getParent();
        } while (node != null);
        return null;
    }

    /**
     * 如果tagName对应的子节点已经存在，则返回子节点，否则新建一个子节点，追加到children集合后返回
     *
     * @param tagName 节点的标签名
     * @return 名称为tagName的子节点
     */
    public XNode makeChild(String tagName) {
        XNode node = this.childByTag(tagName);
        if (node == null) {
            node = XNode.make(tagName);
            appendChild(node);
        }
        return node;
    }

    public XNode makeChildWithAttr(String tagName, String attrName, Object attrValue) {
        XNode node = childWithAttr(attrName, attrValue);
        if (node != null)
            return node;

        node = XNode.make(tagName);
        node.setAttr(attrName, attrValue);
        appendChild(node);
        return node;
    }

    public XNode addChild(String tagName) {
        XNode node = XNode.make(tagName);
        appendChild(node);
        return node;
    }

    void attachChild(XNode child) {
        if (child == null)
            throw newError(ERR_XML_ATTACH_CHILD_NOT_ALLOW_NULL);

        if (child.getParent() != null)
            throw newError(ERR_XML_ATTACH_CHILD_SHOULD_NOT_HAS_PARENT).param(ARG_CHILD, child);

        if (children == EMPTY_CHILDREN) {
            children = new ArrayList<>();

            _normalizeContent(this);
        }
        child._assignParent(this);
    }

    /**
     * 追加子节点到children集合尾部
     */
    public void appendChild(XNode child) {

        checkNotReadOnly();

        attachChild(child);
        children.add(child);
    }

    /**
     * 增加节点到children集合头部
     */
    public void prependChild(XNode child) {

        checkNotReadOnly();

        attachChild(child);
        children.add(0, child);
    }

    /**
     * 插入子节点到children集合的指定位置
     *
     * @param index 插入位置。如果index>=children.size(), 则在集合尾部追加
     */
    public void insertChild(int index, XNode child) {
        checkNotReadOnly();

        if (index <= 0) {
            prependChild(child);
            return;
        }
        if (index >= getChildCount()) {
            appendChild(child);
            return;
        }
        attachChild(child);
        children.add(index, child);
    }

    /**
     * 合并连续的文本节点，并且删除多余的空白节点
     */
    public void normalizeText(boolean cascade) {
        checkNotReadOnly();

        if (content.isNull()) {
            if (children.size() == 1) {
                XNode child = children.get(0);
                if (child.isTextNode()) {
                    children.clear();
                    content = child.content();
                } else if (cascade) {
                    child.normalizeText(true);
                }
            } else if (children.size() > 1) {
                if (_isAllChildText()) {
                    // 所有子节点都是文本节点，可以合并节点内容
                    SourceLocation loc = children.get(0).getLocation();
                    StringBuilder sb = new StringBuilder();
                    for (XNode child : children) {
                        sb.append(child.contentText());
                    }
                    this.content = ValueWithLocation.of(loc, sb.toString());
                    this.children.clear();
                } else {
                    // 删除子节点中头部和尾部的文本节点
                    for (int i = 0, n = children.size(); i < n; i++) {
                        XNode child = children.get(i);
                        if (child.isTextNode() && StringHelper.isBlank(child.contentText())) {
                            children.remove(i);
                            i--;
                            n--;
                        } else {
                            break;
                        }
                    }

                    for (int i = children.size() - 1; i >= 0; i--) {
                        XNode child = children.get(i);
                        if (child.isTextNode() && StringHelper.isBlank(child.contentText())) {
                            children.remove(i);
                        } else {
                            break;
                        }
                    }

                    if (cascade) {
                        for (int i = 0, n = children.size(); i < n; i++) {
                            XNode child = children.get(i);
                            child.normalizeText(true);
                        }
                    }
                }
            }
        }
    }

    boolean _isAllChildText() {
        for (XNode child : children) {
            if (!child.isTextNode())
                return false;
        }
        return true;
    }

    boolean _isAnyChildCDATA() {
        for (XNode child : children) {
            if (child.isCDataText())
                return true;
        }
        return false;
    }

    public void insertChildren(int index, Collection<XNode> children) {

        checkNotReadOnly();

        if (children != null) {
            for (XNode child : children) {
                attachChild(child);
                this.children.add(index, child);
                index++;
            }
        }
    }

    public XNode removeChildByIndex(int index) {

        checkNotReadOnly();

        return this.children.remove(index);
    }

    public XNode removeChildByTag(String tagName) {
        XNode child = childByTag(tagName);
        if (child == null)
            return null;
        removeChild(child);
        return child;
    }

    public void removeChildrenByTag(String tagName) {
        if (children.isEmpty())
            return;

        children.removeIf(node -> node.getTagName().equals(tagName));
    }

    public boolean removeChild(XNode child) {

        checkNotReadOnly();

        return this.children.remove(child);
    }

    public void replaceChildren(int index, Collection<XNode> children) {

        checkNotReadOnly();

        this.removeChildByIndex(index);
        if (children != null) {
            for (XNode child : children) {
                attachChild(child);
                this.children.add(index, child);
                index++;
            }
        }
    }

    public boolean replaceByXml(SourceLocation loc, String xml, boolean forHtml) {
        checkNotReadOnly();

        if (getParent() == null)
            return false;

        if (StringHelper.isEmpty(xml)) {
            detach();
            return true;
        }

        XNode node = XNodeParser.instance().forFragments(true).forHtml(forHtml).parseFromText(loc, xml);
        node.normalizeContent();
        this.getParent().replaceChildren(this.childIndex(), node.getChildren());
        return true;
    }

    public boolean replaceBy(XNode node) {
        if (this == node)
            return true;

        checkNotReadOnly();

        if (getParent() == null)
            return false;

        this.getParent().replaceChild(this.childIndex(), node);
        return true;
    }

    public boolean replaceByList(Collection<XNode> list) {

        checkNotReadOnly();

        if (getParent() == null)
            return false;
        this.getParent().replaceChildren(this.childIndex(), list);
        return true;
    }

    public void replaceChild(int index, XNode child) {

        checkNotReadOnly();

        if (index < 0 || index >= children.size())
            throw newError(ERR_XML_INVALID_CHILD_INDEX).param(ARG_INDEX, index);

        attachChild(child);
        children.set(index, child);
    }

    /**
     * 返回复制的节点
     */
    public XNode cloneInstance() {
        XNode node = makeNode();
        if (!this.attributes.isEmpty()) {
            node.attributes = new LinkedHashMap<>(this.attributes);
        }
        if (!this.children.isEmpty()) {
            List<XNode> clds = new ArrayList<>(this.getChildCount());
            for (XNode child : children) {
                XNode cloneChild = child.cloneInstance();
                cloneChild._assignParent(node);
                clds.add(cloneChild);
            }
            node.children = clds;
        }
        node.content = this.content;
        // clone得到的新节点要删除只读标记
        node.flags = this.flags & ~FLAG_READ_ONLY;
        node.comment = this.comment;
        node.sourceLocation = this.sourceLocation;
        return node;
    }

    /**
     * 返回clone的子节点集合。
     *
     * @return
     */
    public List<XNode> cloneChildren() {
        if (this.children.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<XNode> ret = new ArrayList<>(this.getChildCount());
            for (XNode child : children) {
                ret.add(child.cloneInstance());
            }
            return ret;
        }
    }

    public void appendChildren(Collection<XNode> children) {
        insertChildren(getChildCount(), children);
    }

    public void prependChildren(Collection<XNode> children) {
        insertChildren(0, children);
    }

    /**
     * 将当前节点从父节点中摘除
     */
    public XNode detach() {
        checkNotReadOnly();

        if (this.parent != null) {
            parent.getChildren().remove(this);
            this.parent = null;
        }
        return this;
    }

    public XNode remove() {
        return detach();
    }

    public List<XNode> detachChildren() {
        checkNotReadOnly();

        if (this.children.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<XNode> ret = this.children;
            for (XNode child : children) {
                child._assignParent(null);
            }
            this.children = Collections.emptyList();
            return ret;
        }
    }

    /**
     * 删除节点上的所有属性
     */
    public XNode clearAttrs() {

        checkNotReadOnly();

        attributes.clear();
        return this;
    }

    /**
     * 仅保留第一个子节点，删除所有其他子节点
     */
    public void assureAtMostOneChild() {
        checkNotReadOnly();

        if (children.size() <= 1)
            return;

        for (int i = 1, n = children.size(); i < n; i++) {
            children.remove(i);
            i--;
            n--;
        }
    }

    public void removeNextSiblings() {
        XNode parent = getParent();
        int index = childIndex();
        if (index < 0)
            return;

        parent.checkNotReadOnly();

        for (int i = index + 1, n = parent.getChildren().size(); i < n; i++) {
            parent.getChildren().remove(i);
            i--;
            n--;
        }
    }

    /**
     * 将文本内容作为子节点保存
     */
    public void normalizeContent() {
        checkNotReadOnly();
        this._normalizeContent(this);
    }

    private void _normalizeContent(XNode node) {
        if (!node.content.isNull()) {
            XNode textNode = XNode.makeTextNode();
            textNode.setLocation(node.content.getLocation());
            textNode.content = node.content;
            textNode._assignParent(this);

            node.content = NULL_VALUE;

            if (node.children == EMPTY_CHILDREN) {
                node.children = new ArrayList<>();
            }
            node.children.add(textNode);
        }
    }

    public void prependContent(Object value) {
        prependContent(null, value);
    }

    public void prependContent(SourceLocation loc, Object value) {

        checkNotReadOnly();

        this._normalizeContent(this);
        if (this.hasChild()) {
            XNode node = XNode.makeTextNode();
            node.content(loc, value);
            this.prependChild(node);
        } else {
            this.content(loc, value);
        }
    }

    public void appendContent(SourceLocation loc, Object value) {

        checkNotReadOnly();

        this._normalizeContent(this);
        if (this.hasChild()) {
            XNode node = XNode.makeTextNode();
            node.content(loc, value);
            this.appendChild(node);
        } else {
            this.content(loc, value);
        }
    }

    public void appendContent(Object value) {
        appendContent(null, value);
    }

    public void appendScript(SourceLocation loc, String script) {
        if (StringHelper.isBlank(script))
            return;

        if (!hasBody()) {
            content(loc, script);
        } else {
            if (!hasChild()) {
                normalizeScriptContent();
            }

            XNode node = XNode.make(CoreConstants.TAG_NAME_C_SCRIPT);
            node.content(loc, script);
        }
    }

    public void normalizeScriptContent() {
        checkNotReadOnly();

        if (this.content.isNull())
            return;
        XNode child = XNode.make(CoreConstants.TAG_NAME_C_SCRIPT);
        child.content(content);
        this.content = NULL_VALUE;
        appendChild(child);
    }

    public XNode appendBodyXml(String xml) {
        return appendBodyXml(null, xml, false);
    }

    public XNode appendBodyXml(SourceLocation loc, String xml, boolean forHtml) {

        checkNotReadOnly();

        if (StringHelper.isEmpty(xml))
            return this;

        XNode node = XNodeParser.instance().forFragments(true).forHtml(forHtml).parseFromText(loc, xml);
        _normalizeContent(this);
        this.appendChildren(node.detachChildren());
        return this;
    }

    public void prependBodyXml(String xml) {
        prependBodyXml(null, xml, false);
    }

    public void prependBodyXml(SourceLocation loc, String xml, boolean forHtml) {
        checkNotReadOnly();

        if (StringHelper.isEmpty(xml))
            return;

        XNode node = XNodeParser.instance().forFragments(true).forHtml(forHtml).parseFromText(loc, xml);
        _normalizeContent(this);
        this.insertChildren(0, node.detachChildren());
    }

    public void forEachChild(Consumer<XNode> action) {
        getChildren().forEach(action);
    }

    public void forEachAttr(BiConsumer<String, ValueWithLocation> consumer) {
        attributes.forEach(consumer);
    }

    public void transformAttr(BiFunction<String, ValueWithLocation, ValueWithLocation> fn) {
        Iterator<Map.Entry<String, ValueWithLocation>> it = attributes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            ValueWithLocation vl = fn.apply(entry.getKey(), entry.getValue());
            if (vl == null) {
                it.remove();
            } else if (vl != entry.getValue()) {
                entry.setValue(vl);
            }
        }
    }

    public void insertBeforeXml(String xml) {
        insertBeforeXml(null, xml, false);
    }

    public void insertBeforeXml(SourceLocation loc, String xml, boolean forHtml) {

        checkNotReadOnly();

        if (StringHelper.isEmpty(xml))
            return;

        XNode node = XNodeParser.instance().forFragments(true).forHtml(forHtml).parseFromText(loc, xml);
        _normalizeContent(node);
        this.getParent().insertChildren(childIndex(), node.detachChildren());
    }

    public void insertAfterXml(String xml) {
        insertAfterXml(null, xml, false);
    }

    public void insertAfterXml(SourceLocation loc, String xml, boolean forHtml) {
        checkNotReadOnly();

        if (StringHelper.isEmpty(xml))
            return;

        XNode node = XNodeParser.instance().forFragments(true).forHtml(forHtml).parseFromText(loc, xml);
        _normalizeContent(node);
        this.getParent().insertChildren(childIndex() + 1, node.detachChildren());
    }

    public Object childValue(String name) {
        XNode node = childByTag(name);
        if (node == null)
            return null;
        return node.getContentValue();
    }

    public Object childAttr(String name, String attrName) {
        XNode node = childByTag(name);
        if (node == null)
            return null;
        return node.getAttr(attrName);
    }

    public XNode nodeWithAttr(String attrName, Object attrValue) {
        if (Objects.equals(attrValue, getAttr(attrName)))
            return this;
        return childWithAttr(attrName, attrValue);
    }

    public XNode childWithAttr(String attrName, Object attrValue) {
        if (children == null || children.isEmpty())
            return null;
        for (XNode child : children) {
            Object value = child.getAttr(attrName);
            if (Objects.equals(value, attrValue))
                return child;
        }
        return null;
    }

    public TreeVisitResult visit(ITreeVisitor<XNode> visitor) {
        return TreeVisitors.visitTree(TreeVisitors.childrenStructureAdapter(), this, visitor);
    }

    /**
     * 递归查找满足条件的节点
     *
     * @param filter 满足条件时返回true
     * @return 如果未找到，则返回null
     */
    public XNode find(Predicate<XNode> filter) {
        return TreeVisitors.find(this, true, filter);
    }

    public List<XNode> findAll(Predicate<XNode> filter) {
        return TreeVisitors.findAll(this, true, filter);
    }

    public XNode findById(String id) {
        if (id == null)
            return null;
        return find(node -> id.equals(node.getAttr(ATTR_ID)));
    }

    public XNode findByTag(String tagName) {
        return find(node -> node.getTagName().equals(tagName));
    }

    public List<XNode> findAllByTag(String tagName) {
        return findAll(node -> node.getTagName().equals(tagName));
    }

    public boolean matchAttr(String attrName, Object attrValue) {
        return isAttrEq(getAttr(attrName), attrValue);
    }

    public XNode findByAttr(String attrName, Object attrValue) {
        return find(node -> node.matchAttr(attrName, attrValue));
    }

    public List<XNode> findAllByAttr(String attrName, Object attrValue) {
        return findAll(node -> node.matchAttr(attrName, attrValue));
    }

    public void removeAll(Predicate<XNode> filter) {
        checkNotReadOnly();

        if (filter.test(this)) {
            this.detach();
        } else {
            for (int i = 0, n = children.size(); i < n; i++) {
                XNode child = children.get(i);
                child.removeAll(filter);
            }
        }
    }

    public void removeAllByAttr(String attrName, Object attrValue) {
        this.removeAll(node -> node.matchAttr(attrName, attrValue));
    }

    /**
     * 用wrapper节点包裹所有子节点， 然后把wrapper节点挂接到本节点上。相当于是在本节点和子节点之间增加一层。
     */
    public void wrapChildren(XNode wrapper) {
        if (this.hasContent()) {
            wrapper.content(this.content);
            this.content = NULL_VALUE;
        } else if (hasChild()) {
            wrapper.appendChildren(this.detachChildren());
        }
        appendChild(wrapper);
    }

    /**
     * 将当前节点和它当前的父节点之间掺入一个新的父节点。 originalParentNode -> this ==> originalParentNode -> parent -> this
     *
     * @param parent 需要新增的父节点。
     */
    public void insertParent(XNode parent) {
        replaceBy(parent);
        this.parent = null;
        parent.appendChild(this);
    }

    public void output(IEvalOutput out) {
        if (out instanceof IXNodeHandler) {
            process((IXNodeHandler) out);
        } else if (out instanceof WriterEvalOutput) {
            Writer writer = ((WriterEvalOutput) out).getOut();
            try {
                this.saveToWriter(writer);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        } else {
            throw new IllegalArgumentException("nop.err.invalid-output");
        }
    }

    public void process(IXNodeHandler handler) {
        if (comment != null)
            handler.comment(comment);

        if (this.isTextNode()) {
            handler.value(this.getLocation(), this.content.getValue());
        } else {
            boolean hasChild = this.hasChild();
            boolean hasContent = !this.content.isNull();

            if (hasChild || hasContent) {
                handler.beginNode(this.sourceLocation, tagName, this.attributes);

                if (hasChild) {
                    handler.appendChildren(children);
                } else {
                    handler.value(this.content.getLocation(), this.content.getValue());
                }
                handler.endNode(tagName);
            } else {
                handler.simpleNode(getLocation(), tagName, attributes);
            }
        }
    }

    public String html() {
        return outerXml(true, true);
    }

    public String xml() {
        return outerXml(true, false);
    }

    public String outerXml(boolean indent, boolean forHtml) {
        StringBuilder sb = new StringBuilder();
        CollectXmlHandler handler = new CollectXmlHandler(sb).indentRoot(false).indent(indent).forHtml(forHtml);
        this.process(handler);
        return sb.toString();
    }

    public String bodyFullXml() {
        return bodyFullXml(DUMMY_TAG_NAME);
    }

    public String bodyFullXml(String rootTagName) {
        StringBuilder sb = new StringBuilder();
        CollectXmlHandler handler = new CollectXmlHandler(sb).indentRoot(false).indent(true);

        handler.beginNode(this.sourceLocation, rootTagName, Collections.emptyMap());

        if (hasChild()) {
            handler.appendChildren(children);
        } else {
            handler.value(this.content.getLocation(), this.content.getValue());
        }
        handler.endNode(rootTagName);
        return sb.toString();
    }

    public String innerHtml() {
        return innerXml(true, true);
    }

    public String innerXml() {
        return innerXml(true, false);
    }

    public String innerXml(boolean indent, boolean forHtml) {
        StringBuilder sb = new StringBuilder();
        CollectXmlHandler handler = new CollectXmlHandler(sb).indentRoot(false).indent(indent).forHtml(forHtml);

        if (!this.content.isNull()) {
            handler.value(this.content.getLocation(), this.content.getValue());
        } else if (!this.children.isEmpty()) {
            for (XNode child : children) {
                child.process(handler);
            }
        }
        return sb.toString();
    }

    public String text() {
        if (!this.hasChild())
            return content().asString();

        StringBuilder sb = new StringBuilder();
        this.process(new CollectTextHandler(sb));
        return sb.toString();
    }

    public void setInnerXml(String xml) {
        setInnerXml(null, xml, false);
    }

    public void setInnerXml(SourceLocation loc, String xml, boolean forHtml) {
        this.clearBody();
        appendBodyXml(loc, xml, forHtml);
    }

    /**
     * 直接子节点的所有文本
     */
    public String ownText() {
        if (!this.hasChild())
            return content().asString("");

        StringBuilder sb = new StringBuilder();
        for (XNode child : children) {
            if (child.isTextNode()) {
                sb.append(child.content().asString(""));
            }
        }
        return sb.toString();
    }

    /**
     * fullXml与outerXml()的区别在于，它可能包含xml文件头信息，另外可能会在注释中包含xml节点对应的源码位置信息
     *
     * @param indent  是否缩进
     * @param dumpLoc 是否在注释中包含节点对应的源码位置信息
     */
    public String fullXml(boolean indent, boolean dumpLoc) {
        StringBuilder out = new StringBuilder();
        boolean forHtml = false;
        if (getDocType() != null) {
            forHtml = StringHelper.startsWithIgnoreCase(getDocType(), "html");
        }
        try {
            saveToWriter(out, StringHelper.ENCODING_UTF8, indent, true, dumpLoc, forHtml);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return out.toString();
    }

    public void saveToWriter(Appendable out) throws IOException {
        saveToWriter(out, StringHelper.ENCODING_UTF8, true, true, false, false);
    }

    /**
     * 将XNode作为xml格式输出到文本流中
     *
     * @param out           输出流
     * @param encoding      字符编码，如果传入null，则认为是UTF-8
     * @param indent        是否缩进
     * @param includeProlog 是否包含<?xml version='1.0' ?>这样的文件头
     * @param dumpLoc       是否在注释中输出节点对应的源码路径
     * @param forHtml       是否作为html输出。html格式中只有少数标签允许简写封闭，例如<br/>
     *                      ，其他标签都必须采用完整格式封闭
     */
    public void saveToWriter(Appendable out, String encoding, boolean indent, boolean includeProlog, boolean dumpLoc,
                             boolean forHtml) throws IOException {
        if (includeProlog) {
            out.append("<?xml version=\"1.0\" encoding=\"");
            encoding = encoding == null ? StringHelper.ENCODING_UTF8 : encoding;
            out.append(encoding);
            out.append("\" ?>\n");
        }
        String docType = this.getDocType();
        if (docType != null) {
            out.append("<!DOCTYPE ");
            out.append(docType);
            out.append(">\n");
        }

        String instruction = this.getInstruction();
        if (instruction != null) {
            out.append("<?");
            out.append(instruction);
            out.append("?>\n");
        }

        CollectXmlHandler handler = new CollectXmlHandler(out).indentRoot(true).indent(indent).forHtml(forHtml)
                .dumpSourceLocation(dumpLoc);
        this.process(handler);
    }

    public void saveToStream(OutputStream os, String encoding) throws IOException {
        saveToStream(os, encoding, true, true, false, false);
    }

    public void saveToStream(OutputStream os, String encoding, boolean indent, boolean includeProlog, boolean dumpLoc,
                             boolean forHtml) throws IOException {
        if (encoding == null)
            encoding = StringHelper.ENCODING_UTF8;
        Writer out = new OutputStreamWriter(os, encoding);
        out = new BufferedWriter(out);
        saveToWriter(out, encoding, indent, includeProlog, dumpLoc, forHtml);
        out.flush();
    }

    public void saveToResource(IResource resource, String encoding) {
        saveToResource(resource, encoding, true, true, false, false);
    }

    public void saveToResource(IResource resource, String encoding, boolean indent, boolean includeProlog,
                               boolean dumpLoc, boolean forHtml) {
        OutputStream os = resource.getOutputStream();
        try {
            saveToStream(os, encoding, indent, includeProlog, dumpLoc, forHtml);
            os.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    /**
     * 调试辅助功能，可以打印出每个节点和属性的源码位置。XNode节点合并时会保留原节点中的源码位置， 通过调试输出功能便于追查具体属性和节点的来源。
     *
     * @param title 输出到log文件中的提示性标题。
     */
    public void dump(String title) {
        if (LOG.isInfoEnabled()) {
            LOG.info(getDumpString(title));
        }
    }

    public String getDumpString(String title) {
        StringBuilder sb = new StringBuilder();
        if (title != null)
            sb.append(title).append("=>");
        sb.append(this).append("\n");
        CollectXmlHandler handler = new CollectXmlHandler(sb).indent(true).dumpSourceLocation(true);
        this.process(handler);
        return sb.toString();
    }

    public void dump() {
        dump(null);
    }

    // ==============================DOM4j兼容函数===============================
    public String getTagNameWithoutNs() {
        String tagName = getTagName();
        int pos = tagName.indexOf(':');
        if (pos < 0)
            return tagName;
        return tagName.substring(pos + 1);
    }

    public String elementText(String name) {
        XNode element = this.element(name);
        if (element == null)
            return null;
        return element.contentText();
    }

    public Object elementAttr(String name, String attrName) {
        XNode element = this.element(name);
        if (element == null)
            return null;
        return element.getAttr(attrName);
    }

    /**
     * 忽略名字空间按照标签名查找子节点
     *
     * @param name
     * @return
     */
    public XNode element(String name) {
        int pos = name.indexOf(':');
        if (pos < 0) {
            for (XNode child : children) {
                if (_isElement(child, name))
                    return child;
            }
            return null;
        } else {
            return this.childByTag(name);
        }
    }

    static boolean _isElement(XNode child, String name) {
        String tagName = child.getTagName();
        int pos = tagName.indexOf(':');
        if (pos > 0) {
            return tagName.regionMatches(false, pos + 1, name, 0, name.length());
        } else {
            return tagName.equals(name);
        }
    }

    /**
     * 得到所有tagName为指定值的节点的列表。与childrenByTag的区别在于， 如果name不包含名字空间，则查找时会忽略名字空间。例如
     *
     * <pre>
     *     <root>
     *         <a:child />
     *     </root>
     * </pre>
     * <p>
     * root.elements("child")将返回a:child节点，而root.childByTag("child")将返回null。
     *
     * @param name 标签名。如果不包含名字空间，则匹配任意名字空间中的同名节点。
     * @return 节点列表
     */
    public List<XNode> elements(String name) {
        int pos = name.indexOf(':');
        if (pos < 0) {
            List<XNode> ret = new ArrayList<>();
            for (XNode child : children) {
                if (_isElement(child, name)) {
                    ret.add(child);
                }
            }
            return ret;
        } else {
            return this.childrenByTag(name);
        }
    }

    /**
     * 是否包含文本节点和普通节点的混排
     *
     * @return
     */
    public boolean hasMixedContent() {
        for (XNode node : this.children) {
            if (node.isTextNode())
                return true;
        }
        return false;
    }

    /**
     * 过滤掉所有文本节点之后的子节点列表
     *
     * @return
     */
    public List<XNode> elements() {
        if (hasMixedContent()) {
            return _getElements();
        }
        return this.children;
    }

    List<XNode> _getElements() {
        List<XNode> ret = new ArrayList<>();
        for (XNode node : this.children) {
            if (node.isElementNode()) {
                ret.add(node);
            }
        }
        return ret;
    }

    public boolean hasClass(String className) {
        String value = attrText(ATTR_CLASS);
        return StringHelper.hasClass(value, className);
    }

    /**
     * 得到两个节点的公共父节点
     *
     * @param node
     * @return
     */
    public XNode commonAncestor(XNode node) {
        int d1 = depth();
        int d2 = node.depth();

        XNode n1 = this;
        XNode n2 = node;
        if (d1 > d2) {
            n1 = node;
            n2 = this;
            int d = d2;
            d2 = d1;
            d1 = d;
        }

        // d1 <= d2
        XNode p1 = n1.getParent();
        XNode p2 = n2.parent(d2 - d1);
        do {
            if (p1 == null || p2 == null)
                return null;

            if (p1 == p2)
                return p1;

            p1 = p1.getParent();
            p2 = p2.getParent();
        } while (true);
    }

    /**
     * 找到符合条件的节点，将其替换为转换函数的返回值
     *
     * @param filter 节点的查找条件
     * @param fn     转换函数
     * @return 是否成功转换
     */
    public boolean transformChild(Predicate<XNode> filter, Function<XNode, ?> fn, boolean multiple) {
        this.checkNotReadOnly();

        if (children == null)
            return false;

        boolean bChange = false;
        for (int i = 0, n = children.size(); i < n; i++) {
            XNode child = children.get(i);
            if (filter == null || filter.test(child)) {
                Object o = fn.apply(child);
                if (o == child) {
                    if (child.transformChild(filter, fn, multiple)) {
                        if (!multiple)
                            break;
                        bChange = true;
                    }
                    continue;
                }

                if (Boolean.TRUE.equals(o)) {
                    continue;
                }

                bChange = true;
                if (o == null || Boolean.FALSE.equals(o)) {
                    children.remove(i);
                    i--;
                    n--;
                } else if (o instanceof Collection) {
                    Collection<?> c = (Collection<?>) o;
                    children.remove(i);
                    i--;
                    n--;
                    for (Object elm : c) {
                        XNode sub = XNode.fromTreeBean((ITreeBean) elm);
                        children.add(i, sub);
                        i++;
                        n++;
                    }
                } else {
                    XNode sub = XNode.fromTreeBean((ITreeBean) o);
                    children.set(i, sub);
                }
                if (!multiple)
                    break;
            } else {
                if (child.transformChild(filter, fn, multiple)) {
                    if (!multiple)
                        break;
                    bChange = true;
                }
            }
        }
        return bChange;
    }

    public void forEachNode(Consumer<XNode> action) {
        action.accept(this);
        for (XNode child : getChildren()) {
            child.forEachNode(action);
        }
    }

    public Object selectOne(IXSelector<XNode> selector) {
        return selector.select(this);
    }

    public Collection<?> selectMany(IXSelector<XNode> selector) {
        return selector.selectAll(this);
    }

    public void updateSelected(IXSelector<XNode> selector, Object value) {
        selector.updateSelected(this, value);
    }

    public TreeBean toTreeBean() {
        TreeBean bean = new TreeBean();
        bean.setTagName(tagName);
        bean.setLocation(this.getLocation());
        for (Map.Entry<String, ValueWithLocation> entry : this.attributes.entrySet()) {
            bean.setAttr(entry.getKey(), entry.getValue().getValue());
        }
        if (this.hasContent()) {
            bean.setContentValue(this.getContentValue());
        } else if (this.hasChild()) {
            List<TreeBean> list = new ArrayList<>(getChildCount());
            for (XNode child : children) {
                list.add(child.toTreeBean());
            }
            bean.setChildren(list);
        }
        return bean;
    }

    public void appendTreeBean(TreeBean bean) {
        XNode node = makeNode();
        node.assignTreeBean(bean);
        appendChild(node);
    }

    private void assignTreeBean(ITreeBean bean) {
        setLocation(bean.getLocation());
        setTagName(bean.getTagName());
        setAttrs(bean.getAttrs());
        if (bean.getContentValue() != null) {
            setContentValue(bean.getContentValue());
        }
        if (bean.getChildren() != null) {
            for (ITreeBean child : bean.getChildren()) {
                XNode childNode = makeNode();
                childNode.assignTreeBean(child);
                appendChild(childNode);
            }
        }
    }

    public static XNode fromTreeBean(ITreeBean bean) {
        if (bean instanceof XNode)
            return (XNode) bean;

        XNode node = make(bean.getTagName());
        node.assignTreeBean(bean);
        return node;
    }

    public static XNode fromValue(Object value) {
        if (StringHelper.isEmptyObject(value))
            return null;
        if (value instanceof XNode)
            return (XNode) value;
        if (value instanceof ITreeBean)
            return fromTreeBean((ITreeBean) value);
        if (value instanceof Map)
            return fromTreeBean(TreeBean.createFromJson((Map<String, Object>) value));
        if (value instanceof String)
            return XNodeParser.instance().parseFromText(null, value.toString());
        throw new NopException(ERR_XML_NOT_NODE_VALUE)
                .param(ARG_VALUE, value);
    }

    public String jsonText() {
        return jsonText("  ");
    }

    /**
     * 得到XNode的标准json格式： 标签名用$type表示，子节点用$body表示
     *
     * @param indent 缩进字符
     * @return
     */
    public String jsonText(String indent) {
        CollectTextJsonHandler handler = new CollectTextJsonHandler();
        handler.indent(indent);
        serializeToJson(handler);
        return handler.getOut().toString();
    }

    public Object toJsonObject() {
        BuildJObjectJsonHandler handler = new BuildJObjectJsonHandler();
        serializeToJson(handler);
        return handler.getResult();
    }

    public void serializeToJson(IJsonHandler out) {
        StdXNodeToJsonTransformer.instance().transformToJson(this, out);
    }

    public String childrenToJson(String indent) {
        StringBuilder sb = new StringBuilder();
        CollectTextJsonHandler handler = new CollectTextJsonHandler(sb);
        handler.indent(indent);
        handler.beginArray(getLocation());
        for (XNode child : getChildren()) {
            child.serializeToJson(handler);
        }
        handler.endArray();
        return sb.toString();
    }

    public Object toXJson() {
        CollectJObjectHandler handler = new CollectJObjectHandler();
        return handler.processNode(DUMMY_TAG_NAME, this);
    }

    public Object bodyToXJson() {
        if (!hasChild())
            return getContentValue();

        CollectJObjectHandler handler = new CollectJObjectHandler();
        for (XNode child : getChildren()) {
            child.process(handler);
        }
        return handler.getResult();
    }

    public boolean isXmlEquals(XNode node) {
        return isXmlEquals(node, IEqualsChecker.STRING_EQUALS);
    }

    /**
     * 比较两个节点的内容是否相等
     */
    public boolean isXmlEquals(XNode node, IEqualsChecker<Object> valueEquals) {
        if (!getTagName().equals(node.getTagName()))
            return false;

        if (getAttrCount() != node.getAttrCount())
            return false;

        if (getChildCount() != node.getChildCount())
            return false;

        for (Map.Entry<String, ValueWithLocation> entry : attributes.entrySet()) {
            String name = entry.getKey();
            ValueWithLocation vl = entry.getValue();
            ValueWithLocation other = node.attrValueLoc(name);
            if (other == null)
                return false;

            if (!valueEquals.isEquals(vl.getValue(), other.getValue()))
                return false;
        }

        if (!valueEquals.isEquals(contentText(), node.contentText()))
            return false;

        for (int i = 0, n = children.size(); i < n; i++) {
            XNode child = children.get(i);
            if (!child.isXmlEquals(node.child(i), valueEquals))
                return false;
        }

        return true;
    }

    public void renameNsPrefix(String oldNsPrefix, String newNsPrefix) {
        if (tagName.startsWith(oldNsPrefix))
            setTagName(newNsPrefix + tagName.substring(oldNsPrefix.length()));

        if (!attributes.isEmpty()) {
            List<Map.Entry<String, ValueWithLocation>> renamed = null;
            for (Map.Entry<String, ValueWithLocation> entry : attributes.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith(oldNsPrefix)) {
                    if (renamed == null)
                        renamed = new ArrayList<>();
                    renamed.add(entry);
                }
            }

            if (renamed != null) {
                for (Map.Entry<String, ValueWithLocation> entry : renamed) {
                    String newName = newNsPrefix + entry.getKey().substring(oldNsPrefix.length());
                    attributes.remove(entry.getKey());
                    attributes.put(newName, entry.getValue());
                }
            }
        }

        for (XNode child : children) {
            child.renameNsPrefix(oldNsPrefix, newNsPrefix);
        }
    }

    public void addJsonPrefix() {
        checkNotReadOnly();

        for (Map.Entry<String, ValueWithLocation> entry : attributes.entrySet()) {
            ValueWithLocation vl = entry.getValue();
            Object value = vl.getValue();
            if (needAddJsonPrefix(value)) {
                String str = "@:" + JsonTool.stringify(value);
                entry.setValue(ValueWithLocation.of(vl.getLocation(), str));
            }
        }

        Object value = content.getValue();
        if (needAddJsonPrefix(value)) {
            content = ValueWithLocation.of(content.getLocation(), value);
        }

        for (XNode child : children) {
            child.addJsonPrefix();
        }
    }

    public void removeJsonPrefix() {
        checkNotReadOnly();

        for (Map.Entry<String, ValueWithLocation> entry : attributes.entrySet()) {
            ValueWithLocation vl = entry.getValue();
            Object value = vl.getValue();
            if (value instanceof String && value.toString().startsWith("@:")) {
                Object json = JsonTool.parseNonStrict(value.toString().substring("@:".length()));
                entry.setValue(ValueWithLocation.of(vl.getLocation(), json));
            }
        }

        Object value = content.getValue();
        if (value instanceof String && value.toString().startsWith("@:")) {
            Object json = JsonTool.parseNonStrict(value.toString().substring("@:".length()));
            content = ValueWithLocation.of(content.getLocation(), json);
        }

        for (XNode child : children) {
            child.removeJsonPrefix();
        }
    }

    static boolean needAddJsonPrefix(Object value) {
        if (value == null)
            return false;

        if (value instanceof Number || value instanceof Boolean || value instanceof Collection || value instanceof Map)
            return true;

        return false;
    }
}