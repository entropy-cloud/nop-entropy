//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ImportAsDeclaration extends io.nop.xlang.ast.ModuleDeclaration {
    
    protected io.nop.xlang.ast.Identifier local;
    
    protected io.nop.xlang.ast.XLangASTNode source;
    

    public _ImportAsDeclaration(){
    }

    
    public io.nop.xlang.ast.Identifier getLocal(){
        return local;
    }

    public void setLocal(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.local = value;
    }
    
    public io.nop.xlang.ast.XLangASTNode getSource(){
        return source;
    }

    public void setSource(io.nop.xlang.ast.XLangASTNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.source = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("source",getSource());
       
    }


    public ImportAsDeclaration newInstance(){
      return new ImportAsDeclaration();
    }

    @Override
    public ImportAsDeclaration deepClone(){
       ImportAsDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(source != null){
                  
                          ret.setSource(source.deepClone());
                      
                }
            
                if(local != null){
                  
                          ret.setLocal(local.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(source != null)
                processor.accept(source);
        
            if(local != null)
                processor.accept(local);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(source != null && processor.apply(source) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(local != null && processor.apply(local) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.source == oldChild){
               this.setSource((io.nop.xlang.ast.XLangASTNode)newChild);
               return true;
            }
        
            if(this.local == oldChild){
               this.setLocal((io.nop.xlang.ast.Identifier)newChild);
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
    ImportAsDeclaration other = (ImportAsDeclaration)node;
    
            if(!isNodeEquivalent(this.source,other.getSource())){
               return false;
            }
        
            if(!isNodeEquivalent(this.local,other.getLocal())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ImportAsDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(source != null){
                      
                              json.put("source", source);
                          
                    }
                
                    if(local != null){
                      
                              json.put("local", local);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(source != null)
                    source.freeze(cascade);
                if(local != null)
                    local.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
