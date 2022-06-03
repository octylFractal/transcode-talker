package net.octyl.transcodetalker.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minecraft protocol encoding/decoding. VarInt functions taken from <a
 * href="https://github.com/PaperMC/Velocity">Velocity</a>, adapted to use the data interfaces.
 */
public final class MinecraftProtocol {
    public static String readString(DataInput buf) throws IOException {
        int length = readVarInt(buf);
        if (length < 0) {
            throw new IOException("Negative string length: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeString(DataOutput buf, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.write(bytes);
    }

    public static int readVarInt(DataInput buf) throws IOException {
        int i = 0;
        int maxRead = 5;
        for (int j = 0; j < maxRead; j++) {
            int k = buf.readByte() & 0xFF;
            i |= (k & 0x7F) << j * 7;
            if ((k & 0x80) != 128) {
                return i;
            }
        }
        throw new IllegalStateException("VarInt too big");
    }

    public static void writeVarInt(DataOutput buf, int value) throws IOException {
        // Peel the one and two byte count cases explicitly as they are the most common VarInt sizes
        // that the proxy will write, to improve inlining.
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else {
            writeVarIntFull(buf, value);
        }
    }

    private static void writeVarIntFull(DataOutput buf, int value) throws IOException {
        // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            buf.writeByte(w >>> 16);
            buf.writeShort(w);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
            buf.writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            buf.writeInt(w);
            buf.writeByte(value >>> 28);
        }
    }

    private MinecraftProtocol() {
    }
}
