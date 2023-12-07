package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [38:10:0:0]/nop/schema/schema/obj-schema.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _ObjPropMetaImpl extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowCpExpr
     * 是否支持编译期表达式
     */
    private java.lang.Boolean _allowCpExpr ;
    
    /**
     *  
     * xml name: allowFilterOp
     * 
     */
    private java.util.Set<java.lang.String> _allowFilterOp ;
    
    /**
     *  
     * xml name: arg
     * 对应graphql的argument
     */
    private KeyedList<io.nop.xlang.xmeta.impl.ObjPropArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: auth
     * 配置字段级别的权限约束
     */
    private KeyedList<io.nop.xlang.xmeta.impl.ObjPropAuthModel> _auths = KeyedList.emptyList();
    
    /**
     *  
     * xml name: autoExpr
     * 新增或者修改的时候如果前台没有发送本字段的值，则可以根据autoExpr来自动计算得到
     */
    private io.nop.xlang.xmeta.impl.ObjConditionExpr _autoExpr ;
    
    /**
     *  
     * xml name: childName
     * 集合属性中的单个对象的名称。例如children集合中每个对象称为child， attrs集合中每个对象称为attr。生成器会根据childName生成
     * get${prop.childName}(String name)等方法。
     */
    private java.lang.String _childName ;
    
    /**
     *  
     * xml name: childXmlName
     * 子节点的标签名。有时子节点的标签名没有对应于任何对象属性，因此需要单独记录。
     */
    private java.lang.String _childXmlName ;
    
    /**
     *  
     * xml name: defaultOverride
     * 对应于xdef文件中的xdef:default-override设置
     */
    private io.nop.xlang.xdef.XDefOverride _defaultOverride ;
    
    /**
     *  
     * xml name: defaultValue
     * 缺省值
     */
    private java.lang.Object _defaultValue ;
    
    /**
     *  
     * xml name: depends
     * 获取本字段的值的时候，需要依赖其他字段。例如在批量加载的时候，表示需要把相关字段也进行批量加载
     */
    private java.util.Set<java.lang.String> _depends ;
    
    /**
     *  
     * xml name: deprecated
     * 是否已废弃。标记为废弃的属性不出现在IDE的提示信息里
     */
    private boolean _deprecated  = false;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: getter
     * 根据当前实体生成动态属性。getter和setter都是后台实体对象层的功能，类似Java对象上的get/set。
     * 上下文中可以通过entity变量访问当前实体对象。
     */
    private io.nop.core.lang.eval.IEvalAction _getter ;
    
    /**
     *  
     * xml name: insertable
     * 
     */
    private boolean _insertable  = true;
    
    /**
     *  
     * xml name: internal
     * 是否内部属性。内部属性不出现在IDE的提示列表中，一般情况下在界面上也不可见。
     */
    private boolean _internal  = false;
    
    /**
     *  
     * xml name: kind
     * 
     */
    private io.nop.xlang.xmeta.ObjPropKind _kind ;
    
    /**
     *  
     * xml name: lazy
     * 
     */
    private boolean _lazy  = false;
    
    /**
     *  
     * xml name: mandatory
     * 属性值是否不允许为空值
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: mapToProp
     * 
     */
    private java.lang.String _mapToProp ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: propId
     * 属性的顺序标识，可以对应于protobuf标准中的propId属性
     */
    private java.lang.Integer _propId ;
    
    /**
     *  
     * xml name: published
     * 
     */
    private boolean _published  = true;
    
    /**
     *  
     * xml name: queryable
     * 
     */
    private boolean _queryable  = false;
    
    /**
     *  
     * xml name: readable
     * 是否可以作为返回值返回。readable=false一般是作为输入数据使用，只允许提交，而不允许查看
     */
    private boolean _readable  = true;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: setter
     * 对外部传入的值进行处理，可能会设置entity对象的属性。
     * 上下文中可以通过entity变量访问当前实体，通过value变量访问设置的属性值
     */
    private io.nop.core.lang.eval.IEvalAction _setter ;
    
    /**
     *  
     * xml name: sortable
     * 
     */
    private boolean _sortable  = false;
    
    /**
     *  
     * xml name: tagSet
     * 逗号分隔的自定义附加标识
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: transformIn
     * 对前台输入的值进行适配转换。通过data变量访问前台提交的数据集合，通过value变量访问前台传入的属性值。返回值为变换后的值
     */
    private io.nop.core.lang.eval.IEvalAction _transformIn ;
    
    /**
     *  
     * xml name: transformOut
     * 后台返回的值可能需要进行格式转换。通过entity变量访问当前实体，通过value变量访问属性值，返回值为变换后的值
     */
    private io.nop.core.lang.eval.IEvalAction _transformOut ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: updatable
     * 
     */
    private boolean _updatable  = true;
    
    /**
     *  
     * xml name: virtual
     * 是否虚拟字段。虚拟字段不会更新到实体上
     */
    private boolean _virtual  = false;
    
    /**
     *  
     * xml name: xmlName
     * 转换为xml属性或者节点时对应的标签名，一般情况下与属性名一致
     */
    private java.lang.String _xmlName ;
    
    /**
     *  
     * xml name: xmlPos
     * 
     */
    private io.nop.core.lang.xml.XNodeValuePosition _xmlPos ;
    
    /**
     * 
     * xml name: allowCpExpr
     *  是否支持编译期表达式
     */
    
    public java.lang.Boolean getAllowCpExpr(){
      return _allowCpExpr;
    }

    
    public void setAllowCpExpr(java.lang.Boolean value){
        checkAllowChange();
        
        this._allowCpExpr = value;
           
    }

    
    /**
     * 
     * xml name: allowFilterOp
     *  
     */
    
    public java.util.Set<java.lang.String> getAllowFilterOp(){
      return _allowFilterOp;
    }

    
    public void setAllowFilterOp(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowFilterOp = value;
           
    }

    
    /**
     * 
     * xml name: arg
     *  对应graphql的argument
     */
    
    public java.util.List<io.nop.xlang.xmeta.impl.ObjPropArgModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.xlang.xmeta.impl.ObjPropArgModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.xlang.xmeta.impl.ObjPropArgModel::getName);
           
    }

    
    public io.nop.xlang.xmeta.impl.ObjPropArgModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.xlang.xmeta.impl.ObjPropArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xmeta.impl.ObjPropArgModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xmeta.impl.ObjPropArgModel::getName);
            setArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_args(){
        return this._args.keySet();
    }

    public boolean hasArgs(){
        return !this._args.isEmpty();
    }
    
    /**
     * 
     * xml name: auth
     *  配置字段级别的权限约束
     */
    
    public java.util.List<io.nop.xlang.xmeta.impl.ObjPropAuthModel> getAuths(){
      return _auths;
    }

    
    public void setAuths(java.util.List<io.nop.xlang.xmeta.impl.ObjPropAuthModel> value){
        checkAllowChange();
        
        this._auths = KeyedList.fromList(value, io.nop.xlang.xmeta.impl.ObjPropAuthModel::getFor);
           
    }

    
    public io.nop.xlang.xmeta.impl.ObjPropAuthModel getAuth(String name){
        return this._auths.getByKey(name);
    }

    public boolean hasAuth(String name){
        return this._auths.containsKey(name);
    }

    public void addAuth(io.nop.xlang.xmeta.impl.ObjPropAuthModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xmeta.impl.ObjPropAuthModel> list = this.getAuths();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xmeta.impl.ObjPropAuthModel::getFor);
            setAuths(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_auths(){
        return this._auths.keySet();
    }

    public boolean hasAuths(){
        return !this._auths.isEmpty();
    }
    
    /**
     * 
     * xml name: autoExpr
     *  新增或者修改的时候如果前台没有发送本字段的值，则可以根据autoExpr来自动计算得到
     */
    
    public io.nop.xlang.xmeta.impl.ObjConditionExpr getAutoExpr(){
      return _autoExpr;
    }

    
    public void setAutoExpr(io.nop.xlang.xmeta.impl.ObjConditionExpr value){
        checkAllowChange();
        
        this._autoExpr = value;
           
    }

    
    /**
     * 
     * xml name: childName
     *  集合属性中的单个对象的名称。例如children集合中每个对象称为child， attrs集合中每个对象称为attr。生成器会根据childName生成
     * get${prop.childName}(String name)等方法。
     */
    
    public java.lang.String getChildName(){
      return _childName;
    }

    
    public void setChildName(java.lang.String value){
        checkAllowChange();
        
        this._childName = value;
           
    }

    
    /**
     * 
     * xml name: childXmlName
     *  子节点的标签名。有时子节点的标签名没有对应于任何对象属性，因此需要单独记录。
     */
    
    public java.lang.String getChildXmlName(){
      return _childXmlName;
    }

    
    public void setChildXmlName(java.lang.String value){
        checkAllowChange();
        
        this._childXmlName = value;
           
    }

    
    /**
     * 
     * xml name: defaultOverride
     *  对应于xdef文件中的xdef:default-override设置
     */
    
    public io.nop.xlang.xdef.XDefOverride getDefaultOverride(){
      return _defaultOverride;
    }

    
    public void setDefaultOverride(io.nop.xlang.xdef.XDefOverride value){
        checkAllowChange();
        
        this._defaultOverride = value;
           
    }

    
    /**
     * 
     * xml name: defaultValue
     *  缺省值
     */
    
    public java.lang.Object getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.Object value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
    /**
     * 
     * xml name: depends
     *  获取本字段的值的时候，需要依赖其他字段。例如在批量加载的时候，表示需要把相关字段也进行批量加载
     */
    
    public java.util.Set<java.lang.String> getDepends(){
      return _depends;
    }

    
    public void setDepends(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._depends = value;
           
    }

    
    /**
     * 
     * xml name: deprecated
     *  是否已废弃。标记为废弃的属性不出现在IDE的提示信息里
     */
    
    public boolean isDeprecated(){
      return _deprecated;
    }

    
    public void setDeprecated(boolean value){
        checkAllowChange();
        
        this._deprecated = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: getter
     *  根据当前实体生成动态属性。getter和setter都是后台实体对象层的功能，类似Java对象上的get/set。
     * 上下文中可以通过entity变量访问当前实体对象。
     */
    
    public io.nop.core.lang.eval.IEvalAction getGetter(){
      return _getter;
    }

    
    public void setGetter(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._getter = value;
           
    }

    
    /**
     * 
     * xml name: insertable
     *  
     */
    
    public boolean isInsertable(){
      return _insertable;
    }

    
    public void setInsertable(boolean value){
        checkAllowChange();
        
        this._insertable = value;
           
    }

    
    /**
     * 
     * xml name: internal
     *  是否内部属性。内部属性不出现在IDE的提示列表中，一般情况下在界面上也不可见。
     */
    
    public boolean isInternal(){
      return _internal;
    }

    
    public void setInternal(boolean value){
        checkAllowChange();
        
        this._internal = value;
           
    }

    
    /**
     * 
     * xml name: kind
     *  
     */
    
    public io.nop.xlang.xmeta.ObjPropKind getKind(){
      return _kind;
    }

    
    public void setKind(io.nop.xlang.xmeta.ObjPropKind value){
        checkAllowChange();
        
        this._kind = value;
           
    }

    
    /**
     * 
     * xml name: lazy
     *  
     */
    
    public boolean isLazy(){
      return _lazy;
    }

    
    public void setLazy(boolean value){
        checkAllowChange();
        
        this._lazy = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  属性值是否不允许为空值
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
    /**
     * 
     * xml name: mapToProp
     *  
     */
    
    public java.lang.String getMapToProp(){
      return _mapToProp;
    }

    
    public void setMapToProp(java.lang.String value){
        checkAllowChange();
        
        this._mapToProp = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: propId
     *  属性的顺序标识，可以对应于protobuf标准中的propId属性
     */
    
    public java.lang.Integer getPropId(){
      return _propId;
    }

    
    public void setPropId(java.lang.Integer value){
        checkAllowChange();
        
        this._propId = value;
           
    }

    
    /**
     * 
     * xml name: published
     *  
     */
    
    public boolean isPublished(){
      return _published;
    }

    
    public void setPublished(boolean value){
        checkAllowChange();
        
        this._published = value;
           
    }

    
    /**
     * 
     * xml name: queryable
     *  
     */
    
    public boolean isQueryable(){
      return _queryable;
    }

    
    public void setQueryable(boolean value){
        checkAllowChange();
        
        this._queryable = value;
           
    }

    
    /**
     * 
     * xml name: readable
     *  是否可以作为返回值返回。readable=false一般是作为输入数据使用，只允许提交，而不允许查看
     */
    
    public boolean isReadable(){
      return _readable;
    }

    
    public void setReadable(boolean value){
        checkAllowChange();
        
        this._readable = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    
    /**
     * 
     * xml name: setter
     *  对外部传入的值进行处理，可能会设置entity对象的属性。
     * 上下文中可以通过entity变量访问当前实体，通过value变量访问设置的属性值
     */
    
    public io.nop.core.lang.eval.IEvalAction getSetter(){
      return _setter;
    }

    
    public void setSetter(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._setter = value;
           
    }

    
    /**
     * 
     * xml name: sortable
     *  
     */
    
    public boolean isSortable(){
      return _sortable;
    }

    
    public void setSortable(boolean value){
        checkAllowChange();
        
        this._sortable = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  逗号分隔的自定义附加标识
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: transformIn
     *  对前台输入的值进行适配转换。通过data变量访问前台提交的数据集合，通过value变量访问前台传入的属性值。返回值为变换后的值
     */
    
    public io.nop.core.lang.eval.IEvalAction getTransformIn(){
      return _transformIn;
    }

    
    public void setTransformIn(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._transformIn = value;
           
    }

    
    /**
     * 
     * xml name: transformOut
     *  后台返回的值可能需要进行格式转换。通过entity变量访问当前实体，通过value变量访问属性值，返回值为变换后的值
     */
    
    public io.nop.core.lang.eval.IEvalAction getTransformOut(){
      return _transformOut;
    }

    
    public void setTransformOut(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._transformOut = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: updatable
     *  
     */
    
    public boolean isUpdatable(){
      return _updatable;
    }

    
    public void setUpdatable(boolean value){
        checkAllowChange();
        
        this._updatable = value;
           
    }

    
    /**
     * 
     * xml name: virtual
     *  是否虚拟字段。虚拟字段不会更新到实体上
     */
    
    public boolean isVirtual(){
      return _virtual;
    }

    
    public void setVirtual(boolean value){
        checkAllowChange();
        
        this._virtual = value;
           
    }

    
    /**
     * 
     * xml name: xmlName
     *  转换为xml属性或者节点时对应的标签名，一般情况下与属性名一致
     */
    
    public java.lang.String getXmlName(){
      return _xmlName;
    }

    
    public void setXmlName(java.lang.String value){
        checkAllowChange();
        
        this._xmlName = value;
           
    }

    
    /**
     * 
     * xml name: xmlPos
     *  
     */
    
    public io.nop.core.lang.xml.XNodeValuePosition getXmlPos(){
      return _xmlPos;
    }

    
    public void setXmlPos(io.nop.core.lang.xml.XNodeValuePosition value){
        checkAllowChange();
        
        this._xmlPos = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
           this._auths = io.nop.api.core.util.FreezeHelper.deepFreeze(this._auths);
            
           this._autoExpr = io.nop.api.core.util.FreezeHelper.deepFreeze(this._autoExpr);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("allowCpExpr",this.getAllowCpExpr());
        out.put("allowFilterOp",this.getAllowFilterOp());
        out.put("args",this.getArgs());
        out.put("auths",this.getAuths());
        out.put("autoExpr",this.getAutoExpr());
        out.put("childName",this.getChildName());
        out.put("childXmlName",this.getChildXmlName());
        out.put("defaultOverride",this.getDefaultOverride());
        out.put("defaultValue",this.getDefaultValue());
        out.put("depends",this.getDepends());
        out.put("deprecated",this.isDeprecated());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("getter",this.getGetter());
        out.put("insertable",this.isInsertable());
        out.put("internal",this.isInternal());
        out.put("kind",this.getKind());
        out.put("lazy",this.isLazy());
        out.put("mandatory",this.isMandatory());
        out.put("mapToProp",this.getMapToProp());
        out.put("name",this.getName());
        out.put("propId",this.getPropId());
        out.put("published",this.isPublished());
        out.put("queryable",this.isQueryable());
        out.put("readable",this.isReadable());
        out.put("schema",this.getSchema());
        out.put("setter",this.getSetter());
        out.put("sortable",this.isSortable());
        out.put("tagSet",this.getTagSet());
        out.put("transformIn",this.getTransformIn());
        out.put("transformOut",this.getTransformOut());
        out.put("type",this.getType());
        out.put("updatable",this.isUpdatable());
        out.put("virtual",this.isVirtual());
        out.put("xmlName",this.getXmlName());
        out.put("xmlPos",this.getXmlPos());
    }
}
 // resume CPD analysis - CPD-ON
