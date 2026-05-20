import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import org.cloudburstmc.netty.util.RakUtils;

public class TestRak {
    public static void main(String[] args) throws Exception {
        // Hex string from Geyser packet: 100004ff9d30e9e51ff0c242...
        // Let's test parsing 0004ff9d30e9e51f using readAddress
        String hex = "0004ff9d30e9e51f";
        ByteBuf buf = Unpooled.wrappedBuffer(hexToBytes(hex));
        try {
            InetSocketAddress addr = RakUtils.readAddress(buf);
            System.out.println("Parsed address: " + addr);
        } catch (Exception e) {
            System.out.println("Failed to parse: " + e.toString());
        }

        // What about 04ff9d30e9e51f? (Without the leading 00)
        String hex2 = "04ff9d30e9e51f";
        ByteBuf buf2 = Unpooled.wrappedBuffer(hexToBytes(hex2));
        try {
            InetSocketAddress addr = RakUtils.readAddress(buf2);
            System.out.println("Parsed address 2: " + addr);
        } catch (Exception e) {
            System.out.println("Failed to parse 2: " + e.toString());
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
