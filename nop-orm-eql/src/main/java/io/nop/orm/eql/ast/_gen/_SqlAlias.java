//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlAlias extends EqlASTNode {
    
    protected java.lang.String alias;
    

    public _SqlAlias(){
    }

    
    public java.lang.String getAlias(){
        return alias;
    }

    public void setAlias(java.lang.String value){
        checkAllowChange();
        
        this.alias = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlAlias newInstance(){
      return new SqlAlias();
    }

    @Override
    public SqlAlias deepClone(){
       SqlAlias ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(alias != null){
                  
                          ret.setAlias(alias);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlAlias other = (SqlAlias)node;
    
                if(!isValueEquivalent(this.alias,other.getAlias())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlAlias;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
