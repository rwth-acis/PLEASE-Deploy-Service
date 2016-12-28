package i5.las2peer.services.deployService;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelper {
    private DockerHelper dh;

    public DeployServiceHelper(DockerHelper dh){ this.dh = dh; }

    public Map<String,Object> guardedConfig(JsonObject json) {
        // TODO get limits for authenticated user
        int user_max_memory = (int)50e6;
        int user_max_disk = (int)250e6;
        int user_max_cpu = 512;

        Map<String,Object> config = new HashMap<>();

        config.put("memory", json.getInt("memory", user_max_memory));
        if ((int)config.get("memory") > user_max_memory)
            ;// memory request too high
        config.put("disk", json.getInt("disk", user_max_disk));
        if ((int)config.get("disk") > user_max_disk)
            ;// disk request too high
        config.put("cpu", json.getInt("cpu", user_max_cpu));
        if ((int)config.get("cpu") > user_max_cpu)
            ;// cpu request too high
        config.put("base", json.getString("base", "ubuntu"));
        config.put("command", json.getString("command", "\"echo hello world!\""));

        return config;
    }

    public String startApp(JsonObject json) throws IOException {
        Map<String, Object> config = guardedConfig(json);
        String cid = dh.startContainer(config);
        return cid;
    }

    public String updateApp(String cid_old, String cid_new) throws IOException {
        dh.updateContainer(cid_old, cid_new);
        return "yolo";
    }


    public static JsonStructure stringToJson(String s) {
        JsonReader jr = Json.createReader(new StringReader(s));
        JsonStructure js = jr.read();
        jr.close();

        return js;
    }
}
