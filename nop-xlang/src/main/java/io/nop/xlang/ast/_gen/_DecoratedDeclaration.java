//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.DecoratedDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _DecoratedDeclaration extends io.nop.xlang.ast.Declaration {
    
    protected io.nop.xlang.ast.Decorators decorators;
    

    public _DecoratedDeclaration(){
    }

    
    public io.nop.xlang.ast.Decorators getDecorators(){
        return decorators;
    }

    public void setDecorators(io.nop.xlang.ast.Decorators value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.decorators = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract DecoratedDeclaration deepClone();

}
 // resume CPD analysis - CPD-ON
