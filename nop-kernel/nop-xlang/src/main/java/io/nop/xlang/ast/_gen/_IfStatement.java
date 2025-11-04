//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _IfStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression alternate;
    
    protected io.nop.xlang.ast.Expression consequent;
    
    protected boolean ternaryExpr;
    
    protected io.nop.xlang.ast.Expression test;
    

    public _IfStatement(){
    }

    
    public io.nop.xlang.ast.Expression getAlternate(){
        return alternate;
    }

    public void setAlternate(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.alternate = value;
    }
    
    public io.nop.xlang.ast.Expression getConsequent(){
        return consequent;
    }

    public void setConsequent(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.consequent = value;
    }
    
    public boolean getTernaryExpr(){
        return ternaryExpr;
    }

    public void setTernaryExpr(boolean value){
        checkAllowChange();
        
        this.ternaryExpr = value;
    }
    
    public io.nop.xlang.ast.Expression getTest(){
        return test;
    }

    public void setTest(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.test = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public IfStatement newInstance(){
      return new IfStatement();
    }

    @Override
    public IfStatement deepClone(){
       IfStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
                if(consequent != null){
                  
                          ret.setConsequent(consequent.deepClone());
                      
                }
            
                if(alternate != null){
                  
                          ret.setAlternate(alternate.deepClone());
                      
                }
            
                ret.setTernaryExpr(ternaryExpr);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(test != null)
                processor.accept(test);
        
            if(consequent != null)
                processor.accept(consequent);
        
            if(alternate != null)
                processor.accept(alternate);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(consequent != null && processor.apply(consequent) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(alternate != null && processor.apply(alternate) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.test == oldChild){
               this.setTest((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.consequent == oldChild){
               this.setConsequent((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.alternate == oldChild){
               this.setAlternate((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
            if(this.consequent == child){
                this.setConsequent(null);
                return true;
            }
        
            if(this.alternate == child){
                this.setAlternate(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    IfStatement other = (IfStatement)node;
    
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
            if(!isNodeEquivalent(this.consequent,other.getConsequent())){
               return false;
            }
        
            if(!isNodeEquivalent(this.alternate,other.getAlternate())){
               return false;
            }
        
                if(!isValueEquivalent(this.ternaryExpr,other.getTernaryExpr())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.IfStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
                    if(consequent != null){
                      
                              json.put("consequent", consequent);
                          
                    }
                
                    if(alternate != null){
                      
                              json.put("alternate", alternate);
                          
                    }
                
                   json.put("ternaryExpr", ternaryExpr);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(test != null)
                    test.freeze(cascade);
                if(consequent != null)
                    consequent.freeze(cascade);
                if(alternate != null)
                    alternate.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
