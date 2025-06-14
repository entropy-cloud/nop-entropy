/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLMap;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.IMapLike;
import io.nop.api.core.util.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.nop.api.core.ApiErrors.ARG_ATTR_NAME;
import static io.nop.api.core.ApiErrors.ARG_TAG_NAME;
import static io.nop.api.core.ApiErrors.ERR_JSON_TREE_BEAN_INVALID_ATTR_NAME;
import static io.nop.api.core.ApiErrors.ERR_JSON_TREE_BEAN_INVALID_TAG_NAME;

@DataBean
@GraphQLMap
public class TreeBean extends ExtensibleBean implements ITreeBean, IComponentModel {
    private static final long serialVersionUID = 5994290726377063658L;
    private static final Logger log = LoggerFactory.getLogger(TreeBean.class);
    private SourceLocation loc;
    private String tagName;
    private List<TreeBean> children;
    private Object value;

    public TreeBean() {
    }

    public TreeBean(String tagName) {
        this.setTagName(tagName);
    }

    public boolean treeEquals(TreeBean node) {
        if (!tagName.equals(node.getTagName()))
            return false;

        if (getChildCount() != node.getChildCount())
            return false;

        if (!Objects.equals(value, node.getContentValue()))
            return false;

        if (!Objects.equals(getAttrs(), node.getAttrs()))
            return false;

        if (children != null) {
            for (int i = 0, n = children.size(); i < n; i++) {
                TreeBean child = children.get(i);
                if (!child.treeEquals(node.getChildren().get(i)))
                    return false;
            }
        }
        return true;
    }

    public TreeBean attr(String name, Object value) {
        setAttr(name, value);
        return this;
    }

    @JsonIgnore
    @Override
    public int getChildCount() {
        List<?> children = getChildren();
        return children == null ? 0 : children.size();
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public TreeBean attrIgnoreNull(String name, Object value) {
        if (value == null)
            return this;
        return attr(name, value);
    }

    @JsonProperty(ApiConstants.TREE_BEAN_PROP_TYPE)
    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        if (!isXmlName(tagName))
            throw new NopException(ERR_JSON_TREE_BEAN_INVALID_TAG_NAME)
                    .param(ARG_TAG_NAME, tagName);

        this.tagName = tagName;
    }

    @JsonAnySetter
    public void setAttr(String name, Object value) {
        if (value == null) {
            removeAttr(name);
            return;
        }
        if (!isXmlName(name))
            throw new NopException(ERR_JSON_TREE_BEAN_INVALID_ATTR_NAME)
                    .param(ARG_ATTR_NAME, name);
        super.setAttr(name, value);
    }

    // 仅做快速校验
    private boolean isXmlName(String tagName) {
        if (ApiStringHelper.isEmpty(tagName))
            return false;
        if (tagName.indexOf('<') >= 0)
            return false;
        if (tagName.indexOf('&') >= 0)
            return false;
        if (tagName.indexOf('(') >= 0)
            return false;
        return true;
    }

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(ApiConstants.TREE_BEAN_PROP_LOC)
    public SourceLocation getLocation() {
        return loc;
    }

    public void setLocation(SourceLocation loc) {
        this.loc = loc;
    }


    public QueryBean toQueryBean() {
        QueryBean query = new QueryBean();
        query.setFilter(this);
        return query;
    }

    @JsonIgnore
    public List<TreeBean> getChildren() {
        return children;
    }

    public void setChildren(List<TreeBean> children) {
        this.children = children;
    }

    public void addChild(TreeBean child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public void appendChildren(Collection<TreeBean> children) {
        if (children != null) {
            for (TreeBean child : children) {
                addChild(child);
            }
        }
    }

    public TreeBean childByTag(String tagName) {
        if (children == null)
            return null;
        for (TreeBean child : children) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    public TreeBean nodeWithAttr(String attrName, Object attrValue) {
        if (Objects.equals(attrValue, getAttr(attrName)))
            return this;
        return childWithAttr(attrName, attrValue);
    }

    public TreeBean childWithAttr(String attrName, Object attrValue) {
        if (children == null)
            return null;
        for (TreeBean child : children) {
            Object value = child.getAttr(attrName);
            if (Objects.equals(value, attrValue))
                return child;
        }
        return null;
    }

    @JsonIgnore
    public Object getContentValue() {
        return value;
    }

    public void setContentValue(Object content) {
        this.value = content;
    }

    @JsonInclude(Include.NON_NULL)
    @JsonProperty(ApiConstants.TREE_BEAN_PROP_BODY)
    public Object getBody() {
        if (children != null && !children.isEmpty())
            return children;
        return value;
    }

    public void setBody(Object value) {

        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            this.children = new ArrayList<>(list.size());
            for (Object item : list) {
                Map<String, Object> map = toMap(item);
                TreeBean bean = createFromJson(map);
                children.add(bean);
            }
        } else if (value instanceof Map) {
            TreeBean child = createFromJson((Map<String, Object>) value);
            this.children = new ArrayList<>(1);
            this.children.add(child);
        } else if (value instanceof IMapLike) {
            TreeBean child = createFromJson(((IMapLike) value).toMap());
            this.children = new ArrayList<>(1);
            this.children.add(child);
        } else {
            this.value = value;
        }
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map)
            return (Map<String, Object>) value;
        if (value instanceof IMapLike)
            return ((IMapLike) value).toMap();
        throw new IllegalArgumentException("value not Map");
    }

