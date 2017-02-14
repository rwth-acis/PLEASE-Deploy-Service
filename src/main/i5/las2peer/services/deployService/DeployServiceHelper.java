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
    private ResourceDistribution rd;
    private WebTarget buildhookReceiver;

    public DeployServiceHelper(DockerHelper dh, DatabaseManager dm, ResourceDistribution rd, String buildhookUrl) {
        this.dh = dh; this.dm = dm; this.rd = rd;
        buildhookReceiver = ClientBuilder.newClient().target(buildhookUrl);
        // TODO dm.restore(); + check all cids, imageids and iids with docker daemon
    }

    public Response getDetails(int iid) {
        // TODO statistics for cpu, memory, disk, network
        try {
            ResultSet rs = dm.query("SELECT * FROM deployments WHERE iid=?", iid);
            return Response.ok(Entity.entity("{" +
                "" +
                "}", "application/json")).build();
        } catch (SQLException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public Response getDeployments(Integer app, boolean onlyMy, String userId) {
        try {
            ResultSet rs;
                 if (app == null && !onlyMy) rs = dm.query("SELECT * FROM deployments ORDER BY app");
            else if (app == null &&  onlyMy) rs = dm.query("SELECT * FROM deployments WHERE creator=? ORDER BY app", userId);
            else if (app != null && !onlyMy) rs = dm.query("SELECT * FROM deployments WHERE app=?", app);
            else /*(app != null &&  onlyMy)*/rs = dm.query("SELECT * FROM deployments WHERE (app,creator)=(?,?)", app, userId);

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
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public Response getBuild(Integer app, String version, Long buildid) {
        // TODO implement userOnly
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jg = Json.createGenerator(baos);
            ResultSet rs;
            final long lnull = Long.MAX_VALUE;
            if (app == null) {
                rs = dm.query("SELECT * FROM build_containers ORDER BY app,version,buildid");
            } else if (version == null) {
                rs = dm.query("SELECT * FROM build_containers WHERE app=? ORDER BY app,version,buildid", app);
            } else if (buildid == null) {
                rs = dm.query("SELECT * FROM build_containers WHERE app=? AND version=? ORDER BY app,version,buildid", app, version);
            } else {
                rs = dm.query("SELECT * FROM build_containers WHERE buildid=?", buildid);
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
                jg.write(rs.getLong("buildid"));
            }
            jg.writeEnd().writeEnd().writeEnd().close();
            return Response.ok(baos.toString("utf8")).build();
        } catch (SQLException | IOException | InterruptedException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public static class BuildHook extends DockerHelper.FinishProcessCallback {
        int app; String version; long buildid; WebTarget receiver;
        public BuildHook(int app, String version, long buildid, WebTarget receiver) {
            this.app = app; this.version = version; this.buildid = buildid; this.receiver = receiver;
        }
        @Override
        public void run() {
            receiver.request().post(Entity.entity(
                "{\"app\":"+app+",\"version\":\""+version+"\",\"buildid\":"+buildid+",\"exitCode\":"+exitCode+",\"runtime\":"+runtime+"}"
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
            Map limit = (Map) config.getOrDefault("limit", new HashMap<>());
            limit.putIfAbsent("memory", "400m");
            limit.putIfAbsent("disk", "400m");
            limit.putIfAbsent("cpu", "100");
            // TODO init + inc

            Map<String, Object> docker_config = new HashMap<>();
            docker_config.put("base", base);
            docker_config.put("command", build_full);
            docker_config.put("env", env);
            docker_config.put("limit", limit);

            // TODO this is not 100% thread-safe (seems not to be worth it)
            long buildid = System.currentTimeMillis();
            String cid = dh.startContainer(docker_config, new BuildHook(app, version, buildid, buildhookReceiver));
            dm.update("INSERT INTO build_containers VALUES (?, ?, ?, ?, NULL)", app, version, cid, buildid);
            return Response.ok("{\"buildid\":"+buildid+"}","application/json").build();
        } catch (SQLException | IOException | InterruptedException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
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
        config.putIfAbsent("limit", new HashMap<>());
        Map limit = (Map) config.get("limit");
        limit.putIfAbsent("memory", "50m");
        limit.putIfAbsent("disk", "50m");
        limit.putIfAbsent("cpu", "125");
    }
    public Response deployApp(Map<String, Object> config, String userId) {
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

            Map<String,Integer> limit = rd.parse((Map<String, String>) config.get("limit"));
            if (!rd.checkUserAffordable(limit, userId))
                return Response.status(402).entity("You have not enough free resources: "+rd.account(userId).toString()).build();

            Map<String, Object> container_config = new HashMap<>();
                container_config.put("base", base);
                container_config.put("command", config.get("command"));
                container_config.put("env", config.get("env"));
                container_config.put("limit", limit);

            String cid = dh.startContainer(container_config);
            int iid = dh.getIid(cid);
            dm.update("INSERT INTO deployment_containers VALUES (?,?,?)", iid, version, cid);
            dm.update("INSERT INTO deployments VALUES (?,?,?,?,?,?,?)", iid, app, cid, userId, limit.get("memory"), limit.get("disk"), limit.get("cpu"));
            return Response.created(new URI("http://deployed/"+iid)).entity(
                "{\"iid\":"+iid+", \"ip6\":\""+dh.getIp(cid)+"\"}"
            ).build();
        } catch (IOException | SQLException | URISyntaxException | InterruptedException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public Response updateApp(int iid, Map<String, Object> config, String userId) {
        // TODO after rollback deadline: docker rm -f cid_old
        // TODO resource limit update
        setDeployDefaults(config);

        try {
            ResultSet rs = dm.query("SELECT * FROM deployments WHERE iid=?", iid);
            if (!rs.next())
                return Response.status(404).entity("No deployment found with interface id "+iid+"!").build();
            if (!rs.getString("creator").equals(userId))
                return Response.status(403).entity("only creator can update deployment").build();
            String cid_old = rs.getString("cid");

            if (((Integer)config.get("app")).intValue() != rs.getInt("app"))
                return Response.status(400).entity("App id does not match deployment").build();

            Map<String,Integer> limit = new HashMap<>();
                limit.put("memory", rs.getInt("memory"));
                limit.put("disk", rs.getInt("disk"));
                limit.put("cpu", rs.getInt("cpu"));

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
                container_config.put("limit", limit);

            String cid_new = dh.startContainer(container_config);
            dh.updateContainer(cid_old, cid_new);
            dm.update("INSERT INTO deployment_containers VALUES (?,?,?)", iid, config.get("version"), cid_new);
            dm.update("UPDATE deployments SET cid=? WHERE iid=?", cid_new, iid);
            return Response.ok().build();
        } catch (IOException | SQLException | InterruptedException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public Response undeploy(int iid, String userId) {
        try {
            ResultSet rs = dm.query("SELECT creator FROM deployments WHERE iid=?", iid);
            if (!rs.next())
                return Response.status(404).entity("No deployment found with interface id "+iid+"!").build();
            if (!rs.getString("creator").equals(userId))
                return Response.status(403).entity("only creator can delete deployment").build();
            dm.update("DELETE FROM deployments WHERE iid=?", iid);
            rs = dm.query("SELECT * FROM deployment_containers WHERE iid=?", iid);
            while (rs.next())
                dh.removeContainer(rs.getString("cid"));
            dm.update("DELETE FROM deployment_containers WHERE iid=?", iid);
            return Response.ok().build();
        } catch (SQLException | IOException | InterruptedException e) {
            StringWriter sw = new StringWriter();e.printStackTrace(new PrintWriter(sw));l.error(sw.toString());
        }
        return Response.serverError().build();
    }

    public String getIp(int iid) throws UnknownHostException {
        return dh.getIpForIid(iid);
    }
}
