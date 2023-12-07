//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ArrayExpression extends io.nop.xlang.ast.Expression {
    
    protected java.util.List<io.nop.xlang.ast.XLangASTNode> elements;
    

    public _ArrayExpression(){
    }

    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> getElements(){
        return elements;
    }

    public void setElements(java.util.List<io.nop.xlang.ast.XLangASTNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.elements = value;
    }
    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> makeElements(){
        java.util.List<io.nop.xlang.ast.XLangASTNode> list = getElements();
        if(list == null){
            list = new java.util.ArrayList<>();
            setElements(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public ArrayExpression newInstance(){
      return new ArrayExpression();
    }

    @Override
    public ArrayExpression deepClone(){
       ArrayExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(elements != null){
                  
                          java.util.List<io.nop.xlang.ast.XLangASTNode> copy_elements = new java.util.ArrayList<>(elements.size());
                          for(io.nop.xlang.ast.XLangASTNode item: elements){
                              copy_elements.add(item.deepClone());
                          }
                          ret.setElements(copy_elements);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(elements != null){
               for(io.nop.xlang.ast.XLangASTNode child: elements){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(elements != null){
               for(io.nop.xlang.ast.XLangASTNode child: elements){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.elements != null){
               int index = this.elements.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.replaceInList(this.elements,index,newChild);
                   this.setElements(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.elements != null){
               int index = this.elements.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.removeInList(this.elements,index);
                   this.setElements(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ArrayExpression other = (ArrayExpression)node;
    
            if(isListEquivalent(this.elements,other.getElements())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ArrayExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(elements != null){
                      
                              if(!elements.isEmpty())
                                json.put("elements", elements);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                elements = io.nop.api.core.util.FreezeHelper.freezeList(elements,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
