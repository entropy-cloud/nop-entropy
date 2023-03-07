//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TypeAliasDeclaration extends io.nop.xlang.ast.Declaration {
    
    protected io.nop.xlang.ast.TypeNode defType;
    
    protected io.nop.xlang.ast.Identifier typeName;
    
    protected java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParams;
    

    public _TypeAliasDeclaration(){
    }

    
    public io.nop.xlang.ast.TypeNode getDefType(){
        return defType;
    }

    public void setDefType(io.nop.xlang.ast.TypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.defType = value;
    }
    
    public io.nop.xlang.ast.Identifier getTypeName(){
        return typeName;
    }

    public void setTypeName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.typeName = value;
    }
    
    public java.util.List<io.nop.xlang.ast.TypeParameterNode> getTypeParams(){
        return typeParams;
    }

    public void setTypeParams(java.util.List<io.nop.xlang.ast.TypeParameterNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.typeParams = value;
    }
    
    public java.util.List<io.nop.xlang.ast.TypeParameterNode> makeTypeParams(){
        java.util.List<io.nop.xlang.ast.TypeParameterNode> list = getTypeParams();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTypeParams(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("defType",getDefType());
       
          checkMandatory("typeName",getTypeName());
       
    }


    public TypeAliasDeclaration newInstance(){
      return new TypeAliasDeclaration();
    }

    @Override
    public TypeAliasDeclaration deepClone(){
       TypeAliasDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(typeName != null){
                  
                          ret.setTypeName(typeName.deepClone());
                      
                }
            
                if(typeParams != null){
                  
                          java.util.List<io.nop.xlang.ast.TypeParameterNode> copy_typeParams = new java.util.ArrayList<>(typeParams.size());
                          for(io.nop.xlang.ast.TypeParameterNode item: typeParams){
                              copy_typeParams.add(item.deepClone());
                          }
                          ret.setTypeParams(copy_typeParams);
                      
                }
            
                if(defType != null){
                  
                          ret.setDefType(defType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(typeName != null)
                processor.accept(typeName);
        
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    processor.accept(child);
                }
            }
            if(defType != null)
                processor.accept(defType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(typeName != null && processor.apply(typeName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(defType != null && processor.apply(defType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.typeName == oldChild){
               this.setTypeName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.replaceInList(this.typeParams,index,newChild);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.defType == oldChild){
               this.setDefType((io.nop.xlang.ast.TypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.typeName == child){
                this.setTypeName(null);
                return true;
            }
        
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.removeInList(this.typeParams,index);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.defType == child){
                this.setDefType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    TypeAliasDeclaration other = (TypeAliasDeclaration)node;
    
            if(!isNodeEquivalent(this.typeName,other.getTypeName())){
               return false;
            }
        
            if(isListEquivalent(this.typeParams,other.getTypeParams())){
               return false;
            }
            if(!isNodeEquivalent(this.defType,other.getDefType())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.TypeAliasDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(typeName != null){
                      
                              json.put("typeName", typeName);
                          
                    }
                
                    if(typeParams != null){
                      
                              if(!typeParams.isEmpty())
                                json.put("typeParams", typeParams);
                          
                    }
                
                    if(defType != null){
                      
                              json.put("defType", defType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(typeName != null)
                    typeName.freeze(cascade);
                typeParams = io.nop.api.core.util.FreezeHelper.freezeList(typeParams,cascade);         
                if(defType != null)
                    defType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
