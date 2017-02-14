package i5.las2peer.services.deployService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MultiHashtable;
import jdk.nashorn.api.scripting.JSObject;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.httpserver.HttpServerImpl;

import javax.json.*;
import javax.ws.rs.client.Entity;
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
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelperTest {
    static Logger l = LoggerFactory.getLogger("");

    static DockerHelper dh;
    static BuildhookVerifier bv;
    static String classHash = String.format("%4x", DeployServiceHelperTest.class.getName().hashCode() & 65535);
    static String userId = "someUser";
    static String sinkUrl = "http://localhost:6800";

    @BeforeClass
    public static void setup() throws Exception {
        DockerHelper.removeAllContainers();
        dh = new DockerHelper(classHash, "fc00:"+classHash+"::");
        URI uri = new URI(sinkUrl);
        HttpServer.create(new InetSocketAddress(uri.getHost(), uri.getPort()), 0).start(); // sink
    }


    static int count = 0;
    private DeployServiceHelper getMock(String buildhookUrl) {
        DatabaseManager dm = new DatabaseManager("sa", "",
            "jdbc:h2:mem:deployservicehelpertest_"+count+++";DB_CLOSE_DELAY=-1", "testSchema",
            "./etc/db_migration", "./database");
        ResourceDistribution rd = new ResourceDistribution(dm, "1g", "1g", "1000");
        return new DeployServiceHelper(dh, dm, rd, buildhookUrl);
    }
    private JsonStructure json(Object s) { return JsonHelper.parse(((String)s).replaceAll("'","\"")); }
    private Map<String,Object> map(String s) { return (Map) JsonHelper.toCollection(json(s)); }
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
    private String request(String ip6, int port) throws IOException {
        Socket sock = new Socket(ip6, port);
        Scanner s = new Scanner(sock.getInputStream()).useDelimiter("\\A");
        String res = s.hasNext() ? s.next() : "";
        return res;
    }

    @Test
    public void buildApp() {
        DeployServiceHelper dsh = getMock(sinkUrl);
        long bId0, bId1, bId2, bId3;
        Response r;

        r = dsh.buildApp(map("{'app':457,'version':'v1'}"));
        assertEquals(200, r.getStatus());
        bId0 = ((JsonObject)json(r.getEntity())).getJsonNumber("buildid").longValue();
        assertTrue("<"+bId0+"> must be valid timestamp", bId0 > Integer.MAX_VALUE);
        r = dsh.buildApp(map("{'app':457,'version':'v1'}"));
        assertEquals(200, r.getStatus());
        bId1 = ((JsonObject)json(r.getEntity())).getJsonNumber("buildid").longValue();
        assertTrue("<"+bId1+"> must be valid timestamp", bId1 > bId0);
        r = dsh.buildApp(map("{'app':457,'version':'v2'}"));
        assertEquals(200, r.getStatus());
        bId2 = ((JsonObject)json(r.getEntity())).getJsonNumber("buildid").longValue();
        assertTrue("<"+bId2+"> must be valid timestamp", bId2 > bId1);
        r = dsh.buildApp(map("{'app':458,'version':'v1'}"));
        assertEquals(200, r.getStatus());
        bId3 = ((JsonObject)json(r.getEntity())).getJsonNumber("buildid").longValue();
        assertTrue("<"+bId3+"> must be valid timestamp", bId3 > bId2);
        r = dsh.getBuild(null, null, null);
        assertEquals(200, r.getStatus());
        assertEquals(json("{'457':{'v1':["+bId0+","+bId1+"],'v2':["+bId2+"]},'458':{'v1':["+bId3+"]}}")
            , json(r.getEntity()));
        r = dsh.getBuild(457, null, null);
        assertEquals(200, r.getStatus());
        assertEquals(json("{'457':{'v1':["+bId0+","+bId1+"],'v2':["+bId2+"]}}")
            , json(r.getEntity()));
        r = dsh.getBuild(457, "v1", null);
        assertEquals(200, r.getStatus());
        assertEquals(json("{'457':{'v1':["+bId0+","+bId1+"]}}")
            , json(r.getEntity()));
    }

    @Test
    public void deployApp() throws Exception {
        DeployServiceHelper dsh = getMock(sinkUrl);
        Response r;
        JsonObject jo;

        // build, deploy and test connection
        assertEquals(200, dsh.buildApp(map("{'app':456,'version':'v1'}")).getStatus());

        // final                                   printf •••"hi;••$AA;••${BB_B}•••"
        // in container   nc -l -p 5000 -e sh -c •"printf •\•"hi;••$AA;••${BB_B}•\•"•"
        // docker run     nc -l -p 5000 -e sh -c •"printf •\•"hi;•\$AA;•\${BB_B}•\•"•"
        String command = "nc -l -p 5000 -e sh -c \"printf \\\"hi;\\$AA;\\${BB_B}\\\"\"";
        r = dsh.deployApp(map("{" +
                "'app': 456," +
                "'version': 'v1'," +
                "'base': 'busybox'," +
                "'env': {" +
                    "'AA': 'xx'," +
                    "'BB_B': 'xx x'" +
                "}," +
                "'command': " + JsonHelper.toString(command) +
            "}"), userId);
        assertEquals(201, r.getStatus());
        jo = (JsonObject) json(r.getEntity());
        assertTrue("Response must include iid", jo.getInt("iid",-1) != -1);
        assertTrue("Must be ip6: <"+jo.getString("ip6")+">", jo.getString("ip6").matches("[0-9a-f:]{3,}+"));
        assertEquals("hi;xx;xx x", request(jo.getString("ip6"), 5000));
        r = dsh.getDeployments(null, false, null);
        assertEquals(200, r.getStatus());
        assertTrue("must match {\"456\":\\[[0-9]*\\]}"
            , json(r.getEntity()).toString().matches("^\\{\"456\":\\[[0-9]*\\]\\}"));

        // build and deploy from build
        r = dsh.buildApp(map("{'app':777,'version':'v2.0','base':'busybox','full':"+JsonHelper.toString("sh -c \"printf apple > ./somefile\"")+"}"));
        assertEquals(200, r.getStatus());
        r = dsh.deployApp(map("{'app':777,'version':'v1.9','base':'build','command':'nc -l -p 5000 -e cat ./somefile'}"), userId);
        assertEquals(404, r.getStatus());
        r = dsh.deployApp(map("{'app':777,'version':'v2.0','base':'build','command':'nc -l -p 5000 -e cat ./somefile'}"), userId);
        assertEquals(201, r.getStatus());
        jo = (JsonObject) json(r.getEntity());
        assertEquals("apple", request(jo.getString("ip6"), 5000));
    }

    @Test
    public void updateApp() throws Exception {
        DeployServiceHelper dsh = getMock(sinkUrl);
        Response r;
        Map<String, Object> deploy_config;
        Map<String, Object> build_config;

        // build two versions
        assertEquals(200, dsh.buildApp(map("{'app':4,'version':'v1','full':'printf 111 > somefile'}")).getStatus());
        assertEquals(200, dsh.buildApp(map("{'app':4,'version':'v2','full':'printf 222 > somefile'}")).getStatus());

        // deploy first version
        r = dsh.deployApp(map("{'app':4,'version':'v1','base':'build','command':'nc -ll -p 5000 -e cat somefile'}"), userId);
        assertEquals(201, r.getStatus());
        JsonObject jo = (JsonObject) json(r.getEntity());
        assertEquals("111", request(jo.getString("ip6"), 5000));

        // update to second version
        r = dsh.updateApp(jo.getInt("iid"),
                map("{'app':4,'version':'v2','base':'build','command':'nc -ll -p 5000 -e cat somefile'}"), userId);
        assertEquals(200, r.getStatus());
        assertEquals("222", request(jo.getString("ip6"), 5000));
    }

    @Test
    public void undeploy() {
        DeployServiceHelper dsh = getMock(sinkUrl);
        Response r;

        assertEquals(200, dsh.buildApp(map("{'app':457,'version':'v1'}")).getStatus());
        assertEquals(200, dsh.buildApp(map("{'app':457,'version':'v2'}")).getStatus());
        r = dsh.deployApp(map("{'app':457,'version':'v1','command':'sleep 10m'}"), userId);
        assertEquals(201, r.getStatus());
        int iid1 = ((JsonObject)json(r.getEntity())).getInt("iid");
        r = dsh.deployApp(map("{'app':457,'version':'v1','command':'sleep 10m'}"), userId);
        assertEquals(201, r.getStatus());
        int iid2 = ((JsonObject)json(r.getEntity())).getInt("iid");
        assertNotEquals(iid1, iid2);
        assertEquals(200, dsh.updateApp(iid1, map("{'app':457,'version':'v2','command':'sleep 10m'}"), userId).getStatus());
        assertEquals(200, dsh.undeploy(iid1, userId).getStatus());
        r = dsh.getDeployments(null, false, null);
        assertEquals(200, r.getStatus());
        JsonObject deployments = (JsonObject) json(r.getEntity());
        assertTrue(((JsonArray)deployments.get("457")).size() == 1);
        assertTrue(((JsonArray)deployments.get("457")).getInt(0) == iid2);
    }


    public static class BuildhookVerifier {
        HttpServer server;
        BlockingQueue<String> got = new LinkedBlockingQueue<>();

        public BuildhookVerifier(int port) throws IOException {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/hook", httpExchange -> {
                try {
                    InputStream is = httpExchange.getRequestBody();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    got.add(s.hasNext() ? s.next() : "");
                } catch(Exception e) { StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString()); }
            });
            server.setExecutor(null); // creates a default executor
            server.start();
        }
        public String await(int timeout) throws InterruptedException, TimeoutException {
            String last = got.poll(timeout, TimeUnit.MILLISECONDS);
            if (last == null) throw new TimeoutException();
            return last;
        }
        public String url() {
            try { return new URI("http", null, server.getAddress().getHostName(), server.getAddress().getPort(), "/hook", null, null).toString(); }
            catch (URISyntaxException e) { StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString()); }
            return null;
        }
    }
    @Test
    public void buildhook() throws Exception {
        BuildhookVerifier bv = new BuildhookVerifier(7001);
        DeployServiceHelper dsh = getMock(bv.url());
        Response r;

        r = dsh.buildApp(map("{'app':457,'version':'v1'}"));
        assertEquals(200, r.getStatus());
        long bId = ((JsonObject)json(r.getEntity())).getJsonNumber("buildid").longValue();
        JsonObject jo = (JsonObject) json(bv.await(1000));
        assertEquals(json("{'app':457,'version':'v1','buildid':"+bId+",'exitCode':0,'runtime':"+jo.getInt("runtime")+"}"), jo);
    }

    @Test
    public void limits() throws InterruptedException {
        DeployServiceHelper dsh = getMock(sinkUrl);
        Response r;

        assertEquals(200, dsh.buildApp(map("{'app':457,'version':'v1'}")).getStatus());
        assertEquals(201, dsh.deployApp(map("{'app':457,'version':'v1','limit':{'cpu':'500'},'command':'sleep 10'}"), userId).getStatus());
        assertEquals(201, dsh.deployApp(map("{'app':457,'version':'v1','limit':{'cpu':'500'},'command':'sleep 10'}"), userId).getStatus());
        assertEquals(402, dsh.deployApp(map("{'app':457,'version':'v1','limit':{'cpu':'1'},'command':'sleep 10'}"), userId).getStatus());
    }
}