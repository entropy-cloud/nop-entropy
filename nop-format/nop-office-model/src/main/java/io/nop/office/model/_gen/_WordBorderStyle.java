package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordBorderStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-cell-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordBorderStyle extends io.nop.office.model.OfficeBorderStyle {
    

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

    public WordBorderStyle cloneInstance(){
        WordBorderStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordBorderStyle instance){
        super.copyTo(instance);
        
    }

    protected WordBorderStyle newInstance(){
        return (WordBorderStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
