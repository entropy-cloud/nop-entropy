//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlStatement extends EqlASTNode {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlDecorator> decorators;
    

    public _SqlStatement(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlDecorator> getDecorators(){
        return decorators;
    }

    public void setDecorators(java.util.List<io.nop.orm.eql.ast.SqlDecorator> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.decorators = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlDecorator> makeDecorators(){
        java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = getDecorators();
        if(list == null){
            list = new java.util.ArrayList<>();
            setDecorators(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract SqlStatement deepClone();

}
 // resume CPD analysis - CPD-ON
