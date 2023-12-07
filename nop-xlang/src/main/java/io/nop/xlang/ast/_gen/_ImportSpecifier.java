//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ImportSpecifier;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ImportSpecifier extends io.nop.xlang.ast.ModuleSpecifier {
    
    protected io.nop.xlang.ast.Identifier imported;
    
    protected io.nop.xlang.ast.Identifier local;
    

    public _ImportSpecifier(){
    }

    
    public io.nop.xlang.ast.Identifier getImported(){
        return imported;
    }

    public void setImported(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.imported = value;
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
     
          checkMandatory("imported",getImported());
       
    }


    public ImportSpecifier newInstance(){
      return new ImportSpecifier();
    }

    @Override
    public ImportSpecifier deepClone(){
       ImportSpecifier ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(local != null){
                  
                          ret.setLocal(local.deepClone());
                      
                }
            
                if(imported != null){
                  
                          ret.setImported(imported.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(local != null)
                processor.accept(local);
        
            if(imported != null)
                processor.accept(imported);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(local != null && processor.apply(local) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(imported != null && processor.apply(imported) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.local == oldChild){
               this.setLocal((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.imported == oldChild){
               this.setImported((io.nop.xlang.ast.Identifier)newChild);
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
        
            if(this.imported == child){
                this.setImported(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ImportSpecifier other = (ImportSpecifier)node;
    
            if(!isNodeEquivalent(this.local,other.getLocal())){
               return false;
            }
        
            if(!isNodeEquivalent(this.imported,other.getImported())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ImportSpecifier;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(local != null){
                      
                              json.put("local", local);
                          
                    }
                
                    if(imported != null){
                      
                              json.put("imported", imported);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(local != null)
                    local.freeze(cascade);
                if(imported != null)
                    imported.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
