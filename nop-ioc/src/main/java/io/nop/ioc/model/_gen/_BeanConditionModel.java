package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanConditionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [154:10:0:0]/nop/schema/beans.xdef <p>
 * 满足条件时bean才允许实例化
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanConditionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: if-property
     * 配置变量的值为true或者指定值的时候返回true
     */
    private io.nop.ioc.model.BeanIfPropertyCondition _ifProperty ;
    
    /**
     *  
     * xml name: missing-bean
     * 
     */
    private java.util.Set<java.lang.String> _missingBean ;
    
    /**
     *  
     * xml name: missing-class
     * 当不存在指定class时返回true
     */
    private java.util.Set<java.lang.String> _missingClass ;
    
    /**
     *  
     * xml name: missing-resource
     * 
     */
    private java.util.Set<java.lang.String> _missingResource ;
    
    /**
     *  
     * xml name: on-bean
     * 检查指定名称的bean是否存在
     */
    private java.util.Set<java.lang.String> _onBean ;
    
    /**
     *  
     * xml name: on-bean-type
     * 
     */
    private java.util.Set<java.lang.String> _onBeanType ;
    
    /**
     *  
     * xml name: on-class
     * 是否存在class
     */
    private java.util.Set<java.lang.String> _onClass ;
    
    /**
     *  
     * xml name: on-expr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _onExpr ;
    
    /**
     *  
     * xml name: on-missing-bean-type
     * 
     */
    private java.util.Set<java.lang.String> _onMissingBeanType ;
    
    /**
     *  
     * xml name: on-resource
     * 是否存在资源文件
     */
    private java.util.Set<java.lang.String> _onResource ;
    
    /**
     *  
     * xml name: unless-property
     * 
     */
    private io.nop.ioc.model.BeanUnlessPropertyCondition _unlessProperty ;
    
    /**
     * 
     * xml name: if-property
     *  配置变量的值为true或者指定值的时候返回true
     */
    
    public io.nop.ioc.model.BeanIfPropertyCondition getIfProperty(){
      return _ifProperty;
    }

    
    public void setIfProperty(io.nop.ioc.model.BeanIfPropertyCondition value){
        checkAllowChange();
        
        this._ifProperty = value;
           
    }

    
    /**
     * 
     * xml name: missing-bean
     *  
     */
    
    public java.util.Set<java.lang.String> getMissingBean(){
      return _missingBean;
    }

    
    public void setMissingBean(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._missingBean = value;
           
    }

    
    /**
     * 
     * xml name: missing-class
     *  当不存在指定class时返回true
     */
    
    public java.util.Set<java.lang.String> getMissingClass(){
      return _missingClass;
    }

    
    public void setMissingClass(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._missingClass = value;
           
    }

    
    /**
     * 
     * xml name: missing-resource
     *  
     */
    
    public java.util.Set<java.lang.String> getMissingResource(){
      return _missingResource;
    }

    
    public void setMissingResource(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._missingResource = value;
           
    }

    
    /**
     * 
     * xml name: on-bean
     *  检查指定名称的bean是否存在
     */
    
    public java.util.Set<java.lang.String> getOnBean(){
      return _onBean;
    }

    
    public void setOnBean(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._onBean = value;
           
    }

    
    /**
     * 
     * xml name: on-bean-type
     *  
     */
    
    public java.util.Set<java.lang.String> getOnBeanType(){
      return _onBeanType;
    }

    
    public void setOnBeanType(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._onBeanType = value;
           
    }

    
    /**
     * 
     * xml name: on-class
     *  是否存在class
     */
    
    public java.util.Set<java.lang.String> getOnClass(){
      return _onClass;
    }

    
    public void setOnClass(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._onClass = value;
           
    }

    
    /**
     * 
     * xml name: on-expr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getOnExpr(){
      return _onExpr;
    }

    
    public void setOnExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._onExpr = value;
           
    }

    
    /**
     * 
     * xml name: on-missing-bean-type
     *  
     */
    
    public java.util.Set<java.lang.String> getOnMissingBeanType(){
      return _onMissingBeanType;
    }

    
    public void setOnMissingBeanType(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._onMissingBeanType = value;
           
    }

    
    /**
     * 
     * xml name: on-resource
     *  是否存在资源文件
     */
    
    public java.util.Set<java.lang.String> getOnResource(){
      return _onResource;
    }

    
    public void setOnResource(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._onResource = value;
           
    }

    
    /**
     * 
     * xml name: unless-property
     *  
     */
    
    public io.nop.ioc.model.BeanUnlessPropertyCondition getUnlessProperty(){
      return _unlessProperty;
    }

    
    public void setUnlessProperty(io.nop.ioc.model.BeanUnlessPropertyCondition value){
        checkAllowChange();
        
        this._unlessProperty = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._ifProperty = io.nop.api.core.util.FreezeHelper.deepFreeze(this._ifProperty);
            
           this._unlessProperty = io.nop.api.core.util.FreezeHelper.deepFreeze(this._unlessProperty);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("ifProperty",this.getIfProperty());
        out.put("missingBean",this.getMissingBean());
        out.put("missingClass",this.getMissingClass());
        out.put("missingResource",this.getMissingResource());
        out.put("onBean",this.getOnBean());
        out.put("onBeanType",this.getOnBeanType());
        out.put("onClass",this.getOnClass());
        out.put("onExpr",this.getOnExpr());
        out.put("onMissingBeanType",this.getOnMissingBeanType());
        out.put("onResource",this.getOnResource());
        out.put("unlessProperty",this.getUnlessProperty());
    }

    public BeanConditionModel cloneInstance(){
        BeanConditionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanConditionModel instance){
        super.copyTo(instance);
        
        instance.setIfProperty(this.getIfProperty());
        instance.setMissingBean(this.getMissingBean());
        instance.setMissingClass(this.getMissingClass());
        instance.setMissingResource(this.getMissingResource());
        instance.setOnBean(this.getOnBean());
        instance.setOnBeanType(this.getOnBeanType());
        instance.setOnClass(this.getOnClass());
        instance.setOnExpr(this.getOnExpr());
        instance.setOnMissingBeanType(this.getOnMissingBeanType());
        instance.setOnResource(this.getOnResource());
        instance.setUnlessProperty(this.getUnlessProperty());
    }

    protected BeanConditionModel newInstance(){
        return (BeanConditionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
