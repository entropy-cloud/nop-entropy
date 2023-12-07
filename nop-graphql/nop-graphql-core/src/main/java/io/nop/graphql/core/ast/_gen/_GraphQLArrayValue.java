//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLArrayValue;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLArrayValue extends io.nop.graphql.core.ast.GraphQLValue {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLValue> items;
    

    public _GraphQLArrayValue(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLValue> getItems(){
        return items;
    }

    public void setItems(java.util.List<io.nop.graphql.core.ast.GraphQLValue> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.items = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLValue> makeItems(){
        java.util.List<io.nop.graphql.core.ast.GraphQLValue> list = getItems();
        if(list == null){
            list = new java.util.ArrayList<>();
            setItems(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLArrayValue newInstance(){
      return new GraphQLArrayValue();
    }

    @Override
    public GraphQLArrayValue deepClone(){
       GraphQLArrayValue ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(items != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLValue> copy_items = new java.util.ArrayList<>(items.size());
                          for(io.nop.graphql.core.ast.GraphQLValue item: items){
                              copy_items.add(item.deepClone());
                          }
                          ret.setItems(copy_items);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(items != null){
               for(io.nop.graphql.core.ast.GraphQLValue child: items){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(items != null){
               for(io.nop.graphql.core.ast.GraphQLValue child: items){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.items != null){
               int index = this.items.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLValue> list = this.replaceInList(this.items,index,newChild);
                   this.setItems(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.items != null){
               int index = this.items.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLValue> list = this.removeInList(this.items,index);
                   this.setItems(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLArrayValue other = (GraphQLArrayValue)node;
    
            if(isListEquivalent(this.items,other.getItems())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLArrayValue;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(items != null){
                      
                              if(!items.isEmpty())
                                json.put("items", items);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                items = io.nop.api.core.util.FreezeHelper.freezeList(items,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
