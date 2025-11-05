//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidClassMember;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidClassMember extends MermaidASTNode {
    
    protected java.lang.Boolean isStatic;
    
    protected java.lang.String name;
    
    protected java.lang.String type;
    
    protected io.nop.mermaid.ast.MermaidVisibility visibility;
    

    public _MermaidClassMember(){
    }

    
    public java.lang.Boolean getIsStatic(){
        return isStatic;
    }

    public void setIsStatic(java.lang.Boolean value){
        checkAllowChange();
        
        this.isStatic = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    
    public java.lang.String getType(){
        return type;
    }

    public void setType(java.lang.String value){
        checkAllowChange();
        
        this.type = value;
    }
    
    public io.nop.mermaid.ast.MermaidVisibility getVisibility(){
        return visibility;
    }

    public void setVisibility(io.nop.mermaid.ast.MermaidVisibility value){
        checkAllowChange();
        
        this.visibility = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public MermaidClassMember newInstance(){
      return new MermaidClassMember();
    }

    @Override
    public MermaidClassMember deepClone(){
       MermaidClassMember ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(visibility != null){
                  
                          ret.setVisibility(visibility);
                      
                }
            
                if(type != null){
                  
                          ret.setType(type);
                      
                }
            
                if(isStatic != null){
                  
                          ret.setIsStatic(isStatic);
                      
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
    MermaidClassMember other = (MermaidClassMember)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.visibility,other.getVisibility())){
                   return false;
                }
            
                if(!isValueEquivalent(this.type,other.getType())){
                   return false;
                }
            
                if(!isValueEquivalent(this.isStatic,other.getIsStatic())){
                   return false;
                }
            
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidClassMember;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(visibility != null){
                      
                              json.put("visibility", visibility);
                          
                    }
                
                    if(type != null){
                      
                              json.put("type", type);
                          
                    }
                
                    if(isStatic != null){
                      
                              json.put("isStatic", isStatic);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
