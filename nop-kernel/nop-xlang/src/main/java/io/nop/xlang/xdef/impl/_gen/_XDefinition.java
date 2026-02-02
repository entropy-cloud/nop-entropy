package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * xdef自身的元模型定义。通过此文件实现对xdef的自举定义，即使用xdef来定义xdef本身。
 * 本文件定义了一般的xdef元模型定义文件中允许使用的xdef属性和标签的具体位置和格式。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefinition extends io.nop.xlang.xdef.impl.XDefNode {
    
    /**
     *  
     * xml name: xdef:allow-unknown-std-domain
     * 
     */
    private java.lang.Boolean _xdefAllowUnknownStdDomain ;
    
    /**
     *  
     * xml name: xdef:base
     * 本文件所对应的基础约束，用于识别当前xdef文件是否从某个基础元模型衍生得到
     */
    private java.lang.String _xdefBase ;
    
    /**
     *  
     * xml name: xdef:bean-package
     * 
     */
    private java.lang.String _xdefBeanPackage ;
    
    /**
     *  
     * xml name: xdef:check-ns
     * 指定一组必须要校验的名字空间。这些名字空间中定义的属性和子节点必须在xdef文件中明确声明，
     * xdef:unknown-attr和xdef:unknown-tag不会匹配这些名字空间。
     */
    private java.util.Set<java.lang.String> _xdefCheckNs ;
    
    /**
     *  
     * xml name: xdef:check-ref
     * xdef:check-ref 在指定范围内检查引用合法性。
     */
    private KeyedList<io.nop.xlang.xdef.impl.XDefCheckRef> _xdefCheckRefs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: xdef:check-unique
     * xdef:check-unique 在指定范围内检查唯一性。
     */
    private KeyedList<io.nop.xlang.xdef.impl.XDefCheckUnique> _xdefCheckUniques = KeyedList.emptyList();
    
    /**
     *  
     * xml name: xdef:default-extends
     * 规定缺省的extends模型文件。如果非空，则由此xdef文件描述的模型文件中，总是会缺省继承default-extends所指定的模型文件，
     * 通过此机制可以引入全局模型假定，简化模型配置。特别是结合x:post-extends机制可以实现自定义的可视化设计器。
     */
    private java.lang.String _xdefDefaultExtends ;
    
    /**
     *  
     * xml name: xdef:define
     * 定义xdef片段，可以通过xdef:ref来引用
     */
    private KeyedList<io.nop.xlang.xdef.impl.XDefNode> _xdefDefines = KeyedList.emptyList();
    
    /**
     *  
     * xml name: xdef:model-name-prop
     * 将模型的名称保存到解析后的模型对象上，成为某个属性。因为模型名称有可能体现在它的资源路径中，并不直接在模型中指定
     */
    private java.lang.String _xdefModelNameProp ;
    
    /**
     *  
     * xml name: xdef:model-version-prop
     * 
     */
    private java.lang.String _xdefModelVersionProp ;
    
    /**
     *  
     * xml name: xdef:parse-for-html
     * 
     */
    private java.lang.Boolean _xdefParseForHtml ;
    
    /**
     *  
     * xml name: xdef:parse-keep-comment
     * 
     */
    private java.lang.Boolean _xdefParseKeepComment ;
    
    /**
     *  
     * xml name: xdef:parser-class
     * 
     */
    private java.lang.String _xdefParserClass ;
    
    /**
     *  
     * xml name: xdef:post-parse
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _xdefPostParse ;
    
    /**
     *  
     * xml name: xdef:pre-parse
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _xdefPreParse ;
    
    /**
     *  
     * xml name: xdef:prop-ns
     * 对于没有名字空间的属性和标签名，它们会经过camelCase变换作为java对象的属性名。对于具有名字空间的属性名，则
     * 缺省情况下是作为扩展属性存在，并不会生成对应的java属性。
     * 如果名字空间在prop-ns范围之内，则会把字符:替换为-之后，再经过camelCase变换作为java对象的属性名。
     */
    private java.util.Set<java.lang.String> _xdefPropNs ;
    
    /**
     *  
     * xml name: xdef:transform
     * 完成x:extends合并操作之后，如果配置了xdef:transform则按照xdef:transform指定的规则变换到新的schema格式下。
     */
    private java.lang.String _xdefTransform ;
    
    /**
     *  
     * xml name: xdef:transformer-class
     * 加载得到XNode节点之后调用这个类进行格式转换，可以转换得到标准格式，或者执行版本迁移等
     */
    private java.util.Set<java.lang.String> _xdefTransformerClass ;
    
    /**
     *  
     * xml name: xdef:version
     * 
     */
    private java.lang.String _xdefVersion ;
    
    /**
     * 
     * xml name: xdef:allow-unknown-std-domain
     *  
     */
    
    public java.lang.Boolean getXdefAllowUnknownStdDomain(){
      return _xdefAllowUnknownStdDomain;
    }

    
    public void setXdefAllowUnknownStdDomain(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefAllowUnknownStdDomain = value;
           
    }

    
    /**
     * 
     * xml name: xdef:base
     *  本文件所对应的基础约束，用于识别当前xdef文件是否从某个基础元模型衍生得到
     */
    
    public java.lang.String getXdefBase(){
      return _xdefBase;
    }

    
    public void setXdefBase(java.lang.String value){
        checkAllowChange();
        
        this._xdefBase = value;
           
    }

    
    /**
     * 
     * xml name: xdef:bean-package
     *  
     */
    
    public java.lang.String getXdefBeanPackage(){
      return _xdefBeanPackage;
    }

    
    public void setXdefBeanPackage(java.lang.String value){
        checkAllowChange();
        
        this._xdefBeanPackage = value;
           
    }

    
    /**
     * 
     * xml name: xdef:check-ns
     *  指定一组必须要校验的名字空间。这些名字空间中定义的属性和子节点必须在xdef文件中明确声明，
     * xdef:unknown-attr和xdef:unknown-tag不会匹配这些名字空间。
     */
    
    public java.util.Set<java.lang.String> getXdefCheckNs(){
      return _xdefCheckNs;
    }

    
    public void setXdefCheckNs(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._xdefCheckNs = value;
           
    }

    
    /**
     * 
     * xml name: xdef:check-ref
     *  xdef:check-ref 在指定范围内检查引用合法性。
     */
    
    public java.util.List<io.nop.xlang.xdef.impl.XDefCheckRef> getXdefCheckRefs(){
      return _xdefCheckRefs;
    }

    
    public void setXdefCheckRefs(java.util.List<io.nop.xlang.xdef.impl.XDefCheckRef> value){
        checkAllowChange();
        
        this._xdefCheckRefs = KeyedList.fromList(value, io.nop.xlang.xdef.impl.XDefCheckRef::getId);
           
    }

    
    public io.nop.xlang.xdef.impl.XDefCheckRef getXdefCheckRef(String name){
        return this._xdefCheckRefs.getByKey(name);
    }

    public boolean hasXdefCheckRef(String name){
        return this._xdefCheckRefs.containsKey(name);
    }

    public void addXdefCheckRef(io.nop.xlang.xdef.impl.XDefCheckRef item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xdef.impl.XDefCheckRef> list = this.getXdefCheckRefs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xdef.impl.XDefCheckRef::getId);
            setXdefCheckRefs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_xdefCheckRefs(){
        return this._xdefCheckRefs.keySet();
    }

    public boolean hasXdefCheckRefs(){
        return !this._xdefCheckRefs.isEmpty();
    }
    
    /**
     * 
     * xml name: xdef:check-unique
     *  xdef:check-unique 在指定范围内检查唯一性。
     */
    
    public java.util.List<io.nop.xlang.xdef.impl.XDefCheckUnique> getXdefCheckUniques(){
      return _xdefCheckUniques;
    }

    
    public void setXdefCheckUniques(java.util.List<io.nop.xlang.xdef.impl.XDefCheckUnique> value){
        checkAllowChange();
        
        this._xdefCheckUniques = KeyedList.fromList(value, io.nop.xlang.xdef.impl.XDefCheckUnique::getId);
           
    }

    
    public io.nop.xlang.xdef.impl.XDefCheckUnique getXdefCheckUnique(String name){
        return this._xdefCheckUniques.getByKey(name);
    }

    public boolean hasXdefCheckUnique(String name){
        return this._xdefCheckUniques.containsKey(name);
    }

    public void addXdefCheckUnique(io.nop.xlang.xdef.impl.XDefCheckUnique item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xdef.impl.XDefCheckUnique> list = this.getXdefCheckUniques();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xdef.impl.XDefCheckUnique::getId);
            setXdefCheckUniques(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_xdefCheckUniques(){
        return this._xdefCheckUniques.keySet();
    }

    public boolean hasXdefCheckUniques(){
        return !this._xdefCheckUniques.isEmpty();
    }
    
    /**
     * 
     * xml name: xdef:default-extends
     *  规定缺省的extends模型文件。如果非空，则由此xdef文件描述的模型文件中，总是会缺省继承default-extends所指定的模型文件，
     * 通过此机制可以引入全局模型假定，简化模型配置。特别是结合x:post-extends机制可以实现自定义的可视化设计器。
     */
    
    public java.lang.String getXdefDefaultExtends(){
      return _xdefDefaultExtends;
    }

    
    public void setXdefDefaultExtends(java.lang.String value){
        checkAllowChange();
        
        this._xdefDefaultExtends = value;
           
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
     * xml name: xdef:model-name-prop
     *  将模型的名称保存到解析后的模型对象上，成为某个属性。因为模型名称有可能体现在它的资源路径中，并不直接在模型中指定
     */
    
    public java.lang.String getXdefModelNameProp(){
      return _xdefModelNameProp;
    }

    
    public void setXdefModelNameProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefModelNameProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:model-version-prop
     *  
     */
    
    public java.lang.String getXdefModelVersionProp(){
      return _xdefModelVersionProp;
    }

    
    public void setXdefModelVersionProp(java.lang.String value){
        checkAllowChange();
        
        this._xdefModelVersionProp = value;
           
    }

    
    /**
     * 
     * xml name: xdef:parse-for-html
     *  
     */
    
    public java.lang.Boolean getXdefParseForHtml(){
      return _xdefParseForHtml;
    }

    
    public void setXdefParseForHtml(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefParseForHtml = value;
           
    }

    
    /**
     * 
     * xml name: xdef:parse-keep-comment
     *  
     */
    
    public java.lang.Boolean getXdefParseKeepComment(){
      return _xdefParseKeepComment;
    }

    
    public void setXdefParseKeepComment(java.lang.Boolean value){
        checkAllowChange();
        
        this._xdefParseKeepComment = value;
           
    }

    
    /**
     * 
     * xml name: xdef:parser-class
     *  
     */
    
    public java.lang.String getXdefParserClass(){
      return _xdefParserClass;
    }

    
    public void setXdefParserClass(java.lang.String value){
        checkAllowChange();
        
        this._xdefParserClass = value;
           
    }

    
    /**
     * 
     * xml name: xdef:post-parse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getXdefPostParse(){
      return _xdefPostParse;
    }

    
    public void setXdefPostParse(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._xdefPostParse = value;
           
    }

    
    /**
     * 
     * xml name: xdef:pre-parse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getXdefPreParse(){
      return _xdefPreParse;
    }

    
    public void setXdefPreParse(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._xdefPreParse = value;
           
    }

    
    /**
     * 
     * xml name: xdef:prop-ns
     *  对于没有名字空间的属性和标签名，它们会经过camelCase变换作为java对象的属性名。对于具有名字空间的属性名，则
     * 缺省情况下是作为扩展属性存在，并不会生成对应的java属性。
     * 如果名字空间在prop-ns范围之内，则会把字符:替换为-之后，再经过camelCase变换作为java对象的属性名。
     */
    
    public java.util.Set<java.lang.String> getXdefPropNs(){
      return _xdefPropNs;
    }

    
    public void setXdefPropNs(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._xdefPropNs = value;
           
    }

    
    /**
     * 
     * xml name: xdef:transform
     *  完成x:extends合并操作之后，如果配置了xdef:transform则按照xdef:transform指定的规则变换到新的schema格式下。
     */
    
    public java.lang.String getXdefTransform(){
      return _xdefTransform;
    }

    
    public void setXdefTransform(java.lang.String value){
        checkAllowChange();
        
        this._xdefTransform = value;
           
    }

    
    /**
     * 
     * xml name: xdef:transformer-class
     *  加载得到XNode节点之后调用这个类进行格式转换，可以转换得到标准格式，或者执行版本迁移等
     */
    
    public java.util.Set<java.lang.String> getXdefTransformerClass(){
      return _xdefTransformerClass;
    }

    
    public void setXdefTransformerClass(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._xdefTransformerClass = value;
           
    }

    
    /**
     * 
     * xml name: xdef:version
     *  
     */
    
    public java.lang.String getXdefVersion(){
      return _xdefVersion;
    }

    
    public void setXdefVersion(java.lang.String value){
        checkAllowChange();
        
        this._xdefVersion = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._xdefCheckRefs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefCheckRefs);
            
           this._xdefCheckUniques = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefCheckUniques);
            
           this._xdefDefines = io.nop.api.core.util.FreezeHelper.deepFreeze(this._xdefDefines);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("xdefAllowUnknownStdDomain",this.getXdefAllowUnknownStdDomain());
        out.putNotNull("xdefBase",this.getXdefBase());
        out.putNotNull("xdefBeanPackage",this.getXdefBeanPackage());
        out.putNotNull("xdefCheckNs",this.getXdefCheckNs());
        out.putNotNull("xdefCheckRefs",this.getXdefCheckRefs());
        out.putNotNull("xdefCheckUniques",this.getXdefCheckUniques());
        out.putNotNull("xdefDefaultExtends",this.getXdefDefaultExtends());
        out.putNotNull("xdefDefines",this.getXdefDefines());
        out.putNotNull("xdefModelNameProp",this.getXdefModelNameProp());
        out.putNotNull("xdefModelVersionProp",this.getXdefModelVersionProp());
        out.putNotNull("xdefParseForHtml",this.getXdefParseForHtml());
        out.putNotNull("xdefParseKeepComment",this.getXdefParseKeepComment());
        out.putNotNull("xdefParserClass",this.getXdefParserClass());
        out.putNotNull("xdefPostParse",this.getXdefPostParse());
        out.putNotNull("xdefPreParse",this.getXdefPreParse());
        out.putNotNull("xdefPropNs",this.getXdefPropNs());
        out.putNotNull("xdefTransform",this.getXdefTransform());
        out.putNotNull("xdefTransformerClass",this.getXdefTransformerClass());
        out.putNotNull("xdefVersion",this.getXdefVersion());
    }

    public XDefinition cloneInstance(){
        XDefinition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefinition instance){
        super.copyTo(instance);
        
        instance.setXdefAllowUnknownStdDomain(this.getXdefAllowUnknownStdDomain());
        instance.setXdefBase(this.getXdefBase());
        instance.setXdefBeanPackage(this.getXdefBeanPackage());
        instance.setXdefCheckNs(this.getXdefCheckNs());
        instance.setXdefCheckRefs(this.getXdefCheckRefs());
        instance.setXdefCheckUniques(this.getXdefCheckUniques());
        instance.setXdefDefaultExtends(this.getXdefDefaultExtends());
        instance.setXdefDefines(this.getXdefDefines());
        instance.setXdefModelNameProp(this.getXdefModelNameProp());
        instance.setXdefModelVersionProp(this.getXdefModelVersionProp());
        instance.setXdefParseForHtml(this.getXdefParseForHtml());
        instance.setXdefParseKeepComment(this.getXdefParseKeepComment());
        instance.setXdefParserClass(this.getXdefParserClass());
        instance.setXdefPostParse(this.getXdefPostParse());
        instance.setXdefPreParse(this.getXdefPreParse());
        instance.setXdefPropNs(this.getXdefPropNs());
        instance.setXdefTransform(this.getXdefTransform());
        instance.setXdefTransformerClass(this.getXdefTransformerClass());
        instance.setXdefVersion(this.getXdefVersion());
    }

    protected XDefinition newInstance(){
        return (XDefinition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
