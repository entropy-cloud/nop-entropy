//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ExpressionStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression expression;
    

    public _ExpressionStatement(){
    }

    
    public io.nop.xlang.ast.Expression getExpression(){
        return expression;
    }

    public void setExpression(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expression = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expression",getExpression());
       
    }


    public ExpressionStatement newInstance(){
      return new ExpressionStatement();
    }

    @Override
    public ExpressionStatement deepClone(){
       ExpressionStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expression != null){
                  
                          ret.setExpression(expression.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(expression != null)
                processor.accept(expression);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(expression != null && processor.apply(expression) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.expression == oldChild){
               this.setExpression((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.expression == child){
                this.setExpression(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ExpressionStatement other = (ExpressionStatement)node;
    
            if(!isNodeEquivalent(this.expression,other.getExpression())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ExpressionStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expression != null){
                      
                              json.put("expression", expression);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expression != null)
                    expression.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
