package i5.las2peer.services.deployService;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adabru on 09.01.17.
 */
public class IpPool {
    // 96 net
    String net;
    // 32 interface id
    private Set<Integer> allocated;

    public IpPool(String net_96) throws UnknownHostException {
        byte[] b = InetAddress.getByName(net_96).getAddress();
        b[12] = b[13] = b[14] = b[15] = 0;
        net = InetAddress.getByAddress(b).getCanonicalHostName();
        allocated = new HashSet<>();
    }

    public String getNet() throws UnknownHostException { return net; }

    public String allocIp() throws UnknownHostException {
        for (long i=0; i < (long)1<<32; i++) {
            if (!allocated.contains((int)i)) {
                allocated.add((int) i);
                byte[] b = InetAddress.getByName(net).getAddress();
                b[12]=(byte)(i>>24);b[13]=(byte)(i>>16);b[14]=(byte)(i>>8);b[15]=(byte)i;
                return InetAddress.getByAddress(b).getCanonicalHostName();
            }
        }
        throw new IllegalStateException("ip addresses exhausted");
    }
    public boolean freeIp(String ip6) throws UnknownHostException {
        byte[] b = InetAddress.getByName(ip6).getAddress();
        int i = ((b[12]&0xff)<<24)|((b[13]&0xff)<<16)|((b[14]&0xff)<<8)|((b[15]&0xff)<<0);
        return allocated.remove(i);
    }
}
