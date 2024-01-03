//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLFieldSelection extends io.nop.graphql.core.ast.GraphQLSelection {
    
    protected java.lang.String alias;
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLArgument> arguments;
    
    protected java.lang.String name;
    
    protected io.nop.graphql.core.ast.GraphQLSelectionSet selectionSet;
    

    public _GraphQLFieldSelection(){
    }

    
    public java.lang.String getAlias(){
        return alias;
    }

    public void setAlias(java.lang.String value){
        checkAllowChange();
        
        this.alias = value;
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
    
    public io.nop.graphql.core.ast.GraphQLSelectionSet getSelectionSet(){
        return selectionSet;
    }

    public void setSelectionSet(io.nop.graphql.core.ast.GraphQLSelectionSet value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.selectionSet = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLFieldSelection newInstance(){
      return new GraphQLFieldSelection();
    }

    @Override
    public GraphQLFieldSelection deepClone(){
       GraphQLFieldSelection ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(directives != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLDirective> copy_directives = new java.util.ArrayList<>(directives.size());
                          for(io.nop.graphql.core.ast.GraphQLDirective item: directives){
                              copy_directives.add(item.deepClone());
                          }
                          ret.setDirectives(copy_directives);
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias);
                      
                }
            
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
            
                if(selectionSet != null){
                  
                          ret.setSelectionSet(selectionSet.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(directives != null){
               for(io.nop.graphql.core.ast.GraphQLDirective child: directives){
                    processor.accept(child);
                }
            }
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgument child: arguments){
                    processor.accept(child);
                }
            }
            if(selectionSet != null)
                processor.accept(selectionSet);
        
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(directives != null){
               for(io.nop.graphql.core.ast.GraphQLDirective child: directives){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgument child: arguments){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(selectionSet != null && processor.apply(selectionSet) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.directives != null){
               int index = this.directives.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLDirective> list = this.replaceInList(this.directives,index,newChild);
                   this.setDirectives(list);
                   return true;
               }
            }
            if(this.arguments != null){
               int index = this.arguments.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgument> list = this.replaceInList(this.arguments,index,newChild);
                   this.setArguments(list);
                   return true;
               }
            }
            if(this.selectionSet == oldChild){
               this.setSelectionSet((io.nop.graphql.core.ast.GraphQLSelectionSet)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.directives != null){
               int index = this.directives.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLDirective> list = this.removeInList(this.directives,index);
                   this.setDirectives(list);
                   return true;
               }
            }
            if(this.arguments != null){
               int index = this.arguments.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgument> list = this.removeInList(this.arguments,index);
                   this.setArguments(list);
                   return true;
               }
            }
            if(this.selectionSet == child){
                this.setSelectionSet(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLFieldSelection other = (GraphQLFieldSelection)node;
    
            if(isListEquivalent(this.directives,other.getDirectives())){
               return false;
            }
                if(!isValueEquivalent(this.alias,other.getAlias())){
                   return false;
                }
            
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(isListEquivalent(this.arguments,other.getArguments())){
               return false;
            }
            if(!isNodeEquivalent(this.selectionSet,other.getSelectionSet())){
               return false;
            }
        
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLFieldSelection;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(directives != null){
                      
                              if(!directives.isEmpty())
                                json.put("directives", directives);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(arguments != null){
                      
                              if(!arguments.isEmpty())
                                json.put("arguments", arguments);
                          
                    }
                
                    if(selectionSet != null){
                      
                              json.put("selectionSet", selectionSet);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                arguments = io.nop.api.core.util.FreezeHelper.freezeList(arguments,cascade);         
                if(selectionSet != null)
                    selectionSet.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
