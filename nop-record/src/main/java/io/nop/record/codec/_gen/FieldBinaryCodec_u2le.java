
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;

public class FieldBinaryCodec_u2le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u2le INSTANCE = new FieldBinaryCodec_u2le();

    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context){
	  return input.readU2le();
    }

    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
					   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeU2le(0);
		}else{
			output.writeU2le((Integer)value);
		}	
    }
}
