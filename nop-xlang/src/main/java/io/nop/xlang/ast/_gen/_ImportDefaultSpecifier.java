//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ImportDefaultSpecifier;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ImportDefaultSpecifier extends io.nop.xlang.ast.ModuleSpecifier {
    
    protected io.nop.xlang.ast.Identifier local;
    

    public _ImportDefaultSpecifier(){
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
     
    }


    public ImportDefaultSpecifier newInstance(){
      return new ImportDefaultSpecifier();
    }

    @Override
    public ImportDefaultSpecifier deepClone(){
       ImportDefaultSpecifier ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(local != null){
                  
                          ret.setLocal(local.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(local != null)
                processor.accept(local);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(local != null && processor.apply(local) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.local == oldChild){
               this.setLocal((io.nop.xlang.ast.Identifier)newChild);
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
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ImportDefaultSpecifier other = (ImportDefaultSpecifier)node;
    
            if(!isNodeEquivalent(this.local,other.getLocal())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ImportDefaultSpecifier;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(local != null){
                      
                              json.put("local", local);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(local != null)
                    local.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
