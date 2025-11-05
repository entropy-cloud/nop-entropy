//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidPieItem;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidPieItem extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String label;
    
    protected java.lang.Number value;
    

    public _MermaidPieItem(){
    }

    
    public java.lang.String getLabel(){
        return label;
    }

    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this.label = value;
    }
    
    public java.lang.Number getValue(){
        return value;
    }

    public void setValue(java.lang.Number value){
        checkAllowChange();
        
        this.value = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("label",getLabel());
       
          checkMandatory("value",getValue());
       
    }


    public MermaidPieItem newInstance(){
      return new MermaidPieItem();
    }

    @Override
    public MermaidPieItem deepClone(){
       MermaidPieItem ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(label != null){
                  
                          ret.setLabel(label);
                      
                }
            
                if(value != null){
                  
                          ret.setValue(value);
                      
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
    MermaidPieItem other = (MermaidPieItem)node;
    
                if(!isValueEquivalent(this.label,other.getLabel())){
                   return false;
                }
            
                if(!isValueEquivalent(this.value,other.getValue())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidPieItem;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(label != null){
                      
                              json.put("label", label);
                          
                    }
                
                    if(value != null){
                      
                              json.put("value", value);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