    public void replaceChild(TreeBean oldChild, TreeBean newChild) {
        if (children == null) {
            if (newChild != null) {
                children = new ArrayList<>();
                children.add(newChild);
            }
        } else {
            int index = children.indexOf(oldChild);
            if (index < 0) {
                children.add(newChild);
            } else if (newChild == null) {
                children.remove(index);
            } else {
                children.set(index, newChild);
            }
        }
    }

    /**
     * 找到符合条件的节点，将其替换为转换函数的返回值
     *
     * @param filter 节点的查找条件
     * @param fn     转换函数
     *               <ul>
     *               <li>1. 如果返回当前节点，则递归处理子节点。</li>
     *               <li>2. 如果返回true，则跳过该子节点的处理。</li>
     *               <li>3. 如果返回false或者null，则删除该子节点。</li>
     *               <li>4. 如果返回集合节点，则删除该子节点，然后在原位置插入多个子节点</li>
     *               <li>5. 替换当前子节点</li>
     *                </ul>
     * @return 是否成功转换
     */
    public boolean transformChild(Predicate<TreeBean> filter, Function<TreeBean, ?> fn, boolean multiple) {
        if (children == null)
            return false;

        boolean bChange = false;
        for (int i = 0, n = children.size(); i < n; i++) {
            TreeBean child = children.get(i);
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
                        TreeBean sub = ((ITreeBean) elm).toTreeBean();
                        children.add(i, sub);
                        i++;
                        n++;
                    }
                } else {
                    TreeBean sub = ((ITreeBean) o).toTreeBean();
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

    public TreeBean toTreeBean() {
        return this;
    }

    public TreeBean cloneInstance() {
        TreeBean ret = new TreeBean();
        ret.setTagName(tagName);
        ret.setLocation(loc);
        ret.setContentValue(getContentValue());
        if (getAttrs() != null) {
            ret.setAttrs(new LinkedHashMap<>(getAttrs()));
        }
        if (getChildren() != null) {
            ret.setChildren(cloneChildren());
        }
        return ret;
    }

    @Override
    public Object toJsonObject() {
        return toJsonObject(false);
    }

    public Object toJsonObject(boolean includeLoc) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(ApiConstants.TREE_BEAN_PROP_TYPE, getTagName());
        if (loc != null && includeLoc) {
            ret.put(ApiConstants.TREE_BEAN_PROP_LOC, loc.toString());
        }
        if (getAttrs() != null) {
            ret.putAll(getAttrs());
        }

        if (getContentValue() != null)
            ret.put(ApiConstants.TREE_BEAN_PROP_BODY, getContentValue());

        if (children != null && !children.isEmpty()) {
            List<Object> body = new ArrayList<>(children.size());
            for (TreeBean child : children) {
                body.add(child.toJsonObject());
            }
            ret.put(ApiConstants.TREE_BEAN_PROP_BODY, body);
        }
        return ret;
    }

    public List<TreeBean> cloneChildren() {
        if (children == null)
            return null;

        List<TreeBean> ret = new ArrayList<>(children.size());
        for (TreeBean child : children) {
            ret.add(child.cloneInstance());
        }
        return ret;
    }

    public static TreeBean createFromJson(Map<String, Object> map) {
        String nodeName = (String) map.get(ApiConstants.TREE_BEAN_PROP_TYPE);
        TreeBean bean = new TreeBean();
        bean.setTagName(nodeName);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String name = entry.getKey();
            if (name.equals(ApiConstants.TREE_BEAN_PROP_TYPE))
                continue;
            if (name.equals(ApiConstants.TREE_BEAN_PROP_BODY)) {
                bean.setBody(entry.getValue());
            } else {
                bean.setAttr(name, entry.getValue());
            }
        }
        return bean;
    }

    public static TreeBean fromTreeBean(ITreeBean bean) {
        TreeBean ret = new TreeBean();
        ret.setLocation(bean.getLocation());
        ret.setAttrs(new LinkedHashMap<>(bean.getAttrs()));
        ret.setContentValue(bean.getContentValue());

        List<? extends ITreeBean> children = bean.getChildren();
        if (children != null) {
            for (ITreeBean child : children) {
                ret.addChild(TreeBean.fromTreeBean(child));
            }
        }
        return ret;
    }

    @JsonAnyGetter
    @Override
    public Map<String, Object> getAttrs() {
        return super.getAttrs();
    }
}