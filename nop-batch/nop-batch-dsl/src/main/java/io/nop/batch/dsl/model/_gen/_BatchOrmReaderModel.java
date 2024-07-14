package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchOrmReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchOrmReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: batchLoadProps
     * 
     */
    private java.util.List<java.lang.String> _batchLoadProps ;
    
    /**
     *  
     * xml name: entityName
     * 
     */
    private java.lang.String _entityName ;
    
    /**
     *  
     * xml name: eql
     * 
     */
    private io.nop.core.lang.sql.ISqlGenerator _eql ;
    
    /**
     *  
     * xml name: query
     * 
     */
    private io.nop.core.lang.xml.IXNodeGenerator _query ;
    
    /**
     * 
     * xml name: batchLoadProps
     *  
     */
    
    public java.util.List<java.lang.String> getBatchLoadProps(){
      return _batchLoadProps;
    }

    
    public void setBatchLoadProps(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._batchLoadProps = value;
           
    }

    
    /**
     * 
     * xml name: entityName
     *  
     */
    
    public java.lang.String getEntityName(){
      return _entityName;
    }

    
    public void setEntityName(java.lang.String value){
        checkAllowChange();
        
        this._entityName = value;
           
    }

    
    /**
     * 
     * xml name: eql
     *  
     */
    
    public io.nop.core.lang.sql.ISqlGenerator getEql(){
      return _eql;
    }

    
    public void setEql(io.nop.core.lang.sql.ISqlGenerator value){
        checkAllowChange();
        
        this._eql = value;
           
    }

    
    /**
     * 
     * xml name: query
     *  
     */
    
    public io.nop.core.lang.xml.IXNodeGenerator getQuery(){
      return _query;
    }

    
    public void setQuery(io.nop.core.lang.xml.IXNodeGenerator value){
        checkAllowChange();
        
        this._query = value;
           
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
        
        out.putNotNull("batchLoadProps",this.getBatchLoadProps());
        out.putNotNull("entityName",this.getEntityName());
        out.putNotNull("eql",this.getEql());
        out.putNotNull("query",this.getQuery());
    }

    public BatchOrmReaderModel cloneInstance(){
        BatchOrmReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchOrmReaderModel instance){
        super.copyTo(instance);
        
        instance.setBatchLoadProps(this.getBatchLoadProps());
        instance.setEntityName(this.getEntityName());
        instance.setEql(this.getEql());
        instance.setQuery(this.getQuery());
    }

    protected BatchOrmReaderModel newInstance(){
        return (BatchOrmReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
