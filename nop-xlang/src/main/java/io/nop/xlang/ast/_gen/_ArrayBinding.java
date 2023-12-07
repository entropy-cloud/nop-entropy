//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ArrayBinding extends XLangASTNode implements io.nop.xlang.ast.IdentifierOrPattern{
    
    protected java.util.List<io.nop.xlang.ast.ArrayElementBinding> elements;
    
    protected io.nop.xlang.ast.RestBinding restBinding;
    

    public _ArrayBinding(){
    }

    
    public java.util.List<io.nop.xlang.ast.ArrayElementBinding> getElements(){
        return elements;
    }

    public void setElements(java.util.List<io.nop.xlang.ast.ArrayElementBinding> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.elements = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ArrayElementBinding> makeElements(){
        java.util.List<io.nop.xlang.ast.ArrayElementBinding> list = getElements();
        if(list == null){
            list = new java.util.ArrayList<>();
            setElements(list);
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


    public ArrayBinding newInstance(){
      return new ArrayBinding();
    }

    @Override
    public ArrayBinding deepClone(){
       ArrayBinding ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(elements != null){
                  
                          java.util.List<io.nop.xlang.ast.ArrayElementBinding> copy_elements = new java.util.ArrayList<>(elements.size());
                          for(io.nop.xlang.ast.ArrayElementBinding item: elements){
                              copy_elements.add(item.deepClone());
                          }
                          ret.setElements(copy_elements);
                      
                }
            
                if(restBinding != null){
                  
                          ret.setRestBinding(restBinding.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(elements != null){
               for(io.nop.xlang.ast.ArrayElementBinding child: elements){
                    processor.accept(child);
                }
            }
            if(restBinding != null)
                processor.accept(restBinding);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(elements != null){
               for(io.nop.xlang.ast.ArrayElementBinding child: elements){
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
    
            if(this.elements != null){
               int index = this.elements.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ArrayElementBinding> list = this.replaceInList(this.elements,index,newChild);
                   this.setElements(list);
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
    
            if(this.elements != null){
               int index = this.elements.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ArrayElementBinding> list = this.removeInList(this.elements,index);
                   this.setElements(list);
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
    ArrayBinding other = (ArrayBinding)node;
    
            if(isListEquivalent(this.elements,other.getElements())){
               return false;
            }
            if(!isNodeEquivalent(this.restBinding,other.getRestBinding())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ArrayBinding;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(elements != null){
                      
                              if(!elements.isEmpty())
                                json.put("elements", elements);
                          
                    }
                
                    if(restBinding != null){
                      
                              json.put("restBinding", restBinding);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                elements = io.nop.api.core.util.FreezeHelper.freezeList(elements,cascade);         
                if(restBinding != null)
                    restBinding.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
