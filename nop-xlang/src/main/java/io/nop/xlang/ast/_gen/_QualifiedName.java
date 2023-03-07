//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _QualifiedName extends XLangASTNode {
    
    protected java.lang.String name;
    
    protected io.nop.xlang.ast.QualifiedName next;
    

    public _QualifiedName(){
    }

    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    
    public io.nop.xlang.ast.QualifiedName getNext(){
        return next;
    }

    public void setNext(io.nop.xlang.ast.QualifiedName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.next = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public QualifiedName newInstance(){
      return new QualifiedName();
    }

    @Override
    public QualifiedName deepClone(){
       QualifiedName ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(next != null){
                  
                          ret.setNext(next.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(next != null)
                processor.accept(next);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(next != null && processor.apply(next) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.next == oldChild){
               this.setNext((io.nop.xlang.ast.QualifiedName)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.next == child){
                this.setNext(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    QualifiedName other = (QualifiedName)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.next,other.getNext())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.QualifiedName;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(next != null){
                      
                              json.put("next", next);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(next != null)
                    next.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
