
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_s2le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s2le INSTANCE = new FieldBinaryCodec_s2le();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readS2le();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset,
	                   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeS2le((short) 0);
		}else{
			output.writeS2le((Short)value);
		}	
    }
}
