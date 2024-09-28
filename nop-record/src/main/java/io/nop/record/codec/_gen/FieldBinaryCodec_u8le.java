
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_u8le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u8le INSTANCE = new FieldBinaryCodec_u8le();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readU8le();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset,
	                   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeU8le(0L);
		}else{
			output.writeU8le((Long)value);
		}	
    }
}
