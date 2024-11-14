package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.PacketCodecModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/packet-codec.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PacketCodecModel extends io.nop.record.model.RecordDefinitions {
    
    /**
     *  
     * xml name: decodeTypeDecider
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _decodeTypeDecider ;
    
    /**
     *  
     * xml name: encodeTypeDecider
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _encodeTypeDecider ;
    
    /**
     *  
     * xml name: initialBytesToStrip
     * 
     */
    private int _initialBytesToStrip  = 0;
    
    /**
     *  
     * xml name: lengthAdjustment
     * 
     */
    private int _lengthAdjustment  = 0;
    
    /**
     *  
     * xml name: lengthEndian
     * 
     */
    private io.nop.commons.bytes.EndianKind _lengthEndian ;
    
    /**
     *  
     * xml name: lengthFieldCodec
     * 
     */
    private java.lang.String _lengthFieldCodec ;
    
    /**
     *  
     * xml name: lengthFieldLength
     * 
     */
    private int _lengthFieldLength  = 0;
    
    /**
     *  
     * xml name: lengthFieldOffset
     * 
     */
    private int _lengthFieldOffset  = 0;
    
    /**
     *  
     * xml name: maxFrameLength
     * 
     */
    private int _maxFrameLength  = 0;
    
    /**
     * 
     * xml name: decodeTypeDecider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getDecodeTypeDecider(){
      return _decodeTypeDecider;
    }

    
    public void setDecodeTypeDecider(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._decodeTypeDecider = value;
           
    }

    
    /**
     * 
     * xml name: encodeTypeDecider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getEncodeTypeDecider(){
      return _encodeTypeDecider;
    }

    
    public void setEncodeTypeDecider(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._encodeTypeDecider = value;
           
    }

    
    /**
     * 
     * xml name: initialBytesToStrip
     *  
     */
    
    public int getInitialBytesToStrip(){
      return _initialBytesToStrip;
    }

    
    public void setInitialBytesToStrip(int value){
        checkAllowChange();
        
        this._initialBytesToStrip = value;
           
    }

    
    /**
     * 
     * xml name: lengthAdjustment
     *  
     */
    
    public int getLengthAdjustment(){
      return _lengthAdjustment;
    }

    
    public void setLengthAdjustment(int value){
        checkAllowChange();
        
        this._lengthAdjustment = value;
           
    }

    
    /**
     * 
     * xml name: lengthEndian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getLengthEndian(){
      return _lengthEndian;
    }

    
    public void setLengthEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._lengthEndian = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldCodec
     *  
     */
    
    public java.lang.String getLengthFieldCodec(){
      return _lengthFieldCodec;
    }

    
    public void setLengthFieldCodec(java.lang.String value){
        checkAllowChange();
        
        this._lengthFieldCodec = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldLength
     *  
     */
    
    public int getLengthFieldLength(){
      return _lengthFieldLength;
    }

    
    public void setLengthFieldLength(int value){
        checkAllowChange();
        
        this._lengthFieldLength = value;
           
    }

    
    /**
     * 
     * xml name: lengthFieldOffset
     *  
     */
    
    public int getLengthFieldOffset(){
      return _lengthFieldOffset;
    }

    
    public void setLengthFieldOffset(int value){
        checkAllowChange();
        
        this._lengthFieldOffset = value;
           
    }

    
    /**
     * 
     * xml name: maxFrameLength
     *  
     */
    
    public int getMaxFrameLength(){
      return _maxFrameLength;
    }

    
    public void setMaxFrameLength(int value){
        checkAllowChange();
        
        this._maxFrameLength = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("decodeTypeDecider",this.getDecodeTypeDecider());
        out.putNotNull("encodeTypeDecider",this.getEncodeTypeDecider());
        out.putNotNull("initialBytesToStrip",this.getInitialBytesToStrip());
        out.putNotNull("lengthAdjustment",this.getLengthAdjustment());
        out.putNotNull("lengthEndian",this.getLengthEndian());
        out.putNotNull("lengthFieldCodec",this.getLengthFieldCodec());
        out.putNotNull("lengthFieldLength",this.getLengthFieldLength());
        out.putNotNull("lengthFieldOffset",this.getLengthFieldOffset());
        out.putNotNull("maxFrameLength",this.getMaxFrameLength());
    }

    public PacketCodecModel cloneInstance(){
        PacketCodecModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PacketCodecModel instance){
        super.copyTo(instance);
        
        instance.setDecodeTypeDecider(this.getDecodeTypeDecider());
        instance.setEncodeTypeDecider(this.getEncodeTypeDecider());
        instance.setInitialBytesToStrip(this.getInitialBytesToStrip());
        instance.setLengthAdjustment(this.getLengthAdjustment());
        instance.setLengthEndian(this.getLengthEndian());
        instance.setLengthFieldCodec(this.getLengthFieldCodec());
        instance.setLengthFieldLength(this.getLengthFieldLength());
        instance.setLengthFieldOffset(this.getLengthFieldOffset());
        instance.setMaxFrameLength(this.getMaxFrameLength());
    }

    protected PacketCodecModel newInstance(){
        return (PacketCodecModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
