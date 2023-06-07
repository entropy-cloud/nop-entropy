/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.IFreezable;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xdef.impl.XDefHelper;

import java.util.List;
import java.util.Map;

public interface IXDefNode extends IComponentModel, IFreezable {

    String getTagName();

    default boolean isUnknownTag() {
        return getTagName().equals("*");
    }

    IXDefComment getComment();

    /**
     * 解析xdef文件时为每一个节点自动生成的唯一id
     */
    String getXdefId();

    XDefOverride getXdefDefaultOverride();

    String getXdefName();

    IGenericType getXdefBeanExtendsType();

    List<IGenericType> getXdefBeanImplementsTypes();

    Map<String, ? extends IXDefAttribute> getAttributes();

    default IXDefAttribute getAttribute(String attrName) {
        IXDefAttribute attr = getAttributes().get(attrName);
        return attr;
    }

    default XDefTypeDecl getAttrType(String attrName) {
        IXDefAttribute attr = getAttribute(attrName);
        if (attr == null)
            return getXdefUnknownAttr();
        return attr.getType();
    }

    Map<String, ? extends IXDefNode> getChildren();

    default IXDefNode getChild(String childName) {
        IXDefNode child = getChildren().get(childName);
        if (child == null)
            child = getXdefUnknownTag();
        return child;
    }

    default boolean hasAttr() {
        return !getAttributes().isEmpty() || getXdefUnknownAttr() != null;
    }

    default boolean hasChild() {
        return !getChildren().isEmpty() || getXdefUnknownTag() != null;
    }

    XDefTypeDecl getXdefUnknownAttr();

    IXDefNode getXdefUnknownTag();

    @NoReflection
    default boolean isAllowUnknownTag() {
        return getXdefUnknownTag() != null;
    }

    @NoReflection
    default boolean isAllowUnknownAttr() {
        if (getXdefUnknownAttr() != null)
            return true;
        XDefTypeDecl value = getXdefValue();
        // 如果value是fullXml，则允许根节点包含未知属性
        if (value != null && value.isFullXmlNode())
            return true;
        return false;
    }

    XDefBodyType getXdefBodyType();

    /**
     * 局部元数据对象应用或者指向外部元数据文件的全路径引用
     */
    String getXdefRef();

    /**
     * 如果ref指向局部引用，则返回局部引用的name，否则返回null
     */
    default String getLocalRef() {
        return XDefHelper.getLocalRef(getXdefRef());
    }

    IXDefNode getRefNode();

    /**
     * 本节点以及所有子节点的ref属性是否已经解析得到对应的refNode。
     */
    boolean isRefResolved();

    void setRefResolved(boolean resolved);

    void setRefNode(IXDefNode refNode);

    Boolean getXdefSupportExtends();

    @NoReflection
    default boolean isSupportExtends() {
        return Boolean.TRUE.equals(getXdefSupportExtends());
    }

    /**
     * 是否允许存在同名的兄弟节点
     */
    Boolean getXdefAllowMultiple();

    @NoReflection
    default boolean isAllowMultiple() {
        return Boolean.TRUE.equals(getXdefAllowMultiple());
    }

    Boolean getXdefMandatory();

    @NoReflection
    default boolean isMandatory() {
        return Boolean.TRUE.equals(getXdefMandatory());
    }

    Boolean getXdefDeprecated();

    @NoReflection
    default boolean isDeprecated() {
        return Boolean.TRUE.equals(getXdefDeprecated());
    }

    Boolean getXdefInternal();

    @NoReflection
    default boolean isInternal() {
        return Boolean.TRUE.equals(getXdefInternal());
    }

    String getXdefUniqueAttr();

    String getXdefKeyAttr();

    String getXdefOrderAttr();

    IGenericType getXdefBeanBodyType();

    String getXdefBeanClass();

    String getXdefBeanProp();

    String getXdefBeanRefProp();

    String getXdefBeanBodyProp();

    String getXdefBeanTagProp();

    String getXdefBeanCommentProp();

    String getXdefBeanSubTypeProp();

    String getXdefBeanChildName();

    String getXdefBeanUnknownAttrsProp();

    String getXdefBeanUnknownChildrenProp();

    List<? extends IXDefProp> getXdefProps();

    /**
     * 是否从refNode继承了xdef:value配置
     */
    boolean isValueInherited();

    /**
     * 是否从refNode继承了指定名称的属性配置
     */
    boolean isAttrInherited(String attrName);

    /**
     * 是否从refNode继承了指定名称的子节点配置
     */
    boolean isChildInherited(String childName);

    boolean isUnknownChildInherited();

    boolean isUnknownAttrInherited();

    /**
     * 是通过xdef:define引入的虚拟节点定义
     */
    boolean isExplicitDefine();

    XDefTypeDecl getXdefValue();

    /**
     * 是否除了ref属性之外，没有任何其他属性和子节点。这种情况下完全继承父对象的结构，可以不用生成新的对象类。
     */
    default boolean isOnlyRef() {
        return getXdefRef() != null && !hasAttr() && !hasChild() && getXdefValue() == null;
    }

    /**
     * 没有attr和tagProp定义
     */
    default boolean isSimple() {
        return !hasAttr() && (getXdefBodyType() != null || getXdefValue() != null) && getXdefBeanTagProp() == null
                && getXdefBeanBodyProp() == null && getXdefBeanCommentProp() == null;
    }

    default boolean isObjSchema() {
        return !isSimple();
    }

    /**
     * 类似<description>xxx</description>这种结构，此时XDefNode节点对应于SimpleSchema，而一般情况下节点对应于ObjSchema
     */
    default boolean isSimpleValue() {
        return isSimple() && getXdefBodyType() == null && !hasChild() && getXdefValue() != null;
    }

    default boolean isSimpleUnion() {
        return isSimple() && getXdefBodyType() == XDefBodyType.union;
    }

    default boolean isSimpleList() {
        return isSimple() && getXdefBodyType() == XDefBodyType.list;
    }

    default boolean isSimpleMap() {
        return isSimple() && getXdefBodyType() == XDefBodyType.map;
    }

    /**
     * 将当前节点序列化为XNode结构
     *
     * @param keys
     * @param nodeRefs 用于识别循环引用，其中包含序列化过程中所有已经处理过的IXDefNode节点
     * @return 序列化结果
     */
    XNode toNode(XDefKeys keys, Map<IXDefNode, XNode> nodeRefs);
}