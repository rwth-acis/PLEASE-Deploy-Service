package i5.las2peer.services.deployService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

import java.io.*;
import java.net.Socket;
import java.util.Base64;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class DeployServiceTest {

	static WebTarget wt_anonym, wt_meta, wt_adam;

	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	@BeforeClass
	public static void startServer() throws Exception {
		// start node
		node = LocalNode.newNode();
		UserAgent adam = MockAgentFactory.getAdam();
		adam.unlockPrivateKey("adamspass"); // agent must be unlocked in order to be stored
		node.storeAgent(adam);
		node.launch();

		// TODO make connect and read timeout vendor agnostic (i.e. remove jersey dependency) when javax.rs 2.1 gets released (scheduled Q3 2017)
		Client c = ClientBuilder.newClient()
				.property("jersey.config.client.connectTimeout", 4000)
				.property("jersey.config.client.readTimeout", 4000);
		wt_anonym = c.target("http://127.0.0.1:" + WebConnector.DEFAULT_HTTP_PORT + "/deploy/");
		wt_meta = c.target("http://127.0.0.1:" + WebConnector.DEFAULT_HTTP_PORT + "/deploy/");
		wt_meta.register((ClientRequestFilter) req -> req.getHeaders().add("Authorization", "Basic "+ Base64.getEncoder().encodeToString(("appmetadata:abcdef123456").getBytes("utf8"))));
		wt_adam = c.target("http://127.0.0.1:" + WebConnector.DEFAULT_HTTP_PORT + "/deploy/");
		wt_adam.register((ClientRequestFilter) req -> req.getHeaders().add("Authorization", "Basic "+ Base64.getEncoder().encodeToString((adam.getId()+":adamspass").getBytes("utf8"))));

		// during testing, the specified service version does not matter
		ServiceAgent testService = ServiceAgent.createServiceAgent(
				ServiceNameVersion.fromString(DeployService.class.getName() + "@1.0"), "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		// start connector
		logStream = new ByteArrayOutputStream();

		connector = new WebConnector(true, WebConnector.DEFAULT_HTTP_PORT, false, 1000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
		Thread.sleep(1000); // wait a second for the connector to become ready
	}

	/**
	 * Called after the tests have finished. Shuts down the server and prints out the connector log file for reference.
	 * 
	 * @throws Exception
	 */
	@AfterClass
	public static void shutDownServer() throws Exception {

		connector.stop();
		node.shutDown();

		connector = null;
		node = null;

		LocalNode.reset();

		System.out.println("Connector-Log:");
		System.out.println("--------------");

		System.out.println(logStream.toString());

	}

	@Test
	public void testApi() throws IOException {
//		"\nGET    /deployed?app=7&my                  : show deployment information" +
//				"\nPOST   /deployed                           : deploy an app" +
//				"\nGET    /deployed/{iid}                     : show deployment with iid" +
//				"\nPUT    /deployed/{iid}                     : update deployment with iid" +
//				"\nDELETE /deployed/{iid}                     : delete deployment with iid" +
//				"\n*      /deployed/{iid}/{port}              : forward requests to deployments" +
//				"\nGET    /build?app=7&version=v1&iteration=5 : show build information" +
//				"\nPOST   /build                              : start new build"
		Client c = ClientBuilder.newClient()
				.property("jersey.config.client.connectTimeout", 4000)
				.property("jersey.config.client.readTimeout", 4000);
		Response res;
		Socket s;
		BufferedReader br;

		// POST /build
		res = wt_anonym.path("build").request().post(Entity.entity(
			"{\"app\":7, \"version\":\"v1\", \"full\":\"echo 111 > somefile\"}"
			, "application/json"));
		assertEquals(200, res.getStatus());
		long bId0 = ((JsonObject)toJson(res.readEntity(String.class))).getJsonNumber("buildid").longValue();
		res = wt_anonym.path("build").request().post(Entity.entity(
			"{\"app\":7, \"version\":\"v2\", \"full\":\"echo 222 > somefile\"}"
			, "application/json"));
		assertEquals(200, res.getStatus());
		long bId1 = ((JsonObject)toJson(res.readEntity(String.class))).getJsonNumber("buildid").longValue();

		// GET /build
		res = wt_anonym.path("build").request().get();
		assertEquals(200, res.getStatus());

		// POST /deployed
		res = wt_anonym.path("deployed").request().post(Entity.entity(
			"{\"app\":7, \"version\":\"v1\", \"base\":\"build\", \"command\":\"httpd -f\"}"
			, "application/json"));
		assertEquals(201, res.getStatus());
		JsonObject joRes = (JsonObject) toJson(res.readEntity(String.class));
		int iid = joRes.getInt("iid");
		String ip6 = joRes.getString("ip6");
		res = c.target("http://["+ip6+"]:80/").path("somefile").request().get();
		assertEquals(200, res.getStatus());
		assertEquals("111", res.readEntity(String.class).trim());

		// GET /deployed/{iid}/{port}
		res = wt_anonym.path("deployed/"+iid+"/80/somefile").request().get();
		assertEquals(200, res.getStatus());
		assertEquals("111", res.readEntity(String.class).trim());

		// PUT /deployed/{iid}
		res = wt_adam.path("deployed/"+iid).request().put(Entity.entity(
			"{\"app\":7, \"version\":\"v2\", \"base\":\"build\", \"command\":\"httpd -f\"}"
			, "application/json"));
		assertEquals(403, res.getStatus());
		res = wt_meta.path("deployed/"+iid).request().put(Entity.entity(
			"{\"app\":7, \"version\":\"v2\", \"base\":\"build\", \"command\":\"httpd -f\"}"
			, "application/json"));
		assertEquals(200, res.getStatus());
		res = wt_anonym.path("deployed/"+iid+"/80/somefile").request().get();
		assertEquals(200, res.getStatus());
		assertEquals("222", res.readEntity(String.class).trim());

		// GET /deployed
		res = wt_anonym.path("deployed").request().get();
		assertEquals(200, res.getStatus());
		assertEquals(toJson("{\"7\":["+iid+"]}"), toJson(res.readEntity(String.class)));

		// DELETE /deployed/{iid}
		res = wt_adam.path("deployed/"+iid).request().delete();
		assertEquals(403, res.getStatus());
		res = wt_anonym.path("deployed/"+iid).request().delete();
		assertEquals(200, res.getStatus());
		res = wt_anonym.path("deployed").request().get();
		assertEquals(200, res.getStatus());
		assertEquals(toJson("{}"), toJson(res.readEntity(String.class)));
	}

	@Test
	public void testLife() throws InterruptedException {
		Response result = wt_anonym.path("/").request().get();
		assertEquals(200, result.getStatus());
	}

	public static JsonStructure toJson(String s) {
		JsonReader jr = Json.createReader(new StringReader(s));
		JsonStructure js = jr.read();
		jr.close();

		return js;
	}
}
