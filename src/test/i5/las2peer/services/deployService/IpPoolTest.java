package i5.las2peer.services.deployService;

import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * Created by adabru on 09.01.17.
 */
public class IpPoolTest {

    @Test
    public void all() throws UnknownHostException {
        IpPool ip = new IpPool("2001::1:0:103");
        assertEquals("2001:0:0:0:0:1:0:0", ip.getNet());
        assertEquals("2001:0:0:0:0:1:0:0", ip.allocIp());
        assertEquals("2001:0:0:0:0:1:0:1", ip.allocIp());
        assertEquals("2001:0:0:0:0:1:0:2", ip.allocIp());
        assertEquals(true, ip.freeIp("2001::1:0:1"));
        assertEquals(false, ip.freeIp("2001::1:0:1"));
        assertEquals("2001:0:0:0:0:1:0:1", ip.allocIp());

        ip = new IpPool("::1");
        assertEquals("0:0:0:0:0:0:0:0", ip.allocIp());

        long start = new Date().getTime();
        ip = new IpPool("a::");
        for (int i=0; i<258; i++) {
            ip.allocIp();
        }
        assertEquals(true, ip.freeIp("a::"));
        assertEquals(true, ip.freeIp("a::1"));
        assertEquals(true, ip.freeIp("a::ff"));
        assertEquals(true, ip.freeIp("a::100"));
        assertEquals(true, ip.freeIp("a::101"));
        assertEquals(false, ip.freeIp("a::102"));
        assertEquals(false, ip.freeIp("a::"));
        assertEquals(false, ip.freeIp("a::1"));
        assertEquals(false, ip.freeIp("a::ff"));
        assertEquals(false, ip.freeIp("a::100"));
        assertEquals(false, ip.freeIp("a::101"));
    }
}