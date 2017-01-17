package i5.las2peer.services.deployService;

import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MultiHashtable;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelperTest {
    static Logger l = LoggerFactory.getLogger("");

    static DockerHelper dh;
    static String classHash = String.format("%4x", DeployServiceHelperTest.class.getName().hashCode() & 65535);

    @BeforeClass
    public static void setup() throws IOException {
        DockerHelper.removeAllContainers();
        dh = new DockerHelper(classHash, "fc00:"+classHash+"::/64");
    }

    private DatabaseManager getMock(int testNumber) {
        return new DatabaseManager(
                "sa"
                , ""
                , "jdbc:h2:mem:deployservicehelpertest_"+testNumber+";DB_CLOSE_DELAY=-1"
                , "testSchema"
                , "./etc/db_migration"
                , "./database"
        );
    }

    static Thread thUdp = null;
    private static void logUdp(String host, int port) throws IOException {
        if (thUdp == null) {
            thUdp = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        DatagramChannel dc = null;
                        l.info("logging udp on " + host + " " + port);
                        dc = DatagramChannel.open().bind(
                                new InetSocketAddress(InetAddress.getByName(host), port));
                        ByteBuffer bb = ByteBuffer.allocate(8192);
                        while (true) {
                            bb.clear();
                            dc.receive(bb);
                            bb.flip();
                            l.info(new String(bb.array(), 0, bb.remaining(), "utf8"));
                            System.out.println();
                        }
                    } catch (IOException e) {
                        l.error(e.toString());
                    }
                }
            });
            thUdp.start();
        }
    }

//    @Test
    public void getAllApps() {
        DeployServiceHelper dsh = new DeployServiceHelper(null, getMock(1));
        Response r = dsh.getAllApps();
        assertEquals(200, r.getStatus());
        assertEquals("[]", r.getEntity());
    }

//    @Test
    public void buildApp() {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(2));

        Map<String, Object> build_config = new HashMap<>();
            build_config.put("app", 457);
            build_config.put("version", "v1.2.3");
            build_config.put("base", "busybox");
            build_config.put("full", "echo hi");
        Response r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
        r = dsh.getAllApps();
        assertEquals(200, r.getStatus());
        assertEquals("[\"457\"]", r.getEntity());
    }

//    @Test
    public void deployApp() throws Exception {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(3));
        Map<String, Object> deploy_config = new HashMap<>();
            deploy_config.put("app", 456);
            deploy_config.put("version", "v1");
            deploy_config.put("base", "busybox");
            Map<String, Object> env = new HashMap<>();
                env.put("AA", "xx");
                env.put("BB_B", "xx x");
            deploy_config.put("env", env);
            // final                                   printf •••"hi•••\n••$AA•••\n••${BB_B}•••"
            // in container   nc -l -p 5000 -e sh -c •"printf •\•"hi•\•\n••$AA•\•\n••${BB_B}•\•"•"
            // docker run     nc -l -p 5000 -e sh -c •"printf •\•"hi•\•\n•\$AA•\•\n•\${BB_B}•\•"•"
            String command = "nc -l -p 5000 -e sh -c \"printf \\\"hi\\\\n\\$AA\\\\n\\${BB_B}\\\"\"";
            deploy_config.put("command", command);

        Response r;

        // deploy and test connection
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        String ip6 = new URI(r.getHeaderString("location")).getHost().replaceAll("[\\[\\]]","");
        assertTrue("Must be ip6: <"+ip6+">", ip6.matches("[0-9a-f:]{3,}+"));
        Socket s = new Socket(ip6, 5000);
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        assertEquals("hi", br.readLine());
        assertEquals("xx", br.readLine());
        assertEquals("xx x", br.readLine());
        r = dsh.getAllApps();
        assertEquals(200, r.getStatus());
        assertEquals("[\"456\"]", r.getEntity());

        // build and deploy from build
        Map<String, Object> build_config = new HashMap<>();
            build_config.put("app", 777);
            build_config.put("version", "v2.0");
            build_config.put("base", "busybox");
            build_config.put("full", "sh -c \"echo apple > ./somefile\"");
        r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
        deploy_config = new HashMap<>();
            deploy_config.put("app", 777);
            deploy_config.put("version", "v1.9");
            deploy_config.put("base", "build");
            deploy_config.put("command", "nc -l -p 5000 -e cat ./somefile");
        r = dsh.deployApp(deploy_config);
        assertEquals(404, r.getStatus());
        deploy_config.put("version", "v2.0");
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        ip6 = new URI(r.getHeaderString("location")).getHost().replaceAll("[\\[\\]]","");
        assertTrue("Must be ip6: <"+ip6+">", ip6.matches("[0-9a-f:]{3,}+"));
        s = new Socket(ip6, 5000);
        br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        assertEquals("apple", br.readLine());
    }

    // test is a sample and takes too long for including in regular test
    @Test
    public void samples() throws Exception {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(4));
        Response r;
        // this build requires active internet connection
        dh.setAddBridge(true);
        Map<String, Object> build_config = new HashMap<>();
            build_config.put("app", 101);
            build_config.put("version", "v0.1");
            build_config.put("base", "alpine");
            build_config.put("full", "{ apk add --no-cache git; git clone git://github.com/adabru/PLEASE-sample1 2>&1; pkill nc; }" +
                    " | nc -u fc00:"+classHash+"::1 9999; :");
        r = dsh.buildApp(build_config);
        dh.setAddBridge(false);
        assertEquals(200, r.getStatus());
        Map<String, Object> deploy_config = new HashMap<>();
            deploy_config.put("app", 101);
            deploy_config.put("version", "v0.1");
            deploy_config.put("base", "build");
            deploy_config.put("command", "cd PLEASE-sample1 && httpd -f");
        l.info("waiting for build...");
//        logUdp("fc00:"+classHash+"::1", 9999);
        assertTrue("Build must be successful!", dsh.waitForBuild(101, "v0.1"));
        l.info("build finished!");
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        String ip6 = new URI(r.getHeaderString("location")).getHost().replaceAll("[\\[\\]]","");
        assertTrue("Must be ip6: <"+ip6+">", ip6.matches("[0-9a-f:]{3,}+"));
        InputStream is = new URL("http://["+ip6+"]/index.html").openStream();
        String answer = new Scanner(is).useDelimiter("\\A").next();
        assertTrue("Answer is zero length!", answer.length() > 0);
        l.info("first sample returned: \n"+answer);
    }

//    @Test
//    public void testudp() throws IOException, InterruptedException {
//        logUdp("fc00:"+classHash+"::1", 9999);
//        Thread.sleep(300000);
//    }
}