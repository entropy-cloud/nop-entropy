//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlWindowFrame;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlWindowFrame extends EqlASTNode {
    
    protected io.nop.orm.eql.ast.SqlWindowFrameBound end;
    
    protected io.nop.orm.eql.ast.SqlWindowFrameBound start;
    
    protected io.nop.orm.eql.enums.SqlWindowFrameType unit;
    

    public _SqlWindowFrame(){
    }

    
    public io.nop.orm.eql.ast.SqlWindowFrameBound getEnd(){
        return end;
    }

    public void setEnd(io.nop.orm.eql.ast.SqlWindowFrameBound value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.end = value;
    }
    
    public io.nop.orm.eql.ast.SqlWindowFrameBound getStart(){
        return start;
    }

    public void setStart(io.nop.orm.eql.ast.SqlWindowFrameBound value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.start = value;
    }
    
    public io.nop.orm.eql.enums.SqlWindowFrameType getUnit(){
        return unit;
    }

    public void setUnit(io.nop.orm.eql.enums.SqlWindowFrameType value){
        checkAllowChange();
        
        this.unit = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlWindowFrame newInstance(){
      return new SqlWindowFrame();
    }

    @Override
    public SqlWindowFrame deepClone(){
       SqlWindowFrame ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(unit != null){
                  
                          ret.setUnit(unit);
                      
                }
            
                if(start != null){
                  
                          ret.setStart(start.deepClone());
                      
                }
            
                if(end != null){
                  
                          ret.setEnd(end.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(start != null)
                processor.accept(start);
        
            if(end != null)
                processor.accept(end);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(start != null && processor.apply(start) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(end != null && processor.apply(end) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.start == oldChild){
               this.setStart((io.nop.orm.eql.ast.SqlWindowFrameBound)newChild);
               return true;
            }
        
            if(this.end == oldChild){
               this.setEnd((io.nop.orm.eql.ast.SqlWindowFrameBound)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.start == child){
                this.setStart(null);
                return true;
            }
        
            if(this.end == child){
                this.setEnd(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlWindowFrame other = (SqlWindowFrame)node;
    
                if(!isValueEquivalent(this.unit,other.getUnit())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.start,other.getStart())){
               return false;
            }
        
            if(!isNodeEquivalent(this.end,other.getEnd())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlWindowFrame;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(unit != null){
                      
                              json.put("unit", unit);
                          
                    }
                
                    if(start != null){
                      
                              json.put("start", start);
                          
                    }
                
                    if(end != null){
                      
                              json.put("end", end);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(start != null)
                    start.freeze(cascade);
                if(end != null)
                    end.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
