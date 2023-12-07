//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLArgumentDefinition extends io.nop.graphql.core.ast.WithDirectives {
    
    protected io.nop.graphql.core.ast.GraphQLValue defaultValue;
    
    protected java.lang.String description;
    
    protected java.lang.String name;
    
    protected io.nop.graphql.core.ast.GraphQLType type;
    

    public _GraphQLArgumentDefinition(){
    }

    
    public io.nop.graphql.core.ast.GraphQLValue getDefaultValue(){
        return defaultValue;
    }

    public void setDefaultValue(io.nop.graphql.core.ast.GraphQLValue value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.defaultValue = value;
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


    public GraphQLArgumentDefinition newInstance(){
      return new GraphQLArgumentDefinition();
    }

    @Override
    public GraphQLArgumentDefinition deepClone(){
       GraphQLArgumentDefinition ret = newInstance();
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
            
                if(defaultValue != null){
                  
                          ret.setDefaultValue(defaultValue.deepClone());
                      
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
        
            if(defaultValue != null)
                processor.accept(defaultValue);
        
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
        
            if(defaultValue != null && processor.apply(defaultValue) == ProcessResult.STOP)
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
            if(this.type == oldChild){
               this.setType((io.nop.graphql.core.ast.GraphQLType)newChild);
               return true;
            }
        
            if(this.defaultValue == oldChild){
               this.setDefaultValue((io.nop.graphql.core.ast.GraphQLValue)newChild);
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
            if(this.type == child){
                this.setType(null);
                return true;
            }
        
            if(this.defaultValue == child){
                this.setDefaultValue(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLArgumentDefinition other = (GraphQLArgumentDefinition)node;
    
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
        
            if(!isNodeEquivalent(this.defaultValue,other.getDefaultValue())){
               return false;
            }
        
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLArgumentDefinition;
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
                
                    if(defaultValue != null){
                      
                              json.put("defaultValue", defaultValue);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                if(type != null)
                    type.freeze(cascade);
                if(defaultValue != null)
                    defaultValue.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
