package i5.las2peer.services.deployService;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adabru on 09.01.17.
 */
public class IpPool {
    private byte[] address;
    private int prefix;
    private Set<BigInteger> allocated;

    public IpPool(String net) {
        Pattern pattern = Pattern.compile("([0-9a-f:]+)/([0-9]+)");
        Matcher matcher = pattern.matcher(net);
        if (!matcher.find()) throw new IllegalArgumentException("net must be [address]/[prefix]");
        address = parseIp6(matcher.group(1));
        prefix = Integer.parseInt(matcher.group(2));
        allocated = new HashSet<>();
    }

    public String getNet() {
        return formatIp6(address) + "/" + prefix;
    }

    public String allocIp() {
        BigInteger start = new BigInteger(address).and(BigInteger.valueOf(-1).shiftLeft(128 - prefix));
        BigInteger end = new BigInteger(address).or(BigInteger.valueOf(-1).shiftLeft(128 - prefix).not());
        BigInteger i = start;
        while (i.compareTo(end) <= 0) {
            if (!allocated.contains(i)) {
                allocated.add(i);
                return formatIp6(i);
            } else {
                i = i.add(BigInteger.ONE);
            }
        }
        throw new IllegalStateException("ip addresses exhausted");
    }
    public boolean freeIp(String ip6) {
        return allocated.remove(new BigInteger(parseIp6(ip6)));
    }

    public static String formatIp6(BigInteger b) {
        byte[] t1 = b.toByteArray();
        byte[] t2 = new byte[16];
        System.arraycopy(t1, 0, t2, 16-t1.length, t1.length);
        return formatIp6(t2);
    }
    public static String formatIp6(byte[] b) {
        return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x",
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
                b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]);
    }
    public static byte[] parseIp6(String ip6) {
        if (ip6.startsWith("::")) ip6 = "0"+ip6;
        String[] arr = ip6.split(":");
        byte[] blocks = new byte[16];
        for (int b=0,a=0; a<arr.length; a++) {
            if (arr[a].equals("")) {
                int null_block_count = 8 - (arr.length-1);
                while(null_block_count-- > 0) {
                    blocks[b++] = 0;
                    blocks[b++] = 0;
                }
            } else {
                int two_bytes = Integer.decode("0x"+arr[a]);
                blocks[b++] = (byte)((two_bytes & 0xff00) >> 8);
                blocks[b++] = (byte)(two_bytes & 0x00ff);
            }
        }
        return blocks;
    }
}
