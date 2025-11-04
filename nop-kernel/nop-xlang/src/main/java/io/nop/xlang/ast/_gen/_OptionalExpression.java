//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.OptionalExpression;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
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
