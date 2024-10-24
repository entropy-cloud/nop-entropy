
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;

public class FieldBinaryCodec_s4le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s4le INSTANCE = new FieldBinaryCodec_s4le();

    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context){
	  return input.readS4le();
    }

    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
					   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeS4le(0);
		}else{
			output.writeS4le((Integer)value);
		}	
    }
}
