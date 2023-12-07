//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLFragment;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLFragment extends io.nop.graphql.core.ast.GraphQLDefinition {
    
    protected java.lang.String onType;
    
    protected io.nop.graphql.core.ast.GraphQLSelectionSet selectionSet;
    

    public _GraphQLFragment(){
    }

    
    public java.lang.String getOnType(){
        return onType;
    }

    public void setOnType(java.lang.String value){
        checkAllowChange();
        
        this.onType = value;
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


    public GraphQLFragment newInstance(){
      return new GraphQLFragment();
    }

    @Override
    public GraphQLFragment deepClone(){
       GraphQLFragment ret = newInstance();
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
            
                if(onType != null){
                  
                          ret.setOnType(onType);
                      
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
    GraphQLFragment other = (GraphQLFragment)node;
    
            if(isListEquivalent(this.directives,other.getDirectives())){
               return false;
            }
                if(!isValueEquivalent(this.description,other.getDescription())){
                   return false;
                }
            
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.onType,other.getOnType())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.selectionSet,other.getSelectionSet())){
               return false;
            }
        
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLFragment;
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
                
                    if(onType != null){
                      
                              json.put("onType", onType);
                          
                    }
                
                    if(selectionSet != null){
                      
                              json.put("selectionSet", selectionSet);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                directives = io.nop.api.core.util.FreezeHelper.freezeList(directives,cascade);         
                if(selectionSet != null)
                    selectionSet.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
