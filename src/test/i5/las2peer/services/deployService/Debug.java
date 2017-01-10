package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Scanner;

/**
 * Created by adabru on 10.01.17.
 */
public class Debug {
    private static Logger l = LoggerFactory.getLogger(DockerHelper.class.getName());

    // copied from DockerHelper.java
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

    public static void printAllRunningContainerIps() throws IOException {
        String ips = executeProcess("docker inspect --format='{{.NetworkSettings.Networks.please.GlobalIPv6Address}}' $(docker ps -q)");
        l.info("Currently running containers:\n"+ips);
    }

//    public static String dumpDatabase()

}
