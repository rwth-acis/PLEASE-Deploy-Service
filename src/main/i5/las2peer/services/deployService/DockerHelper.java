package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Produces;
import java.io.ByteArrayOutputStream;
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

    public DockerHelper() throws IOException, InterruptedException { this("please"); }
    public DockerHelper(String network) throws IOException, InterruptedException { this(network, null); }
    public DockerHelper(String network, String net_96) throws IOException, InterruptedException {
        if (!network.matches("[a-zA-Z0-9]+"))
            throw new IllegalArgumentException("network name must be [a-zA-Z0-9]+ but is <"+network+">");
        this.network = network;
        if (net_96 == null) {
            String subnet = executeProcess("docker network inspect "+network+" --format='{{with index .IPAM.Config 1}}{{.IPRange}}{{end}}'").stdout;
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
            if (!executeProcess("docker network ls -q -f NAME="+network).stdout.equals(""))
                executeProcess("docker network rm " + network);
            executeProcess("docker network create --driver=bridge --ipv6 --subnet=" + net_96 + "/96 --ip-range=" + net_96 + "/96 --gateway=" + ips.allocIp() + " " + network);
        }
    }
    public void setAddBridge(boolean newValue) { addBridge = newValue; }

    public static class ProcessOutput {
        String stdout, stderr;
        int code;
        public ProcessOutput(int code, String stdout, String stderr){this.code=code; this.stdout=stdout; this.stderr=stderr;}
    }
    private static class InputStreamAbsorber implements Runnable {
        InputStream is;
        int limit; //soft
        String res;
        public InputStreamAbsorber(InputStream is) {this(is,Integer.MAX_VALUE);}
        public InputStreamAbsorber(InputStream is, int limit) {this.is = is;this.limit = limit;}
        @Override
        public void run() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            try {
                while ((n = is.read(buf)) != -1 && baos.size() < limit)
                    baos.write(buf, 0, n);
                res = baos.toString("UTF-8");
            } catch (IOException e) {
                l.warn(e.getMessage());
            }
        }
    }
    public static ProcessOutput executeProcess(String shellcommand) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("sh","-c",shellcommand).start();
        InputStreamAbsorber isa1 = new InputStreamAbsorber(p.getInputStream(), (int)10e6);
        InputStreamAbsorber isa2 = new InputStreamAbsorber(p.getErrorStream(), (int)10e6);
        Thread t1 = new Thread(isa1);
        Thread t2 = new Thread(isa2);
        t1.start(); t2.start();
        int code = p.waitFor();
        t1.join(); t2.join();
        ProcessOutput res = new ProcessOutput(code, isa1.res, isa2.res);

        if (res.stderr.length() > 0) {
            l.info(shellcommand);
            l.warn(res.stderr);
        }
        if (res.code != 0) throw new IOException("Error exeucting <"+shellcommand+">:"+res.stderr);
        return res;
    }
    public static abstract class FinishProcessCallback implements Runnable {
        public int exitCode;
        public int runtime;
        public void run(int exitCode, int runtime) {
            this.exitCode = exitCode;
            this.runtime = runtime;
            run();
        }
    }
    public static void executeProcess(String shellcommand, FinishProcessCallback callback) throws IOException {
        Process p = new ProcessBuilder("sh","-c",shellcommand).start();
        InputStreamAbsorber isa1 = new InputStreamAbsorber(p.getInputStream(), (int)10e6);
        InputStreamAbsorber isa2 = new InputStreamAbsorber(p.getErrorStream(), (int)10e6);
        Thread t1 = new Thread(isa1);
        Thread t2 = new Thread(isa2);
        t1.start(); t2.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long time = new Date().getTime();
                    int code = p.waitFor();
                    if (callback != null)
                        callback.run(code, (int)(new Date().getTime() - time));
                } catch (InterruptedException e) {
                    l.warn(e.getMessage());
                }
            }
        }).start();
    }

    // returns cid
    public String startContainer(Map<String, Object> config) throws IOException, InterruptedException { return startContainer(config,null); }
    public String startContainer(Map<String, Object> config, FinishProcessCallback finishContainerHook) throws IOException, InterruptedException {
        // TODO recycle ip6 addresses
        StringBuilder env = new StringBuilder("");
        ((Map<String, String>)config.getOrDefault("env", new HashMap<>())).forEach(
                (key, value) -> env.append(" -e "+key+"=\""+value.replaceAll("\"", "\\\"")+"\"")
        );

        String command = "sh -c \"" + ((String)config.get("command")).replaceAll("\\\\","\\\\\\\\").replaceAll("\"","\\\\\"").replaceAll("\\$","\\\\\\$") + "\"";

        String dockercmd = "docker create"
                + " --network="+network
                + " --memory "            + ((Map)config.get("limit")).get("memory")
                + " --cpu-shares "        + ((Map)config.get("limit")).get("cpu")
//                + " --storage-opt size="  + ((Map)config.get("limit")).get("disk")
                + " --ip6="              + config.getOrDefault("ip6", ips.allocIp())
                + env
                + " "                     + config.get("base")
                + " "                     + command;
        String cid = executeProcess(dockercmd).stdout.trim();
        if (addBridge)
            executeProcess("docker network connect bridge "+cid);
        executeProcess("docker start "+cid);
        executeProcess("docker wait "+cid, finishContainerHook);
        return cid;
    }

    public String getIp(String cid) throws IOException, InterruptedException {
        String ip6 = executeProcess("docker inspect --format='{{.NetworkSettings.Networks."+network+".GlobalIPv6Address}}' "+cid).stdout.trim();
        if (ip6.equals("<no value>")) ip6 = null;
        assert ip6 == null || ip6.matches("[0-9a-f:]{3,}+") : "ip must be null or valid, but is: <"+ip6+">";
        return ip6;
    }
    public String getIpForIid(int iid) throws UnknownHostException {
        byte[] b = InetAddress.getByName(ips.net).getAddress();
        b[12]=(byte)(iid>>24);b[13]=(byte)(iid>>16);b[14]=(byte)(iid>>8);b[15]=(byte)iid;
        return InetAddress.getByAddress(b).getCanonicalHostName();
    }
    public int getIid(String cid) throws IOException, InterruptedException {
        byte[] b = InetAddress.getByName(getIp(cid)).getAddress();
        return ((b[12]&0xff)<<24)|((b[13]&0xff)<<16)|((b[14]&0xff)<<8)|((b[15]&0xff)<<0);
    }

    public void updateContainer(String cid_old, String cid_new) throws IOException, InterruptedException {
        String ip6 = getIp(cid_old);
        String ip6_freed = getIp(cid_new);
        executeProcess("docker network disconnect "+network+" "+cid_old);
        executeProcess("docker network disconnect "+network+" "+cid_new);
        executeProcess("docker network connect --ip6="+ip6+" "+network+" "+cid_new);
        executeProcess("docker pause "+cid_old);
        ips.freeIp(ip6_freed);
    }

    public void rollbackContainer(String cid, String cid_old) throws IOException, InterruptedException {
        String ip6 = getIp(cid);
        executeProcess("docker unpause "+cid_old);
        executeProcess("docker network disconnect "+network+" "+cid);
        executeProcess("docker network connect --ip6="+ip6+" "+network+" "+cid_old);
    }

    public String commitContainer(String cid) throws IOException, InterruptedException {
        String imageid = executeProcess("docker commit "+cid).stdout;
        imageid = imageid.replace("sha256:","").replace("\n","");
        return imageid;
    }

    public static void removeAllContainers() throws IOException, InterruptedException {
        String allContainers = executeProcess("docker ps -a -q").stdout.replaceAll("\n"," ");
        if (allContainers.equals(""))
            return;
        l.info("removing all containers, may take a while…");
        String pausedContainers = executeProcess("docker ps --filter status=paused -q").stdout;
        if (!pausedContainers.equals(""))
            executeProcess("docker unpause "+pausedContainers);
        executeProcess("docker rm -f "+allContainers);
    }

    public int waitContainer(String cid) throws IOException, InterruptedException {
        return Integer.parseInt(executeProcess("docker wait "+cid).stdout.trim());
    }

    public void removeContainer(String cid) throws IOException, InterruptedException {
        try { ips.freeIp(getIp(cid)); }
        catch (UnknownHostException e) {}
        if (executeProcess("docker inspect --format='{{.State.Status}}' "+cid).stdout.trim().equals("paused"))
            executeProcess("docker unpause "+cid);
        executeProcess("docker rm -f "+cid);
    }
}
