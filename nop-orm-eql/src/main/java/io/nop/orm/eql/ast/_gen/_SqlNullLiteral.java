//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlNullLiteral;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlNullLiteral extends io.nop.orm.eql.ast.SqlExpr {
    

    public _SqlNullLiteral(){
    }

    

    public void validate(){
       super.validate();
     
    }


    public SqlNullLiteral newInstance(){
      return new SqlNullLiteral();
    }

    @Override
    public SqlNullLiteral deepClone(){
       SqlNullLiteral ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
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
    SqlNullLiteral other = (SqlNullLiteral)node;
    
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlNullLiteral;
    }

    protected void serializeFields(IJsonHandler json) {
        
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
