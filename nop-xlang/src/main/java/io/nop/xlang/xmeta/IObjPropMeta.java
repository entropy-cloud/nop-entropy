/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XNodeValuePosition;
import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.xdef.SchemaKind;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;
import io.nop.xlang.xmeta.impl.ObjPropArgModel;
import io.nop.xlang.xmeta.impl.ObjPropAuthModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IObjPropMeta
        extends IKeyedElement, ISourceLocationGetter, Comparable<IObjPropMeta>, ITagSetSupport, IExtensibleObject {
    default String key() {
        return getName();
    }

    /**
     * 先按照propId排序，然后再按照属性名排序
     */
    @Override
    default int compareTo(IObjPropMeta o) {
        Integer propId = getPropId();
        if (propId == null)
            propId = 0;

        Integer otherPropId = o.getPropId();
        if (otherPropId == null)
            otherPropId = 0;

        int cmp = Integer.compare(propId, otherPropId);
        if (cmp != 0)
            return cmp;

        return getName().compareTo(o.getName());
    }

    String getName();

    String getDisplayName();

    String getDescription();

    Integer getPropId();

    List<ObjPropAuthModel> getAuths();

    ObjPropAuthModel getAuth(String authFor);

    ActionAuthMeta getReadAuth();

    ActionAuthMeta getWriteAuth();

    ActionAuthMeta getDeleteAuth();

    List<ObjPropArgModel> getArgs();

    default String getStdDomain() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getStdDomain();
    }

    boolean isPublished();

    boolean isMandatory();

    boolean isVirtual();

    /**
     * 是否内部属性。内部属性不出现在IDE的提示列表中，一般情况下在界面上也不可见。
     */
    boolean isInternal();

    boolean isLazy();

    boolean isQueryable();

    boolean isSortable();

    Set<String> getAllowFilterOp();

    Boolean getAllowCpExpr();

    /**
     * 是否已废弃。标记为废弃的属性不出现在IDE的提示信息里
     */

    boolean isDeprecated();

    boolean isInsertable();

    boolean isUpdatable();

    boolean isReadable();

    /**
     * 转换为xml属性或者节点时对应的标签名，一般情况下与属性名一致
     */
    String getXmlName();

    String getChildXmlName();

    String getChildName();

    default String getBizObjName() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        return schema.getBizObjName();
    }

    default String getItemBizObjName() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        ISchema itemSchema = schema.getItemSchema();
        if (itemSchema == null)
            return null;
        return itemSchema.getBizObjName();
    }

    /**
     * 缺省override配置
     */
    XDefOverride getDefaultOverride();

    /**
     * 获取本字段的值的时候，需要依赖其他字段。例如在批量加载的时候，表示需要把相关字段也进行批量加载
     */
    Set<String> getDepends();

    String getMapToProp();

    /**
     * 编辑页面上的提示信息
     */
    // String getHint();

    // String getPlaceholder();

    Set<String> getTagSet();

    ObjConditionExpr getAutoExpr();

    /**
     * 导出时执行的格式转换代码
     */
    // IEvalAction getExportExpr();

    /**
     * 导入时需要执行的格式解析和转换代码
     */
    // IEvalAction getImportExpr();

    /**
     * 动态计算属性的取值函数
     */
    // IEvalAction getGetter();

    /**
     * 动态计算属性的设值函数
     */
    // IEvalAction getSetter();

    Object getDefaultValue();

    ISchema getSchema();

    XNodeValuePosition getXmlPos();

    default String getMethodGetName() {
        IGenericType type = getType();
        if (type == PredefinedGenericTypes.PRIMITIVE_BOOLEAN_TYPE)
            return StringHelper.methodGet(getName(), true);
        return StringHelper.methodGet(getName());
    }

    default String getMethodSetName() {
        return StringHelper.methodSet(getName());
    }

    IGenericType getType();

    void setType(IGenericType type);

    default boolean isListSchema() {
        return getSchemaKind() == SchemaKind.LIST;
    }

    default SchemaKind getSchemaKind() {
        return getSchema().getSchemaKind();
    }

    default ISchema getItemSchema() {
        return getSchema().getItemSchema();
    }

    default String getSimpleClassName() {
        return getSchema().getSimpleClassName();
    }

    default IGenericType getComponentType() {
        IGenericType type = getType();
        if (type != null) {
            if (type.isArray() || type.isCollectionLike())
                return type.getComponentType();
        }
        ISchema itemSchema = getItemSchema();
        if (itemSchema != null)
            return itemSchema.getType();
        return null;
    }

    default String getItemSimpleClassName() {
        IGenericType type = getComponentType();
        if (type != null)
            return type.getSimpleClassName();

        return null;
    }

    default StdDataType getStdDataType() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        return schema.getStdDataType();
    }

    IEvalAction getTransformIn();

    IEvalAction getTransformOut();

    IEvalAction getGetter();

    IEvalAction getSetter();

    //String getObjMeta();

    XNode toNode(Map<ISchemaNode, XNode> nodeRefs);
}
