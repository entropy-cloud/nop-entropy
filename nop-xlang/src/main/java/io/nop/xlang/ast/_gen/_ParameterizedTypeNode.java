//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ParameterizedTypeNode extends io.nop.xlang.ast.NamedTypeNode {
    
    protected java.util.List<io.nop.xlang.ast.NamedTypeNode> typeArgs;
    
    protected java.lang.String typeName;
    

    public _ParameterizedTypeNode(){
    }

    
    public java.util.List<io.nop.xlang.ast.NamedTypeNode> getTypeArgs(){
        return typeArgs;
    }

    public void setTypeArgs(java.util.List<io.nop.xlang.ast.NamedTypeNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.typeArgs = value;
    }
    
    public java.util.List<io.nop.xlang.ast.NamedTypeNode> makeTypeArgs(){
        java.util.List<io.nop.xlang.ast.NamedTypeNode> list = getTypeArgs();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTypeArgs(list);
        }
        return list;
    }
    
    public java.lang.String getTypeName(){
        return typeName;
    }

    public void setTypeName(java.lang.String value){
        checkAllowChange();
        
        this.typeName = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("typeArgs",getTypeArgs());
       
          checkMandatory("typeName",getTypeName());
       
    }


    public ParameterizedTypeNode newInstance(){
      return new ParameterizedTypeNode();
    }

    @Override
    public ParameterizedTypeNode deepClone(){
       ParameterizedTypeNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(typeName != null){
                  
                          ret.setTypeName(typeName);
                      
                }
            
                if(typeArgs != null){
                  
                          java.util.List<io.nop.xlang.ast.NamedTypeNode> copy_typeArgs = new java.util.ArrayList<>(typeArgs.size());
                          for(io.nop.xlang.ast.NamedTypeNode item: typeArgs){
                              copy_typeArgs.add(item.deepClone());
                          }
                          ret.setTypeArgs(copy_typeArgs);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(typeArgs != null){
               for(io.nop.xlang.ast.NamedTypeNode child: typeArgs){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(typeArgs != null){
               for(io.nop.xlang.ast.NamedTypeNode child: typeArgs){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.typeArgs != null){
               int index = this.typeArgs.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.NamedTypeNode> list = this.replaceInList(this.typeArgs,index,newChild);
                   this.setTypeArgs(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.typeArgs != null){
               int index = this.typeArgs.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.NamedTypeNode> list = this.removeInList(this.typeArgs,index);
                   this.setTypeArgs(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ParameterizedTypeNode other = (ParameterizedTypeNode)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
                if(!isValueEquivalent(this.typeName,other.getTypeName())){
                   return false;
                }
            
            if(isListEquivalent(this.typeArgs,other.getTypeArgs())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ParameterizedTypeNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(typeName != null){
                      
                              json.put("typeName", typeName);
                          
                    }
                
                    if(typeArgs != null){
                      
                              if(!typeArgs.isEmpty())
                                json.put("typeArgs", typeArgs);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                typeArgs = io.nop.api.core.util.FreezeHelper.freezeList(typeArgs,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
