//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.SwitchCaseExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SwitchCaseExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Expression caseValue;
    
    protected io.nop.xlang.ast.Expression consequence;
    

    public _SwitchCaseExpression(){
    }

    
    public io.nop.xlang.ast.Expression getCaseValue(){
        return caseValue;
    }

    public void setCaseValue(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.caseValue = value;
    }
    
    public io.nop.xlang.ast.Expression getConsequence(){
        return consequence;
    }

    public void setConsequence(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.consequence = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("caseValue",getCaseValue());
       
          checkMandatory("consequence",getConsequence());
       
    }


    public SwitchCaseExpression newInstance(){
      return new SwitchCaseExpression();
    }

    @Override
    public SwitchCaseExpression deepClone(){
       SwitchCaseExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(caseValue != null){
                  
                          ret.setCaseValue(caseValue.deepClone());
                      
                }
            
                if(consequence != null){
                  
                          ret.setConsequence(consequence.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(caseValue != null)
                processor.accept(caseValue);
        
            if(consequence != null)
                processor.accept(consequence);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(caseValue != null && processor.apply(caseValue) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(consequence != null && processor.apply(consequence) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.caseValue == oldChild){
               this.setCaseValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.consequence == oldChild){
               this.setConsequence((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.caseValue == child){
                this.setCaseValue(null);
                return true;
            }
        
            if(this.consequence == child){
                this.setConsequence(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SwitchCaseExpression other = (SwitchCaseExpression)node;
    
            if(!isNodeEquivalent(this.caseValue,other.getCaseValue())){
               return false;
            }
        
            if(!isNodeEquivalent(this.consequence,other.getConsequence())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.SwitchCaseExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(caseValue != null){
                      
                              json.put("caseValue", caseValue);
                          
                    }
                
                    if(consequence != null){
                      
                              json.put("consequence", consequence);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(caseValue != null)
                    caseValue.freeze(cascade);
                if(consequence != null)
                    consequence.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
