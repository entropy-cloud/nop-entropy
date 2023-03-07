//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlExistsExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlExistsExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected boolean not;
    
    protected io.nop.orm.eql.ast.SqlSubQueryExpr query;
    

    public _SqlExistsExpr(){
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
     
          checkMandatory("query",getQuery());
       
    }


    public SqlExistsExpr newInstance(){
      return new SqlExistsExpr();
    }

    @Override
    public SqlExistsExpr deepClone(){
       SqlExistsExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNot(not);
            
                if(query != null){
                  
                          ret.setQuery(query.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(query != null)
                processor.accept(query);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(query != null && processor.apply(query) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.query == oldChild){
               this.setQuery((io.nop.orm.eql.ast.SqlSubQueryExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
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
    SqlExistsExpr other = (SqlExistsExpr)node;
    
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
       return EqlASTKind.SqlExistsExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("not", not);
                
                    if(query != null){
                      
                              json.put("query", query);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(query != null)
                    query.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
