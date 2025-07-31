//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidFlowNode;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidFlowNode extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String id;
    
    protected io.nop.mermaid.ast.MermaidNodeShape shape;
    
    protected java.lang.String text;
    

    public _MermaidFlowNode(){
    }

    
    public java.lang.String getId(){
        return id;
    }

    public void setId(java.lang.String value){
        checkAllowChange();
        
        this.id = value;
    }
    
    public io.nop.mermaid.ast.MermaidNodeShape getShape(){
        return shape;
    }

    public void setShape(io.nop.mermaid.ast.MermaidNodeShape value){
        checkAllowChange();
        
        this.shape = value;
    }
    
    public java.lang.String getText(){
        return text;
    }

    public void setText(java.lang.String value){
        checkAllowChange();
        
        this.text = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("id",getId());
       
    }


    public MermaidFlowNode newInstance(){
      return new MermaidFlowNode();
    }

    @Override
    public MermaidFlowNode deepClone(){
       MermaidFlowNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id);
                      
                }
            
                if(text != null){
                  
                          ret.setText(text);
                      
                }
            
                if(shape != null){
                  
                          ret.setShape(shape);
                      
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
    MermaidFlowNode other = (MermaidFlowNode)node;
    
                if(!isValueEquivalent(this.id,other.getId())){
                   return false;
                }
            
                if(!isValueEquivalent(this.text,other.getText())){
                   return false;
                }
            
                if(!isValueEquivalent(this.shape,other.getShape())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidFlowNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(text != null){
                      
                              json.put("text", text);
                          
                    }
                
                    if(shape != null){
                      
                              json.put("shape", shape);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
