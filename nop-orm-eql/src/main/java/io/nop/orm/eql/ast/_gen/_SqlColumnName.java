//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlColumnName extends io.nop.orm.eql.ast.SqlExpr {
    
    protected java.lang.String name;
    
    protected io.nop.orm.eql.ast.SqlQualifiedName owner;
    

    public _SqlColumnName(){
    }

    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
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
     
          checkMandatory("name",getName());
       
    }


    public SqlColumnName newInstance(){
      return new SqlColumnName();
    }

    @Override
    public SqlColumnName deepClone(){
       SqlColumnName ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(owner != null){
                  
                          ret.setOwner(owner.deepClone());
                      
                }
            
                if(name != null){
                  
                          ret.setName(name);
                      
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
    SqlColumnName other = (SqlColumnName)node;
    
            if(!isNodeEquivalent(this.owner,other.getOwner())){
               return false;
            }
        
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlColumnName;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(owner != null){
                      
                              json.put("owner", owner);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
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
