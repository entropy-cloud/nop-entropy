//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidStyleStatement;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidStyleStatement extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> attributes;
    
    protected java.lang.String target;
    

    public _MermaidStyleStatement(){
    }

    
    public java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> getAttributes(){
        return attributes;
    }

    public void setAttributes(java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((MermaidASTNode)this));
                }
            
        this.attributes = value;
    }
    
    public java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> makeAttributes(){
        java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> list = getAttributes();
        if(list == null){
            list = new java.util.ArrayList<>();
            setAttributes(list);
        }
        return list;
    }
    
    public java.lang.String getTarget(){
        return target;
    }

    public void setTarget(java.lang.String value){
        checkAllowChange();
        
        this.target = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("target",getTarget());
       
    }


    public MermaidStyleStatement newInstance(){
      return new MermaidStyleStatement();
    }

    @Override
    public MermaidStyleStatement deepClone(){
       MermaidStyleStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(target != null){
                  
                          ret.setTarget(target);
                      
                }
            
                if(attributes != null){
                  
                          java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> copy_attributes = new java.util.ArrayList<>(attributes.size());
                          for(io.nop.mermaid.ast.MermaidStyleAttribute item: attributes){
                              copy_attributes.add(item.deepClone());
                          }
                          ret.setAttributes(copy_attributes);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<MermaidASTNode> processor){
    
            if(attributes != null){
               for(io.nop.mermaid.ast.MermaidStyleAttribute child: attributes){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<MermaidASTNode,ProcessResult> processor){
    
            if(attributes != null){
               for(io.nop.mermaid.ast.MermaidStyleAttribute child: attributes){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(MermaidASTNode oldChild, MermaidASTNode newChild){
    
            if(this.attributes != null){
               int index = this.attributes.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> list = this.replaceInList(this.attributes,index,newChild);
                   this.setAttributes(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(MermaidASTNode child){
    
            if(this.attributes != null){
               int index = this.attributes.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> list = this.removeInList(this.attributes,index);
                   this.setAttributes(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(MermaidASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MermaidStyleStatement other = (MermaidStyleStatement)node;
    
                if(!isValueEquivalent(this.target,other.getTarget())){
                   return false;
                }
            
            if(isListEquivalent(this.attributes,other.getAttributes())){
               return false;
            }
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidStyleStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(target != null){
                      
                              json.put("target", target);
                          
                    }
                
                    if(attributes != null){
                      
                              if(!attributes.isEmpty())
                                json.put("attributes", attributes);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                attributes = io.nop.api.core.util.FreezeHelper.freezeList(attributes,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
