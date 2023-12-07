//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ObjectExpression extends io.nop.xlang.ast.Expression {
    
    protected java.util.List<io.nop.xlang.ast.XLangASTNode> properties;
    

    public _ObjectExpression(){
    }

    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> getProperties(){
        return properties;
    }

    public void setProperties(java.util.List<io.nop.xlang.ast.XLangASTNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.properties = value;
    }
    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> makeProperties(){
        java.util.List<io.nop.xlang.ast.XLangASTNode> list = getProperties();
        if(list == null){
            list = new java.util.ArrayList<>();
            setProperties(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public ObjectExpression newInstance(){
      return new ObjectExpression();
    }

    @Override
    public ObjectExpression deepClone(){
       ObjectExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(properties != null){
                  
                          java.util.List<io.nop.xlang.ast.XLangASTNode> copy_properties = new java.util.ArrayList<>(properties.size());
                          for(io.nop.xlang.ast.XLangASTNode item: properties){
                              copy_properties.add(item.deepClone());
                          }
                          ret.setProperties(copy_properties);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.XLangASTNode child: properties){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.XLangASTNode child: properties){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.properties != null){
               int index = this.properties.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.replaceInList(this.properties,index,newChild);
                   this.setProperties(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.properties != null){
               int index = this.properties.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.removeInList(this.properties,index);
                   this.setProperties(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ObjectExpression other = (ObjectExpression)node;
    
            if(isListEquivalent(this.properties,other.getProperties())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ObjectExpression;
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
