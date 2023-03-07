//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLFieldDefinition extends io.nop.graphql.core.ast.WithDirectives {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> arguments;
    
    protected java.lang.String description;
    
    protected java.lang.String name;
    
    protected io.nop.graphql.core.ast.GraphQLType type;
    

    public _GraphQLFieldDefinition(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> getArguments(){
        return arguments;
    }

    public void setArguments(java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.arguments = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> makeArguments(){
        java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> list = getArguments();
        if(list == null){
            list = new java.util.ArrayList<>();
            setArguments(list);
        }
        return list;
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


    public GraphQLFieldDefinition newInstance(){
      return new GraphQLFieldDefinition();
    }

    @Override
    public GraphQLFieldDefinition deepClone(){
       GraphQLFieldDefinition ret = newInstance();
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
            
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(description != null){
                  
                          ret.setDescription(description);
                      
                }
            
                if(type != null){
                  
                          ret.setType(type.deepClone());
                      
                }
            
                if(arguments != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> copy_arguments = new java.util.ArrayList<>(arguments.size());
                          for(io.nop.graphql.core.ast.GraphQLArgumentDefinition item: arguments){
                              copy_arguments.add(item.deepClone());
                          }
                          ret.setArguments(copy_arguments);
                      
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
            if(type != null)
                processor.accept(type);
        
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgumentDefinition child: arguments){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(directives != null){
               for(io.nop.graphql.core.ast.GraphQLDirective child: directives){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(type != null && processor.apply(type) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(arguments != null){
               for(io.nop.graphql.core.ast.GraphQLArgumentDefinition child: arguments){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
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
            if(this.type == oldChild){
               this.setType((io.nop.graphql.core.ast.GraphQLType)newChild);
               return true;
            }
        
            if(this.arguments != null){
               int index = this.arguments.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> list = this.replaceInList(this.arguments,index,newChild);
                   this.setArguments(list);
                   return true;
               }
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
            if(this.type == child){
                this.setType(null);
                return true;
            }
        
            if(this.arguments != null){
               int index = this.arguments.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLArgumentDefinition> list = this.removeInList(this.arguments,index);
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
    GraphQLFieldDefinition other = (GraphQLFieldDefinition)node;
    
            if(isListEquivalent(this.directives,other.getDirectives())){
               return false;
            }
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.description,other.getDescription())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.type,other.getType())){
               return false;
            }
        
            if(isListEquivalent(this.arguments,other.getArguments())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLFieldDefinition;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(directives != null){
                      
                              if(!directives.isEmpty())
                                json.put("directives", directives);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(description != null){
                      
                              json.put("description", description);
                          
                    }
                
                    if(type != null){
                      
                              json.put("type", type);
                          
                    }
                
                    if(arguments != null){
                      
                              if(!arguments.isEmpty())
                                json.put("arguments", arguments);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                if(type != null)
                    type.freeze(cascade);
                arguments = io.nop.api.core.util.FreezeHelper.freezeList(arguments,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
