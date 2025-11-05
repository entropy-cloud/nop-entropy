//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidGanttTask;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidGanttTask extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String duration;
    
    protected java.lang.String id;
    
    protected java.lang.String start;
    
    protected java.lang.String title;
    

    public _MermaidGanttTask(){
    }

    
    public java.lang.String getDuration(){
        return duration;
    }

    public void setDuration(java.lang.String value){
        checkAllowChange();
        
        this.duration = value;
    }
    
    public java.lang.String getId(){
        return id;
    }

    public void setId(java.lang.String value){
        checkAllowChange();
        
        this.id = value;
    }
    
    public java.lang.String getStart(){
        return start;
    }

    public void setStart(java.lang.String value){
        checkAllowChange();
        
        this.start = value;
    }
    
    public java.lang.String getTitle(){
        return title;
    }

    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this.title = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("id",getId());
       
          checkMandatory("title",getTitle());
       
    }


    public MermaidGanttTask newInstance(){
      return new MermaidGanttTask();
    }

    @Override
    public MermaidGanttTask deepClone(){
       MermaidGanttTask ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id);
                      
                }
            
                if(title != null){
                  
                          ret.setTitle(title);
                      
                }
            
                if(start != null){
                  
                          ret.setStart(start);
                      
                }
            
                if(duration != null){
                  
                          ret.setDuration(duration);
                      
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
    MermaidGanttTask other = (MermaidGanttTask)node;
    
                if(!isValueEquivalent(this.id,other.getId())){
                   return false;
                }
            
                if(!isValueEquivalent(this.title,other.getTitle())){
                   return false;
                }
            
                if(!isValueEquivalent(this.start,other.getStart())){
                   return false;
                }
            
                if(!isValueEquivalent(this.duration,other.getDuration())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidGanttTask;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(title != null){
                      
                              json.put("title", title);
                          
                    }
                
                    if(start != null){
                      
                              json.put("start", start);
                          
                    }
                
                    if(duration != null){
                      
                              json.put("duration", duration);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
