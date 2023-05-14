//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlLimit extends EqlASTNode {
    
    protected io.nop.orm.eql.ast.SqlExpr limit;
    
    protected io.nop.orm.eql.ast.SqlExpr offset;
    

    public _SqlLimit(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getLimit(){
        return limit;
    }

    public void setLimit(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.limit = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getOffset(){
        return offset;
    }

    public void setOffset(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.offset = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlLimit newInstance(){
      return new SqlLimit();
    }

    @Override
    public SqlLimit deepClone(){
       SqlLimit ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(limit != null){
                  
                          ret.setLimit(limit.deepClone());
                      
                }
            
                if(offset != null){
                  
                          ret.setOffset(offset.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(limit != null)
                processor.accept(limit);
        
            if(offset != null)
                processor.accept(offset);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(limit != null && processor.apply(limit) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(offset != null && processor.apply(offset) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.limit == oldChild){
               this.setLimit((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.offset == oldChild){
               this.setOffset((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.limit == child){
                this.setLimit(null);
                return true;
            }
        
            if(this.offset == child){
                this.setOffset(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlLimit other = (SqlLimit)node;
    
            if(!isNodeEquivalent(this.limit,other.getLimit())){
               return false;
            }
        
            if(!isNodeEquivalent(this.offset,other.getOffset())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlLimit;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(limit != null){
                      
                              json.put("limit", limit);
                          
                    }
                
                    if(offset != null){
                      
                              json.put("offset", offset);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(limit != null)
                    limit.freeze(cascade);
                if(offset != null)
                    offset.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
