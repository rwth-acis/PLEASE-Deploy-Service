package i5.las2peer.services.deployService;

import i5.las2peer.api.Configurable;
import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import org.glassfish.jersey.server.Uri;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.json.*;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.*;

@ServicePath("deploy")
public class DeployService extends RESTService {

	// from properties file, injected by LAS2peer
	public String jdbcLogin;
	public String jdbcPass;
	public String jdbcUrl;
	public String jdbcSchema;
	public String dockerNetwork;
	public String dockerSubnet;
	public String buildhookUrl;

	public DeployServiceHelper dsh;

	public DeployService() throws IOException, InterruptedException {
		setFieldValues();
		this.dsh = new DeployServiceHelper(
				new DockerHelper(dockerNetwork, dockerSubnet)
				, new DatabaseManager(jdbcLogin, jdbcPass, jdbcUrl, jdbcSchema, "etc/db_migration", "database")
				, buildhookUrl
		);
	}
	@Override
	protected void initResources() { getResourceConfig().register(RootResource.class); }


	@Path("/")
	public static class RootResource {

		// instantiate the logger class
		private final L2pLogger l = L2pLogger.getInstance(DeployService.class.getName());


		private DeployServiceHelper dsh;

		public RootResource() throws IOException {
			this.dsh = ((DeployService) Context.getCurrent().getService()).dsh;
		}

		@GET
		@Produces(MediaType.TEXT_PLAIN)
		public Response signOfLife() {
			return Response.ok(
					"PLEASE service runner to build and run apps" +
							"\n\n" +
							"\nGET    /deployed?app=7&my                  : show deployment information" +
							"\nPOST   /deployed                           : deploy an app" +
							"\nGET    /deployed/{iid}                     : show deployment with iid" +
							"\nPUT    /deployed/{iid}                     : update deployment with iid" +
							"\nDELETE /deployed/{iid}                     : delete deployment with iid" +
							"\n*      /deployed/{iid}/{port}              : forward requests to deployments" +
							"\nGET    /build?app=7&version=v1&iteration=5 : show build information" +
							"\nPOST   /build                              : start new build" +
							"\n"
			).build();
		}

		@GET
		@Path("/deployed")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getDeployments(@QueryParam("app") Integer app/*, TODO @QueryParam("my") String my*/) {
			return dsh.getDeployments(app);
		}

		@POST
		@Path("/deployed")
		@Produces(MediaType.APPLICATION_JSON)
		public Response deployApp(String config) {
			return dsh.deployApp((Map<String, Object>) toCollection(toJson(config)));
		}

		@GET
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getDeploymentDetails(@PathParam("iid") int iid) {
			// TODO return dsh.getDetails(iid);
			return Response.serverError().entity("Not implemented!").build();
		}

		@PUT
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response updateApp(@PathParam("iid") int iid, String config) {
			return dsh.updateApp(iid, (Map<String, Object>) toCollection(toJson(config)));
		}

		@DELETE
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response undeploy(@PathParam("iid") int iid) {
			return dsh.undeploy(iid);
		}

		// ip4 compatibility function
		// TODO bug: for unknown reason jersey(?) adds a slash to the end of path
		// TODO forward all http methods, low performance
		@GET
		@Path("/deployed/{iid}/{port}/{path : .*[^/]}")
		public Response httpForward(@PathParam("iid") int iid, @PathParam("port") int port, @PathParam("path") String path
				, @javax.ws.rs.core.Context final ContainerRequestContext req) {
			URI req_uri = req.getUriInfo().getRequestUri();
			String forward_uri = null;
			String query = req_uri.getRawQuery();
			String fragment = req_uri.getRawFragment();
			try {
				forward_uri = "http://" +
                        "[" + dsh.getIp(iid) + "]:" +
                        port + "/" +
                        path + ((query != null) ? "?"+query : "") +  ((fragment != null) ? "#"+fragment : "");
				return ClientBuilder.newClient().target(forward_uri)
						.request().headers((MultivaluedMap)req.getHeaders()).method(req.getMethod());
			} catch (UnknownHostException e) {
				l.warning(e.toString());
			}
			return Response.serverError().build();
		}

		@GET
		@Path("/build")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getBuild(@QueryParam("app") Integer app, @QueryParam("version") String version, @QueryParam("iteration") Integer iteration) {
			return dsh.getBuild(app, version, iteration);
		}

		@POST
		@Path("/build")
		@Produces(MediaType.APPLICATION_JSON)
		public Response buildApp(String config) {
			return dsh.buildApp((Map<String, Object>) toCollection(toJson(config)));
		}

		public static JsonStructure toJson(String s) {
			JsonReader jr = Json.createReader(new StringReader(s));
			JsonStructure js = jr.read();
			jr.close();

			return js;
		}

		public static Object toCollection(JsonValue json) {
			if (json instanceof JsonObject) {
				Map<String, Object> res = new HashMap<>();
				((JsonObject) json).forEach(
						(key, value) ->
								res.put(key, toCollection(value))
				);
				return res;
			} else if (json instanceof JsonArray) {
				List<Object> res = new LinkedList<>();
				((JsonArray) json).forEach(
						(value) ->
								res.add(toCollection(value))
				);
				return res;
			} else if (json instanceof JsonNumber) {
				if (((JsonNumber) json).isIntegral())
					return ((JsonNumber) json).intValue();
				else
					return ((JsonNumber) json).doubleValue();
			} else if (json instanceof JsonString) {
				return ((JsonString) json).getString();
			} else if (json.equals(JsonValue.FALSE)) {
				return false;
			} else if (json.equals(JsonValue.TRUE)) {
				return true;
			} else /*if (json.equals(JsonValue.NULL))*/ {
				return null;
			}
		}
	}
}
