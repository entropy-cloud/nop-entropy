//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidDirectionStatement;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidDirectionStatement extends io.nop.mermaid.ast.MermaidStatement {
    
    protected io.nop.mermaid.ast.MermaidDirection direction;
    

    public _MermaidDirectionStatement(){
    }

    
    public io.nop.mermaid.ast.MermaidDirection getDirection(){
        return direction;
    }

    public void setDirection(io.nop.mermaid.ast.MermaidDirection value){
        checkAllowChange();
        
        this.direction = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("direction",getDirection());
       
    }


    public MermaidDirectionStatement newInstance(){
      return new MermaidDirectionStatement();
    }

    @Override
    public MermaidDirectionStatement deepClone(){
       MermaidDirectionStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(direction != null){
                  
                          ret.setDirection(direction);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<MermaidASTNode> processor){
    
    }

    @Override
    public ProcessResult processChild(Function<MermaidASTNode,ProcessResult> processor){
    
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(MermaidASTNode oldChild, MermaidASTNode newChild){
    
        return false;
    }

    @Override
    public boolean removeChild(MermaidASTNode child){
    
    return false;
    }

    @Override
    public boolean isEquivalentTo(MermaidASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MermaidDirectionStatement other = (MermaidDirectionStatement)node;
    
                if(!isValueEquivalent(this.direction,other.getDirection())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidDirectionStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(direction != null){
                      
                              json.put("direction", direction);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
