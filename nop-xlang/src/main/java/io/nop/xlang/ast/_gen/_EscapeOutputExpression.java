//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _EscapeOutputExpression extends io.nop.xlang.ast.OutputExpression {
    
    protected io.nop.xlang.ast.XLangEscapeMode escapeMode;
    
    protected io.nop.xlang.ast.Expression text;
    

    public _EscapeOutputExpression(){
    }

    
    public io.nop.xlang.ast.XLangEscapeMode getEscapeMode(){
        return escapeMode;
    }

    public void setEscapeMode(io.nop.xlang.ast.XLangEscapeMode value){
        checkAllowChange();
        
        this.escapeMode = value;
    }
    
    public io.nop.xlang.ast.Expression getText(){
        return text;
    }

    public void setText(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.text = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("escapeMode",getEscapeMode());
       
          checkMandatory("text",getText());
       
    }


    public EscapeOutputExpression newInstance(){
      return new EscapeOutputExpression();
    }

    @Override
    public EscapeOutputExpression deepClone(){
       EscapeOutputExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(escapeMode != null){
                  
                          ret.setEscapeMode(escapeMode);
                      
                }
            
                if(text != null){
                  
                          ret.setText(text.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(text != null)
                processor.accept(text);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(text != null && processor.apply(text) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.text == oldChild){
               this.setText((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.text == child){
                this.setText(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    EscapeOutputExpression other = (EscapeOutputExpression)node;
    
                if(!isValueEquivalent(this.escapeMode,other.getEscapeMode())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.text,other.getText())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.EscapeOutputExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(escapeMode != null){
                      
                              json.put("escapeMode", escapeMode);
                          
                    }
                
                    if(text != null){
                      
                              json.put("text", text);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(text != null)
                    text.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
