package i5.las2peer.services.deployService;

import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MultiHashtable;
import jdk.nashorn.api.scripting.JSObject;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
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
    public static void setup() throws Exception {
        DockerHelper.removeAllContainers();
        dh = new DockerHelper(classHash, "fc00:"+classHash+"::");
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
                        InetSocketAddress isa = new InetSocketAddress(InetAddress.getByName(host), port);
                        System.out.println(isa);
                        dc = DatagramChannel.open();
                        while (dc.getLocalAddress() == null)
                            try { dc.bind(isa); }
                            catch(BindException e) { }
                        l.info("logging udp on " + host + " " + port);
                        ByteBuffer bb = ByteBuffer.allocate(8192);
                        while (true) {
                            bb.clear();
                            dc.receive(bb);
                            bb.flip();
                            l.info(new String(bb.array(), 0, bb.remaining(), "utf8"));
                        }
                    } catch (IOException e) {
                        l.error(e.toString());
                    }
                }
            });
            thUdp.start();
        }
    }
    public static JsonStructure toJson(String s) {
        JsonReader jr = Json.createReader(new StringReader(s));
        JsonStructure js = jr.read();
        jr.close();

        return js;
    }
    private String request(String ip6, int port) throws IOException {
        Socket sock = new Socket(ip6, port);
        Scanner s = new Scanner(sock.getInputStream()).useDelimiter("\\A");
        String res = s.hasNext() ? s.next() : "";
        return res;
    }

    @Test
    public void buildApp() {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(2));

        Map<String, Object> build_config = new HashMap<>();
            build_config.put("app", 457);
            build_config.put("version", "v1");
        Response r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
        r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
            build_config.put("version", "v2");
        r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
            build_config.put("app", 458);
            build_config.put("version", "v1");
        r = dsh.buildApp(build_config);
        assertEquals(200, r.getStatus());
        r = dsh.getBuild(null, null, null);
        assertEquals(200, r.getStatus());
        assertEquals(toJson("{\"457\":{\"v1\":[0,1],\"v2\":[0]},\"458\":{\"v1\":[0]}}")
            , toJson(r.getEntity().toString()));
        r = dsh.getBuild(457, null, null);
        assertEquals(200, r.getStatus());
        assertEquals(toJson("{\"457\":{\"v1\":[0,1],\"v2\":[0]}}")
            , toJson(r.getEntity().toString()));
        r = dsh.getBuild(457, "v1", null);
        assertEquals(200, r.getStatus());
        assertEquals(toJson("{\"457\":{\"v1\":[0,1]}}")
            , toJson(r.getEntity().toString()));
    }

    @Test
    public void deployApp() throws Exception {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(3));
        Map<String, Object> deploy_config;
        Map<String, Object> build_config;
        Response r;
        JsonObject jo;

        // build, deploy and test connection
        build_config = new HashMap<>();
            build_config.put("app", 456);
            build_config.put("version", "v1");
        assertEquals(200, dsh.buildApp(build_config).getStatus());

        deploy_config = new HashMap<>();
            deploy_config.put("app", 456);
            deploy_config.put("version", "v1");
            deploy_config.put("base", "busybox");
                Map<String, Object> env = new HashMap<>();
                env.put("AA", "xx");
                env.put("BB_B", "xx x");
            deploy_config.put("env", env);
            // final                                   printf •••"hi;••$AA;••${BB_B}•••"
            // in container   nc -l -p 5000 -e sh -c •"printf •\•"hi;••$AA;••${BB_B}•\•"•"
            // docker run     nc -l -p 5000 -e sh -c •"printf •\•"hi;•\$AA;•\${BB_B}•\•"•"
            String command = "nc -l -p 5000 -e sh -c \"printf \\\"hi;\\$AA;\\${BB_B}\\\"\"";
            deploy_config.put("command", command);
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        jo = (JsonObject) toJson(r.getEntity().toString());
        assertTrue("Response must include iid", jo.getInt("iid",-1) != -1);
        assertTrue("Must be ip6: <"+jo.getString("ip6")+">", jo.getString("ip6").matches("[0-9a-f:]{3,}+"));
        assertEquals("hi;xx;xx x", request(jo.getString("ip6"), 5000));
        r = dsh.getDeployments(null);
        assertEquals(200, r.getStatus());
        assertTrue("must match {\"456\":\\[[0-9]*\\]}"
            , toJson(r.getEntity().toString()).toString().matches("^\\{\"456\":\\[[0-9]*\\]\\}"));

        // build and deploy from build
        build_config = new HashMap<>();
            build_config.put("app", 777);
            build_config.put("version", "v2.0");
            build_config.put("base", "busybox");
            build_config.put("full", "sh -c \"printf apple > ./somefile\"");
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
        jo = (JsonObject) toJson(r.getEntity().toString());
        assertEquals("apple", request(jo.getString("ip6"), 5000));
    }

    @Test
    public void updateApp() throws Exception {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(4));
        Response r;
        Map<String, Object> deploy_config;
        Map<String, Object> build_config;

        // build two versions
        build_config = new HashMap<>();
            build_config.put("app", 4);
            build_config.put("version", "v1");
            build_config.put("full", "printf 111 > somefile");
        assertEquals(200, dsh.buildApp(build_config).getStatus());
        build_config = new HashMap<>();
            build_config.put("app", 4);
            build_config.put("version", "v2");
            build_config.put("full", "printf 222 > somefile");
        assertEquals(200, dsh.buildApp(build_config).getStatus());

        // deploy first version
        deploy_config = new HashMap<>();
            deploy_config.put("app", 4);
            deploy_config.put("version", "v1");
            deploy_config.put("base", "build");
            deploy_config.put("command", "nc -ll -p 5000 -e cat somefile");
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        JsonObject jo = (JsonObject) toJson(r.getEntity().toString());
        assertEquals("111", request(jo.getString("ip6"), 5000));

        // update to second version
            deploy_config.put("version", "v2");
        r = dsh.updateApp(jo.getInt("iid"), deploy_config);
        assertEquals(200, r.getStatus());
        assertEquals("222", request(jo.getString("ip6"), 5000));
    }

    @Test
    public void undeploy() {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(5));
        Response r;

        Map<String, Object> build_config = new HashMap<>();
            build_config.put("app", 457);
            build_config.put("version", "v1");
        assertEquals(200, dsh.buildApp(build_config).getStatus());
            build_config.put("version", "v2");
        assertEquals(200, dsh.buildApp(build_config).getStatus());
        Map<String, Object> deploy_config = new HashMap<>();
            deploy_config.put("app", 457);
            deploy_config.put("version", "v1");
            deploy_config.put("command", "sleep 10m");
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        int iid1 = ((JsonObject)toJson(r.getEntity().toString())).getInt("iid");
        r = dsh.deployApp(deploy_config);
        assertEquals(201, r.getStatus());
        int iid2 = ((JsonObject)toJson(r.getEntity().toString())).getInt("iid");
        assertNotEquals(iid1, iid2);
            deploy_config.put("version", "v2");
        assertEquals(200, dsh.updateApp(iid1, deploy_config).getStatus());
        assertEquals(200, dsh.undeploy(iid1).getStatus());
        r = dsh.getDeployments(null);
        assertEquals(200, r.getStatus());
        JsonObject deployments = (JsonObject) toJson(r.getEntity().toString());
        assertTrue(((JsonArray)deployments.get("457")).size() == 1);
        assertTrue(((JsonArray)deployments.get("457")).getInt(0) == iid2);
    }
}