package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [20:2:0:0]/nop/schema/xdef.xdef <p>
 * xdef自身的元模型定义。通过此文件实现对xdef的自举定义，即使用xdef来定义xdef本身。
 * 本文件定义了一般的xdef元模型定义文件中允许使用的xdef属性和标签的具体位置和格式。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _XDefinition extends io.nop.xlang.xdef.impl.XDefNode {
    
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
     * xml name: xdef:default-extends
     * 规定缺省的extends模型文件。如果非空，则由此xdef文件描述的模型文件中，总是会缺省继承default-extends所指定的模型文件，
     * 通过此机制可以引入全局模型假定，简化模型配置。特别是结合x:post-extends机制可以实现自定义的可视化设计器。
     */
    private java.lang.String _xdefDefaultExtends ;
    
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
     * xml name: xdef:version
     * 
     */
    private java.lang.String _xdefVersion ;
    
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("xdefBase",this.getXdefBase());
        out.put("xdefBeanPackage",this.getXdefBeanPackage());
        out.put("xdefCheckNs",this.getXdefCheckNs());
        out.put("xdefDefaultExtends",this.getXdefDefaultExtends());
        out.put("xdefParseForHtml",this.getXdefParseForHtml());
        out.put("xdefParseKeepComment",this.getXdefParseKeepComment());
        out.put("xdefParserClass",this.getXdefParserClass());
        out.put("xdefPostParse",this.getXdefPostParse());
        out.put("xdefPropNs",this.getXdefPropNs());
        out.put("xdefTransform",this.getXdefTransform());
        out.put("xdefVersion",this.getXdefVersion());
    }
}
 // resume CPD analysis - CPD-ON
