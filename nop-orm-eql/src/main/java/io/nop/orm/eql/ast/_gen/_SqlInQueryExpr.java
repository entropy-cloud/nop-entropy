//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlInQueryExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlInQueryExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected boolean not;
    
    protected io.nop.orm.eql.ast.SqlSubQueryExpr query;
    

    public _SqlInQueryExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public boolean getNot(){
        return not;
    }

    public void setNot(boolean value){
        checkAllowChange();
        
        this.not = value;
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
       
          checkMandatory("query",getQuery());
       
    }


    public SqlInQueryExpr newInstance(){
      return new SqlInQueryExpr();
    }

    @Override
    public SqlInQueryExpr deepClone(){
       SqlInQueryExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                ret.setNot(not);
            
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
    SqlInQueryExpr other = (SqlInQueryExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.not,other.getNot())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.query,other.getQuery())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlInQueryExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                   json.put("not", not);
                
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
