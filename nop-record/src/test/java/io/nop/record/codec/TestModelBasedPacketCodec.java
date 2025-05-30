package io.nop.record.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.record.codec.impl.ModelBasedPacketCodec;
import io.nop.record.model.PacketCodecModel;
import io.nop.record.netty.ByteBufBinaryDataReader;
import io.nop.record.netty.ByteBufBinaryDataWriter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class TestModelBasedPacketCodec extends JunitBaseTestCase {
    @Test
    public void testByteBuf() {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        buf.writeLong(333);
        long value = buf.readLong();
        assertEquals(333L, value);
    }

    @Test
    public void testByteBufInputOutput() throws IOException {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        ByteBufBinaryDataReader reader = new ByteBufBinaryDataReader(buf);
        ByteBufBinaryDataWriter writer = new ByteBufBinaryDataWriter(buf);

        writer.writeU1(1);
        assertEquals(1, reader.readU1());

        writer.writeU2be(2);
        assertEquals(2, reader.readU2be());

        writer.writeU2le(3);
        assertEquals(3, reader.readU2le());

        writer.writeU4be(4);
        assertEquals(4, reader.readU4be());

        writer.writeU4le(5);
        assertEquals(5, reader.readU4le());

        writer.writeU8be(6);
        assertEquals(6, reader.readU8be());

        writer.writeU8le(7);
        assertEquals(7, reader.readU8le());

        writer.writeS1((byte) 8);
        assertEquals(8, reader.readS1());

        writer.writeS2be((short) 9);
        assertEquals(9, reader.readS2be());

        writer.writeS2le((short) 10);
        assertEquals(10, reader.readS2le());

        writer.writeS4be(11);
        assertEquals(11, reader.readS4be());

        writer.writeS4le(12);
        assertEquals(12, reader.readS4le());

        writer.writeS8be(13);
        assertEquals(13, reader.readS8be());

        writer.writeS8le(14);
        assertEquals(14, reader.readS8le());
    }

    @Test
    public void testCodec() {
        PacketCodecModel codecModel = (PacketCodecModel) ResourceComponentManager.instance().loadComponentModel("/test/record/test.packet-codec.xml");
        ModelBasedPacketCodec codec = new ModelBasedPacketCodec(codecModel, FieldCodecRegistry.DEFAULT);
        Map<String, Object> map = new HashMap<>();
        map.put("id", 333L);
        map.put("name", "abc");

        byte[] bytes = codec.encodeToBytes(map);
        Map<String, Object> map2 = (Map<String, Object>) codec.decodeFromBytes(bytes);
        assertEquals(333L, map2.get("id"));
        assertEquals("abc", map2.get("name"));
    }
}
