//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlAllProjection;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlAllProjection extends io.nop.orm.eql.ast.SqlProjection {
    
    protected io.nop.orm.eql.ast.SqlQualifiedName owner;
    

    public _SqlAllProjection(){
    }

    
    public io.nop.orm.eql.ast.SqlQualifiedName getOwner(){
        return owner;
    }

    public void setOwner(io.nop.orm.eql.ast.SqlQualifiedName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.owner = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlAllProjection newInstance(){
      return new SqlAllProjection();
    }

    @Override
    public SqlAllProjection deepClone(){
       SqlAllProjection ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(owner != null){
                  
                          ret.setOwner(owner.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(owner != null)
                processor.accept(owner);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(owner != null && processor.apply(owner) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.owner == oldChild){
               this.setOwner((io.nop.orm.eql.ast.SqlQualifiedName)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.owner == child){
                this.setOwner(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlAllProjection other = (SqlAllProjection)node;
    
            if(!isNodeEquivalent(this.owner,other.getOwner())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlAllProjection;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(owner != null){
                      
                              json.put("owner", owner);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(owner != null)
                    owner.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
