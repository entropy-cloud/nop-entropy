//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ExportSpecifier;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ExportSpecifier extends io.nop.xlang.ast.ModuleSpecifier {
    
    protected io.nop.xlang.ast.Identifier exported;
    
    protected io.nop.xlang.ast.Identifier local;
    

    public _ExportSpecifier(){
    }

    
    public io.nop.xlang.ast.Identifier getExported(){
        return exported;
    }

    public void setExported(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.exported = value;
    }
    
    public io.nop.xlang.ast.Identifier getLocal(){
        return local;
    }

    public void setLocal(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.local = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("exported",getExported());
       
    }


    public ExportSpecifier newInstance(){
      return new ExportSpecifier();
    }

    @Override
    public ExportSpecifier deepClone(){
       ExportSpecifier ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(local != null){
                  
                          ret.setLocal(local.deepClone());
                      
                }
            
                if(exported != null){
                  
                          ret.setExported(exported.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(local != null)
                processor.accept(local);
        
            if(exported != null)
                processor.accept(exported);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(local != null && processor.apply(local) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(exported != null && processor.apply(exported) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.local == oldChild){
               this.setLocal((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.exported == oldChild){
               this.setExported((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.local == child){
                this.setLocal(null);
                return true;
            }
        
            if(this.exported == child){
                this.setExported(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ExportSpecifier other = (ExportSpecifier)node;
    
            if(!isNodeEquivalent(this.local,other.getLocal())){
               return false;
            }
        
            if(!isNodeEquivalent(this.exported,other.getExported())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ExportSpecifier;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(local != null){
                      
                              json.put("local", local);
                          
                    }
                
                    if(exported != null){
                      
                              json.put("exported", exported);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(local != null)
                    local.freeze(cascade);
                if(exported != null)
                    exported.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
