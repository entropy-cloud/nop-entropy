//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlLikeExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlLikeExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr escape;
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected boolean ignoreCase;
    
    protected boolean not;
    
    protected io.nop.orm.eql.ast.SqlExpr value;
    

    public _SqlLikeExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getEscape(){
        return escape;
    }

    public void setEscape(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.escape = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public boolean getIgnoreCase(){
        return ignoreCase;
    }

    public void setIgnoreCase(boolean value){
        checkAllowChange();
        
        this.ignoreCase = value;
    }
    
    public boolean getNot(){
        return not;
    }

    public void setNot(boolean value){
        checkAllowChange();
        
        this.not = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getValue(){
        return value;
    }

    public void setValue(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
          checkMandatory("value",getValue());
       
    }


    public SqlLikeExpr newInstance(){
      return new SqlLikeExpr();
    }

    @Override
    public SqlLikeExpr deepClone(){
       SqlLikeExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                ret.setNot(not);
            
                ret.setIgnoreCase(ignoreCase);
            
                if(value != null){
                  
                          ret.setValue(value.deepClone());
                      
                }
            
                if(escape != null){
                  
                          ret.setEscape(escape.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
            if(value != null)
                processor.accept(value);
        
            if(escape != null)
                processor.accept(escape);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(value != null && processor.apply(value) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(escape != null && processor.apply(escape) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.value == oldChild){
               this.setValue((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.escape == oldChild){
               this.setEscape((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.expr == child){
                this.setExpr(null);
                return true;
            }
        
            if(this.value == child){
                this.setValue(null);
                return true;
            }
        
            if(this.escape == child){
                this.setEscape(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlLikeExpr other = (SqlLikeExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.not,other.getNot())){
                   return false;
                }
            
                if(!isValueEquivalent(this.ignoreCase,other.getIgnoreCase())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.value,other.getValue())){
               return false;
            }
        
            if(!isNodeEquivalent(this.escape,other.getEscape())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlLikeExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                   json.put("not", not);
                
                   json.put("ignoreCase", ignoreCase);
                
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
                    if(escape != null){
                      
                              json.put("escape", escape);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
                if(value != null)
                    value.freeze(cascade);
                if(escape != null)
                    escape.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
