//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.OutputXmlExtAttrsExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _OutputXmlExtAttrsExpression extends io.nop.xlang.ast.OutputExpression {
    
    protected java.util.Set<java.lang.String> excludeNames;
    
    protected io.nop.xlang.ast.Expression extAttrs;
    

    public _OutputXmlExtAttrsExpression(){
    }

    
    public java.util.Set<java.lang.String> getExcludeNames(){
        return excludeNames;
    }

    public void setExcludeNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this.excludeNames = value;
    }
    
    public io.nop.xlang.ast.Expression getExtAttrs(){
        return extAttrs;
    }

    public void setExtAttrs(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.extAttrs = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("excludeNames",getExcludeNames());
       
          checkMandatory("extAttrs",getExtAttrs());
       
    }


    public OutputXmlExtAttrsExpression newInstance(){
      return new OutputXmlExtAttrsExpression();
    }

    @Override
    public OutputXmlExtAttrsExpression deepClone(){
       OutputXmlExtAttrsExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(excludeNames != null){
                  
                          ret.setExcludeNames(excludeNames);
                      
                }
            
                if(extAttrs != null){
                  
                          ret.setExtAttrs(extAttrs.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(extAttrs != null)
                processor.accept(extAttrs);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(extAttrs != null && processor.apply(extAttrs) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.extAttrs == oldChild){
               this.setExtAttrs((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.extAttrs == child){
                this.setExtAttrs(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    OutputXmlExtAttrsExpression other = (OutputXmlExtAttrsExpression)node;
    
                if(!isValueEquivalent(this.excludeNames,other.getExcludeNames())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.extAttrs,other.getExtAttrs())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.OutputXmlExtAttrsExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(excludeNames != null){
                      
                              json.put("excludeNames", excludeNames);
                          
                    }
                
                    if(extAttrs != null){
                      
                              json.put("extAttrs", extAttrs);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(extAttrs != null)
                    extAttrs.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
