//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.RegExpLiteral;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _RegExpLiteral extends io.nop.xlang.ast.Literal {
    
    protected java.lang.String flags;
    
    protected java.lang.String pattern;
    

    public _RegExpLiteral(){
    }

    
    public java.lang.String getFlags(){
        return flags;
    }

    public void setFlags(java.lang.String value){
        checkAllowChange();
        
        this.flags = value;
    }
    
    public java.lang.String getPattern(){
        return pattern;
    }

    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this.pattern = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public RegExpLiteral newInstance(){
      return new RegExpLiteral();
    }

    @Override
    public RegExpLiteral deepClone(){
       RegExpLiteral ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(value != null){
                  
                          ret.setValue(value);
                      
                }
            
                if(pattern != null){
                  
                          ret.setPattern(pattern);
                      
                }
            
                if(flags != null){
                  
                          ret.setFlags(flags);
                      
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
    RegExpLiteral other = (RegExpLiteral)node;
    
                if(!isValueEquivalent(this.value,other.getValue())){
                   return false;
                }
            
                if(!isValueEquivalent(this.pattern,other.getPattern())){
                   return false;
                }
            
                if(!isValueEquivalent(this.flags,other.getFlags())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.RegExpLiteral;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
                    if(pattern != null){
                      
                              json.put("pattern", pattern);
                          
                    }
                
                    if(flags != null){
                      
                              json.put("flags", flags);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
