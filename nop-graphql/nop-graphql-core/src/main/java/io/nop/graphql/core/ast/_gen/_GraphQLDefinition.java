//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLDefinition extends io.nop.graphql.core.ast.WithDirectives {
    
    protected java.lang.String description;
    
    protected java.lang.String name;
    

    public _GraphQLDefinition(){
    }

    
    public java.lang.String getDescription(){
        return description;
    }

    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this.description = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract GraphQLDefinition deepClone();

}
 // resume CPD analysis - CPD-ON
