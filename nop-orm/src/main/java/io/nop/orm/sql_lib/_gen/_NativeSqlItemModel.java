package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [68:10:0:0]/nop/schema/orm/sql-lib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _NativeSqlItemModel extends io.nop.orm.sql_lib.SqlItemModel {
    
    /**
     *  
     * xml name: colNameCamelCase
     * sql语句返回的列名是否按照下划线分隔变换成camelCase形式作为返回字段名
     */
    private boolean _colNameCamelCase  = false;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.sql.ISqlGenerator _source ;
    
    /**
     * 
     * xml name: colNameCamelCase
     *  sql语句返回的列名是否按照下划线分隔变换成camelCase形式作为返回字段名
     */
    
    public boolean isColNameCamelCase(){
      return _colNameCamelCase;
    }

    
    public void setColNameCamelCase(boolean value){
        checkAllowChange();
        
        this._colNameCamelCase = value;
           
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
        
        out.put("colNameCamelCase",this.isColNameCamelCase());
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
