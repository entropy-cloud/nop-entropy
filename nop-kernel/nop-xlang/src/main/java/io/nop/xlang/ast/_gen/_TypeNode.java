//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TypeNode;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TypeNode extends XLangASTNode {
    
    protected boolean notNull;
    

    public _TypeNode(){
    }

    
    public boolean getNotNull(){
        return notNull;
    }

    public void setNotNull(boolean value){
        checkAllowChange();
        
        this.notNull = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract TypeNode deepClone();

}
 // resume CPD analysis - CPD-ON
