package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchWriteFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchWriteFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: sqlType
     * 
     */
    private io.nop.commons.type.StdSqlType _sqlType ;
    
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
     * xml name: sqlType
     *  
     */
    
    public io.nop.commons.type.StdSqlType getSqlType(){
      return _sqlType;
    }

    
    public void setSqlType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._sqlType = value;
           
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
        out.putNotNull("name",this.getName());
        out.putNotNull("sqlType",this.getSqlType());
    }

    public BatchWriteFieldModel cloneInstance(){
        BatchWriteFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchWriteFieldModel instance){
        super.copyTo(instance);
        
        instance.setFrom(this.getFrom());
        instance.setName(this.getName());
        instance.setSqlType(this.getSqlType());
    }

    protected BatchWriteFieldModel newInstance(){
        return (BatchWriteFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
