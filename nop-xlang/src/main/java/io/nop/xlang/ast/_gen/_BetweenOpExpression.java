//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _BetweenOpExpression extends io.nop.xlang.ast.FilterOpExpression {
    
    protected boolean excludeMax;
    
    protected boolean excludeMin;
    
    protected io.nop.xlang.ast.Expression max;
    
    protected io.nop.xlang.ast.Expression min;
    
    protected io.nop.xlang.ast.Expression value;
    

    public _BetweenOpExpression(){
    }

    
    public boolean getExcludeMax(){
        return excludeMax;
    }

    public void setExcludeMax(boolean value){
        checkAllowChange();
        
        this.excludeMax = value;
    }
    
    public boolean getExcludeMin(){
        return excludeMin;
    }

    public void setExcludeMin(boolean value){
        checkAllowChange();
        
        this.excludeMin = value;
    }
    
    public io.nop.xlang.ast.Expression getMax(){
        return max;
    }

    public void setMax(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.max = value;
    }
    
    public io.nop.xlang.ast.Expression getMin(){
        return min;
    }

    public void setMin(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.min = value;
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


    public BetweenOpExpression newInstance(){
      return new BetweenOpExpression();
    }

    @Override
    public BetweenOpExpression deepClone(){
       BetweenOpExpression ret = newInstance();
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
            
                if(min != null){
                  
                          ret.setMin(min.deepClone());
                      
                }
            
                if(max != null){
                  
                          ret.setMax(max.deepClone());
                      
                }
            
                ret.setExcludeMin(excludeMin);
            
                ret.setExcludeMax(excludeMax);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(value != null)
                processor.accept(value);
        
            if(min != null)
                processor.accept(min);
        
            if(max != null)
                processor.accept(max);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(min != null && processor.apply(min) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(max != null && processor.apply(max) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.value == oldChild){
               this.setValue((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.min == oldChild){
               this.setMin((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.max == oldChild){
               this.setMax((io.nop.xlang.ast.Expression)newChild);
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
        
            if(this.min == child){
                this.setMin(null);
                return true;
            }
        
            if(this.max == child){
                this.setMax(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    BetweenOpExpression other = (BetweenOpExpression)node;
    
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
        
            if(!isNodeEquivalent(this.min,other.getMin())){
               return false;
            }
        
            if(!isNodeEquivalent(this.max,other.getMax())){
               return false;
            }
        
                if(!isValueEquivalent(this.excludeMin,other.getExcludeMin())){
                   return false;
                }
            
                if(!isValueEquivalent(this.excludeMax,other.getExcludeMax())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.BetweenOpExpression;
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
                
                    if(min != null){
                      
                              json.put("min", min);
                          
                    }
                
                    if(max != null){
                      
                              json.put("max", max);
                          
                    }
                
                   json.put("excludeMin", excludeMin);
                
                   json.put("excludeMax", excludeMax);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(value != null)
                    value.freeze(cascade);
                if(min != null)
                    min.freeze(cascade);
                if(max != null)
                    max.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
