package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.sql_lib.EqlSqlItemModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [78:10:0:0]/nop/schema/orm/sql-lib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _EqlSqlItemModel extends io.nop.orm.sql_lib.SqlItemModel {
    
    /**
     *  
     * xml name: allowUnderscoreName
     * 
     */
    private boolean _allowUnderscoreName  = false;
    
    /**
     *  
     * xml name: enableFilter
     * 
     */
    private boolean _enableFilter  = false;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.sql.ISqlGenerator _source ;
    
    /**
     * 
     * xml name: allowUnderscoreName
     *  
     */
    
    public boolean isAllowUnderscoreName(){
      return _allowUnderscoreName;
    }

    
    public void setAllowUnderscoreName(boolean value){
        checkAllowChange();
        
        this._allowUnderscoreName = value;
           
    }

    
    /**
     * 
     * xml name: enableFilter
     *  
     */
    
    public boolean isEnableFilter(){
      return _enableFilter;
    }

    
    public void setEnableFilter(boolean value){
        checkAllowChange();
        
        this._enableFilter = value;
           
    }

    
    /**
     * 
     * xml name: source
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
        
        out.putNotNull("allowUnderscoreName",this.isAllowUnderscoreName());
        out.putNotNull("enableFilter",this.isEnableFilter());
        out.putNotNull("source",this.getSource());
    }

    public EqlSqlItemModel cloneInstance(){
        EqlSqlItemModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(EqlSqlItemModel instance){
        super.copyTo(instance);
        
        instance.setAllowUnderscoreName(this.isAllowUnderscoreName());
        instance.setEnableFilter(this.isEnableFilter());
        instance.setSource(this.getSource());
    }

    protected EqlSqlItemModel newInstance(){
        return (EqlSqlItemModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
