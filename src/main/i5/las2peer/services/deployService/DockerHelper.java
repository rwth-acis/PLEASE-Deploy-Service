package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by adabru on 27.12.16.
 */
public class DockerHelper {
    private Logger l = LoggerFactory.getLogger(DockerHelper.class.getName());

    public String executeProcess(String shellcommand) throws IOException {
        String errOut=null, stdOut=null;
        Process p = new ProcessBuilder("sh","-c",shellcommand).start();
        Scanner s;
        s = new Scanner(p.getInputStream()).useDelimiter("\\A");
        if (s.hasNext()) stdOut = s.next(); else stdOut = "";
        s = new Scanner(p.getErrorStream()).useDelimiter("\\A");
        if (s.hasNext()) errOut = s.next(); else errOut = "";
        if (errOut.length() > 0) {
            l.info(shellcommand);
            l.warn(errOut);
        }
        return stdOut;
    }

    public String startContainer(Map<String, Object> config) throws IOException {
        String dockercmd = "docker run -d"
                + " --network=please"
                + " --memory "            + config.get("memory")
                + " --cpu-shares "        + config.get("cpu")
                + " --storage-opt size="  + config.get("disk")
                + " --env="               + config.get("env")
                + " "                     + config.get("base")
                + " "                     + config.get("command");
        return executeProcess(dockercmd).trim();
    }

    public String getIp(String cid) throws IOException {
        String ip6 = executeProcess("docker inspect --format='{{.NetworkSettings.Networks.please.GlobalIPv6Address}}' "+cid).trim();
        assert ip6.matches("[0-9a-f:]{3,}+");
        return ip6;
    }

    public void updateContainer(String cid_old, String cid_new) throws IOException {
        // after rollback deadline: docker rm -f cid_old
        String ip6 = getIp(cid_old);
        executeProcess("docker network disconnect please "+cid_old);
        executeProcess("docker network disconnect please "+cid_new);
        executeProcess("docker network connect --ip6="+ip6+" please "+cid_new);
        executeProcess("docker pause "+cid_old);
    }

    public void rollbackContainer(String cid, String cid_old) throws IOException {
        String ip6 = getIp(cid);
        executeProcess("docker unpause "+cid_old);
        executeProcess("docker network disconnect please "+cid);
        executeProcess("docker network connect --ip6="+ip6+" please "+cid_old);
    }

    public void removeAllContainers() throws IOException {
        String allContainers = executeProcess("docker ps -a -q").replaceAll("\n"," ");
        if (allContainers.equals(""))
            return;
        l.info("removing all containers, may take a while…");
        executeProcess("docker unpause "+allContainers+" 2>/dev/null");
        executeProcess("docker rm -f "+allContainers);
    }
}
