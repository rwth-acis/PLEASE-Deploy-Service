package i5.las2peer.services.deployService;

import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.security.AgentException;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.UserAgent;
import i5.las2peer.security.UserAgentException;
import i5.las2peer.tools.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.*;

@ServicePath("deploy")
public class DeployService extends RESTService {

	Logger l = LoggerFactory.getLogger(DeployService.class.getName());

	// from properties file, injected by LAS2peer
	public String jdbcLogin;
	public String jdbcPass;
	public String jdbcUrl;
	public String jdbcSchema;
	public String dockerNetwork;
	public String dockerSubnet;
	public String appmetadataUrl;
	public String user_limit_memory;
	public String user_limit_disk;
	public String user_limit_cpu;

	public DeployServiceHelper dsh;

	public DeployService() throws IOException, InterruptedException, CryptoException, L2pSecurityException, AgentException {
		setFieldValues();
		DatabaseManager dm = new DatabaseManager(jdbcLogin, jdbcPass, jdbcUrl, jdbcSchema, "etc/db_migration", "database");
		this.dsh = new DeployServiceHelper(
				new DockerHelper(dockerNetwork, dockerSubnet),
				dm,
				new ResourceDistribution(dm, user_limit_memory, user_limit_disk, user_limit_cpu),
				appmetadataUrl+"/hook"
		);
	}
	@Override
	protected void initResources() { getResourceConfig().register(RootResource.class); }


	@Path("/")
	public static class RootResource {

		static Logger l = LoggerFactory.getLogger(DeployService.RootResource.class.getName());

		private DeployServiceHelper dsh;
		// static required for lazy thread-safe instantiation
		private static UserAgent appmetadataService = createAppmetadataAgent();

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
							"\nGET    /build?app=7&version=v1&buildid=5   : show build information" +
							"\nPOST   /build                              : start new build" +
							"\n"
			).build();
		}

		@GET
		@Path("/deployed")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getDeployments(@QueryParam("app") Integer app, @QueryParam("my") String my) {
			return dsh.getDeployments(app, my != null, getActiveUser());
		}

		@POST
		@Path("/deployed")
		@Produces(MediaType.APPLICATION_JSON)
		public Response deployApp(String config) {
			return dsh.deployApp((Map<String, Object>) JsonHelper.toCollection(config), getActiveUser());
		}

		@GET
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getDeploymentDetails(@PathParam("iid") int iid) {
			return dsh.getDetails(iid);
		}

		@PUT
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response updateApp(@PathParam("iid") int iid, String config) {
			return dsh.updateApp(iid, (Map<String, Object>) JsonHelper.toCollection(config), getActiveUser());
		}

		@DELETE
		@Path("/deployed/{iid}")
		@Produces(MediaType.APPLICATION_JSON)
		public Response undeploy(@PathParam("iid") int iid) {
			return dsh.undeploy(iid, getActiveUser());
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
				l.warn(e.toString());
			}
			return Response.serverError().build();
		}

		@GET
		@Path("/build")
		@Produces(MediaType.APPLICATION_JSON)
		public Response getBuild(@QueryParam("app") Integer app, @QueryParam("version") String version, @QueryParam("buildid") Long buildid) {
			return dsh.getBuild(app, version, buildid);
		}

		@POST
		@Path("/build")
		@Produces(MediaType.APPLICATION_JSON)
		public Response buildApp(String config) {
			return dsh.buildApp((Map<String, Object>) JsonHelper.toCollection(config));
		}

		private static UserAgent createAppmetadataAgent() {
			UserAgent ua = null;
			String pleaseSecret = System.getenv("PLEASE_SECRET");
			if (pleaseSecret == null) {
				l.warn("no env variable PLEASE_SECRET exists, using default secret (insecure)");
				pleaseSecret = "abcdef123456";
			}
			try {
				ua = UserAgent.createUserAgent(pleaseSecret);
				ua.unlockPrivateKey(pleaseSecret);
				ua.setLoginName("appmetadata");
				Context.getCurrent().getLocalNode().storeAgent(ua);
			} catch (AgentException | CryptoException | L2pSecurityException e) {
				StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); l.error(sw.toString());
			}
			return ua;
		}
		private String getActiveUser() {
			// TODO find a better way to register appmetadata's useragent
			UserAgent ua = (UserAgent) Context.getCurrent().getMainAgent();
			if(ua.getId() == Context.getCurrent().getLocalNode().getAnonymous().getId())
				return "anonymous";
			else if(ua.getId() == appmetadataService.getId())
				return "appmetadata";
			else
				return String.valueOf(ua.getId());
		}
	}
}
