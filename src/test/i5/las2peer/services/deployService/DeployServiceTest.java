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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class DeployServiceTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgent testAgent;
	private static final String testPass = "adamspass";

	/**
	 * Called before the tests start.
	 * 
	 * Sets up the node and initializes connector and users that can be used throughout the tests.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void startServer() throws Exception {

		// start node
		node = LocalNode.newNode();
		testAgent = MockAgentFactory.getAdam();
		testAgent.unlockPrivateKey(testPass); // agent must be unlocked in order to be stored
		node.storeAgent(testAgent);
		node.launch();

		// during testing, the specified service version does not matter
		ServiceAgent testService = ServiceAgent.createServiceAgent(
				ServiceNameVersion.fromString(DeployService.class.getName() + "@1.0"), "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		// start connector
		logStream = new ByteArrayOutputStream();

		connector = new WebConnector(true, HTTP_PORT, false, 1000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
		Thread.sleep(1000); // wait a second for the connector to become ready
		testAgent = MockAgentFactory.getAdam(); // get a locked agent

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
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c.setLogin(Long.toString(testAgent.getId()), testPass);
		ClientResponse res;
		Socket s;
		BufferedReader br;

		// POST /build
		res = c.sendRequest("POST", "deploy/build"
			, "{\"app\":7, \"version\":\"v1\", \"full\":\"echo 111 > somefile\"}");
		assertEquals(200, res.getHttpCode());
		res = c.sendRequest("POST", "deploy/build"
			, "{\"app\":7, \"version\":\"v2\", \"full\":\"echo 222 > somefile\"}");
		assertEquals(200, res.getHttpCode());

		// GET /build
		res = c.sendRequest("GET", "deploy/build", "");
		assertEquals(200, res.getHttpCode());
		assertEquals(toJson("{\"7\":{\"v1\":[0],\"v2\":[0]}}")
			, toJson(res.getResponse()));

		// POST /deployed
		res = c.sendRequest("POST", "deploy/deployed"
			, "{\"app\":7, \"version\":\"v1\", \"base\":\"build\", \"command\":\"httpd -f\"}");
		assertEquals(201, res.getHttpCode());
		int iid = ((JsonObject)toJson(res.getResponse())).getInt("iid");
		String ip6 = ((JsonObject)toJson(res.getResponse())).getString("ip6");
		c.setAddressPort("http://["+ip6+"]", 80);
		res = c.sendRequest("GET", "somefile", "");
		assertEquals(200, res.getHttpCode());
		assertEquals("111", res.getResponse().trim());
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		// GET /deployed/{iid}/{port}
		res = c.sendRequest("GET", "deploy/deployed/"+iid+"/80/somefile", "");
		assertEquals(200, res.getHttpCode());
		assertEquals("111", res.getResponse().trim());

		// PUT /deployed/{iid}
		res = c.sendRequest("PUT", "deploy/deployed/"+iid
				, "{\"app\":7, \"version\":\"v2\", \"base\":\"build\", \"command\":\"httpd -f\"}");
		assertEquals(200, res.getHttpCode());
		res = c.sendRequest("GET", "deploy/deployed/"+iid+"/80/somefile", "");
		assertEquals(200, res.getHttpCode());
		assertEquals("222", res.getResponse().trim());

		// GET /deployed
		res = c.sendRequest("GET", "deploy/deployed", "");
		assertEquals(200, res.getHttpCode());
		assertEquals(toJson("{\"7\":["+iid+"]}"), toJson(res.getResponse()));

		// DELETE /deployed/{iid}
		res = c.sendRequest("DELETE", "deploy/deployed/"+iid, "");
		assertEquals(200, res.getHttpCode());
		res = c.sendRequest("GET", "deploy/deployed", "");
		assertEquals(200, res.getHttpCode());
		assertEquals(toJson("{}"), toJson(res.getResponse()));
	}

	@Test
	public void testLife() throws InterruptedException {
		Response result = ClientBuilder.newClient()
				.target(HTTP_ADDRESS+":"+HTTP_PORT+"/deploy").request().get();
		assertEquals(200, result.getStatus());
	}

	public static JsonStructure toJson(String s) {
		JsonReader jr = Json.createReader(new StringReader(s));
		JsonStructure js = jr.read();
		jr.close();

		return js;
	}
}
