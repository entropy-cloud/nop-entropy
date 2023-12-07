//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.FunctionArgTypeDef;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _FunctionArgTypeDef extends io.nop.xlang.ast.StructuredTypeDef {
    
    protected io.nop.xlang.ast.Identifier argName;
    
    protected io.nop.xlang.ast.NamedTypeNode argType;
    
    protected boolean optional;
    

    public _FunctionArgTypeDef(){
    }

    
    public io.nop.xlang.ast.Identifier getArgName(){
        return argName;
    }

    public void setArgName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.argName = value;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getArgType(){
        return argType;
    }

    public void setArgType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.argType = value;
    }
    
    public boolean getOptional(){
        return optional;
    }

    public void setOptional(boolean value){
        checkAllowChange();
        
        this.optional = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("argName",getArgName());
       
          checkMandatory("argType",getArgType());
       
    }


    public FunctionArgTypeDef newInstance(){
      return new FunctionArgTypeDef();
    }

    @Override
    public FunctionArgTypeDef deepClone(){
       FunctionArgTypeDef ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(argName != null){
                  
                          ret.setArgName(argName.deepClone());
                      
                }
            
                if(argType != null){
                  
                          ret.setArgType(argType.deepClone());
                      
                }
            
                ret.setOptional(optional);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(argName != null)
                processor.accept(argName);
        
            if(argType != null)
                processor.accept(argType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(argName != null && processor.apply(argName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(argType != null && processor.apply(argType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.argName == oldChild){
               this.setArgName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.argType == oldChild){
               this.setArgType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.argName == child){
                this.setArgName(null);
                return true;
            }
        
            if(this.argType == child){
                this.setArgType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    FunctionArgTypeDef other = (FunctionArgTypeDef)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.argName,other.getArgName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.argType,other.getArgType())){
               return false;
            }
        
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.FunctionArgTypeDef;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(argName != null){
                      
                              json.put("argName", argName);
                          
                    }
                
                    if(argType != null){
                      
                              json.put("argType", argType);
                          
                    }
                
                   json.put("optional", optional);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(argName != null)
                    argName.freeze(cascade);
                if(argType != null)
                    argType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
