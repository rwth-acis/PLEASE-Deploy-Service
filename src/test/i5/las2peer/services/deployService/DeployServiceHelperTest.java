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
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelperTest {
    static Logger l = LoggerFactory.getLogger("");

    private DockerHelper dh;

    @BeforeClass
    public static void setup() throws IOException {
        new DockerHelper().removeAllContainers();
    }

    public DeployServiceHelperTest() throws IOException {
        dh = new DockerHelper();
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

//    @Test
    public void getAllApps() {
        DeployServiceHelper dsh = new DeployServiceHelper(null, getMock(1), null);
        Response r = dsh.getAllApps();
        assertEquals(200, r.getStatus());
        assertEquals("[]", r.getEntity());
    }

//    @Test
    public void buildApp() {
        AppMetadataHelper amh = new AppMetadataHelper("example.com"){
            @Override
            public Map<String, Object> getApp(int appId) {
                Map<String, Object> app = new HashMap<>();
                    Map<String, Object> build = new HashMap<>(); app.put("build", build);
                        build.put("base", "busybox");
                        build.put("full", "echo hi");
                return app;
            }
        };
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(2), amh);

        Response r = dsh.buildApp(457, "v1.2.3");
        assertEquals(200, r.getStatus());
        r = dsh.getAllApps();
        assertEquals(200, r.getStatus());
        assertEquals("[\"457\"]", r.getEntity());
    }

    @Test
    public void deployApp() throws Exception {
        DeployServiceHelper dsh = new DeployServiceHelper(dh, getMock(3), null);
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
        Field field_amh = DeployServiceHelper.class.getDeclaredField("amh");
        field_amh.setAccessible(true);
        field_amh.set(dsh, new AppMetadataHelper("example.com"){
            @Override
            public Map<String, Object> getApp(int appId) {
                Map<String, Object> app = new HashMap<>();
                Map<String, Object> build = new HashMap<>(); app.put("build", build);
                build.put("base", "busybox");
                build.put("full", "sh -c \"echo apple > ./somefile\"");
                return app;
            }
        });
        r = dsh.buildApp(777, "v2.0");
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
}