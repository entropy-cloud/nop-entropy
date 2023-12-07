//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TextOutputExpression extends io.nop.xlang.ast.OutputExpression {
    
    protected java.lang.String text;
    

    public _TextOutputExpression(){
    }

    
    public java.lang.String getText(){
        return text;
    }

    public void setText(java.lang.String value){
        checkAllowChange();
        
        this.text = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public TextOutputExpression newInstance(){
      return new TextOutputExpression();
    }

    @Override
    public TextOutputExpression deepClone(){
       TextOutputExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(text != null){
                  
                          ret.setText(text);
                      
                }
            
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
    TextOutputExpression other = (TextOutputExpression)node;
    
                if(!isValueEquivalent(this.text,other.getText())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.TextOutputExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(text != null){
                      
                              json.put("text", text);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
