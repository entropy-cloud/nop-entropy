//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ForStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.Expression init;
    
    protected io.nop.xlang.ast.Expression test;
    
    protected io.nop.xlang.ast.Expression update;
    

    public _ForStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.Expression getInit(){
        return init;
    }

    public void setInit(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.init = value;
    }
    
    public io.nop.xlang.ast.Expression getTest(){
        return test;
    }

    public void setTest(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.test = value;
    }
    
    public io.nop.xlang.ast.Expression getUpdate(){
        return update;
    }

    public void setUpdate(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.update = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
    }


    public ForStatement newInstance(){
      return new ForStatement();
    }

    @Override
    public ForStatement deepClone(){
       ForStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(init != null){
                  
                          ret.setInit(init.deepClone());
                      
                }
            
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
                if(update != null){
                  
                          ret.setUpdate(update.deepClone());
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(init != null)
                processor.accept(init);
        
            if(test != null)
                processor.accept(test);
        
            if(update != null)
                processor.accept(update);
        
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(init != null && processor.apply(init) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(update != null && processor.apply(update) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.init == oldChild){
               this.setInit((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.test == oldChild){
               this.setTest((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.update == oldChild){
               this.setUpdate((io.nop.xlang.ast.Expression)newChild);
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
    
            if(this.init == child){
                this.setInit(null);
                return true;
            }
        
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
            if(this.update == child){
                this.setUpdate(null);
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
    ForStatement other = (ForStatement)node;
    
            if(!isNodeEquivalent(this.init,other.getInit())){
               return false;
            }
        
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
            if(!isNodeEquivalent(this.update,other.getUpdate())){
               return false;
            }
        
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ForStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(init != null){
                      
                              json.put("init", init);
                          
                    }
                
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
                    if(update != null){
                      
                              json.put("update", update);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(init != null)
                    init.freeze(cascade);
                if(test != null)
                    test.freeze(cascade);
                if(update != null)
                    update.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
