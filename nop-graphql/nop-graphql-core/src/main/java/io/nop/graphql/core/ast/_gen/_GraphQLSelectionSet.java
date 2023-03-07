//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLSelectionSet extends GraphQLASTNode {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLSelection> selections;
    

    public _GraphQLSelectionSet(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLSelection> getSelections(){
        return selections;
    }

    public void setSelections(java.util.List<io.nop.graphql.core.ast.GraphQLSelection> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.selections = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLSelection> makeSelections(){
        java.util.List<io.nop.graphql.core.ast.GraphQLSelection> list = getSelections();
        if(list == null){
            list = new java.util.ArrayList<>();
            setSelections(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLSelectionSet newInstance(){
      return new GraphQLSelectionSet();
    }

    @Override
    public GraphQLSelectionSet deepClone(){
       GraphQLSelectionSet ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(selections != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLSelection> copy_selections = new java.util.ArrayList<>(selections.size());
                          for(io.nop.graphql.core.ast.GraphQLSelection item: selections){
                              copy_selections.add(item.deepClone());
                          }
                          ret.setSelections(copy_selections);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(selections != null){
               for(io.nop.graphql.core.ast.GraphQLSelection child: selections){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(selections != null){
               for(io.nop.graphql.core.ast.GraphQLSelection child: selections){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.selections != null){
               int index = this.selections.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLSelection> list = this.replaceInList(this.selections,index,newChild);
                   this.setSelections(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.selections != null){
               int index = this.selections.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLSelection> list = this.removeInList(this.selections,index);
                   this.setSelections(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLSelectionSet other = (GraphQLSelectionSet)node;
    
            if(isListEquivalent(this.selections,other.getSelections())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLSelectionSet;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(selections != null){
                      
                              if(!selections.isEmpty())
                                json.put("selections", selections);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                selections = io.nop.api.core.util.FreezeHelper.freezeList(selections,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
