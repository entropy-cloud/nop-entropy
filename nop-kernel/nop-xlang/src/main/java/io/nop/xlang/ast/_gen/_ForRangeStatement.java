//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ForRangeStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ForRangeStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression begin;
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.Expression end;
    
    protected io.nop.xlang.ast.Identifier index;
    
    protected io.nop.xlang.ast.Expression step;
    
    protected io.nop.xlang.ast.Identifier var;
    

    public _ForRangeStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBegin(){
        return begin;
    }

    public void setBegin(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.begin = value;
    }
    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.Expression getEnd(){
        return end;
    }

    public void setEnd(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.end = value;
    }
    
    public io.nop.xlang.ast.Identifier getIndex(){
        return index;
    }

    public void setIndex(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.index = value;
    }
    
    public io.nop.xlang.ast.Expression getStep(){
        return step;
    }

    public void setStep(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.step = value;
    }
    
    public io.nop.xlang.ast.Identifier getVar(){
        return var;
    }

    public void setVar(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.var = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("begin",getBegin());
       
          checkMandatory("body",getBody());
       
          checkMandatory("end",getEnd());
       
          checkMandatory("step",getStep());
       
    }


    public ForRangeStatement newInstance(){
      return new ForRangeStatement();
    }

    @Override
    public ForRangeStatement deepClone(){
       ForRangeStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(var != null){
                  
                          ret.setVar(var.deepClone());
                      
                }
            
                if(index != null){
                  
                          ret.setIndex(index.deepClone());
                      
                }
            
                if(begin != null){
                  
                          ret.setBegin(begin.deepClone());
                      
                }
            
                if(end != null){
                  
                          ret.setEnd(end.deepClone());
                      
                }
            
                if(step != null){
                  
                          ret.setStep(step.deepClone());
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(var != null)
                processor.accept(var);
        
            if(index != null)
                processor.accept(index);
        
            if(begin != null)
                processor.accept(begin);
        
            if(end != null)
                processor.accept(end);
        
            if(step != null)
                processor.accept(step);
        
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(var != null && processor.apply(var) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(index != null && processor.apply(index) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(begin != null && processor.apply(begin) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(end != null && processor.apply(end) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(step != null && processor.apply(step) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.var == oldChild){
               this.setVar((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.index == oldChild){
               this.setIndex((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.begin == oldChild){
               this.setBegin((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.end == oldChild){
               this.setEnd((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.step == oldChild){
               this.setStep((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.var == child){
                this.setVar(null);
                return true;
            }
        
            if(this.index == child){
                this.setIndex(null);
                return true;
            }
        
            if(this.begin == child){
                this.setBegin(null);
                return true;
            }
        
            if(this.end == child){
                this.setEnd(null);
                return true;
            }
        
            if(this.step == child){
                this.setStep(null);
                return true;
            }
        
            if(this.body == child){
                this.setBody(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ForRangeStatement other = (ForRangeStatement)node;
    
            if(!isNodeEquivalent(this.var,other.getVar())){
               return false;
            }
        
            if(!isNodeEquivalent(this.index,other.getIndex())){
               return false;
            }
        
            if(!isNodeEquivalent(this.begin,other.getBegin())){
               return false;
            }
        
            if(!isNodeEquivalent(this.end,other.getEnd())){
               return false;
            }
        
            if(!isNodeEquivalent(this.step,other.getStep())){
               return false;
            }
        
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ForRangeStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(var != null){
                      
                              json.put("var", var);
                          
                    }
                
                    if(index != null){
                      
                              json.put("index", index);
                          
                    }
                
                    if(begin != null){
                      
                              json.put("begin", begin);
                          
                    }
                
                    if(end != null){
                      
                              json.put("end", end);
                          
                    }
                
                    if(step != null){
                      
                              json.put("step", step);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(var != null)
                    var.freeze(cascade);
                if(index != null)
                    index.freeze(cascade);
                if(begin != null)
                    begin.freeze(cascade);
                if(end != null)
                    end.freeze(cascade);
                if(step != null)
                    step.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
