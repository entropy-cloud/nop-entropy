//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCastExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlTypeExpr dataType;
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    

    public _SqlCastExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlTypeExpr getDataType(){
        return dataType;
    }

    public void setDataType(io.nop.orm.eql.ast.SqlTypeExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.dataType = value;
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
     
          checkMandatory("dataType",getDataType());
       
          checkMandatory("expr",getExpr());
       
    }


    public SqlCastExpr newInstance(){
      return new SqlCastExpr();
    }

    @Override
    public SqlCastExpr deepClone(){
       SqlCastExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                if(dataType != null){
                  
                          ret.setDataType(dataType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
            if(dataType != null)
                processor.accept(dataType);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(dataType != null && processor.apply(dataType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.dataType == oldChild){
               this.setDataType((io.nop.orm.eql.ast.SqlTypeExpr)newChild);
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
        
            if(this.dataType == child){
                this.setDataType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCastExpr other = (SqlCastExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
            if(!isNodeEquivalent(this.dataType,other.getDataType())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCastExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                    if(dataType != null){
                      
                              json.put("dataType", dataType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
                if(dataType != null)
                    dataType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
