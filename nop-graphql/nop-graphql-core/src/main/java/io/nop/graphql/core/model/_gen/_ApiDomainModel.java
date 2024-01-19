package io.nop.graphql.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.graphql.core.model.ApiDomainModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [10:10:0:0]/nop/schema/api.xdef <p>
 * 数据域定义。orm模型解析完毕之后，domain的定义会合并到column上。如果设置了domain是以domain的设置为准
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiDomainModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: precision
     * 
     */
    private java.lang.Integer _precision ;
    
    /**
     *  
     * xml name: scale
     * 
     */
    private java.lang.Integer _scale ;
    
    /**
     *  
     * xml name: stdDataType
     * 
     */
    private io.nop.commons.type.StdDataType _stdDataType ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: stdSqlType
     * 
     */
    private io.nop.commons.type.StdSqlType _stdSqlType ;
    
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
     * xml name: precision
     *  
     */
    
    public java.lang.Integer getPrecision(){
      return _precision;
    }

    
    public void setPrecision(java.lang.Integer value){
        checkAllowChange();
        
        this._precision = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  
     */
    
    public java.lang.Integer getScale(){
      return _scale;
    }

    
    public void setScale(java.lang.Integer value){
        checkAllowChange();
        
        this._scale = value;
           
    }

    
    /**
     * 
     * xml name: stdDataType
     *  
     */
    
    public io.nop.commons.type.StdDataType getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._stdDataType = value;
           
    }

    
    /**
     * 
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
    }

    
    /**
     * 
     * xml name: stdSqlType
     *  
     */
    
    public io.nop.commons.type.StdSqlType getStdSqlType(){
      return _stdSqlType;
    }

    
    public void setStdSqlType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._stdSqlType = value;
           
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
        
        out.put("displayName",this.getDisplayName());
        out.put("name",this.getName());
        out.put("precision",this.getPrecision());
        out.put("scale",this.getScale());
        out.put("stdDataType",this.getStdDataType());
        out.put("stdDomain",this.getStdDomain());
        out.put("stdSqlType",this.getStdSqlType());
    }

    public ApiDomainModel cloneInstance(){
        ApiDomainModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiDomainModel instance){
        super.copyTo(instance);
        
        instance.setDisplayName(this.getDisplayName());
        instance.setName(this.getName());
        instance.setPrecision(this.getPrecision());
        instance.setScale(this.getScale());
        instance.setStdDataType(this.getStdDataType());
        instance.setStdDomain(this.getStdDomain());
        instance.setStdSqlType(this.getStdSqlType());
    }

    protected ApiDomainModel newInstance(){
        return (ApiDomainModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
