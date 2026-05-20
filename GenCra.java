import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import org.cloudburstmc.netty.util.RakUtils;

public class GenCra {
    public static void main(String[] args) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0x10);
        
        // 1. clientAddress
        RakUtils.writeAddress(buf, new InetSocketAddress("127.0.0.1", 54321));
        
        // 2. systemIndex
        buf.writeShort(0);
        
        // 3. systemAddresses (10 of them)
        for (int i = 0; i < 10; i++) {
            RakUtils.writeAddress(buf, new InetSocketAddress("127.0.0.1", 19132));
        }
        
        // 4. clientTimestamp
        buf.writeLong(12345678L);
        
        // 5. serverTimestamp
        buf.writeLong(87654321L);
        
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        System.out.println("CRA hex (len=" + bytes.length + "):");
        System.out.println(bytesToHex(bytes));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
