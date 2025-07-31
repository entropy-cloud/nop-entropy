//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidStateNode;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidStateNode extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String description;
    
    protected java.lang.String id;
    

    public _MermaidStateNode(){
    }

    
    public java.lang.String getDescription(){
        return description;
    }

    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this.description = value;
    }
    
    public java.lang.String getId(){
        return id;
    }

    public void setId(java.lang.String value){
        checkAllowChange();
        
        this.id = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("id",getId());
       
    }


    public MermaidStateNode newInstance(){
      return new MermaidStateNode();
    }

    @Override
    public MermaidStateNode deepClone(){
       MermaidStateNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id);
                      
                }
            
                if(description != null){
                  
                          ret.setDescription(description);
                      
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
    MermaidStateNode other = (MermaidStateNode)node;
    
                if(!isValueEquivalent(this.id,other.getId())){
                   return false;
                }
            
                if(!isValueEquivalent(this.description,other.getDescription())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidStateNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(description != null){
                      
                              json.put("description", description);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
