package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adabru on 27.12.16.
 */
public class DockerHelper {
    private static Logger l = LoggerFactory.getLogger(DockerHelper.class.getName());
    private IpPool ips;
    private String network;
    private boolean addBridge = false;

    public DockerHelper() throws IOException { this("please"); }
    public DockerHelper(String network) throws IOException { this(network, null); }
    public DockerHelper(String network, String net_96) throws IOException {
        if (!network.matches("[a-zA-Z0-9]+"))
            throw new IllegalArgumentException("network name must be [a-zA-Z0-9]+ but is <"+network+">");
        this.network = network;
        if (net_96 == null) {
            String subnet = executeProcess("docker network inspect "+network+" --format='{{with index .IPAM.Config 1}}{{.IPRange}}{{end}}'");
            Matcher matcher = Pattern.compile("[0-9a-f:]+/([0-9]+)").matcher(net_96);
            if (!matcher.find())
                throw new IllegalArgumentException("network <"+network+"> causes problems");
            net_96 = matcher.group(1);
            if(Integer.parseInt(matcher.group(2)) > 96)
                throw new IllegalArgumentException("address range for network too small");
            ips = new IpPool(net_96);
            // docker doesn't use ::
            ips.allocIp();
            // reservce default gateway ::1
            ips.allocIp();
        } else {
            ips = new IpPool(net_96);
            ips.allocIp();
            executeProcess("docker network rm " + network);
            executeProcess("docker network create --driver=bridge --ipv6 --subnet=" + net_96 + "/96 --ip-range=" + net_96 + "/96 --gateway=" + ips.allocIp() + " " + network);
        }
    }
    public void setAddBridge(boolean newValue) { addBridge = newValue; }

    public static String executeProcess(String shellcommand) throws IOException {
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

    // returns cid
    public String startContainer(Map<String, Object> config) throws IOException {
        // TODO recycle ip6 addresses
        StringBuilder env = new StringBuilder("");
        ((Map<String, String>)config.getOrDefault("env", new HashMap<>())).forEach(
                (key, value) -> env.append(" -e "+key+"=\""+value.replaceAll("\"", "\\\"")+"\"")
        );

        String command = "sh -c \"" + ((String)config.get("command")).replaceAll("\\\\","\\\\\\\\").replaceAll("\"","\\\\\"").replaceAll("\\$","\\\\\\$") + "\"";

        String dockercmd = "docker create"
                + " --network="+network
                + " --memory "            + config.get("memory")
                + " --cpu-shares "        + config.get("cpu")
//                + " --storage-opt size="  + config.get("disk")
                + " --ip6="              + config.getOrDefault("ip6", ips.allocIp())
                + env
                + " "                     + config.get("base")
                + " "                     + command;
        String cid = executeProcess(dockercmd).trim();
        if (addBridge)
            executeProcess("docker network connect bridge "+cid);
        executeProcess("docker start "+cid);
        return cid;
    }

    public String getIp(String cid) throws IOException {
        String ip6 = executeProcess("docker inspect --format='{{.NetworkSettings.Networks."+network+".GlobalIPv6Address}}' "+cid).trim();
        if (ip6.equals("<no value>")) ip6 = null;
        assert ip6 == null || ip6.matches("[0-9a-f:]{3,}+") : "ip must be null or valid, but is: "+ip6;
        return ip6;
    }

    public int getIid(String cid) throws IOException {
        byte[] b = InetAddress.getByName(getIp(cid)).getAddress();
        return ((b[12]&0xff)<<24)|((b[13]&0xff)<<16)|((b[14]&0xff)<<8)|((b[15]&0xff)<<0);
    }

    public void updateContainer(String cid_old, String cid_new) throws IOException {
        String ip6 = getIp(cid_old);
        String ip6_freed = getIp(cid_new);
        executeProcess("docker network disconnect "+network+" "+cid_old);
        executeProcess("docker network disconnect "+network+" "+cid_new);
        executeProcess("docker network connect --ip6="+ip6+" "+network+" "+cid_new);
        executeProcess("docker pause "+cid_old);
        ips.freeIp(ip6_freed);
    }

    public void rollbackContainer(String cid, String cid_old) throws IOException {
        String ip6 = getIp(cid);
        executeProcess("docker unpause "+cid_old);
        executeProcess("docker network disconnect "+network+" "+cid);
        executeProcess("docker network connect --ip6="+ip6+" "+network+" "+cid_old);
    }

    public String commitContainer(String cid) throws IOException {
        String imageid = executeProcess("docker commit "+cid);
        imageid = imageid.replace("sha256:","").replace("\n","");
        return imageid;
    }

    public static void removeAllContainers() throws IOException {
        String allContainers = executeProcess("docker ps -a -q").replaceAll("\n"," ");
        if (allContainers.equals(""))
            return;
        l.info("removing all containers, may take a while…");
        executeProcess("docker unpause "+allContainers+" 2>/dev/null");
        executeProcess("docker rm -f "+allContainers);
    }

    public int waitContainer(String cid) throws IOException {
        return Integer.parseInt(executeProcess("docker wait "+cid).trim());
    }

    public void removeContainer(String cid) throws IOException {
        try { ips.freeIp(getIp(cid)); }
        catch (UnknownHostException e) {}
        executeProcess("docker unpause "+cid);
        executeProcess("docker rm -f "+cid);
    }
}
