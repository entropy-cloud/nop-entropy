//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLNonNullType;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLNonNullType extends io.nop.graphql.core.ast.GraphQLType {
    
    protected io.nop.graphql.core.ast.GraphQLType type;
    

    public _GraphQLNonNullType(){
    }

    
    public io.nop.graphql.core.ast.GraphQLType getType(){
        return type;
    }

    public void setType(io.nop.graphql.core.ast.GraphQLType value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.type = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLNonNullType newInstance(){
      return new GraphQLNonNullType();
    }

    @Override
    public GraphQLNonNullType deepClone(){
       GraphQLNonNullType ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(type != null){
                  
                          ret.setType(type.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(type != null)
                processor.accept(type);
        
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(type != null && processor.apply(type) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.type == oldChild){
               this.setType((io.nop.graphql.core.ast.GraphQLType)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.type == child){
                this.setType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLNonNullType other = (GraphQLNonNullType)node;
    
            if(!isNodeEquivalent(this.type,other.getType())){
               return false;
            }
        
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLNonNullType;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(type != null){
                      
                              json.put("type", type);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(type != null)
                    type.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
