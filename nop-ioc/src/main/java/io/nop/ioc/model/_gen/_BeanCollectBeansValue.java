package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanCollectBeansValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [51:10:0:0]/nop/schema/beans.xdef <p>
 * 按照类型或者注解收集所有符合条件的bean，返回bean的集合
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanCollectBeansValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: as-map
     * 如果为true，则收集到的bean按照id组织成Map形式。如果为false，则作为List形式
     */
    private boolean _asMap  = false;
    
    /**
     *  
     * xml name: by-annotation
     * 
     */
    private java.lang.String _byAnnotation ;
    
    /**
     *  
     * xml name: by-type
     * 
     */
    private java.lang.String _byType ;
    
    /**
     *  
     * xml name: exclude-tag
     * 
     */
    private java.util.Set<java.lang.String> _excludeTag ;
    
    /**
     *  
     * xml name: include-tag
     * 
     */
    private java.util.Set<java.lang.String> _includeTag ;
    
    /**
     *  
     * xml name: ioc:ignore-depends
     * 
     */
    private boolean _iocIgnoreDepends  = false;
    
    /**
     *  
     * xml name: ioc:optional
     * 
     */
    private boolean _iocOptional  = false;
    
    /**
     *  
     * xml name: name-prefix
     * 
     */
    private java.lang.String _namePrefix ;
    
    /**
     *  
     * xml name: only-concrete-classes
     * 只收集具体的实现类，忽略抽象类和AOP代理接口对象
     */
    private boolean _onlyConcreteClasses  = false;
    
    /**
     * 
     * xml name: as-map
     *  如果为true，则收集到的bean按照id组织成Map形式。如果为false，则作为List形式
     */
    
    public boolean isAsMap(){
      return _asMap;
    }

    
    public void setAsMap(boolean value){
        checkAllowChange();
        
        this._asMap = value;
           
    }

    
    /**
     * 
     * xml name: by-annotation
     *  
     */
    
    public java.lang.String getByAnnotation(){
      return _byAnnotation;
    }

    
    public void setByAnnotation(java.lang.String value){
        checkAllowChange();
        
        this._byAnnotation = value;
           
    }

    
    /**
     * 
     * xml name: by-type
     *  
     */
    
    public java.lang.String getByType(){
      return _byType;
    }

    
    public void setByType(java.lang.String value){
        checkAllowChange();
        
        this._byType = value;
           
    }

    
    /**
     * 
     * xml name: exclude-tag
     *  
     */
    
    public java.util.Set<java.lang.String> getExcludeTag(){
      return _excludeTag;
    }

    
    public void setExcludeTag(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._excludeTag = value;
           
    }

    
    /**
     * 
     * xml name: include-tag
     *  
     */
    
    public java.util.Set<java.lang.String> getIncludeTag(){
      return _includeTag;
    }

    
    public void setIncludeTag(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._includeTag = value;
           
    }

    
    /**
     * 
     * xml name: ioc:ignore-depends
     *  
     */
    
    public boolean isIocIgnoreDepends(){
      return _iocIgnoreDepends;
    }

    
    public void setIocIgnoreDepends(boolean value){
        checkAllowChange();
        
        this._iocIgnoreDepends = value;
           
    }

    
    /**
     * 
     * xml name: ioc:optional
     *  
     */
    
    public boolean isIocOptional(){
      return _iocOptional;
    }

    
    public void setIocOptional(boolean value){
        checkAllowChange();
        
        this._iocOptional = value;
           
    }

    
    /**
     * 
     * xml name: name-prefix
     *  
     */
    
    public java.lang.String getNamePrefix(){
      return _namePrefix;
    }

    
    public void setNamePrefix(java.lang.String value){
        checkAllowChange();
        
        this._namePrefix = value;
           
    }

    
    /**
     * 
     * xml name: only-concrete-classes
     *  只收集具体的实现类，忽略抽象类和AOP代理接口对象
     */
    
    public boolean isOnlyConcreteClasses(){
      return _onlyConcreteClasses;
    }

    
    public void setOnlyConcreteClasses(boolean value){
        checkAllowChange();
        
        this._onlyConcreteClasses = value;
           
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
        
        out.putNotNull("asMap",this.isAsMap());
        out.putNotNull("byAnnotation",this.getByAnnotation());
        out.putNotNull("byType",this.getByType());
        out.putNotNull("excludeTag",this.getExcludeTag());
        out.putNotNull("includeTag",this.getIncludeTag());
        out.putNotNull("iocIgnoreDepends",this.isIocIgnoreDepends());
        out.putNotNull("iocOptional",this.isIocOptional());
        out.putNotNull("namePrefix",this.getNamePrefix());
        out.putNotNull("onlyConcreteClasses",this.isOnlyConcreteClasses());
    }

    public BeanCollectBeansValue cloneInstance(){
        BeanCollectBeansValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanCollectBeansValue instance){
        super.copyTo(instance);
        
        instance.setAsMap(this.isAsMap());
        instance.setByAnnotation(this.getByAnnotation());
        instance.setByType(this.getByType());
        instance.setExcludeTag(this.getExcludeTag());
        instance.setIncludeTag(this.getIncludeTag());
        instance.setIocIgnoreDepends(this.isIocIgnoreDepends());
        instance.setIocOptional(this.isIocOptional());
        instance.setNamePrefix(this.getNamePrefix());
        instance.setOnlyConcreteClasses(this.isOnlyConcreteClasses());
    }

    protected BeanCollectBeansValue newInstance(){
        return (BeanCollectBeansValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
