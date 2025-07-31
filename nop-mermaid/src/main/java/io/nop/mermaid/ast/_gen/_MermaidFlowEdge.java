//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidFlowEdge;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidFlowEdge extends io.nop.mermaid.ast.MermaidStatement {
    
    protected io.nop.mermaid.ast.MermaidEdgeType edgeType;
    
    protected java.lang.String from;
    
    protected java.lang.String label;
    
    protected java.lang.String to;
    

    public _MermaidFlowEdge(){
    }

    
    public io.nop.mermaid.ast.MermaidEdgeType getEdgeType(){
        return edgeType;
    }

    public void setEdgeType(io.nop.mermaid.ast.MermaidEdgeType value){
        checkAllowChange();
        
        this.edgeType = value;
    }
    
    public java.lang.String getFrom(){
        return from;
    }

    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this.from = value;
    }
    
    public java.lang.String getLabel(){
        return label;
    }

    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this.label = value;
    }
    
    public java.lang.String getTo(){
        return to;
    }

    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this.to = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("from",getFrom());
       
          checkMandatory("to",getTo());
       
    }


    public MermaidFlowEdge newInstance(){
      return new MermaidFlowEdge();
    }

    @Override
    public MermaidFlowEdge deepClone(){
       MermaidFlowEdge ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(from != null){
                  
                          ret.setFrom(from);
                      
                }
            
                if(to != null){
                  
                          ret.setTo(to);
                      
                }
            
                if(label != null){
                  
                          ret.setLabel(label);
                      
                }
            
                if(edgeType != null){
                  
                          ret.setEdgeType(edgeType);
                      
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
    MermaidFlowEdge other = (MermaidFlowEdge)node;
    
                if(!isValueEquivalent(this.from,other.getFrom())){
                   return false;
                }
            
                if(!isValueEquivalent(this.to,other.getTo())){
                   return false;
                }
            
                if(!isValueEquivalent(this.label,other.getLabel())){
                   return false;
                }
            
                if(!isValueEquivalent(this.edgeType,other.getEdgeType())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidFlowEdge;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(from != null){
                      
                              json.put("from", from);
                          
                    }
                
                    if(to != null){
                      
                              json.put("to", to);
                          
                    }
                
                    if(label != null){
                      
                              json.put("label", label);
                          
                    }
                
                    if(edgeType != null){
                      
                              json.put("edgeType", edgeType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
