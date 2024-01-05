package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtMappingMatchModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [23:10:0:0]/nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtMappingMatchModel extends io.nop.xlang.xt.model.XtRuleGroupModel {
    
    /**
     *  
     * xml name: tag
     * 
     */
    private java.lang.String _tag ;
    
    /**
     * 
     * xml name: tag
     *  
     */
    
    public java.lang.String getTag(){
      return _tag;
    }

    
    public void setTag(java.lang.String value){
        checkAllowChange();
        
        this._tag = value;
           
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
        
        out.put("tag",this.getTag());
    }

    public XtMappingMatchModel cloneInstance(){
        XtMappingMatchModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtMappingMatchModel instance){
        super.copyTo(instance);
        
        instance.setTag(this.getTag());
    }

    protected XtMappingMatchModel newInstance(){
        return (XtMappingMatchModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
