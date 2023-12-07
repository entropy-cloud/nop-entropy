//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLUnionTypeDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLUnionTypeDefinition extends io.nop.graphql.core.ast.GraphQLTypeDefinition {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> types;
    

    public _GraphQLUnionTypeDefinition(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> getTypes(){
        return types;
    }

    public void setTypes(java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.types = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> makeTypes(){
        java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> list = getTypes();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTypes(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLUnionTypeDefinition newInstance(){
      return new GraphQLUnionTypeDefinition();
    }

    @Override
    public GraphQLUnionTypeDefinition deepClone(){
       GraphQLUnionTypeDefinition ret = newInstance();
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
            
                if(types != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> copy_types = new java.util.ArrayList<>(types.size());
                          for(io.nop.graphql.core.ast.GraphQLNamedType item: types){
                              copy_types.add(item.deepClone());
                          }
                          ret.setTypes(copy_types);
                      
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
            if(types != null){
               for(io.nop.graphql.core.ast.GraphQLNamedType child: types){
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
            if(types != null){
               for(io.nop.graphql.core.ast.GraphQLNamedType child: types){
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
            if(this.types != null){
               int index = this.types.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> list = this.replaceInList(this.types,index,newChild);
                   this.setTypes(list);
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
            if(this.types != null){
               int index = this.types.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLNamedType> list = this.removeInList(this.types,index);
                   this.setTypes(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLUnionTypeDefinition other = (GraphQLUnionTypeDefinition)node;
    
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
            
            if(isListEquivalent(this.types,other.getTypes())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLUnionTypeDefinition;
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
                
                    if(types != null){
                      
                              if(!types.isEmpty())
                                json.put("types", types);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                types = io.nop.api.core.util.FreezeHelper.freezeList(types,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
