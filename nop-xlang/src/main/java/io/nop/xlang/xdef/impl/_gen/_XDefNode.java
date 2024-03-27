package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefNode;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [50:6:0:0]/nop/schema/xdef.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefNode extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefAttribute> _attributes ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefNode> _children ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.xlang.xdef.impl.XDefComment _comment ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _tagName ;
    
    /**
     *  
     * xml name: xdef:allow-multiple
     * 是否允许多个实例。声明了unique-attr，或者父节点声明了key-attr的情况下，
     * 本节点的allow-multiple缺省为true
     */
    private java.lang.Boolean _xdefAllowMultiple ;
    
    /**
     *  
     * xml name: xdef:bean-body-prop
     * 
     */
    private java.lang.String _xdefBeanBodyProp ;
    
    /**
     *  
     * xml name: xdef:bean-body-type
     * 
     */
    private io.nop.core.type.IGenericType _xdefBeanBodyType ;
    
    /**
     *  
     * xml name: xdef:bean-child-name
     * 
     */
    private java.lang.String _xdefBeanChildName ;
    
    /**
     *  
     * xml name: xdef:bean-class
     * 
     */
    private java.lang.String _xdefBeanClass ;
    
    /**
     *  
     * xml name: xdef:bean-comment-prop
     * 
     */
    private java.lang.String _xdefBeanCommentProp ;
    
    /**
     *  
     * xml name: xdef:bean-extends-type
     * 
     */
    private io.nop.core.type.IGenericType _xdefBeanExtendsType ;
    
    /**
     *  
     * xml name: xdef:bean-implements-types
     * 
     */
    private java.util.List<io.nop.core.type.IGenericType> _xdefBeanImplementsTypes ;
    
    /**
     *  
     * xml name: xdef:bean-prop
     * 
     */
    private java.lang.String _xdefBeanProp ;
    
    /**
     *  
     * xml name: xdef:bean-ref-prop
     * 
     */
    private java.lang.String _xdefBeanRefProp ;
    
    /**
     *  
     * xml name: xdef:bean-sub-type-prop
     * 
     */
    private java.lang.String _xdefBeanSubTypeProp ;
    
    /**
     *  
     * xml name: xdef:bean-tag-prop
     * 
     */
    private java.lang.String _xdefBeanTagProp ;
    
    /**
     *  
     * xml name: xdef:bean-unknown-attrs-prop
     * 
     */
    private java.lang.String _xdefBeanUnknownAttrsProp ;
    
    /**
     *  
     * xml name: xdef:bean-unknown-children-prop
     * 
     */
    private java.lang.String _xdefBeanUnknownChildrenProp ;
    
    /**
     *  
     * xml name: xdef:body-type
     * 定义节点的结构类型。set表示子节点需要具有唯一标签名或者唯一key，而list表示可以出现重复的子节点
     */
    private io.nop.xlang.xdef.XDefBodyType _xdefBodyType ;
    
    /**
     *  
     * xml name: xdef:default-override
     * 
     */
    private io.nop.xlang.xdef.XDefOverride _xdefDefaultOverride ;
    
    /**
     *  
     * xml name: xdef:define
     * 定义xdef片段，可以通过xdef:ref来引用
     */
    private KeyedList<io.nop.xlang.xdef.impl.XDefNode> _xdefDefines = KeyedList.emptyList();
    
    /**
     *  
     * xml name: xdef:deprecated
     * 已废弃，不应再使用
     */
    private java.lang.Boolean _xdefDeprecated ;
    
    /**
     *  
     * xml name: xdef:id
     * 
     */
    private java.lang.String _xdefId ;
    
    /**
     *  
     * xml name: xdef:internal
     * 内部属性，一般保留为系统内部使用，不作为api对外开放
     */
    private java.lang.Boolean _xdefInternal ;
    
    /**
     *  
     * xml name: xdef:key-attr
     * 当body-type=list且key-attr不为空时，则子节点解析后对应KeyedList类型。
     */
    private java.lang.String _xdefKeyAttr ;
    
    /**
     *  
     * xml name: xdef:mandatory
     * 是否是必须存在的子节点
     */
    private java.lang.Boolean _xdefMandatory ;
    
    /**
     *  
     * xml name: xdef:name
     * 将本节点注册为xdef片段，其他节点可以通过xdef:ref来引用该片段。一般对应于Java类名，会根据它和根节点上的xdef:bean-package设置
     * 自动生成xdef:bean-class属性。
     */
    private java.lang.String _xdefName ;
    
    /**
     *  
     * xml name: xdef:order-attr
     * 
     */
    private java.lang.String _xdefOrderAttr ;
    
    /**
     *  
     * xml name: xdef:prop
     * xdef转换为objMeta时，objPropMeta上存在的扩展属性
     */
    private KeyedList<io.nop.xlang.xdef.impl.XDefProp> _xdefProps = KeyedList.emptyList();
    
    /**
     *  
     * xml name: xdef:ref
     * 引用本文件中定义的xdef片段或者外部xdef定义。 引用相当于是继承已有定义。如果再增加属性或者子节点则表示在已有定义基础上扩展。
     */
    private java.lang.String _xdefRef ;
    
    /**
     *  
     * xml name: xdef:ref-resolved
     * 
     */
    private java.lang.Boolean _xdefRefResolved ;
    
    /**
     *  
     * xml name: xdef:support-extends
     * 
     */
    private java.lang.Boolean _xdefSupportExtends ;
    
    /**
     *  
     * xml name: xdef:unique-attr
     * 表示本节点允许多个实例，通过指定的属性来进行区分。所有具有指定节点名的子节点解析成一个KeyedList类型的java属性。
     */
    private java.lang.String _xdefUniqueAttr ;
    
    /**
     *  
     * xml name: xdef:unknown-attr
     * 允许本节点具有未明确定义的属性。所有未定义的属性的类型都满足这里的设置。
     */
    private io.nop.xlang.xdef.XDefTypeDecl _xdefUnknownAttr ;
    
    /**
     *  
     * xml name: xdef:unknown-tag
     * 这里的xdef:unknown-tag表示在xdef文件的节点中可以存在xdef:unknown-tag定义。因为xdef名字空间设置为check-ns，因此上面的
     * meta:unknown-tag是无法匹配xdef名字空间的，xdef名字空间中的所有属性和节点都必须明确声明。
     */
    private io.nop.xlang.xdef.impl.XDefNode _xdefUnknownTag ;
    
    /**
     *  
     * xml name: xdef:value
     * 定义节点body的数据类型。在xdef文件中，只有叶子节点可以声明xdef:value
     */
    private io.nop.xlang.xdef.XDefTypeDecl _xdefValue ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefAttribute> getAttributes(){
      return _attributes;
    }

    
    public void setAttributes(java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefAttribute> value){
        checkAllowChange();
        
        this._attributes = value;
           
    }

    
    public boolean hasAttributes(){
        return this._attributes != null && !this._attributes.isEmpty();
    }
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefNode> getChildren(){
      return _children;
    }

    
    public void setChildren(java.util.Map<java.lang.String,io.nop.xlang.xdef.impl.XDefNode> value){
        checkAllowChange();
        
        this._children = value;
           
    }

    
    public boolean hasChildren(){
        return this._children != null && !this._children.isEmpty();
    }
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.xlang.xdef.impl.XDefComment getComment(){
      return _comment;
    }

    
    public void setComment(io.nop.xlang.xdef.impl.XDefComment value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getTagName(){
      return _tagName;
    }

    
    public void setTagName(java.lang.String value){
        checkAllowChange();
        
        this._tagName = value;
           
    }

    
    /**
     * 
     * xml name: xdef:allow-multiple
     *  是否允许多个实例。声明了unique-attr，或者父节点声明了key-attr的情况下，
     * 本节点的allow-multiple缺省为true
     */
    
    public java.lang.Boolean getXdefAllowMultiple(){
      return _xdefAllowMultiple;
    }

    
    public void setXdefAllowMultiple(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefAllowMultiple = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-body-prop
     *  
     */
    
    public java.lang.String getXdefBeanBodyProp(){
      return _xdefBeanBodyProp;
    }

    
    public void setXdefBeanBodyProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanBodyProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-body-type
     *  
     */
    
    public io.nop.core.type.IGenericType getXdefBeanBodyType(){
      return _xdefBeanBodyType;
    }

    
    public void setXdefBeanBodyType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._xdefBeanBodyType = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-child-name
     *  
     */
    
    public java.lang.String getXdefBeanChildName(){
      return _xdefBeanChildName;
    }

    
    public void setXdefBeanChildName(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanChildName = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-class
     *  
     */
    
    public java.lang.String getXdefBeanClass(){
      return _xdefBeanClass;
    }

    
    public void setXdefBeanClass(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanClass = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-comment-prop
     *  
     */
    
    public java.lang.String getXdefBeanCommentProp(){
      return _xdefBeanCommentProp;
    }

    
    public void setXdefBeanCommentProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanCommentProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-extends-type
     *  
     */
    
    public io.nop.core.type.IGenericType getXdefBeanExtendsType(){
      return _xdefBeanExtendsType;
    }

    
    public void setXdefBeanExtendsType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._xdefBeanExtendsType = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-implements-types
     *  
     */
    
    public java.util.List<io.nop.core.type.IGenericType> getXdefBeanImplementsTypes(){
      return _xdefBeanImplementsTypes;
    }

    
    public void setXdefBeanImplementsTypes(java.util.List<io.nop.core.type.IGenericType> value){
        checkAllowChange();
        
        this._xdefBeanImplementsTypes = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-prop
     *  
     */
    
    public java.lang.String getXdefBeanProp(){
      return _xdefBeanProp;
    }

    
    public void setXdefBeanProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-ref-prop
     *  
     */
    
    public java.lang.String getXdefBeanRefProp(){
      return _xdefBeanRefProp;
    }

    
    public void setXdefBeanRefProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanRefProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-sub-type-prop
     *  
     */
    
    public java.lang.String getXdefBeanSubTypeProp(){
      return _xdefBeanSubTypeProp;
    }

    
    public void setXdefBeanSubTypeProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanSubTypeProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-tag-prop
     *  
     */
    
    public java.lang.String getXdefBeanTagProp(){
      return _xdefBeanTagProp;
    }

    
    public void setXdefBeanTagProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanTagProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-unknown-attrs-prop
     *  
     */
    
    public java.lang.String getXdefBeanUnknownAttrsProp(){
      return _xdefBeanUnknownAttrsProp;
    }

    
    public void setXdefBeanUnknownAttrsProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanUnknownAttrsProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-unknown-children-prop
     *  
     */
    
    public java.lang.String getXdefBeanUnknownChildrenProp(){
      return _xdefBeanUnknownChildrenProp;
    }

    
    public void setXdefBeanUnknownChildrenProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanUnknownChildrenProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:body-type
     *  定义节点的结构类型。set表示子节点需要具有唯一标签名或者唯一key，而list表示可以出现重复的子节点
     */
    
    public io.nop.xlang.xdef.XDefBodyType getXdefBodyType(){
      return _xdefBodyType;
    }

    
    public void setXdefBodyType(io.nop.xlang.xdef.XDefBodyType value){
        checkAllowChange();
        
        this._xdefBodyType = value;
           
    }

    
    /**
     * 
     * xml name: xdef:default-override
     *  
     */
    
    public io.nop.xlang.xdef.XDefOverride getXdefDefaultOverride(){
      return _xdefDefaultOverride;
    }

    
    public void setXdefDefaultOverride(io.nop.xlang.xdef.XDefOverride value){
        checkAllowChange();
        
        this._xdefDefaultOverride = value;
           
    }

    
    /**
     * 
     * xml name: xdef:define
     *  定义xdef片段，可以通过xdef:ref来引用
     */
    
    public java.util.List<io.nop.xlang.xdef.impl.XDefNode> getXdefDefines(){
      return _xdefDefines;
    }

    
    public void setXdefDefines(java.util.List<io.nop.xlang.xdef.impl.XDefNode> value){
        checkAllowChange();
        
        this._xdefDefines = KeyedList.fromList(value, io.nop.xlang.xdef.impl.XDefNode::getXdefName);
           
    }

    
    public io.nop.xlang.xdef.impl.XDefNode getXdefDefine(String name){
        return this._xdefDefines.getByKey(name);
    }

    public boolean hasXdefDefine(String name){
        return this._xdefDefines.containsKey(name);
    }

    public void addXdefDefine(io.nop.xlang.xdef.impl.XDefNode item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xdef.impl.XDefNode> list = this.getXdefDefines();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xdef.impl.XDefNode::getXdefName);
            setXdefDefines(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_xdefDefines(){
        return this._xdefDefines.keySet();
    }

    public boolean hasXdefDefines(){
        return !this._xdefDefines.isEmpty();
    }
    
    /**
     * 
     * xml name: xdef:deprecated
     *  已废弃，不应再使用
     */
    
    public java.lang.Boolean getXdefDeprecated(){
      return _xdefDeprecated;
    }

    
    public void setXdefDeprecated(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefDeprecated = value;
           
    }

    
    /**
     * 
     * xml name: xdef:id
     *  
     */
    @Deprecated
    public java.lang.String getXdefId(){
      return _xdefId;
    }

    @Deprecated
    public void setXdefId(java.lang.String value){
        checkAllowChange();
        
        this._xdefId = value;
           
    }

    
    /**
     * 
     * xml name: xdef:internal
     *  内部属性，一般保留为系统内部使用，不作为api对外开放
     */
    
    public java.lang.Boolean getXdefInternal(){
      return _xdefInternal;
    }

    
    public void setXdefInternal(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefInternal = value;
           
    }

    
    /**
     * 
     * xml name: xdef:key-attr
     *  当body-type=list且key-attr不为空时，则子节点解析后对应KeyedList类型。
     */
    
    public java.lang.String getXdefKeyAttr(){
      return _xdefKeyAttr;
    }

    
    public void setXdefKeyAttr(java.lang.String value){
        checkAllowChange();
        
        this._xdefKeyAttr = value;
           
    }

    
    /**
     * 
     * xml name: xdef:mandatory
     *  是否是必须存在的子节点
     */
    
    public java.lang.Boolean getXdefMandatory(){
      return _xdefMandatory;
    }

    
    public void setXdefMandatory(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefMandatory = value;
           
    }

    
    /**
     * 
     * xml name: xdef:name
     *  将本节点注册为xdef片段，其他节点可以通过xdef:ref来引用该片段。一般对应于Java类名，会根据它和根节点上的xdef:bean-package设置
     * 自动生成xdef:bean-class属性。
     */
    
    public java.lang.String getXdefName(){
      return _xdefName;
    }

    
    public void setXdefName(java.lang.String value){
        checkAllowChange();
        
        this._xdefName = value;
           
    }

    
    /**
     * 
     * xml name: xdef:order-attr
     *  
     */
    
    public java.lang.String getXdefOrderAttr(){
      return _xdefOrderAttr;
    }

    
    public void setXdefOrderAttr(java.lang.String value){
        checkAllowChange();
        
        this._xdefOrderAttr = value;
           
    }

    
    /**
     * 
     * xml name: xdef:prop
     *  xdef转换为objMeta时，objPropMeta上存在的扩展属性
     */
    
    public java.util.List<io.nop.xlang.xdef.impl.XDefProp> getXdefProps(){
      return _xdefProps;
    }

    
    public void setXdefProps(java.util.List<io.nop.xlang.xdef.impl.XDefProp> value){
        checkAllowChange();
        
        this._xdefProps = KeyedList.fromList(value, io.nop.xlang.xdef.impl.XDefProp::getName);
           
    }

    
    public io.nop.xlang.xdef.impl.XDefProp getXdefProp(String name){
        return this._xdefProps.getByKey(name);
    }

    public boolean hasXdefProp(String name){
        return this._xdefProps.containsKey(name);
    }

    public void addXdefProp(io.nop.xlang.xdef.impl.XDefProp item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xdef.impl.XDefProp> list = this.getXdefProps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xdef.impl.XDefProp::getName);
            setXdefProps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_xdefProps(){
        return this._xdefProps.keySet();
    }

    public boolean hasXdefProps(){
        return !this._xdefProps.isEmpty();
    }
    
    /**
     * 
     * xml name: xdef:ref
     *  引用本文件中定义的xdef片段或者外部xdef定义。 引用相当于是继承已有定义。如果再增加属性或者子节点则表示在已有定义基础上扩展。
     */
    
    public java.lang.String getXdefRef(){
      return _xdefRef;
    }

    
    public void setXdefRef(java.lang.String value){
        checkAllowChange();
        
        this._xdefRef = value;
           
    }

    
    /**
     * 
     * xml name: xdef:ref-resolved
     *  
     */
    @Deprecated
    public java.lang.Boolean getXdefRefResolved(){
      return _xdefRefResolved;
    }

    @Deprecated
    public void setXdefRefResolved(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefRefResolved = value;
           
    }

    
    /**
     * 
     * xml name: xdef:support-extends
     *  
     */
    
    public java.lang.Boolean getXdefSupportExtends(){
      return _xdefSupportExtends;
    }

    
    public void setXdefSupportExtends(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefSupportExtends = value;
           
    }

    
    /**
     * 
     * xml name: xdef:unique-attr
     *  表示本节点允许多个实例，通过指定的属性来进行区分。所有具有指定节点名的子节点解析成一个KeyedList类型的java属性。
     */
    
    public java.lang.String getXdefUniqueAttr(){
      return _xdefUniqueAttr;
    }

    
    public void setXdefUniqueAttr(java.lang.String value){
        checkAllowChange();
        
        this._xdefUniqueAttr = value;
           
    }

    
    /**
     * 
     * xml name: xdef:unknown-attr
     *  允许本节点具有未明确定义的属性。所有未定义的属性的类型都满足这里的设置。
     */
    
    public io.nop.xlang.xdef.XDefTypeDecl getXdefUnknownAttr(){
      return _xdefUnknownAttr;
    }

    
    public void setXdefUnknownAttr(io.nop.xlang.xdef.XDefTypeDecl value){
        checkAllowChange();
        
        this._xdefUnknownAttr = value;
           
    }

    
    /**
     * 
     * xml name: xdef:unknown-tag
     *  这里的xdef:unknown-tag表示在xdef文件的节点中可以存在xdef:unknown-tag定义。因为xdef名字空间设置为check-ns，因此上面的
     * meta:unknown-tag是无法匹配xdef名字空间的，xdef名字空间中的所有属性和节点都必须明确声明。
     */
    
    public io.nop.xlang.xdef.impl.XDefNode getXdefUnknownTag(){
      return _xdefUnknownTag;
    }

    
    public void setXdefUnknownTag(io.nop.xlang.xdef.impl.XDefNode value){
        checkAllowChange();
        
        this._xdefUnknownTag = value;
           
    }

    
    /**
     * 
     * xml name: xdef:value
     *  定义节点body的数据类型。在xdef文件中，只有叶子节点可以声明xdef:value
     */
    
    public io.nop.xlang.xdef.XDefTypeDecl getXdefValue(){
      return _xdefValue;
    }

    
    public void setXdefValue(io.nop.xlang.xdef.XDefTypeDecl value){
        checkAllowChange();
        
        this._xdefValue = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._attributes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._attributes);
            
           this._children = io.nop.api.core.util.FreezeHelper.deepFreeze(this._children);
            
           this._xdefDefines = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefDefines);
            
           this._xdefProps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefProps);
            
           this._xdefUnknownTag = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefUnknownTag);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("attributes",this.getAttributes());
        out.putNotNull("children",this.getChildren());
        out.putNotNull("comment",this.getComment());
        out.putNotNull("tagName",this.getTagName());
        out.putNotNull("xdefAllowMultiple",this.getXdefAllowMultiple());
        out.putNotNull("xdefBeanBodyProp",this.getXdefBeanBodyProp());
        out.putNotNull("xdefBeanBodyType",this.getXdefBeanBodyType());
        out.putNotNull("xdefBeanChildName",this.getXdefBeanChildName());
        out.putNotNull("xdefBeanClass",this.getXdefBeanClass());
        out.putNotNull("xdefBeanCommentProp",this.getXdefBeanCommentProp());
        out.putNotNull("xdefBeanExtendsType",this.getXdefBeanExtendsType());
        out.putNotNull("xdefBeanImplementsTypes",this.getXdefBeanImplementsTypes());
        out.putNotNull("xdefBeanProp",this.getXdefBeanProp());
        out.putNotNull("xdefBeanRefProp",this.getXdefBeanRefProp());
        out.putNotNull("xdefBeanSubTypeProp",this.getXdefBeanSubTypeProp());
        out.putNotNull("xdefBeanTagProp",this.getXdefBeanTagProp());
        out.putNotNull("xdefBeanUnknownAttrsProp",this.getXdefBeanUnknownAttrsProp());
        out.putNotNull("xdefBeanUnknownChildrenProp",this.getXdefBeanUnknownChildrenProp());
        out.putNotNull("xdefBodyType",this.getXdefBodyType());
        out.putNotNull("xdefDefaultOverride",this.getXdefDefaultOverride());
        out.putNotNull("xdefDefines",this.getXdefDefines());
        out.putNotNull("xdefDeprecated",this.getXdefDeprecated());
        out.putNotNull("xdefId",this.getXdefId());
        out.putNotNull("xdefInternal",this.getXdefInternal());
        out.putNotNull("xdefKeyAttr",this.getXdefKeyAttr());
        out.putNotNull("xdefMandatory",this.getXdefMandatory());
        out.putNotNull("xdefName",this.getXdefName());
        out.putNotNull("xdefOrderAttr",this.getXdefOrderAttr());
        out.putNotNull("xdefProps",this.getXdefProps());
        out.putNotNull("xdefRef",this.getXdefRef());
        out.putNotNull("xdefRefResolved",this.getXdefRefResolved());
        out.putNotNull("xdefSupportExtends",this.getXdefSupportExtends());
        out.putNotNull("xdefUniqueAttr",this.getXdefUniqueAttr());
        out.putNotNull("xdefUnknownAttr",this.getXdefUnknownAttr());
        out.putNotNull("xdefUnknownTag",this.getXdefUnknownTag());
        out.putNotNull("xdefValue",this.getXdefValue());
    }

    public XDefNode cloneInstance(){
        XDefNode instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefNode instance){
        super.copyTo(instance);
        
        instance.setAttributes(this.getAttributes());
        instance.setChildren(this.getChildren());
        instance.setComment(this.getComment());
        instance.setTagName(this.getTagName());
        instance.setXdefAllowMultiple(this.getXdefAllowMultiple());
        instance.setXdefBeanBodyProp(this.getXdefBeanBodyProp());
        instance.setXdefBeanBodyType(this.getXdefBeanBodyType());
        instance.setXdefBeanChildName(this.getXdefBeanChildName());
        instance.setXdefBeanClass(this.getXdefBeanClass());
        instance.setXdefBeanCommentProp(this.getXdefBeanCommentProp());
        instance.setXdefBeanExtendsType(this.getXdefBeanExtendsType());
        instance.setXdefBeanImplementsTypes(this.getXdefBeanImplementsTypes());
        instance.setXdefBeanProp(this.getXdefBeanProp());
        instance.setXdefBeanRefProp(this.getXdefBeanRefProp());
        instance.setXdefBeanSubTypeProp(this.getXdefBeanSubTypeProp());
        instance.setXdefBeanTagProp(this.getXdefBeanTagProp());
        instance.setXdefBeanUnknownAttrsProp(this.getXdefBeanUnknownAttrsProp());
        instance.setXdefBeanUnknownChildrenProp(this.getXdefBeanUnknownChildrenProp());
        instance.setXdefBodyType(this.getXdefBodyType());
        instance.setXdefDefaultOverride(this.getXdefDefaultOverride());
        instance.setXdefDefines(this.getXdefDefines());
        instance.setXdefDeprecated(this.getXdefDeprecated());
        instance.setXdefId(this.getXdefId());
        instance.setXdefInternal(this.getXdefInternal());
        instance.setXdefKeyAttr(this.getXdefKeyAttr());
        instance.setXdefMandatory(this.getXdefMandatory());
        instance.setXdefName(this.getXdefName());
        instance.setXdefOrderAttr(this.getXdefOrderAttr());
        instance.setXdefProps(this.getXdefProps());
        instance.setXdefRef(this.getXdefRef());
        instance.setXdefRefResolved(this.getXdefRefResolved());
        instance.setXdefSupportExtends(this.getXdefSupportExtends());
        instance.setXdefUniqueAttr(this.getXdefUniqueAttr());
        instance.setXdefUnknownAttr(this.getXdefUnknownAttr());
        instance.setXdefUnknownTag(this.getXdefUnknownTag());
        instance.setXdefValue(this.getXdefValue());
    }

    protected XDefNode newInstance(){
        return (XDefNode) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
