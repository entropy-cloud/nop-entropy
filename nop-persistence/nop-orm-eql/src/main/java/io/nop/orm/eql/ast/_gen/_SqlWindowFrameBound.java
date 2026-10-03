//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlWindowFrameBound;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlWindowFrameBound extends EqlASTNode {
    
    protected io.nop.orm.eql.enums.SqlWindowFrameBoundType boundType;
    
    protected io.nop.orm.eql.ast.SqlExpr offset;
    

    public _SqlWindowFrameBound(){
    }

    
    public io.nop.orm.eql.enums.SqlWindowFrameBoundType getBoundType(){
        return boundType;
    }

    public void setBoundType(io.nop.orm.eql.enums.SqlWindowFrameBoundType value){
        checkAllowChange();
        
        this.boundType = value;
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


    public SqlWindowFrameBound newInstance(){
      return new SqlWindowFrameBound();
    }

    @Override
    public SqlWindowFrameBound deepClone(){
       SqlWindowFrameBound ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(boundType != null){
                  
                          ret.setBoundType(boundType);
                      
                }
            
                if(offset != null){
                  
                          ret.setOffset(offset.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(offset != null)
                processor.accept(offset);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(offset != null && processor.apply(offset) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.offset == oldChild){
               this.setOffset((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
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
    SqlWindowFrameBound other = (SqlWindowFrameBound)node;
    
                if(!isValueEquivalent(this.boundType,other.getBoundType())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.offset,other.getOffset())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlWindowFrameBound;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(boundType != null){
                      
                              json.put("boundType", boundType);
                          
                    }
                
                    if(offset != null){
                      
                              json.put("offset", offset);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(offset != null)
                    offset.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
