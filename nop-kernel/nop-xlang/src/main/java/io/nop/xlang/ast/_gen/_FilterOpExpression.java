//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.FilterOpExpression;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _FilterOpExpression extends io.nop.xlang.ast.Expression {
    
    protected java.lang.String errorCode;
    
    protected java.lang.String label;
    
    protected java.lang.String op;
    

    public _FilterOpExpression(){
    }

    
    public java.lang.String getErrorCode(){
        return errorCode;
    }

    public void setErrorCode(java.lang.String value){
        checkAllowChange();
        
        this.errorCode = value;
    }
    
    public java.lang.String getLabel(){
        return label;
    }

    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this.label = value;
    }
    
    public java.lang.String getOp(){
        return op;
    }

    public void setOp(java.lang.String value){
        checkAllowChange();
        
        this.op = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract FilterOpExpression deepClone();

}
 // resume CPD analysis - CPD-ON
