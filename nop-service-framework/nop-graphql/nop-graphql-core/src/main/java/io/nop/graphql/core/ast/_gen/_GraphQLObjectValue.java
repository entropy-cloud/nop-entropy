//__XGEN_FORCE_OVERRIDE__
package io.nop.graphql.core.ast._gen;

import io.nop.graphql.core.ast.GraphQLObjectValue;
import io.nop.graphql.core.ast.GraphQLASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GraphQLObjectValue extends io.nop.graphql.core.ast.GraphQLValue {
    
    protected java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> properties;
    

    public _GraphQLObjectValue(){
    }

    
    public java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> getProperties(){
        return properties;
    }

    public void setProperties(java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((GraphQLASTNode)this));
                }
            
        this.properties = value;
    }
    
    public java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> makeProperties(){
        java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> list = getProperties();
        if(list == null){
            list = new java.util.ArrayList<>();
            setProperties(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GraphQLObjectValue newInstance(){
      return new GraphQLObjectValue();
    }

    @Override
    public GraphQLObjectValue deepClone(){
       GraphQLObjectValue ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(properties != null){
                  
                          java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> copy_properties = new java.util.ArrayList<>(properties.size());
                          for(io.nop.graphql.core.ast.GraphQLPropertyValue item: properties){
                              copy_properties.add(item.deepClone());
                          }
                          ret.setProperties(copy_properties);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<GraphQLASTNode> processor){
    
            if(properties != null){
               for(io.nop.graphql.core.ast.GraphQLPropertyValue child: properties){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<GraphQLASTNode,ProcessResult> processor){
    
            if(properties != null){
               for(io.nop.graphql.core.ast.GraphQLPropertyValue child: properties){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(GraphQLASTNode oldChild, GraphQLASTNode newChild){
    
            if(this.properties != null){
               int index = this.properties.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> list = this.replaceInList(this.properties,index,newChild);
                   this.setProperties(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(GraphQLASTNode child){
    
            if(this.properties != null){
               int index = this.properties.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.graphql.core.ast.GraphQLPropertyValue> list = this.removeInList(this.properties,index);
                   this.setProperties(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(GraphQLASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GraphQLObjectValue other = (GraphQLObjectValue)node;
    
            if(isListEquivalent(this.properties,other.getProperties())){
               return false;
            }
        return true;
    }

    @Override
    public GraphQLASTKind getASTKind(){
       return GraphQLASTKind.GraphQLObjectValue;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(properties != null){
                      
                              if(!properties.isEmpty())
                                json.put("properties", properties);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                properties = io.nop.api.core.util.FreezeHelper.freezeList(properties,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
