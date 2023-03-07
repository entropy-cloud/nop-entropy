//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.OptionalExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _OptionalExpression extends io.nop.xlang.ast.Expression {
    
    protected boolean optional;
    

    public _OptionalExpression(){
    }

    
    public boolean getOptional(){
        return optional;
    }

    public void setOptional(boolean value){
        checkAllowChange();
        
        this.optional = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract OptionalExpression deepClone();

}
 // resume CPD analysis - CPD-ON
