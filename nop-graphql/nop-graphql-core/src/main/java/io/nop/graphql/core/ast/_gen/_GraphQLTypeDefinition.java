//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLTypeDefinition extends io.nop.graphql.core.ast.GraphQLDefinition {
    
    protected boolean extension;
    

    public _GraphQLTypeDefinition(){
    }

    
    public boolean getExtension(){
        return extension;
    }

    public void setExtension(boolean value){
        checkAllowChange();
        
        this.extension = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract GraphQLTypeDefinition deepClone();

}
 // resume CPD analysis - CPD-ON
