//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlWindowExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlWindowExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlFunction function;
    
    protected io.nop.orm.eql.ast.SqlOrderBy orderBy;
    
    protected io.nop.orm.eql.ast.SqlPartitionBy partitionBy;
    

    public _SqlWindowExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlFunction getFunction(){
        return function;
    }

    public void setFunction(io.nop.orm.eql.ast.SqlFunction value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.function = value;
    }
    
    public io.nop.orm.eql.ast.SqlOrderBy getOrderBy(){
        return orderBy;
    }

    public void setOrderBy(io.nop.orm.eql.ast.SqlOrderBy value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.orderBy = value;
    }
    
    public io.nop.orm.eql.ast.SqlPartitionBy getPartitionBy(){
        return partitionBy;
    }

    public void setPartitionBy(io.nop.orm.eql.ast.SqlPartitionBy value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.partitionBy = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlWindowExpr newInstance(){
      return new SqlWindowExpr();
    }

    @Override
    public SqlWindowExpr deepClone(){
       SqlWindowExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(function != null){
                  
                          ret.setFunction(function.deepClone());
                      
                }
            
                if(partitionBy != null){
                  
                          ret.setPartitionBy(partitionBy.deepClone());
                      
                }
            
                if(orderBy != null){
                  
                          ret.setOrderBy(orderBy.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(function != null)
                processor.accept(function);
        
            if(partitionBy != null)
                processor.accept(partitionBy);
        
            if(orderBy != null)
                processor.accept(orderBy);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(function != null && processor.apply(function) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(partitionBy != null && processor.apply(partitionBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(orderBy != null && processor.apply(orderBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.function == oldChild){
               this.setFunction((io.nop.orm.eql.ast.SqlFunction)newChild);
               return true;
            }
        
            if(this.partitionBy == oldChild){
               this.setPartitionBy((io.nop.orm.eql.ast.SqlPartitionBy)newChild);
               return true;
            }
        
            if(this.orderBy == oldChild){
               this.setOrderBy((io.nop.orm.eql.ast.SqlOrderBy)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.function == child){
                this.setFunction(null);
                return true;
            }
        
            if(this.partitionBy == child){
                this.setPartitionBy(null);
                return true;
            }
        
            if(this.orderBy == child){
                this.setOrderBy(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlWindowExpr other = (SqlWindowExpr)node;
    
            if(!isNodeEquivalent(this.function,other.getFunction())){
               return false;
            }
        
            if(!isNodeEquivalent(this.partitionBy,other.getPartitionBy())){
               return false;
            }
        
            if(!isNodeEquivalent(this.orderBy,other.getOrderBy())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlWindowExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(function != null){
                      
                              json.put("function", function);
                          
                    }
                
                    if(partitionBy != null){
                      
                              json.put("partitionBy", partitionBy);
                          
                    }
                
                    if(orderBy != null){
                      
                              json.put("orderBy", orderBy);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(function != null)
                    function.freeze(cascade);
                if(partitionBy != null)
                    partitionBy.freeze(cascade);
                if(orderBy != null)
                    orderBy.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
