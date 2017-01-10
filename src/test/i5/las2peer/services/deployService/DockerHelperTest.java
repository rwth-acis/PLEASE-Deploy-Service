package i5.las2peer.services.deployService;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by adabru on 27.12.16.
 */
public class DockerHelperTest {
    private static DockerHelper dh;

    static String classHash = String.format("%4x", DockerHelperTest.class.getName().hashCode() & 65535);

    @BeforeClass
    public static void setup() throws IOException {
        DockerHelper.removeAllContainers();
        dh = new DockerHelper(classHash, "fc00:"+classHash+"::/32");
    }

    private Map<String, Object> getConfig(int cpu, int memory, int disk, String base, String command) {
        Map<String, Object> config = new HashMap<>();
        config.put("cpu", cpu);
        config.put("memory", memory);
        config.put("disk", disk);
        config.put("base", base);
        config.put("command", command);
        return config;
    }

    @Test
    public void startContainer() throws Exception {
        Map<String, Object> config = getConfig(512, 100_000_000, 220_000_000, "busybox", "echo \"hello world!\"");
        String cid = dh.startContainer(config);
        assertTrue("<"+cid+"> is not valid container id", cid.matches("[0-9a-f]{15,}+"));
    }

    @Test
    public void updateAndRollbackContainer() throws Exception {
        String[] output;
        Map<String, Object> config = getConfig(512, 100_000_000, 220_000_000, "busybox", null);

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

        // rollback
        dh.rollbackContainer(cid2, cid1);
        assertEquals("first apple",
                dh.executeProcess("nc "+ip6+" 4444").trim());
        assertEquals(ip6, dh.getIp(cid1));
    }
}