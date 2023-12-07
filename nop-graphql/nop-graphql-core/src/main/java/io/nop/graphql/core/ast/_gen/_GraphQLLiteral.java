//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLLiteral;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLLiteral extends io.nop.graphql.core.ast.GraphQLValue {
    
    protected java.lang.Object value;
    

    public _GraphQLLiteral(){
    }

    
    public java.lang.Object getValue(){
        return value;
    }

    public void setValue(java.lang.Object value){
        checkAllowChange();
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLLiteral newInstance(){
      return new GraphQLLiteral();
    }

    @Override
    public GraphQLLiteral deepClone(){
       GraphQLLiteral ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(value != null){
                  
                          ret.setValue(value);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLLiteral other = (GraphQLLiteral)node;
    
                if(!isValueEquivalent(this.value,other.getValue())){
                   return false;
                }
            
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLLiteral;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
