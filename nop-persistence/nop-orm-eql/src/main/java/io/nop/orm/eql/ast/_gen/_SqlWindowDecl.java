//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlWindowDecl;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlWindowDecl extends EqlASTNode {
    
    protected io.nop.orm.eql.ast.SqlWindowFrame frame;
    
    protected java.lang.String name;
    
    protected io.nop.orm.eql.ast.SqlOrderBy orderBy;
    
    protected io.nop.orm.eql.ast.SqlPartitionBy partitionBy;
    

    public _SqlWindowDecl(){
    }

    
    public io.nop.orm.eql.ast.SqlWindowFrame getFrame(){
        return frame;
    }

    public void setFrame(io.nop.orm.eql.ast.SqlWindowFrame value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.frame = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
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


    public SqlWindowDecl newInstance(){
      return new SqlWindowDecl();
    }

    @Override
    public SqlWindowDecl deepClone(){
       SqlWindowDecl ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(partitionBy != null){
                  
                          ret.setPartitionBy(partitionBy.deepClone());
                      
                }
            
                if(orderBy != null){
                  
                          ret.setOrderBy(orderBy.deepClone());
                      
                }
            
                if(frame != null){
                  
                          ret.setFrame(frame.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(partitionBy != null)
                processor.accept(partitionBy);
        
            if(orderBy != null)
                processor.accept(orderBy);
        
            if(frame != null)
                processor.accept(frame);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(partitionBy != null && processor.apply(partitionBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(orderBy != null && processor.apply(orderBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(frame != null && processor.apply(frame) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.partitionBy == oldChild){
               this.setPartitionBy((io.nop.orm.eql.ast.SqlPartitionBy)newChild);
               return true;
            }
        
            if(this.orderBy == oldChild){
               this.setOrderBy((io.nop.orm.eql.ast.SqlOrderBy)newChild);
               return true;
            }
        
            if(this.frame == oldChild){
               this.setFrame((io.nop.orm.eql.ast.SqlWindowFrame)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.partitionBy == child){
                this.setPartitionBy(null);
                return true;
            }
        
            if(this.orderBy == child){
                this.setOrderBy(null);
                return true;
            }
        
            if(this.frame == child){
                this.setFrame(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlWindowDecl other = (SqlWindowDecl)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.partitionBy,other.getPartitionBy())){
               return false;
            }
        
            if(!isNodeEquivalent(this.orderBy,other.getOrderBy())){
               return false;
            }
        
            if(!isNodeEquivalent(this.frame,other.getFrame())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlWindowDecl;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(partitionBy != null){
                      
                              json.put("partitionBy", partitionBy);
                          
                    }
                
                    if(orderBy != null){
                      
                              json.put("orderBy", orderBy);
                          
                    }
                
                    if(frame != null){
                      
                              json.put("frame", frame);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(partitionBy != null)
                    partitionBy.freeze(cascade);
                if(orderBy != null)
                    orderBy.freeze(cascade);
                if(frame != null)
                    frame.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
