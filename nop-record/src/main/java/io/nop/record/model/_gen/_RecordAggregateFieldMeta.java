package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordAggregateFieldMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [65:14:0:0]/nop/schema/record/record-file.xdef <p>
 * 定长记录的定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordAggregateFieldMeta extends io.nop.record.model.RecordSimpleFieldMeta {
    
    /**
     *  
     * xml name: aggFunc
     * 
     */
    private java.lang.String _aggFunc ;
    
    /**
     * 
     * xml name: aggFunc
     *  
     */
    
    public java.lang.String getAggFunc(){
      return _aggFunc;
    }

    
    public void setAggFunc(java.lang.String value){
        checkAllowChange();
        
        this._aggFunc = value;
           
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
        
        out.putNotNull("aggFunc",this.getAggFunc());
    }

    public RecordAggregateFieldMeta cloneInstance(){
        RecordAggregateFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordAggregateFieldMeta instance){
        super.copyTo(instance);
        
        instance.setAggFunc(this.getAggFunc());
    }

    protected RecordAggregateFieldMeta newInstance(){
        return (RecordAggregateFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
