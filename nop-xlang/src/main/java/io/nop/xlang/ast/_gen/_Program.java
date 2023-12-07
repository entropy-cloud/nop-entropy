//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _Program extends io.nop.xlang.ast.Expression {
    
    protected java.util.List<io.nop.xlang.ast.XLangASTNode> body;
    
    protected java.lang.String sourceType;
    

    public _Program(){
    }

    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> getBody(){
        return body;
    }

    public void setBody(java.util.List<io.nop.xlang.ast.XLangASTNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.body = value;
    }
    
    public java.util.List<io.nop.xlang.ast.XLangASTNode> makeBody(){
        java.util.List<io.nop.xlang.ast.XLangASTNode> list = getBody();
        if(list == null){
            list = new java.util.ArrayList<>();
            setBody(list);
        }
        return list;
    }
    
    public java.lang.String getSourceType(){
        return sourceType;
    }

    public void setSourceType(java.lang.String value){
        checkAllowChange();
        
        this.sourceType = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
    }


    public Program newInstance(){
      return new Program();
    }

    @Override
    public Program deepClone(){
       Program ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(sourceType != null){
                  
                          ret.setSourceType(sourceType);
                      
                }
            
                if(body != null){
                  
                          java.util.List<io.nop.xlang.ast.XLangASTNode> copy_body = new java.util.ArrayList<>(body.size());
                          for(io.nop.xlang.ast.XLangASTNode item: body){
                              copy_body.add(item.deepClone());
                          }
                          ret.setBody(copy_body);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(body != null){
               for(io.nop.xlang.ast.XLangASTNode child: body){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(body != null){
               for(io.nop.xlang.ast.XLangASTNode child: body){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.body != null){
               int index = this.body.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.replaceInList(this.body,index,newChild);
                   this.setBody(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.body != null){
               int index = this.body.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.XLangASTNode> list = this.removeInList(this.body,index);
                   this.setBody(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    Program other = (Program)node;
    
                if(!isValueEquivalent(this.sourceType,other.getSourceType())){
                   return false;
                }
            
            if(isListEquivalent(this.body,other.getBody())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.Program;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(sourceType != null){
                      
                              json.put("sourceType", sourceType);
                          
                    }
                
                    if(body != null){
                      
                              if(!body.isEmpty())
                                json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                body = io.nop.api.core.util.FreezeHelper.freezeList(body,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
