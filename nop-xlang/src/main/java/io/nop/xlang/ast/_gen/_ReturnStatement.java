//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ReturnStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression argument;
    

    public _ReturnStatement(){
    }

    
    public io.nop.xlang.ast.Expression getArgument(){
        return argument;
    }

    public void setArgument(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.argument = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public ReturnStatement newInstance(){
      return new ReturnStatement();
    }

    @Override
    public ReturnStatement deepClone(){
       ReturnStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(argument != null){
                  
                          ret.setArgument(argument.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(argument != null)
                processor.accept(argument);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(argument != null && processor.apply(argument) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.argument == oldChild){
               this.setArgument((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.argument == child){
                this.setArgument(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ReturnStatement other = (ReturnStatement)node;
    
            if(!isNodeEquivalent(this.argument,other.getArgument())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ReturnStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(argument != null){
                      
                              json.put("argument", argument);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(argument != null)
                    argument.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
