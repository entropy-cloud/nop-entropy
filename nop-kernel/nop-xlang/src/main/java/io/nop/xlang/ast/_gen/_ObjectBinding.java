//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ObjectBinding extends XLangASTNode implements io.nop.xlang.ast.IdentifierOrPattern{
    
    protected java.util.List<io.nop.xlang.ast.PropertyBinding> properties;
    
    protected io.nop.xlang.ast.RestBinding restBinding;
    

    public _ObjectBinding(){
    }

    
    public java.util.List<io.nop.xlang.ast.PropertyBinding> getProperties(){
        return properties;
    }

    public void setProperties(java.util.List<io.nop.xlang.ast.PropertyBinding> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.properties = value;
    }
    
    public java.util.List<io.nop.xlang.ast.PropertyBinding> makeProperties(){
        java.util.List<io.nop.xlang.ast.PropertyBinding> list = getProperties();
        if(list == null){
            list = new java.util.ArrayList<>();
            setProperties(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.RestBinding getRestBinding(){
        return restBinding;
    }

    public void setRestBinding(io.nop.xlang.ast.RestBinding value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.restBinding = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public ObjectBinding newInstance(){
      return new ObjectBinding();
    }

    @Override
    public ObjectBinding deepClone(){
       ObjectBinding ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(properties != null){
                  
                          java.util.List<io.nop.xlang.ast.PropertyBinding> copy_properties = new java.util.ArrayList<>(properties.size());
                          for(io.nop.xlang.ast.PropertyBinding item: properties){
                              copy_properties.add(item.deepClone());
                          }
                          ret.setProperties(copy_properties);
                      
                }
            
                if(restBinding != null){
                  
                          ret.setRestBinding(restBinding.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.PropertyBinding child: properties){
                    processor.accept(child);
                }
            }
            if(restBinding != null)
                processor.accept(restBinding);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(properties != null){
               for(io.nop.xlang.ast.PropertyBinding child: properties){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(restBinding != null && processor.apply(restBinding) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.properties != null){
               int index = this.properties.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.PropertyBinding> list = this.replaceInList(this.properties,index,newChild);
                   this.setProperties(list);
                   return true;
               }
            }
            if(this.restBinding == oldChild){
               this.setRestBinding((io.nop.xlang.ast.RestBinding)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.properties != null){
               int index = this.properties.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.PropertyBinding> list = this.removeInList(this.properties,index);
                   this.setProperties(list);
                   return true;
               }
            }
            if(this.restBinding == child){
                this.setRestBinding(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ObjectBinding other = (ObjectBinding)node;
    
            if(isListEquivalent(this.properties,other.getProperties())){
               return false;
            }
            if(!isNodeEquivalent(this.restBinding,other.getRestBinding())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ObjectBinding;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(properties != null){
                      
                              if(!properties.isEmpty())
                                json.put("properties", properties);
                          
                    }
                
                    if(restBinding != null){
                      
                              json.put("restBinding", restBinding);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                properties = io.nop.api.core.util.FreezeHelper.freezeList(properties,cascade);         
                if(restBinding != null)
                    restBinding.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
