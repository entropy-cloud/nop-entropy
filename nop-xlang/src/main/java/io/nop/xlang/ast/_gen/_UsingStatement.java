//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.UsingStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _UsingStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.VariableDeclaration vars;
    

    public _UsingStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.VariableDeclaration getVars(){
        return vars;
    }

    public void setVars(io.nop.xlang.ast.VariableDeclaration value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.vars = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
          checkMandatory("vars",getVars());
       
    }


    public UsingStatement newInstance(){
      return new UsingStatement();
    }

    @Override
    public UsingStatement deepClone(){
       UsingStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(vars != null){
                  
                          ret.setVars(vars.deepClone());
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(vars != null)
                processor.accept(vars);
        
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(vars != null && processor.apply(vars) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.vars == oldChild){
               this.setVars((io.nop.xlang.ast.VariableDeclaration)newChild);
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
    
            if(this.vars == child){
                this.setVars(null);
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
    UsingStatement other = (UsingStatement)node;
    
            if(!isNodeEquivalent(this.vars,other.getVars())){
               return false;
            }
        
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.UsingStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(vars != null){
                      
                              json.put("vars", vars);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(vars != null)
                    vars.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
