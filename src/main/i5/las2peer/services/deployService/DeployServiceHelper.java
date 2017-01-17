package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.core.Response;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelper {
    private Logger l = LoggerFactory.getLogger(DockerHelper.class.getName());

    private DockerHelper dh;
    private DatabaseManager dm;

    public DeployServiceHelper(DockerHelper dh, DatabaseManager dm) {
        this.dh = dh; this.dm = dm;
    }

    private Map<String,Object> guardedConfig(Map<String,Object> orig) {
        // TODO get limits for authenticated user
        int user_max_memory = (int)50e6;
        int user_max_disk = (int)250e6;
        int user_max_cpu = 512;

        Map<String,Object> config = new HashMap<>();

        config.put("memory", orig.getOrDefault("memory", user_max_memory));
        if ((int)config.get("memory") > user_max_memory)
            ;// memory request too high
        config.put("disk", orig.getOrDefault("disk", user_max_disk));
        if ((int)config.get("disk") > user_max_disk)
            ;// disk request too high
        config.put("cpu", orig.getOrDefault("cpu", user_max_cpu));
        if ((int)config.get("cpu") > user_max_cpu)
            ;// cpu request too high
        config.put("base", orig.getOrDefault("base", "busybox"));
        config.put("command", orig.getOrDefault("command", "\"echo hello world!\""));
        config.put("env", orig.getOrDefault("env", new HashMap<>()));

        return config;
    }

    public Response getAllApps() {
        try {
            ResultSet rs = dm.query("SELECT app FROM build_containers UNION SELECT app FROM deployments");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jg = Json.createGenerator(baos);
            jg.writeStartArray();
            while (rs.next())
                jg.write(rs.getString(1));
            jg.writeEnd().close();
            return Response.ok().entity(baos.toString("utf8")).build();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    public Response buildApp(Map<String, Object> config) {
        try {
            int app = (int) config.get("app");
            String version = (String) config.get("version");
            String build_full = (String) config.get("full");
            String base = (String) config.get("base");
            // TODO init + inc

            Map<String, Object> docker_config = new HashMap<>();
            docker_config.put("base", base);
            docker_config.put("command", build_full);
            docker_config = guardedConfig(docker_config);

            String cid = dh.startContainer(docker_config);
            dm.update("INSERT INTO build_containers VALUES (?, ?, ?, NULL)", app, version, cid);
            return Response.ok().build();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    public boolean waitForBuild(int app, String version) throws SQLException, IOException {
        ResultSet rs = dm.query("SELECT cid FROM build_containers WHERE app=? AND version=?", app, version);
        if (!rs.next())
            throw new IllegalArgumentException("no build exists for app "+app+" with version "+version);
        String cid = rs.getString("cid");
        return dh.waitContainer(cid) == 0;
    }

    public Response deployApp(Map<String, Object> config) throws SQLException, IOException {
        int app = (int) config.get("app");
        String version = (String) config.get("version");
        String base = (String) config.get("base");
        Map<String, Object> env = (Map<String, Object>) config.get("env");
        String command = (String) config.get("command");

        if (base.equals("build")) {
            ResultSet rs = dm.query("SELECT imageid,cid FROM build_containers WHERE app=? AND version=?", app, version);
            if (!rs.next())
                return Response.status(404).entity("No build found for given app and version!").build();
            String imageid = rs.getString("imageid");
            if (imageid == null) {
                imageid = dh.commitContainer(rs.getString("cid"));
                dm.update("UPDATE build_containers SET imageid=? WHERE app=? AND version=?", imageid, app, version);
            }
            base = imageid;
        }

        Map<String, Object> container_config = new HashMap<>();
        container_config.put("base", base);
        if (env != null) container_config.put("env", env);
        container_config.put("command", command);
        try {
            String cid = dh.startContainer(guardedConfig(container_config));
            String ip6 = dh.getIp(cid);
            dm.update("INSERT INTO deployment_containers VALUES (?,?,?)", ip6, version, cid);
            dm.update("INSERT INTO deployments VALUES (?,?,?)", ip6, app, cid);
            return Response.created(new URI("//["+ip6+"]")).build();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return Response.serverError().build();
    }

    public String updateApp(String cid_old, String cid_new) throws IOException {
        // TODO
        // get ip
        // start new
        dh.updateContainer(cid_old, cid_new);
        // free ip
        return "yolo";
    }

    public static JsonStructure stringToJson(String s) {
        JsonReader jr = Json.createReader(new StringReader(s));
        JsonStructure js = jr.read();
        jr.close();

        return js;
    }
}
