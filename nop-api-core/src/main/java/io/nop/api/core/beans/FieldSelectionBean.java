/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.IMergeable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.api.core.ApiErrors.ARG_PROP_NAME;
import static io.nop.api.core.util.CloneHelper.deepMerge;
import static io.nop.api.core.util.FreezeHelper.checkNotFrozen;
import static io.nop.api.core.util.FreezeHelper.freezeItems;
import static io.nop.api.core.util.FreezeHelper.freezeMap;

/**
 * 类似于GraphQL请求，指定需要访问的对象结构
 */
@DataBean
@SuppressWarnings("PMD.TooManyStaticImports")
public class FieldSelectionBean implements Serializable, IDeepCloneable, IFreezable,
        IMergeable<FieldSelectionBean>, IJsonString {
    private static final long serialVersionUID = -3939122561810898355L;
    public static final FieldSelectionBean DEFAULT_SELECTION = new FieldSelectionBean(true, false);
    public static final FieldSelectionBean HIDDEN_SELECTION = new FieldSelectionBean(true, true);

    private boolean frozen;

    private String name;
    private boolean hidden;
    private Map<String, Object> args;

    private Map<String, Map<String, Object>> directives;

    /**
     * key为结果对象的属性名，FieldSelectionBean中的name一般为null，如果不为null，则表示从源对象获取的属性名
     */
    private Map<String, FieldSelectionBean> fields;

    public FieldSelectionBean() {
    }

    public FieldSelectionBean(boolean frozen, boolean hidden) {
        this.frozen = frozen;
        this.hidden = hidden;
    }

    public FieldSelectionBean(String name) {
        this.name = name;
    }

    public static FieldSelectionBean fromProps(Collection<String> propNames) {
        FieldSelectionBean ret = new FieldSelectionBean();
        for (String propName : propNames) {
            ret.addCompositeField(propName, false);
        }
        return ret;
    }

    public static FieldSelectionBean fromProp(String... propNames) {
        FieldSelectionBean ret = new FieldSelectionBean();
        for (String propName : propNames) {
            ret.addCompositeField(propName, false);
        }
        return ret;
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    public FieldSelectionBean deepClone() {
        FieldSelectionBean bean = newFieldSelectionBean();
        bean.name = name;
        bean.hidden = hidden;
        bean.args = CloneHelper.deepCloneMap(args);
        bean.directives = CloneHelper.deepCloneMap(directives);
        bean.fields = CloneHelper.deepCloneMap(fields);
        return bean;
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean pretty) {
        return FieldSelectionPrinter.instance().print(this, pretty);
    }

    public void printTo(StringBuilder sb, boolean pretty) {
        FieldSelectionPrinter.instance().printTo(sb, this, pretty);
    }

    protected FieldSelectionBean newFieldSelectionBean() {
        return new FieldSelectionBean();
    }

    public int getAllDirectiveCount() {
        int count = directives == null ? 0 : directives.size();
        if (fields != null) {
            for (FieldSelectionBean field : fields.values()) {
                count += field.getAllDirectiveCount();
            }
        }
        return count;
    }

    /**
     * 是否简单字段，没有除字段名之外的配置信息
     */
    @JsonIgnore
    public boolean isSimpleField() {
        if (this == DEFAULT_SELECTION)
            return true;

        if (name != null)
            return false;

        if (args != null && !args.isEmpty())
            return false;
        if (directives != null && !directives.isEmpty())
            return false;
        if (fields != null && !fields.isEmpty())
            return false;
        return true;
    }

    public void freeze(boolean cascade) {
        if (!frozen) {
            this.frozen = true;
            this.args = freezeMap(args, true);
            this.directives = freezeMap(directives, true);
        }
        if (fields != null)
            freezeItems(fields.values(), cascade);
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Map<String, Object>> getDirectives() {
        return directives;
    }

    public void setDirectives(Map<String, Map<String, Object>> directives) {
        checkNotFrozen(this);
        this.directives = directives;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getDirectiveArg(String directName, String argName) {
        Map<String, Object> directive = getDirective(directName);
        return directive == null ? null : directive.get(argName);
    }

    public void setDirectiveArg(String directiveName, String argName, Object value) {
        Map<String, Object> directive = getDirective(directiveName);
        if (directive == null) {
            directive = new LinkedHashMap<>();
            setDirective(directiveName, directive);
        }
        directive.put(argName, value);
    }

    @JsonIgnore
    public String getKeyProp() {
        return (String) getDirectiveArg(ApiConstants.DIRECTIVE_PROP_META, ApiConstants.ARG_KEY_PROP);
    }

    public void setKeyProp(String keyProp) {
        setDirectiveArg(ApiConstants.DIRECTIVE_PROP_META, ApiConstants.ARG_KEY_PROP, keyProp);
    }

    @JsonIgnore
    public String getOrderProp() {
        return (String) getDirectiveArg(ApiConstants.DIRECTIVE_PROP_META, ApiConstants.ARG_ORDER_PROP);
    }

    public void setOrderProp(String orderProp) {
        setDirectiveArg(ApiConstants.DIRECTIVE_PROP_META, ApiConstants.ARG_ORDER_PROP, orderProp);
    }

    public Map<String, Object> getDirective(String name) {
        if (directives == null)
            return null;
        return directives.get(name);
    }

    protected void checkAllowChange() {
        checkNotFrozen(this);
    }

    public void setDirective(String name, Map<String, Object> directive) {
        checkNotFrozen(this);
        if (directives == null)
            directives = new LinkedHashMap<>();
        directives.put(name, directive);
    }

    public void addDirectives(Map<String, Map<String, Object>> directives) {
        checkNotFrozen(this);
        if (directives == null || directives.isEmpty())
            return;
        if (this.directives == null)
            this.directives = new LinkedHashMap<>();
        this.directives.putAll(directives);
    }

    /**
     * 隐藏字段仅在内部处理时使用，一般不返回给外部调用者。例如前段请求计算字段a, 后端处理时发现为了计算字段a需要加载字段b和c,
     * 则会给selection集合追加两个字段，然后交给底层装载器去批量加载。
     */
    @JsonInclude(Include.NON_DEFAULT)
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        checkNotFrozen(this);
        this.hidden = hidden;
    }

    @JsonIgnore
    public Set<String> getSourceFields() {
        if (fields == null || fields.isEmpty())
            return null;

        Set<String> fields = new LinkedHashSet<>();
        this.fields.forEach((name, field) -> {
            if (field.getName() != null) {
                fields.add(field.getName());
            } else {
                fields.add(name);
            }
        });
        return fields;
    }

    public boolean hasField() {
        return fields != null && !fields.isEmpty();
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, FieldSelectionBean> getFields() {
        return fields == null ? Collections.emptyMap() : fields;
    }

    public void setFields(Map<String, FieldSelectionBean> fields) {
        checkNotFrozen(this);

        if (fields != null) {
            // 确保entry不为null，与逐个加入的field一致
            for (Map.Entry<String, FieldSelectionBean> entry : fields.entrySet()) {
                if (entry.getValue() == null)
                    entry.setValue(DEFAULT_SELECTION);
            }
        }
        this.fields = fields;
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        checkNotFrozen(this);
        this.args = args;
    }

    public void addArgs(Map<String, Object> args) {
        checkNotFrozen(this);
        if (args == null || args.isEmpty())
            return;

        if (this.args == null)
            this.args = new LinkedHashMap<>();
        this.args.putAll(args);
    }

    public Object getArg(String name) {
        if (args == null)
            return null;
        return args.get(name);
    }

    public void setArg(String name, Object value) {
        checkNotFrozen(this);
        if (args == null)
            args = new LinkedHashMap<>();
        args.put(name, value);
    }

    public boolean hasField(String name) {
        return fields != null && fields.containsKey(name);
    }

    public boolean hasSourceField(String name) {
        return getSourceField(name) != null;
    }

    public FieldSelectionBean getSourceField(String name) {
        if (fields == null)
            return null;

        FieldSelectionBean field = fields.get(name);
        if (field != null)
            return field;

        // 有可能为字段指定别名
        for (FieldSelectionBean f : fields.values()) {
            if (f.getName() == null)
                continue;
            if (name.equals(f.getName()))
                return f;
        }
        return null;
    }

    public FieldSelectionBean getField(String name) {
        if (fields == null)
            return null;
        return fields.get(name);
    }

    public void addField(String name, FieldSelectionBean field) {
        Guard.notNull(field, "field");
        checkNotFrozen(this);
        if (fields == null)
            fields = new LinkedHashMap<>();
        FieldSelectionBean old = fields.putIfAbsent(name, field);
        if (old != null && old != DEFAULT_SELECTION)
            throw new NopException(ApiErrors.ERR_SELECTION_DUPLICATE_FIELD)
                    .param(ARG_PROP_NAME, name);
    }

    public void addField(String name) {
        addField(name, DEFAULT_SELECTION);
    }

    public void addHiddenField(String name) {
        addField(name, HIDDEN_SELECTION);
    }

    /**
     * 向当前选择集合中加入复合属性，需要进行递归处理。
     * 例如当前选择集为 a,b:{b1,b2}, 合并b.b1.c1之后的选择集为a,b:{b1: {c1}, b2}
     *
     * @param field   通过.分隔的复合属性名称
     * @param hasNext 如果hasNext为true，则返回一个可以修改的subField，用于追加子属性
     * @return field锁对应的属性选择对象
     */
    public FieldSelectionBean addCompositeField(String field, boolean hasNext) {
        checkNotFrozen(this);
        // fragment
        if (field.startsWith("...")) {
            return makeSubField(field, hasNext);
        }

        int pos = field.indexOf('.');
        if (pos < 0) {
            return makeSubField(field, hasNext);
        }

        FieldSelectionBean selection = makeSubField(field.substring(0, pos), true);

        do {
            int pos1 = pos + 1;
            pos = field.indexOf('.', pos1);
            if (pos < 0) {
                return selection.makeSubField(field.substring(pos1), hasNext);
            } else {
                String name = field.substring(pos1, pos);
                selection = selection.makeSubField(name, true);
            }
        } while (true);
    }

    public FieldSelectionBean makeSubField(String name, boolean hasNext) {
        checkNotFrozen(this);
        Map<String, FieldSelectionBean> fields = makeFields();
        FieldSelectionBean field = fields.get(name);
        if (hasNext) {
            if (field == null) {
                field = new FieldSelectionBean();
                fields.put(name, field);
            } else if (field.frozen()) {
                field = field.deepClone();
                fields.put(name, field);
            }
        } else {
            if (field == null) {
                field = DEFAULT_SELECTION;
                fields.put(name, field);
            }
        }
        return field;
    }

    Map<String, FieldSelectionBean> makeFields() {
        if (fields == null)
            fields = new LinkedHashMap<>();
        return fields;
    }

    @JsonIgnore
    public Set<String> flattenFields() {
        Set<String> ret = new TreeSet<>();

        _flatten(ret, null, this);
        return ret;
    }

    void _flatten(Set<String> ret, String prefix, FieldSelectionBean subField) {
        if (subField.fields != null) {
            for (Map.Entry<String, FieldSelectionBean> entry : subField.fields.entrySet()) {
                _flatten(ret, prefix == null ? entry.getKey() :
                        prefix + entry.getKey() + ".", entry.getValue());
            }
        }
    }

    public FieldSelectionBean merge(FieldSelectionBean bean) {
        if (this == bean)
            return this;

        if (bean.isSimpleField())
            return this;

        if (this.frozen()) {
            FieldSelectionBean ret = this.deepClone();
            return ret._merge(bean);
        }

        return this._merge(bean);
    }

    private FieldSelectionBean _merge(FieldSelectionBean bean) {
        if (bean.name != null)
            this.name = bean.name;
        this.args = deepMerge(this.args, bean.args);
        this.directives = deepMerge((Map) this.directives, (Map) bean.directives);

        return mergeFields(bean.getFields());
    }

    public FieldSelectionBean mergeFields(Map<String, FieldSelectionBean> fields) {
        if (this.frozen) {
            return deepClone()._mergeFields(fields);
        }
        return _mergeFields(fields);
    }

    private FieldSelectionBean _mergeFields(Map<String, FieldSelectionBean> fields) {
        if (fields.isEmpty())
            return this;

        for (Map.Entry<String, FieldSelectionBean> entry : fields.entrySet()) {
            String name = entry.getKey();
            FieldSelectionBean field = entry.getValue();
            makeSubField(name, !field.isSimpleField()).merge(field);
        }
        return this;
    }
}