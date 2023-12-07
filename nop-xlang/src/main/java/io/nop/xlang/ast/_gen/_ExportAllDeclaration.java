//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ExportAllDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ExportAllDeclaration extends io.nop.xlang.ast.ModuleDeclaration {
    
    protected io.nop.xlang.ast.Literal source;
    

    public _ExportAllDeclaration(){
    }

    
    public io.nop.xlang.ast.Literal getSource(){
        return source;
    }

    public void setSource(io.nop.xlang.ast.Literal value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.source = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("source",getSource());
       
    }


    public ExportAllDeclaration newInstance(){
      return new ExportAllDeclaration();
    }

    @Override
    public ExportAllDeclaration deepClone(){
       ExportAllDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(source != null){
                  
                          ret.setSource(source.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(source != null)
                processor.accept(source);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(source != null && processor.apply(source) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.source == oldChild){
               this.setSource((io.nop.xlang.ast.Literal)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.source == child){
                this.setSource(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ExportAllDeclaration other = (ExportAllDeclaration)node;
    
            if(!isNodeEquivalent(this.source,other.getSource())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ExportAllDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(source != null){
                      
                              json.put("source", source);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(source != null)
                    source.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
