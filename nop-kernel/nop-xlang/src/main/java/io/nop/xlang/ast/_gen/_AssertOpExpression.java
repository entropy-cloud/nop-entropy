//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _AssertOpExpression extends io.nop.xlang.ast.FilterOpExpression {
    
    protected io.nop.xlang.ast.Expression value;
    

    public _AssertOpExpression(){
    }

    
    public io.nop.xlang.ast.Expression getValue(){
        return value;
    }

    public void setValue(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("value",getValue());
       
    }


    public AssertOpExpression newInstance(){
      return new AssertOpExpression();
    }

    @Override
    public AssertOpExpression deepClone(){
       AssertOpExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(op != null){
                  
                          ret.setOp(op);
                      
                }
            
                if(errorCode != null){
                  
                          ret.setErrorCode(errorCode);
                      
                }
            
                if(label != null){
                  
                          ret.setLabel(label);
                      
                }
            
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(value != null)
                processor.accept(value);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    AssertOpExpression other = (AssertOpExpression)node;
    
                if(!isValueEquivalent(this.op,other.getOp())){
                   return false;
                }
            
                if(!isValueEquivalent(this.errorCode,other.getErrorCode())){
                   return false;
                }
            
                if(!isValueEquivalent(this.label,other.getLabel())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.AssertOpExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(op != null){
                      
                              json.put("op", op);
                          
                    }
                
                    if(errorCode != null){
                      
                              json.put("errorCode", errorCode);
                          
                    }
                
                    if(label != null){
                      
                              json.put("label", label);
                          
                    }
                
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(value != null)
                    value.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
