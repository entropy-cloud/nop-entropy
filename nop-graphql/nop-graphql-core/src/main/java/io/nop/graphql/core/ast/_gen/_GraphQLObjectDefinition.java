//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLObjectDefinition extends io.nop.graphql.core.ast.GraphQLTypeDefinition {
    
    protected boolean extension;
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> fields;
    

    public _GraphQLObjectDefinition(){
    }

    
    public boolean getExtension(){
        return extension;
    }

    public void setExtension(boolean value){
        checkAllowChange();
        
        this.extension = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> getFields(){
        return fields;
    }

    public void setFields(java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.fields = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> makeFields(){
        java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> list = getFields();
        if(list == null){
            list = new java.util.ArrayList<>();
            setFields(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLObjectDefinition newInstance(){
      return new GraphQLObjectDefinition();
    }

    @Override
    public GraphQLObjectDefinition deepClone(){
       GraphQLObjectDefinition ret = newInstance();
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
            
                if(fields != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> copy_fields = new java.util.ArrayList<>(fields.size());
                          for(io.nop.graphql.core.ast.GraphQLFieldDefinition item: fields){
                              copy_fields.add(item.deepClone());
                          }
                          ret.setFields(copy_fields);
                      
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
            if(fields != null){
               for(io.nop.graphql.core.ast.GraphQLFieldDefinition child: fields){
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
            if(fields != null){
               for(io.nop.graphql.core.ast.GraphQLFieldDefinition child: fields){
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
            if(this.fields != null){
               int index = this.fields.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> list = this.replaceInList(this.fields,index,newChild);
                   this.setFields(list);
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
            if(this.fields != null){
               int index = this.fields.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLFieldDefinition> list = this.removeInList(this.fields,index);
                   this.setFields(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLObjectDefinition other = (GraphQLObjectDefinition)node;
    
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
            
            if(isListEquivalent(this.fields,other.getFields())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLObjectDefinition;
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
                
                    if(fields != null){
                      
                              if(!fields.isEmpty())
                                json.put("fields", fields);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                fields = io.nop.api.core.util.FreezeHelper.freezeList(fields,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
