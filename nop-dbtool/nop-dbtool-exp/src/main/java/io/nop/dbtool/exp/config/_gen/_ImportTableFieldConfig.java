package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ImportTableFieldConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/import-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ImportTableFieldConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: dictName
     * 
     */
    private java.lang.String _dictName ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: stdDataType
     * 
     */
    private java.lang.String _stdDataType ;
    
    /**
     *  
     * xml name: transformExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _transformExpr ;
    
    /**
     * 
     * xml name: dictName
     *  
     */
    
    public java.lang.String getDictName(){
      return _dictName;
    }

    
    public void setDictName(java.lang.String value){
        checkAllowChange();
        
        this._dictName = value;
           
    }

    
    /**
     * 
     * xml name: from
     *  
     */
    
    public java.lang.String getFrom(){
      return _from;
    }

    
    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this._from = value;
           
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
     * xml name: stdDataType
     *  
     */
    
    public java.lang.String getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(java.lang.String value){
        checkAllowChange();
        
        this._stdDataType = value;
           
    }

    
    /**
     * 
     * xml name: transformExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getTransformExpr(){
      return _transformExpr;
    }

    
    public void setTransformExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._transformExpr = value;
           
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
        
        out.putNotNull("dictName",this.getDictName());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("name",this.getName());
        out.putNotNull("stdDataType",this.getStdDataType());
        out.putNotNull("transformExpr",this.getTransformExpr());
    }

    public ImportTableFieldConfig cloneInstance(){
        ImportTableFieldConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ImportTableFieldConfig instance){
        super.copyTo(instance);
        
        instance.setDictName(this.getDictName());
        instance.setFrom(this.getFrom());
        instance.setName(this.getName());
        instance.setStdDataType(this.getStdDataType());
        instance.setTransformExpr(this.getTransformExpr());
    }

    protected ImportTableFieldConfig newInstance(){
        return (ImportTableFieldConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
