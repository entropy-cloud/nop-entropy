package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ExportTableFieldConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/export-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExportTableFieldConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: ignore
     * 
     */
    private boolean _ignore  = false;
    
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
    private io.nop.commons.type.StdDataType _stdDataType ;
    
    /**
     *  
     * xml name: transformExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _transformExpr ;
    
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
     * xml name: ignore
     *  
     */
    
    public boolean isIgnore(){
      return _ignore;
    }

    
    public void setIgnore(boolean value){
        checkAllowChange();
        
        this._ignore = value;
           
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
    
    public io.nop.commons.type.StdDataType getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(io.nop.commons.type.StdDataType value){
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
        
        out.putNotNull("from",this.getFrom());
        out.putNotNull("ignore",this.isIgnore());
        out.putNotNull("name",this.getName());
        out.putNotNull("stdDataType",this.getStdDataType());
        out.putNotNull("transformExpr",this.getTransformExpr());
    }

    public ExportTableFieldConfig cloneInstance(){
        ExportTableFieldConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExportTableFieldConfig instance){
        super.copyTo(instance);
        
        instance.setFrom(this.getFrom());
        instance.setIgnore(this.isIgnore());
        instance.setName(this.getName());
        instance.setStdDataType(this.getStdDataType());
        instance.setTransformExpr(this.getTransformExpr());
    }

    protected ExportTableFieldConfig newInstance(){
        return (ExportTableFieldConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
