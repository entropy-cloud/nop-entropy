//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ExportDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ExportDeclaration extends io.nop.xlang.ast.Declaration {
    
    protected io.nop.xlang.ast.Declaration declaration;
    

    public _ExportDeclaration(){
    }

    
    public io.nop.xlang.ast.Declaration getDeclaration(){
        return declaration;
    }

    public void setDeclaration(io.nop.xlang.ast.Declaration value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.declaration = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("declaration",getDeclaration());
       
    }


    public ExportDeclaration newInstance(){
      return new ExportDeclaration();
    }

    @Override
    public ExportDeclaration deepClone(){
       ExportDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(declaration != null){
                  
                          ret.setDeclaration(declaration.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(declaration != null)
                processor.accept(declaration);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(declaration != null && processor.apply(declaration) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.declaration == oldChild){
               this.setDeclaration((io.nop.xlang.ast.Declaration)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.declaration == child){
                this.setDeclaration(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ExportDeclaration other = (ExportDeclaration)node;
    
            if(!isNodeEquivalent(this.declaration,other.getDeclaration())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ExportDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(declaration != null){
                      
                              json.put("declaration", declaration);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(declaration != null)
                    declaration.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
