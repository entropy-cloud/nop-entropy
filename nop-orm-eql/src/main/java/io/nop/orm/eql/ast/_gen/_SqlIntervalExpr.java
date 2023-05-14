//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlIntervalExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlIntervalExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected io.nop.orm.eql.enums.SqlIntervalUnit intervalUnit;
    

    public _SqlIntervalExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public io.nop.orm.eql.enums.SqlIntervalUnit getIntervalUnit(){
        return intervalUnit;
    }

    public void setIntervalUnit(io.nop.orm.eql.enums.SqlIntervalUnit value){
        checkAllowChange();
        
        this.intervalUnit = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
          checkMandatory("intervalUnit",getIntervalUnit());
       
    }


    public SqlIntervalExpr newInstance(){
      return new SqlIntervalExpr();
    }

    @Override
    public SqlIntervalExpr deepClone(){
       SqlIntervalExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                if(intervalUnit != null){
                  
                          ret.setIntervalUnit(intervalUnit);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
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
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlIntervalExpr other = (SqlIntervalExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.intervalUnit,other.getIntervalUnit())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlIntervalExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                    if(intervalUnit != null){
                      
                              json.put("intervalUnit", intervalUnit);
                          
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
