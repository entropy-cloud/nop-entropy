//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.SuperExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SuperExpression extends io.nop.xlang.ast.Expression {
    

    public _SuperExpression(){
    }

    

    public void validate(){
       super.validate();
     
    }


    public SuperExpression newInstance(){
      return new SuperExpression();
    }

    @Override
    public SuperExpression deepClone(){
       SuperExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SuperExpression other = (SuperExpression)node;
    
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.SuperExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
