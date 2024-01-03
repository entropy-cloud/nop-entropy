//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ArrowFunctionExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected java.util.List<io.nop.xlang.ast.ParameterDeclaration> params;
    
    protected io.nop.xlang.ast.NamedTypeNode returnType;
    

    public _ArrowFunctionExpression(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
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
       
          checkMandatory("params",getParams());
       
    }


    public ArrowFunctionExpression newInstance(){
      return new ArrowFunctionExpression();
    }

    @Override
    public ArrowFunctionExpression deepClone(){
       ArrowFunctionExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
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
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
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
    ArrowFunctionExpression other = (ArrowFunctionExpression)node;
    
            if(isListEquivalent(this.params,other.getParams())){
               return false;
            }
            if(!isNodeEquivalent(this.returnType,other.getReturnType())){
               return false;
            }
        
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ArrowFunctionExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(params != null){
                      
                              if(!params.isEmpty())
                                json.put("params", params);
                          
                    }
                
                    if(returnType != null){
                      
                              json.put("returnType", returnType);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                params = io.nop.api.core.util.FreezeHelper.freezeList(params,cascade);         
                if(returnType != null)
                    returnType.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
