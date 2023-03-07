//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlDateTimeLiteral;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlDateTimeLiteral extends io.nop.orm.eql.ast.SqlLiteral {
    
    protected io.nop.orm.eql.enums.SqlDateTimeType type;
    
    protected java.lang.String value;
    

    public _SqlDateTimeLiteral(){
    }

    
    public io.nop.orm.eql.enums.SqlDateTimeType getType(){
        return type;
    }

    public void setType(io.nop.orm.eql.enums.SqlDateTimeType value){
        checkAllowChange();
        
        this.type = value;
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
     
          checkMandatory("type",getType());
       
          checkMandatory("value",getValue());
       
    }


    public SqlDateTimeLiteral newInstance(){
      return new SqlDateTimeLiteral();
    }

    @Override
    public SqlDateTimeLiteral deepClone(){
       SqlDateTimeLiteral ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(type != null){
                  
                          ret.setType(type);
                      
                }
            
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
    SqlDateTimeLiteral other = (SqlDateTimeLiteral)node;
    
                if(!isValueEquivalent(this.type,other.getType())){
                   return false;
                }
            
                if(!isValueEquivalent(this.value,other.getValue())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlDateTimeLiteral;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(type != null){
                      
                              json.put("type", type);
                          
                    }
                
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
