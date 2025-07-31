//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidParticipant;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidParticipant extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String alias;
    
    protected java.lang.String name;
    

    public _MermaidParticipant(){
    }

    
    public java.lang.String getAlias(){
        return alias;
    }

    public void setAlias(java.lang.String value){
        checkAllowChange();
        
        this.alias = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public MermaidParticipant newInstance(){
      return new MermaidParticipant();
    }

    @Override
    public MermaidParticipant deepClone(){
       MermaidParticipant ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias);
                      
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
    MermaidParticipant other = (MermaidParticipant)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.alias,other.getAlias())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidParticipant;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
