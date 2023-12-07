//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _EnumDeclaration extends io.nop.xlang.ast.Declaration {
    
    protected java.util.List<io.nop.xlang.ast.EnumMember> members;
    
    protected io.nop.xlang.ast.Identifier name;
    

    public _EnumDeclaration(){
    }

    
    public java.util.List<io.nop.xlang.ast.EnumMember> getMembers(){
        return members;
    }

    public void setMembers(java.util.List<io.nop.xlang.ast.EnumMember> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.members = value;
    }
    
    public java.util.List<io.nop.xlang.ast.EnumMember> makeMembers(){
        java.util.List<io.nop.xlang.ast.EnumMember> list = getMembers();
        if(list == null){
            list = new java.util.ArrayList<>();
            setMembers(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.Identifier getName(){
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.name = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("members",getMembers());
       
          checkMandatory("name",getName());
       
    }


    public EnumDeclaration newInstance(){
      return new EnumDeclaration();
    }

    @Override
    public EnumDeclaration deepClone(){
       EnumDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name.deepClone());
                      
                }
            
                if(members != null){
                  
                          java.util.List<io.nop.xlang.ast.EnumMember> copy_members = new java.util.ArrayList<>(members.size());
                          for(io.nop.xlang.ast.EnumMember item: members){
                              copy_members.add(item.deepClone());
                          }
                          ret.setMembers(copy_members);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(name != null)
                processor.accept(name);
        
            if(members != null){
               for(io.nop.xlang.ast.EnumMember child: members){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(name != null && processor.apply(name) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(members != null){
               for(io.nop.xlang.ast.EnumMember child: members){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.name == oldChild){
               this.setName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.members != null){
               int index = this.members.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.EnumMember> list = this.replaceInList(this.members,index,newChild);
                   this.setMembers(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.name == child){
                this.setName(null);
                return true;
            }
        
            if(this.members != null){
               int index = this.members.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.EnumMember> list = this.removeInList(this.members,index);
                   this.setMembers(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    EnumDeclaration other = (EnumDeclaration)node;
    
            if(!isNodeEquivalent(this.name,other.getName())){
               return false;
            }
        
            if(isListEquivalent(this.members,other.getMembers())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.EnumDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(members != null){
                      
                              if(!members.isEmpty())
                                json.put("members", members);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(name != null)
                    name.freeze(cascade);
                members = io.nop.api.core.util.FreezeHelper.freezeList(members,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
