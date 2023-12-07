//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlBitValueLiteral;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlBitValueLiteral extends io.nop.orm.eql.ast.SqlLiteral {
    
    protected java.lang.String value;
    

    public _SqlBitValueLiteral(){
    }

    
    public java.lang.String getValue(){
        return value;
    }

    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("value",getValue());
       
    }


    public SqlBitValueLiteral newInstance(){
      return new SqlBitValueLiteral();
    }

    @Override
    public SqlBitValueLiteral deepClone(){
       SqlBitValueLiteral ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(value != null){
                  
                          ret.setValue(value);
                      
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
    SqlBitValueLiteral other = (SqlBitValueLiteral)node;
    
                if(!isValueEquivalent(this.value,other.getValue())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlBitValueLiteral;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
