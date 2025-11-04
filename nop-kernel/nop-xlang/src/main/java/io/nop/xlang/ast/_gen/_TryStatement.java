//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TryStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TryStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression block;
    
    protected io.nop.xlang.ast.CatchClause catchHandler;
    
    protected io.nop.xlang.ast.Expression finalizer;
    

    public _TryStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBlock(){
        return block;
    }

    public void setBlock(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.block = value;
    }
    
    public io.nop.xlang.ast.CatchClause getCatchHandler(){
        return catchHandler;
    }

    public void setCatchHandler(io.nop.xlang.ast.CatchClause value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.catchHandler = value;
    }
    
    public io.nop.xlang.ast.Expression getFinalizer(){
        return finalizer;
    }

    public void setFinalizer(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.finalizer = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("block",getBlock());
       
    }


    public TryStatement newInstance(){
      return new TryStatement();
    }

    @Override
    public TryStatement deepClone(){
       TryStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(block != null){
                  
                          ret.setBlock(block.deepClone());
                      
                }
            
                if(catchHandler != null){
                  
                          ret.setCatchHandler(catchHandler.deepClone());
                      
                }
            
                if(finalizer != null){
                  
                          ret.setFinalizer(finalizer.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(block != null)
                processor.accept(block);
        
            if(catchHandler != null)
                processor.accept(catchHandler);
        
            if(finalizer != null)
                processor.accept(finalizer);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(block != null && processor.apply(block) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(catchHandler != null && processor.apply(catchHandler) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(finalizer != null && processor.apply(finalizer) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.block == oldChild){
               this.setBlock((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.catchHandler == oldChild){
               this.setCatchHandler((io.nop.xlang.ast.CatchClause)newChild);
               return true;
            }
        
            if(this.finalizer == oldChild){
               this.setFinalizer((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.block == child){
                this.setBlock(null);
                return true;
            }
        
            if(this.catchHandler == child){
                this.setCatchHandler(null);
                return true;
            }
        
            if(this.finalizer == child){
                this.setFinalizer(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    TryStatement other = (TryStatement)node;
    
            if(!isNodeEquivalent(this.block,other.getBlock())){
               return false;
            }
        
            if(!isNodeEquivalent(this.catchHandler,other.getCatchHandler())){
               return false;
            }
        
            if(!isNodeEquivalent(this.finalizer,other.getFinalizer())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.TryStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(block != null){
                      
                              json.put("block", block);
                          
                    }
                
                    if(catchHandler != null){
                      
                              json.put("catchHandler", catchHandler);
                          
                    }
                
                    if(finalizer != null){
                      
                              json.put("finalizer", finalizer);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(block != null)
                    block.freeze(cascade);
                if(catchHandler != null)
                    catchHandler.freeze(cascade);
                if(finalizer != null)
                    finalizer.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
