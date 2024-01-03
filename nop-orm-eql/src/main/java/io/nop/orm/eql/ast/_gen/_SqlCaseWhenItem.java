//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCaseWhenItem;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCaseWhenItem extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr then;
    
    protected io.nop.orm.eql.ast.SqlExpr when;
    

    public _SqlCaseWhenItem(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getThen(){
        return then;
    }

    public void setThen(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.then = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getWhen(){
        return when;
    }

    public void setWhen(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.when = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("when",getWhen());
       
    }


    public SqlCaseWhenItem newInstance(){
      return new SqlCaseWhenItem();
    }

    @Override
    public SqlCaseWhenItem deepClone(){
       SqlCaseWhenItem ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(when != null){
                  
                          ret.setWhen(when.deepClone());
                      
                }
            
                if(then != null){
                  
                          ret.setThen(then.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(when != null)
                processor.accept(when);
        
            if(then != null)
                processor.accept(then);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(when != null && processor.apply(when) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(then != null && processor.apply(then) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.when == oldChild){
               this.setWhen((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.then == oldChild){
               this.setThen((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.when == child){
                this.setWhen(null);
                return true;
            }
        
            if(this.then == child){
                this.setThen(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCaseWhenItem other = (SqlCaseWhenItem)node;
    
            if(!isNodeEquivalent(this.when,other.getWhen())){
               return false;
            }
        
            if(!isNodeEquivalent(this.then,other.getThen())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCaseWhenItem;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(when != null){
                      
                              json.put("when", when);
                          
                    }
                
                    if(then != null){
                      
                              json.put("then", then);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(when != null)
                    when.freeze(cascade);
                if(then != null)
                    then.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
