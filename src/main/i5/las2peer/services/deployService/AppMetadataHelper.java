package i5.las2peer.services.deployService;

import javax.json.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by adabru on 30.12.16.
 */
public class AppMetadataHelper {
    private String serviceUrl;

    public AppMetadataHelper(String url) {
        serviceUrl = url;
    }

    public static Object jsonToCollection(JsonValue json) {
        if (json instanceof JsonObject) {
            Map<String, Object> res = new HashMap<>();
            ((JsonObject) json).forEach(
                    (key, value) ->
                            res.put(key, jsonToCollection(value))
            );
            return res;
        } else if (json instanceof JsonArray) {
            List<Object> res = new LinkedList<>();
            ((JsonArray)json).forEach(
                    (value) ->
                            res.add(jsonToCollection(value))
            );
            return res;
        } else if (json instanceof JsonNumber) {
            return ((JsonNumber) json).doubleValue();
        } else if (json instanceof JsonString) {
            return ((JsonString)json).getString();
        } else if (json.equals(JsonValue.FALSE)) {
            return false;
        } else if (json.equals(JsonValue.TRUE)) {
            return true;
        } else /*if (json.equals(JsonValue.NULL))*/ {
            return null;
        }
    }

    public Map<String, Object> getApp(int appId) {
        Map<String, Object> app = null;
        try (
            JsonReader jr = Json.createReader(openHttpStream(serviceUrl+"/apps/"+appId));
        ) {
            app = (Map<String, Object>) jsonToCollection(jr.read());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return app;
    }

    // method to be overwritten for mocking
    public InputStream openHttpStream(String url) throws IOException {
        return new URL(url).openStream();
    }
}
