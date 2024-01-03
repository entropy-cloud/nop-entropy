//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLDocument extends GraphQLASTNode {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> definitions;
    

    public _GraphQLDocument(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> getDefinitions(){
        return definitions;
    }

    public void setDefinitions(java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.definitions = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> makeDefinitions(){
        java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> list = getDefinitions();
        if(list == null){
            list = new java.util.ArrayList<>();
            setDefinitions(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLDocument newInstance(){
      return new GraphQLDocument();
    }

    @Override
    public GraphQLDocument deepClone(){
       GraphQLDocument ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(definitions != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> copy_definitions = new java.util.ArrayList<>(definitions.size());
                          for(io.nop.graphql.core.ast.GraphQLDefinition item: definitions){
                              copy_definitions.add(item.deepClone());
                          }
                          ret.setDefinitions(copy_definitions);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(definitions != null){
               for(io.nop.graphql.core.ast.GraphQLDefinition child: definitions){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(definitions != null){
               for(io.nop.graphql.core.ast.GraphQLDefinition child: definitions){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.definitions != null){
               int index = this.definitions.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> list = this.replaceInList(this.definitions,index,newChild);
                   this.setDefinitions(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.definitions != null){
               int index = this.definitions.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLDefinition> list = this.removeInList(this.definitions,index);
                   this.setDefinitions(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLDocument other = (GraphQLDocument)node;
    
            if(isListEquivalent(this.definitions,other.getDefinitions())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLDocument;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(definitions != null){
                      
                              if(!definitions.isEmpty())
                                json.put("definitions", definitions);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                definitions = io.nop.api.core.util.FreezeHelper.freezeList(definitions,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
