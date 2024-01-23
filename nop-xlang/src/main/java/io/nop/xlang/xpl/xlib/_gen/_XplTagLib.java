package io.nop.xlang.xpl.xlib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xpl.xlib.XplTagLib;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [11:2:0:0]/nop/schema/xlib.xdef <p>
 * 一个标签库可以看作一个服务实例。一个java服务接口可以自动转换为标签库，而标签库也可以自动生成java接口。
 * 可以通过x:post-extends段实现对标签（函数）的aop加工。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XplTagLib extends io.nop.xlang.xdsl.AbstractDslModel {
    
    /**
     *  描述信息
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  显示名称
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  接口列表
     * xml name: interfaces
     * 标签库提供了一组函数，它们满足哪些接口要求
     */
    private java.util.List<io.nop.core.type.IGenericType> _interfaces ;
    
    /**
     *  
     * xml name: namespace
     * 
     */
    private java.lang.String _namespace ;
    
    /**
     *  
     * xml name: tags
     * 
     */
    private java.util.Map<java.lang.String,io.nop.xlang.xpl.xlib.XplTag> _tags = java.util.Collections.emptyMap();
    
    /**
     * 描述信息
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
     * 显示名称
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
     * 接口列表
     * xml name: interfaces
     *  标签库提供了一组函数，它们满足哪些接口要求
     */
    
    public java.util.List<io.nop.core.type.IGenericType> getInterfaces(){
      return _interfaces;
    }

    
    public void setInterfaces(java.util.List<io.nop.core.type.IGenericType> value){
        checkAllowChange();
        
        this._interfaces = value;
           
    }

    
    /**
     * 
     * xml name: namespace
     *  
     */
    
    public java.lang.String getNamespace(){
      return _namespace;
    }

    
    public void setNamespace(java.lang.String value){
        checkAllowChange();
        
        this._namespace = value;
           
    }

    
    /**
     * 
     * xml name: tags
     *  
     */
    
    public java.util.Map<java.lang.String,io.nop.xlang.xpl.xlib.XplTag> getTags(){
      return _tags;
    }

    
    public void setTags(java.util.Map<java.lang.String,io.nop.xlang.xpl.xlib.XplTag> value){
        checkAllowChange();
        
        this._tags = value;
           
    }

    
    public io.nop.xlang.xpl.xlib.XplTag getTag(String name){
        return this._tags.get(name);
    }

    public boolean hasTag(String name){
        return this._tags.containsKey(name);
    }
    
    public boolean hasTags(){
        return this._tags != null && !this._tags.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._tags = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tags);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("interfaces",this.getInterfaces());
        out.putNotNull("namespace",this.getNamespace());
        out.putNotNull("tags",this.getTags());
    }

    public XplTagLib cloneInstance(){
        XplTagLib instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XplTagLib instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setInterfaces(this.getInterfaces());
        instance.setNamespace(this.getNamespace());
        instance.setTags(this.getTags());
    }

    protected XplTagLib newInstance(){
        return (XplTagLib) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
