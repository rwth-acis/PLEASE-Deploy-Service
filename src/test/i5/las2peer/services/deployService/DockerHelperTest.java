package i5.las2peer.services.deployService;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by adabru on 27.12.16.
 */
public class DockerHelperTest {
    private static Logger l;

    @BeforeClass
    public static void setup() throws IOException {
        new DockerHelper().removeAllContainers();
        l = MyLogger.getLogger();
    }

    @Test
    public void startContainer() throws Exception {
        DockerHelper dh = new DockerHelper();
        Map<String, Object> config = new HashMap<>();
        config.put("cpu", 512);
        config.put("memory", (int)100e6);
        config.put("disk", (int)220e6);
        config.put("base", "busybox");
        config.put("command", "echo \"hello world!\"");
        String cid = dh.startContainer(config);
        assertTrue("<"+cid+"> is not valid container id", cid.matches("[0-9a-f]{15,}+"));
    }

    @Test
    public void updateContainer() throws Exception {
        DockerHelper dh = new DockerHelper();
        String[] output;
        Map<String, Object> config = new HashMap<>();
        config.put("cpu", 512);
        config.put("memory", (int)100e6);
        config.put("disk", (int)220e6);
        config.put("base", "busybox");

        // start first
        config.put("command", "sh -c \"while(true); do nc -l -p 4444 -e printf 'first apple'; done\"");
        String cid1 = dh.startContainer(config);
        assertTrue("<"+cid1+"> is not valid container id", cid1.matches("[0-9a-f]{15,}+"));
        String ip6 = dh.getIp(cid1);
        assertTrue("<"+ip6+"> is not valid ip6 address", ip6.matches("[0-9a-f:]{3,}+"));
        assertEquals("first apple",
                dh.executeProcess("nc "+ip6+" 4444").trim());

        // start second
        config.put("command", "sh -c \"while(true); do nc -l -p 4444 -e printf 'second banana'; done\"");
        String cid2 = dh.startContainer(config);
        assertTrue("<"+cid2+"> is not valid container id", cid2.matches("[0-9a-f]{15,}+"));

        // update
        dh.updateContainer(cid1, cid2);
        assertEquals("second banana",
                dh.executeProcess("nc "+ip6+" 4444").trim());
        assertEquals(ip6, dh.getIp(cid2));
    }

//    @Test
//    public void rollbackContainer() throws Exception {
//
//    }
}