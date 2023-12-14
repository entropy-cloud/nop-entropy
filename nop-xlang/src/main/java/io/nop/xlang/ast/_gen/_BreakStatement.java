//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _BreakStatement extends io.nop.xlang.ast.Statement {
    

    public _BreakStatement(){
    }

    

    public void validate(){
       super.validate();
     
    }


    public BreakStatement newInstance(){
      return new BreakStatement();
    }

    @Override
    public BreakStatement deepClone(){
       BreakStatement ret = newInstance();
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
    BreakStatement other = (BreakStatement)node;
    
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.BreakStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
