package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtRuleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtRuleModel extends io.nop.core.resource.component.AbstractComponentModel implements io.nop.xlang.xt.model.IXtRuleModel{
    

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
        
    }

    public XtRuleModel cloneInstance(){
        XtRuleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtRuleModel instance){
        super.copyTo(instance);
        
    }

    protected XtRuleModel newInstance(){
        return (XtRuleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
