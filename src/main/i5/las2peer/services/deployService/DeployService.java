package i5.las2peer.services.deployService;

import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import java.net.HttpURLConnection;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@ServicePath("deploy")
public class DeployService extends RESTService {

	@Override
	protected void initResources() {
		getResourceConfig().register(RootResource.class);
	}

	public DeployService() {
		setFieldValues();
	}

	@Path("/")
	public static class RootResource {
		// instantiate the logger class
		private final L2pLogger logger = L2pLogger.getInstance(DeployService.class.getName());

		// get access to the service class
		private final DeployService service = (DeployService) Context.getCurrent().getService();

		@GET
		@Path("/get")
		@Produces(MediaType.TEXT_PLAIN)
		public Response getTemplate() {
			String returnString = "result";
			return Response.ok().entity(returnString).build();
		}

		@POST
		@Path("/post/{input}")
		@Produces(MediaType.TEXT_PLAIN)
		public Response postTemplate(@PathParam("input") String myInput) {
			String returnString = "";
			returnString += "Input " + myInput;
			return Response.ok().entity(returnString).build();
		}
	}
}
