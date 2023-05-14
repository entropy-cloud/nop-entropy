//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlAssignment;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlAssignment extends EqlASTNode {
    
    protected io.nop.orm.eql.ast.SqlColumnName columnName;
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    

    public _SqlAssignment(){
    }

    
    public io.nop.orm.eql.ast.SqlColumnName getColumnName(){
        return columnName;
    }

    public void setColumnName(io.nop.orm.eql.ast.SqlColumnName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.columnName = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("columnName",getColumnName());
       
          checkMandatory("expr",getExpr());
       
    }


    public SqlAssignment newInstance(){
      return new SqlAssignment();
    }

    @Override
    public SqlAssignment deepClone(){
       SqlAssignment ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(columnName != null){
                  
                          ret.setColumnName(columnName.deepClone());
                      
                }
            
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(columnName != null)
                processor.accept(columnName);
        
            if(expr != null)
                processor.accept(expr);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(columnName != null && processor.apply(columnName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.columnName == oldChild){
               this.setColumnName((io.nop.orm.eql.ast.SqlColumnName)newChild);
               return true;
            }
        
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.columnName == child){
                this.setColumnName(null);
                return true;
            }
        
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
    SqlAssignment other = (SqlAssignment)node;
    
            if(!isNodeEquivalent(this.columnName,other.getColumnName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlAssignment;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(columnName != null){
                      
                              json.put("columnName", columnName);
                          
                    }
                
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(columnName != null)
                    columnName.freeze(cascade);
                if(expr != null)
                    expr.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
