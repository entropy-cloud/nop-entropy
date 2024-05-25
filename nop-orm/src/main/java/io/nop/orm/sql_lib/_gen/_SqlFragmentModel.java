package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.sql_lib.SqlFragmentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [65:10:0:0]/nop/schema/orm/sql-lib.xdef <p>
 * 用于保存可以被复用的SQL片段，在下面的sql配置中可以通过<sql:fragment id="xx" />这种标签函数来引用SQL片段
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SqlFragmentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.sql.ISqlGenerator _source ;
    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.sql.ISqlGenerator getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.sql.ISqlGenerator value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("source",this.getSource());
    }

    public SqlFragmentModel cloneInstance(){
        SqlFragmentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SqlFragmentModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setSource(this.getSource());
    }

    protected SqlFragmentModel newInstance(){
        return (SqlFragmentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
