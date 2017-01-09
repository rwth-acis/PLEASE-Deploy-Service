package i5.las2peer.services.deployService;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by adabru on 02.01.17.
 */
public class AppMetadataHelperTest {
    @Test
    public void getApp() throws Exception {
        AppMetadataHelper amh = new AppMetadataHelper("blabla"){
            @Override
            public InputStream openHttpStream(String url) {
                assertEquals("blabla/apps/456", url);
                InputStream is = null;
                try {
                    is = new ByteArrayInputStream(("{" +
                                "\"a\": [1, \"2\", true, false]," +
                                "\"b\": null" +
                            "}").getBytes("utf-8"));
                } catch (UnsupportedEncodingException e) {
                    fail(e.getMessage() + e.toString());
                }
                return is;
            }
        };
        Object res = amh.getApp(456);
        Map<String, Object> t1 = (Map<String, Object>) res;
        List<Object> t2 = (List<Object>) t1.get("a");
        double t3 = (double) t2.get(0);
        String t4 = (String) t2.get(1);
        boolean t5 = (boolean) t2.get(2);
        boolean t6 = (boolean) t2.get(3);
        assertEquals(null, t1.get("b"));
    }
}