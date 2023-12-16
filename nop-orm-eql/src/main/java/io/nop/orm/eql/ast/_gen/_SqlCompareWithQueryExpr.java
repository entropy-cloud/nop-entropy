//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCompareWithQueryExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCompareWithQueryExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.enums.SqlCompareRange compareRange;
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected io.nop.orm.eql.enums.SqlOperator operator;
    
    protected io.nop.orm.eql.ast.SqlSubQueryExpr query;
    

    public _SqlCompareWithQueryExpr(){
    }

    
    public io.nop.orm.eql.enums.SqlCompareRange getCompareRange(){
        return compareRange;
    }

    public void setCompareRange(io.nop.orm.eql.enums.SqlCompareRange value){
        checkAllowChange();
        
        this.compareRange = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public io.nop.orm.eql.enums.SqlOperator getOperator(){
        return operator;
    }

    public void setOperator(io.nop.orm.eql.enums.SqlOperator value){
        checkAllowChange();
        
        this.operator = value;
    }
    
    public io.nop.orm.eql.ast.SqlSubQueryExpr getQuery(){
        return query;
    }

    public void setQuery(io.nop.orm.eql.ast.SqlSubQueryExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.query = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
          checkMandatory("operator",getOperator());
       
          checkMandatory("query",getQuery());
       
    }


    public SqlCompareWithQueryExpr newInstance(){
      return new SqlCompareWithQueryExpr();
    }

    @Override
    public SqlCompareWithQueryExpr deepClone(){
       SqlCompareWithQueryExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                if(operator != null){
                  
                          ret.setOperator(operator);
                      
                }
            
                if(compareRange != null){
                  
                          ret.setCompareRange(compareRange);
                      
                }
            
                if(query != null){
                  
                          ret.setQuery(query.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
            if(query != null)
                processor.accept(query);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(query != null && processor.apply(query) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.query == oldChild){
               this.setQuery((io.nop.orm.eql.ast.SqlSubQueryExpr)newChild);
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
        
            if(this.query == child){
                this.setQuery(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCompareWithQueryExpr other = (SqlCompareWithQueryExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.operator,other.getOperator())){
                   return false;
                }
            
                if(!isValueEquivalent(this.compareRange,other.getCompareRange())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.query,other.getQuery())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCompareWithQueryExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                    if(operator != null){
                      
                              json.put("operator", operator);
                          
                    }
                
                    if(compareRange != null){
                      
                              json.put("compareRange", compareRange);
                          
                    }
                
                    if(query != null){
                      
                              json.put("query", query);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
                if(query != null)
                    query.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
