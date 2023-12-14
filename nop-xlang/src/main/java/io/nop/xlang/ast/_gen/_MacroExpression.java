//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.MacroExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MacroExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Expression expr;
    

    public _MacroExpression(){
    }

    
    public io.nop.xlang.ast.Expression getExpr(){
        return expr;
    }

    public void setExpr(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
    }


    public MacroExpression newInstance(){
      return new MacroExpression();
    }

    @Override
    public MacroExpression deepClone(){
       MacroExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.expr == child){
                this.setExpr(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MacroExpression other = (MacroExpression)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.MacroExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
