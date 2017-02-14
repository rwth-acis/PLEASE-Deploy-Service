package i5.las2peer.services.deployService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import javax.json.stream.JsonGenerationException;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by adabru on 01.02.17.
 */
public class JsonHelper {
    private static Logger l = LoggerFactory.getLogger(JsonHelper.class.getName());

    public static JsonStructure parse(String s) {
        try {
            JsonReader jr = Json.createReader(new StringReader(s));
            JsonStructure js = jr.read();
            jr.close();

            return js;
        } catch(JsonParsingException e) {
            l.error(s);
            throw e;
        }
    }
    public static Object toCollection(String json) {
        return toCollection(parse(json));
    }
    public static Object toCollection(JsonValue json) {
        if (json instanceof JsonObject) {
            Map<String, Object> res = new HashMap<>();
            ((JsonObject) json).forEach(
                    (key, value) ->
                            res.put(key, toCollection(value))
            );
            return res;
        } else if (json instanceof JsonArray) {
            List<Object> res = new LinkedList<>();
            ((JsonArray) json).forEach(
                    (value) ->
                            res.add(toCollection(value))
            );
            return res;
        } else if (json instanceof JsonNumber) {
            if (((JsonNumber) json).isIntegral())
                return ((JsonNumber) json).intValue();
            else
                return ((JsonNumber) json).doubleValue();
        } else if (json instanceof JsonString) {
            return ((JsonString) json).getString();
        } else if (json.equals(JsonValue.FALSE)) {
            return false;
        } else if (json.equals(JsonValue.TRUE)) {
            return true;
        } else /*if (json.equals(JsonValue.NULL))*/ {
            return null;
        }
    }
    public static String toString(Object o) {
        try {
            if ((o instanceof Map) || (o instanceof List)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                JsonGenerator jg = Json.createGenerator(baos);
                writeToGenerator(o, null, jg);
                jg.close();
                return baos.toString();
            } else if (o instanceof String) {
                return Json.createArrayBuilder().add((String)o).build().get(0).toString();
            }
        } catch(JsonGenerationException e) {
            l.error((o == null) ? "null" : o.toString());
            throw e;
        }
        return null;
    }
    private static void writeToGenerator(Object o, String key, JsonGenerator jg) {
        if (o instanceof Map) {
            if (key != null) jg.writeStartObject(key); else jg.writeStartObject();
            for (Map.Entry<String, Object> entry : ((Map<String,Object>)o).entrySet())
                writeToGenerator(entry.getValue(), entry.getKey(), jg);
            jg.writeEnd();
        } else if (o instanceof List) {
            if (key != null) jg.writeStartArray(key); else jg.writeStartArray();
            for (Object element : (List)o)
                writeToGenerator(element, null, jg);
            jg.writeEnd();
        } else if (o instanceof Integer)
            if (key != null) jg.write(key, (Integer)o); else jg.write((Integer)o);
        else if (o instanceof Boolean)
            if (key != null) jg.write(key, (Boolean)o); else jg.write((Boolean)o);
        else if (o instanceof Double)
            if (key != null) jg.write(key, (Double)o); else jg.write((Double)o);
        else if (o instanceof String)
            if (key != null) jg.write(key, (String)o); else jg.write((String)o);
        else if (o == null)
            if (key != null) jg.writeNull(key); else jg.writeNull();
    }
}
