//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidClassNode extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String className;
    
    protected java.util.List<io.nop.mermaid.ast.MermaidClassMember> members;
    

    public _MermaidClassNode(){
    }

    
    public java.lang.String getClassName(){
        return className;
    }

    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this.className = value;
    }
    
    public java.util.List<io.nop.mermaid.ast.MermaidClassMember> getMembers(){
        return members;
    }

    public void setMembers(java.util.List<io.nop.mermaid.ast.MermaidClassMember> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((MermaidASTNode)this));
                }
            
        this.members = value;
    }
    
    public java.util.List<io.nop.mermaid.ast.MermaidClassMember> makeMembers(){
        java.util.List<io.nop.mermaid.ast.MermaidClassMember> list = getMembers();
        if(list == null){
            list = new java.util.ArrayList<>();
            setMembers(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("className",getClassName());
       
    }


    public MermaidClassNode newInstance(){
      return new MermaidClassNode();
    }

    @Override
    public MermaidClassNode deepClone(){
       MermaidClassNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(className != null){
                  
                          ret.setClassName(className);
                      
                }
            
                if(members != null){
                  
                          java.util.List<io.nop.mermaid.ast.MermaidClassMember> copy_members = new java.util.ArrayList<>(members.size());
                          for(io.nop.mermaid.ast.MermaidClassMember item: members){
                              copy_members.add(item.deepClone());
                          }
                          ret.setMembers(copy_members);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<MermaidASTNode> processor){
    
            if(members != null){
               for(io.nop.mermaid.ast.MermaidClassMember child: members){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<MermaidASTNode,ProcessResult> processor){
    
            if(members != null){
               for(io.nop.mermaid.ast.MermaidClassMember child: members){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(MermaidASTNode oldChild, MermaidASTNode newChild){
    
            if(this.members != null){
               int index = this.members.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidClassMember> list = this.replaceInList(this.members,index,newChild);
                   this.setMembers(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(MermaidASTNode child){
    
            if(this.members != null){
               int index = this.members.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidClassMember> list = this.removeInList(this.members,index);
                   this.setMembers(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(MermaidASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MermaidClassNode other = (MermaidClassNode)node;
    
                if(!isValueEquivalent(this.className,other.getClassName())){
                   return false;
                }
            
            if(isListEquivalent(this.members,other.getMembers())){
               return false;
            }
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidClassNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(className != null){
                      
                              json.put("className", className);
                          
                    }
                
                    if(members != null){
                      
                              if(!members.isEmpty())
                                json.put("members", members);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                members = io.nop.api.core.util.FreezeHelper.freezeList(members,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
