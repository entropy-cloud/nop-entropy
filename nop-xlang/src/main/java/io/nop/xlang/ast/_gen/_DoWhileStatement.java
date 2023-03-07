//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _DoWhileStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.Expression test;
    

    public _DoWhileStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
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
     
          checkMandatory("body",getBody());
       
          checkMandatory("test",getTest());
       
    }


    public DoWhileStatement newInstance(){
      return new DoWhileStatement();
    }

    @Override
    public DoWhileStatement deepClone(){
       DoWhileStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(body != null)
                processor.accept(body);
        
            if(test != null)
                processor.accept(test);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.test == oldChild){
               this.setTest((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.body == child){
                this.setBody(null);
                return true;
            }
        
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    DoWhileStatement other = (DoWhileStatement)node;
    
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.DoWhileStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(body != null)
                    body.freeze(cascade);
                if(test != null)
                    test.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
