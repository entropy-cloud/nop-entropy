//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlSubQueryExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlSelect select;
    

    public _SqlSubQueryExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlSelect getSelect(){
        return select;
    }

    public void setSelect(io.nop.orm.eql.ast.SqlSelect value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.select = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlSubQueryExpr newInstance(){
      return new SqlSubQueryExpr();
    }

    @Override
    public SqlSubQueryExpr deepClone(){
       SqlSubQueryExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(select != null){
                  
                          ret.setSelect(select.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(select != null)
                processor.accept(select);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(select != null && processor.apply(select) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.select == oldChild){
               this.setSelect((io.nop.orm.eql.ast.SqlSelect)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.select == child){
                this.setSelect(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlSubQueryExpr other = (SqlSubQueryExpr)node;
    
            if(!isNodeEquivalent(this.select,other.getSelect())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlSubQueryExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(select != null){
                      
                              json.put("select", select);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(select != null)
                    select.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
