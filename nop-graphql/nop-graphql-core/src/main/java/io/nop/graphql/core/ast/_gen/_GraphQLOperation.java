//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLOperation extends io.nop.graphql.core.ast.GraphQLDefinition {
    
    protected io.nop.graphql.core.ast.GraphQLOperationType operationType;
    
    protected io.nop.graphql.core.ast.GraphQLSelectionSet selectionSet;
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> variableDefinitions;
    

    public _GraphQLOperation(){
    }

    
    public io.nop.graphql.core.ast.GraphQLOperationType getOperationType(){
        return operationType;
    }

    public void setOperationType(io.nop.graphql.core.ast.GraphQLOperationType value){
        checkAllowChange();
        
        this.operationType = value;
    }
    
    public io.nop.graphql.core.ast.GraphQLSelectionSet getSelectionSet(){
        return selectionSet;
    }

    public void setSelectionSet(io.nop.graphql.core.ast.GraphQLSelectionSet value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.selectionSet = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> getVariableDefinitions(){
        return variableDefinitions;
    }

    public void setVariableDefinitions(java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.variableDefinitions = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> makeVariableDefinitions(){
        java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> list = getVariableDefinitions();
        if(list == null){
            list = new java.util.ArrayList<>();
            setVariableDefinitions(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLOperation newInstance(){
      return new GraphQLOperation();
    }

    @Override
    public GraphQLOperation deepClone(){
       GraphQLOperation ret = newInstance();
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
            
                if(description != null){
                  
                          ret.setDescription(description);
                      
                }
            
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(operationType != null){
                  
                          ret.setOperationType(operationType);
                      
                }
            
                if(variableDefinitions != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> copy_variableDefinitions = new java.util.ArrayList<>(variableDefinitions.size());
                          for(io.nop.graphql.core.ast.GraphQLVariableDefinition item: variableDefinitions){
                              copy_variableDefinitions.add(item.deepClone());
                          }
                          ret.setVariableDefinitions(copy_variableDefinitions);
                      
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
            if(variableDefinitions != null){
               for(io.nop.graphql.core.ast.GraphQLVariableDefinition child: variableDefinitions){
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
            if(variableDefinitions != null){
               for(io.nop.graphql.core.ast.GraphQLVariableDefinition child: variableDefinitions){
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
            if(this.variableDefinitions != null){
               int index = this.variableDefinitions.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> list = this.replaceInList(this.variableDefinitions,index,newChild);
                   this.setVariableDefinitions(list);
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
            if(this.variableDefinitions != null){
               int index = this.variableDefinitions.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLVariableDefinition> list = this.removeInList(this.variableDefinitions,index);
                   this.setVariableDefinitions(list);
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
    GraphQLOperation other = (GraphQLOperation)node;
    
            if(isListEquivalent(this.directives,other.getDirectives())){
               return false;
            }
                if(!isValueEquivalent(this.description,other.getDescription())){
                   return false;
                }
            
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.operationType,other.getOperationType())){
                   return false;
                }
            
            if(isListEquivalent(this.variableDefinitions,other.getVariableDefinitions())){
               return false;
            }
            if(!isNodeEquivalent(this.selectionSet,other.getSelectionSet())){
               return false;
            }
        
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLOperation;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(directives != null){
                      
                              if(!directives.isEmpty())
                                json.put("directives", directives);
                          
                    }
                
                    if(description != null){
                      
                              json.put("description", description);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(operationType != null){
                      
                              json.put("operationType", operationType);
                          
                    }
                
                    if(variableDefinitions != null){
                      
                              if(!variableDefinitions.isEmpty())
                                json.put("variableDefinitions", variableDefinitions);
                          
                    }
                
                    if(selectionSet != null){
                      
                              json.put("selectionSet", selectionSet);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                variableDefinitions = io.nop.api.core.util.FreezeHelper.freezeList(variableDefinitions,cascade);         
                if(selectionSet != null)
                    selectionSet.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
