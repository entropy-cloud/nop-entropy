
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_u8be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u8be INSTANCE = new FieldBinaryCodec_u8be();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readU8be();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context){
	    if(value == null){
		    output.writeU8be(0L);
		}else{
			output.writeU8be((Long)value);
		}	
    }
}
