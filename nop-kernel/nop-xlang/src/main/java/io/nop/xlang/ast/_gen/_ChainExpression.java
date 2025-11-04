//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ChainExpression extends io.nop.xlang.ast.OptionalExpression {
    
    protected io.nop.xlang.ast.Expression expr;
    
    protected boolean notEmpty;
    
    protected java.lang.String target;
    

    public _ChainExpression(){
    }

    
    public io.nop.xlang.ast.Expression getExpr(){
        return expr;
    }

    public void setExpr(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public boolean getNotEmpty(){
        return notEmpty;
    }

    public void setNotEmpty(boolean value){
        checkAllowChange();
        
        this.notEmpty = value;
    }
    
    public java.lang.String getTarget(){
        return target;
    }

    public void setTarget(java.lang.String value){
        checkAllowChange();
        
        this.target = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
    }


    public ChainExpression newInstance(){
      return new ChainExpression();
    }

    @Override
    public ChainExpression deepClone(){
       ChainExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setOptional(optional);
            
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                if(target != null){
                  
                          ret.setTarget(target);
                      
                }
            
                ret.setNotEmpty(notEmpty);
            
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
    ChainExpression other = (ChainExpression)node;
    
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.target,other.getTarget())){
                   return false;
                }
            
                if(!isValueEquivalent(this.notEmpty,other.getNotEmpty())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ChainExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("optional", optional);
                
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                    if(target != null){
                      
                              json.put("target", target);
                          
                    }
                
                   json.put("notEmpty", notEmpty);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
