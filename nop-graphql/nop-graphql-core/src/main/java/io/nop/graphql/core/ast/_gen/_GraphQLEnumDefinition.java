//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLEnumDefinition extends io.nop.graphql.core.ast.GraphQLTypeDefinition {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> enumValues;
    

    public _GraphQLEnumDefinition(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> getEnumValues(){
        return enumValues;
    }

    public void setEnumValues(java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.enumValues = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> makeEnumValues(){
        java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> list = getEnumValues();
        if(list == null){
            list = new java.util.ArrayList<>();
            setEnumValues(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLEnumDefinition newInstance(){
      return new GraphQLEnumDefinition();
    }

    @Override
    public GraphQLEnumDefinition deepClone(){
       GraphQLEnumDefinition ret = newInstance();
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
            
                ret.setExtension(extension);
            
                if(enumValues != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> copy_enumValues = new java.util.ArrayList<>(enumValues.size());
                          for(io.nop.graphql.core.ast.GraphQLEnumValueDefinition item: enumValues){
                              copy_enumValues.add(item.deepClone());
                          }
                          ret.setEnumValues(copy_enumValues);
                      
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
            if(enumValues != null){
               for(io.nop.graphql.core.ast.GraphQLEnumValueDefinition child: enumValues){
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
            if(enumValues != null){
               for(io.nop.graphql.core.ast.GraphQLEnumValueDefinition child: enumValues){
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
            if(this.enumValues != null){
               int index = this.enumValues.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> list = this.replaceInList(this.enumValues,index,newChild);
                   this.setEnumValues(list);
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
            if(this.enumValues != null){
               int index = this.enumValues.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLEnumValueDefinition> list = this.removeInList(this.enumValues,index);
                   this.setEnumValues(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLEnumDefinition other = (GraphQLEnumDefinition)node;
    
            if(isListEquivalent(this.directives,other.getDirectives())){
               return false;
            }
                if(!isValueEquivalent(this.description,other.getDescription())){
                   return false;
                }
            
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.extension,other.getExtension())){
                   return false;
                }
            
            if(isListEquivalent(this.enumValues,other.getEnumValues())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLEnumDefinition;
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
                
                   json.put("extension", extension);
                
                    if(enumValues != null){
                      
                              if(!enumValues.isEmpty())
                                json.put("enumValues", enumValues);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                enumValues = io.nop.api.core.util.FreezeHelper.freezeList(enumValues,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
