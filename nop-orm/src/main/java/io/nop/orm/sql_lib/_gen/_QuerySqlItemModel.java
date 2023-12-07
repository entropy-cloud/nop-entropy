package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [78:10:0:0]/nop/schema/orm/sql-lib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _QuerySqlItemModel extends io.nop.orm.sql_lib.SqlItemModel {
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.xml.IXNodeGenerator _source ;
    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.xml.IXNodeGenerator getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.xml.IXNodeGenerator value){
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
        
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
