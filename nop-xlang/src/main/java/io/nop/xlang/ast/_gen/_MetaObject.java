//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.MetaObject;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MetaObject extends XLangASTNode {
    
    protected java.util.List<io.nop.xlang.ast.MetaProperty> properties;
    

    public _MetaObject(){
    }

    
    public java.util.List<io.nop.xlang.ast.MetaProperty> getProperties(){
        return properties;
    }

    public void setProperties(java.util.List<io.nop.xlang.ast.MetaProperty> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.properties = value;
    }
    
    public java.util.List<io.nop.xlang.ast.MetaProperty> makeProperties(){
        java.util.List<io.nop.xlang.ast.MetaProperty> list = getProperties();
        if(list == null){
            list = new java.util.ArrayList<>();
            setProperties(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public MetaObject newInstance(){
      return new MetaObject();
    }

    @Override
    public MetaObject deepClone(){
       MetaObject ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(properties != null){
                  
                          java.util.List<io.nop.xlang.ast.MetaProperty> copy_properties = new java.util.ArrayList<>(properties.size());
                          for(io.nop.xlang.ast.MetaProperty item: properties){
                              copy_properties.add(item.deepClone());
                          }
                          ret.setProperties(copy_properties);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.MetaProperty child: properties){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.MetaProperty child: properties){
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
                   java.util.List<io.nop.xlang.ast.MetaProperty> list = this.replaceInList(this.properties,index,newChild);
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
                   java.util.List<io.nop.xlang.ast.MetaProperty> list = this.removeInList(this.properties,index);
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
    MetaObject other = (MetaObject)node;
    
            if(isListEquivalent(this.properties,other.getProperties())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.MetaObject;
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
