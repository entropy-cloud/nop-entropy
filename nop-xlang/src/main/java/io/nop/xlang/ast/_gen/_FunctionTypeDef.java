//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.FunctionTypeDef;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _FunctionTypeDef extends io.nop.xlang.ast.StructuredTypeDef {
    
    protected java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> args;
    
    protected io.nop.xlang.ast.NamedTypeNode returnType;
    
    protected java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParams;
    
    protected boolean varArgs;
    

    public _FunctionTypeDef(){
    }

    
    public java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> getArgs(){
        return args;
    }

    public void setArgs(java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.args = value;
    }
    
    public java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> makeArgs(){
        java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> list = getArgs();
        if(list == null){
            list = new java.util.ArrayList<>();
            setArgs(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getReturnType(){
        return returnType;
    }

    public void setReturnType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.returnType = value;
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
    
    public boolean getVarArgs(){
        return varArgs;
    }

    public void setVarArgs(boolean value){
        checkAllowChange();
        
        this.varArgs = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("args",getArgs());
       
          checkMandatory("returnType",getReturnType());
       
    }


    public FunctionTypeDef newInstance(){
      return new FunctionTypeDef();
    }

    @Override
    public FunctionTypeDef deepClone(){
       FunctionTypeDef ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(typeParams != null){
                  
                          java.util.List<io.nop.xlang.ast.TypeParameterNode> copy_typeParams = new java.util.ArrayList<>(typeParams.size());
                          for(io.nop.xlang.ast.TypeParameterNode item: typeParams){
                              copy_typeParams.add(item.deepClone());
                          }
                          ret.setTypeParams(copy_typeParams);
                      
                }
            
                if(args != null){
                  
                          java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> copy_args = new java.util.ArrayList<>(args.size());
                          for(io.nop.xlang.ast.FunctionArgTypeDef item: args){
                              copy_args.add(item.deepClone());
                          }
                          ret.setArgs(copy_args);
                      
                }
            
                ret.setVarArgs(varArgs);
            
                if(returnType != null){
                  
                          ret.setReturnType(returnType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    processor.accept(child);
                }
            }
            if(args != null){
               for(io.nop.xlang.ast.FunctionArgTypeDef child: args){
                    processor.accept(child);
                }
            }
            if(returnType != null)
                processor.accept(returnType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(args != null){
               for(io.nop.xlang.ast.FunctionArgTypeDef child: args){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(returnType != null && processor.apply(returnType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.replaceInList(this.typeParams,index,newChild);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.args != null){
               int index = this.args.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> list = this.replaceInList(this.args,index,newChild);
                   this.setArgs(list);
                   return true;
               }
            }
            if(this.returnType == oldChild){
               this.setReturnType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.removeInList(this.typeParams,index);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.args != null){
               int index = this.args.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FunctionArgTypeDef> list = this.removeInList(this.args,index);
                   this.setArgs(list);
                   return true;
               }
            }
            if(this.returnType == child){
                this.setReturnType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    FunctionTypeDef other = (FunctionTypeDef)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
            if(isListEquivalent(this.typeParams,other.getTypeParams())){
               return false;
            }
            if(isListEquivalent(this.args,other.getArgs())){
               return false;
            }
                if(!isValueEquivalent(this.varArgs,other.getVarArgs())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.returnType,other.getReturnType())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.FunctionTypeDef;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(typeParams != null){
                      
                              if(!typeParams.isEmpty())
                                json.put("typeParams", typeParams);
                          
                    }
                
                    if(args != null){
                      
                              if(!args.isEmpty())
                                json.put("args", args);
                          
                    }
                
                   json.put("varArgs", varArgs);
                
                    if(returnType != null){
                      
                              json.put("returnType", returnType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                typeParams = io.nop.api.core.util.FreezeHelper.freezeList(typeParams,cascade);         
                args = io.nop.api.core.util.FreezeHelper.freezeList(args,cascade);         
                if(returnType != null)
                    returnType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
