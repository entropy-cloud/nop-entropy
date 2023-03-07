//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLDirective;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLDirective extends GraphQLASTNode {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLArgument> arguments;
    
    protected java.lang.String name;
    

    public _GraphQLDirective(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLArgument> getArguments(){
        return arguments;
    }

    public void setArguments(java.util.List<io.nop.graphql.core.ast.GraphQLArgument> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.arguments = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLArgument> makeArguments(){
        java.util.List<io.nop.graphql.core.ast.GraphQLArgument> list = getArguments();
        if(list == null){
            list = new java.util.ArrayList<>();
            setArguments(list);
        }
        return list;
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


    public GraphQLDirective newInstance(){
      return new GraphQLDirective();
    }

    @Override
    public GraphQLDirective deepClone(){
       GraphQLDirective ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(arguments != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLArgument> copy_arguments = new java.util.ArrayList<>(arguments.size());
                          for(io.nop.graphql.core.ast.GraphQLArgument item: arguments){
                              copy_arguments.add(item.deepClone());
                          }
                          ret.setArguments(copy_arguments);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgument child: arguments){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgument child: arguments){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.arguments != null){
               int index = this.arguments.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgument> list = this.replaceInList(this.arguments,index,newChild);
                   this.setArguments(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.arguments != null){
               int index = this.arguments.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgument> list = this.removeInList(this.arguments,index);
                   this.setArguments(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLDirective other = (GraphQLDirective)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(isListEquivalent(this.arguments,other.getArguments())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLDirective;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(arguments != null){
                      
                              if(!arguments.isEmpty())
                                json.put("arguments", arguments);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                arguments = io.nop.api.core.util.FreezeHelper.freezeList(arguments,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
