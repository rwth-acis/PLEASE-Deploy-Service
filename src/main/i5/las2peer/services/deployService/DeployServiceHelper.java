package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import javax.print.URIException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
    private WebTarget buildhookReceiver;

    public DeployServiceHelper(DockerHelper dh, DatabaseManager dm, String buildhookUrl) {
        this.dh = dh; this.dm = dm;
        buildhookReceiver = ClientBuilder.newClient().target(buildhookUrl);
        // TODO dm.restore(); + check all cids, imageids and iids with docker daemon
    }

    private Map<String,Object> guardedConfig(Map<String,Object> orig) {
        // TODO get limits for authenticated user
        int user_max_memory = (int)200e6;
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

    // {
    //   "7": [449845, 64981, 98],
    //   "13": [498, 0],
    //    _
    // }
    public Response getDeployments(Integer app) {
        // TODO implement userOnly
        try {
            ResultSet rs;
            if (app == null)
                rs = dm.query("SELECT * FROM deployments ORDER BY app");
            else
                rs = dm.query("SELECT * FROM deployments WHERE app=?", app);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jg = Json.createGenerator(baos);
            long curApp = Long.MAX_VALUE;
            jg.writeStartObject();
            while (rs.next()) {
                if (curApp != rs.getInt("app")) {
                    if (curApp != Long.MAX_VALUE)
                        jg.writeEnd();
                    curApp = rs.getInt("app");
                    jg.writeStartArray(String.valueOf(curApp));
                }
                jg.write(rs.getInt("iid"));
            }
            if (curApp != Long.MAX_VALUE)
                jg.writeEnd();
            jg.writeEnd().close();
            return Response.ok().entity(baos.toString("utf8")).build();
        } catch (SQLException | UnsupportedEncodingException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    // {
    //   "7": {
    //      "v1": [0,1,2],
    //      "v2": [0,1]
    //   },
    //   "8": { …
    //   }
    // }
    public Response getBuild(Integer app, String version, Integer iteration) {
        // TODO implement userOnly
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jg = Json.createGenerator(baos);
            ResultSet rs;
            final long lnull = Long.MAX_VALUE;
            if (app == null) {
                rs = dm.query("SELECT * FROM build_containers ORDER BY app,version,iteration");
            } else if (version == null) {
                rs = dm.query("SELECT * FROM build_containers WHERE app=? ORDER BY app,version,iteration", app);
            } else if (iteration == null) {
                rs = dm.query("SELECT * FROM build_containers WHERE app=? AND version=? ORDER BY app,version,iteration", app, version);
            } else {
                rs = dm.query("SELECT * FROM build_containers WHERE app=? AND version=? AND iteration=?", app, version, iteration);
                jg.writeStartObject();
                jg.write("ip6", dh.getIp(rs.getString("cid")));
                jg.writeEnd().close();
                return Response.ok().entity(baos.toString("utf8")).build();
            }
            long curApp = lnull;
            String curVersion = null;
            jg.writeStartObject();
            while (rs.next()) {
                if (curApp != rs.getInt("app")) {
                    if (curApp != lnull)
                        jg.writeEnd().writeEnd();
                    curApp = rs.getInt("app");
                    curVersion = null;
                    jg.writeStartObject(String.valueOf(curApp));
                }
                if (!rs.getString("version").equals(curVersion)) {
                    if (curVersion != null)
                        jg.writeEnd();
                    curVersion = rs.getString("version");
                    jg.writeStartArray(curVersion);
                }
                jg.write(rs.getInt("iteration"));
            }
            jg.writeEnd().writeEnd().writeEnd().close();
            return Response.ok(baos.toString("utf8")).build();
        } catch (SQLException | IOException | InterruptedException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    public static class BuildHook extends DockerHelper.FinishProcessCallback {
        int app; String version; int iteration; WebTarget receiver;
        public BuildHook(int app, String version, int iteration, WebTarget receiver) {
            this.app = app; this.version = version; this.iteration = iteration; this.receiver = receiver;
        }
        @Override
        public void run() {
            receiver.request().post(Entity.entity(
                "{\"app\":"+app+",\"version\":\""+version+"\",\"iteration\":"+iteration+",\"exitCode\":"+exitCode+",\"runtime\":"+runtime+"}"
                ,"application/json"));
        }
    }
    public Response buildApp(Map<String, Object> config) {
        try {
            int app = (int) config.get("app");
            String version = (String) config.get("version");
            String build_full = (String) config.getOrDefault("full", "");
            String base = (String) config.getOrDefault("base", "busybox");
            Map<String, Object> env = (Map<String, Object>) config.getOrDefault("env", new HashMap<>());
            // TODO init + inc

            Map<String, Object> docker_config = new HashMap<>();
            docker_config.put("base", base);
            docker_config.put("command", build_full);
            docker_config.put("env", env);
            docker_config = guardedConfig(docker_config);

            // TODO this is not 100% thread-safe (seems not to be worth it)
            ResultSet rs = dm.query("SELECT COUNT(*) FROM build_containers WHERE app=? AND version=?", app, version);
            rs.next();
            int iteration = rs.getInt(1);
            dm.update("INSERT INTO build_containers VALUES (?, ?, NULL, ?, NULL)", app, version, iteration);
            String cid = dh.startContainer(docker_config, new BuildHook(app, version, iteration, buildhookReceiver));
            dm.update("UPDATE build_containers SET cid=? WHERE (app,version,iteration)=(?,?,?)", cid, app, version, iteration);
            return Response.ok().build();
        } catch (SQLException | IOException | InterruptedException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    public boolean waitForBuild(int app, String version) throws SQLException, IOException, InterruptedException {
        ResultSet rs = dm.query("SELECT cid FROM build_containers WHERE app=? AND version=?", app, version);
        if (!rs.next())
            throw new IllegalArgumentException("no build exists for app "+app+" with version "+version);
        String cid = rs.getString("cid");
        return dh.waitContainer(cid) == 0;
    }
    private void setDeployDefaults(Map<String, Object> config) {
        config.putIfAbsent("base", "busybox");
        config.putIfAbsent("command", "");
        config.putIfAbsent("env", new HashMap<>());
    }
    public Response deployApp(Map<String, Object> config) {
        int app = (int) config.get("app");
        String version = (String) config.get("version");
        setDeployDefaults(config);

        try {
            ResultSet rs = dm.query("SELECT * FROM build_containers WHERE app=? AND version=?", app, version);
            if (!rs.next())
                return Response.status(404).entity("No build found for given app and version!").build();

            String base = (String) config.get("base");
            if (base.equals("build")) {
                String imageid = rs.getString("imageid");
                if (imageid == null) {
                    imageid = dh.commitContainer(rs.getString("cid"));
                    dm.update("UPDATE build_containers SET imageid=? WHERE app=? AND version=?", imageid, app, version);
                }
                base = imageid;
            }

            Map<String, Object> container_config = new HashMap<>();
                container_config.put("base", base);
                container_config.put("command", config.get("command"));
                container_config.put("env", config.get("env"));

            String cid = dh.startContainer(guardedConfig(container_config));
            int iid = dh.getIid(cid);
            dm.update("INSERT INTO deployment_containers VALUES (?,?,?)", iid, version, cid);
            dm.update("INSERT INTO deployments VALUES (?,?,?)", iid, app, cid);
            return Response.created(new URI("http://deployed/"+iid)).entity(
                "{\"iid\":"+iid+", \"ip6\":\""+dh.getIp(cid)+"\"}"
            ).build();
        } catch (IOException | SQLException | URISyntaxException | InterruptedException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    public Response updateApp(int iid, Map<String, Object> config) {
        // TODO after rollback deadline: docker rm -f cid_old
        setDeployDefaults(config);

        try {
            ResultSet rs = dm.query("SELECT * FROM deployments WHERE iid=?", iid);
            if (!rs.next())
                return Response.status(404).entity("No deployment found with interface id "+iid+"!").build();
            String cid_old = rs.getString("cid");

            rs = dm.query("SELECT * FROM build_containers WHERE app=? AND version=?", rs.getInt("app"), config.get("version"));
            if (!rs.next())
                return Response.status(404).entity("No build found for given app and version!").build();

            String base = (String) config.get("base");
            if (base.equals("build")) {
                String imageid = rs.getString("imageid");
                if (imageid == null) {
                    imageid = dh.commitContainer(rs.getString("cid"));
                    dm.update("UPDATE build_containers SET imageid=? WHERE cid=?", imageid, rs.getString("cid"));
                }
                base = imageid;
            }

            // start new
            Map<String, Object> container_config = new HashMap<>();
                container_config.put("base", base);
                container_config.put("command", config.get("command"));
                container_config.put("env", config.get("env"));

            String cid_new = dh.startContainer(guardedConfig(container_config));
            dh.updateContainer(cid_old, cid_new);
            dm.update("INSERT INTO deployment_containers VALUES (?,?,?)", iid, config.get("version"), cid_new);
            dm.update("UPDATE deployments SET cid=? WHERE iid=?", cid_new, iid);
            return Response.ok().build();
        } catch (IOException | SQLException | InterruptedException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    public Response undeploy(int iid) {
        try {
            dm.update("DELETE FROM deployments WHERE iid=?", iid);
            ResultSet rs = dm.query("SELECT * FROM deployment_containers WHERE iid=?", iid);
            while (rs.next())
                dh.removeContainer(rs.getString("cid"));
            dm.update("DELETE FROM deployment_containers WHERE iid=?", iid);
            return Response.ok().build();
        } catch (SQLException | IOException | InterruptedException e) {
            l.error(e.toString());
        }
        return Response.serverError().build();
    }

    public String getIp(int iid) throws UnknownHostException {
        return dh.getIpForIid(iid);
    }
}
