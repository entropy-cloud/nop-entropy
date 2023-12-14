//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.WithDirectives;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _WithDirectives extends GraphQLASTNode {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLDirective> directives;
    

    public _WithDirectives(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLDirective> getDirectives(){
        return directives;
    }

    public void setDirectives(java.util.List<io.nop.graphql.core.ast.GraphQLDirective> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.directives = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLDirective> makeDirectives(){
        java.util.List<io.nop.graphql.core.ast.GraphQLDirective> list = getDirectives();
        if(list == null){
            list = new java.util.ArrayList<>();
            setDirectives(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    @Override
    public abstract WithDirectives deepClone();

}
 // resume CPD analysis - CPD-ON
