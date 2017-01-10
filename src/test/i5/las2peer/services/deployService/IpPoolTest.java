package i5.las2peer.services.deployService;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by adabru on 09.01.17.
 */
public class IpPoolTest {

    @Test
    public void all() {
        IpPool ip = new IpPool("2001::1:103/126");
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0103/126", ip.getNet());
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0100", ip.allocIp());
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0101", ip.allocIp());
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0102", ip.allocIp());
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0103", ip.allocIp());
        try {
            String s = ip.allocIp();
            fail("ip addresses should be exhausted, but returned "+s);
        } catch (IllegalStateException e) { } // Ok
        assertEquals(true, ip.freeIp("2001::1:101"));
        assertEquals(false, ip.freeIp("2001::1:101"));
        assertEquals("2001:0000:0000:0000:0000:0000:0001:0101", ip.allocIp());

        ip = new IpPool("::1/127");
        assertEquals("0000:0000:0000:0000:0000:0000:0000:0000", ip.allocIp());
    }
}