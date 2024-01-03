//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _FunctionDeclaration extends io.nop.xlang.ast.DecoratedDeclaration {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected int modifiers;
    
    protected io.nop.xlang.ast.Identifier name;
    
    protected java.util.List<io.nop.xlang.ast.ParameterDeclaration> params;
    
    protected boolean resultOptional;
    
    protected io.nop.xlang.ast.NamedTypeNode returnType;
    

    public _FunctionDeclaration(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public int getModifiers(){
        return modifiers;
    }

    public void setModifiers(int value){
        checkAllowChange();
        
        this.modifiers = value;
    }
    
    public io.nop.xlang.ast.Identifier getName(){
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.name = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ParameterDeclaration> getParams(){
        return params;
    }

    public void setParams(java.util.List<io.nop.xlang.ast.ParameterDeclaration> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.params = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ParameterDeclaration> makeParams(){
        java.util.List<io.nop.xlang.ast.ParameterDeclaration> list = getParams();
        if(list == null){
            list = new java.util.ArrayList<>();
            setParams(list);
        }
        return list;
    }
    
    public boolean getResultOptional(){
        return resultOptional;
    }

    public void setResultOptional(boolean value){
        checkAllowChange();
        
        this.resultOptional = value;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getReturnType(){
        return returnType;
    }

    public void setReturnType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.returnType = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
          checkMandatory("name",getName());
       
          checkMandatory("params",getParams());
       
    }


    public FunctionDeclaration newInstance(){
      return new FunctionDeclaration();
    }

    @Override
    public FunctionDeclaration deepClone(){
       FunctionDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(decorators != null){
                  
                          ret.setDecorators(decorators.deepClone());
                      
                }
            
                if(name != null){
                  
                          ret.setName(name.deepClone());
                      
                }
            
                if(params != null){
                  
                          java.util.List<io.nop.xlang.ast.ParameterDeclaration> copy_params = new java.util.ArrayList<>(params.size());
                          for(io.nop.xlang.ast.ParameterDeclaration item: params){
                              copy_params.add(item.deepClone());
                          }
                          ret.setParams(copy_params);
                      
                }
            
                if(returnType != null){
                  
                          ret.setReturnType(returnType.deepClone());
                      
                }
            
                ret.setResultOptional(resultOptional);
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
                ret.setModifiers(modifiers);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(decorators != null)
                processor.accept(decorators);
        
            if(name != null)
                processor.accept(name);
        
            if(params != null){
               for(io.nop.xlang.ast.ParameterDeclaration child: params){
                    processor.accept(child);
                }
            }
            if(returnType != null)
                processor.accept(returnType);
        
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(decorators != null && processor.apply(decorators) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(name != null && processor.apply(name) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(params != null){
               for(io.nop.xlang.ast.ParameterDeclaration child: params){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(returnType != null && processor.apply(returnType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.decorators == oldChild){
               this.setDecorators((io.nop.xlang.ast.Decorators)newChild);
               return true;
            }
        
            if(this.name == oldChild){
               this.setName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.params != null){
               int index = this.params.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ParameterDeclaration> list = this.replaceInList(this.params,index,newChild);
                   this.setParams(list);
                   return true;
               }
            }
            if(this.returnType == oldChild){
               this.setReturnType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.decorators == child){
                this.setDecorators(null);
                return true;
            }
        
            if(this.name == child){
                this.setName(null);
                return true;
            }
        
            if(this.params != null){
               int index = this.params.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ParameterDeclaration> list = this.removeInList(this.params,index);
                   this.setParams(list);
                   return true;
               }
            }
            if(this.returnType == child){
                this.setReturnType(null);
                return true;
            }
        
            if(this.body == child){
                this.setBody(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    FunctionDeclaration other = (FunctionDeclaration)node;
    
            if(!isNodeEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
        
            if(!isNodeEquivalent(this.name,other.getName())){
               return false;
            }
        
            if(isListEquivalent(this.params,other.getParams())){
               return false;
            }
            if(!isNodeEquivalent(this.returnType,other.getReturnType())){
               return false;
            }
        
                if(!isValueEquivalent(this.resultOptional,other.getResultOptional())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
                if(!isValueEquivalent(this.modifiers,other.getModifiers())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.FunctionDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              json.put("decorators", decorators);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(params != null){
                      
                              if(!params.isEmpty())
                                json.put("params", params);
                          
                    }
                
                    if(returnType != null){
                      
                              json.put("returnType", returnType);
                          
                    }
                
                   json.put("resultOptional", resultOptional);
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
                   json.put("modifiers", modifiers);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(decorators != null)
                    decorators.freeze(cascade);
                if(name != null)
                    name.freeze(cascade);
                params = io.nop.api.core.util.FreezeHelper.freezeList(params,cascade);         
                if(returnType != null)
                    returnType.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
