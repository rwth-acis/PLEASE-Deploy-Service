package i5.las2peer.services.deployService;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.JsonObject;

import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelperTest {
    static Logger l = LoggerFactory.getLogger("");

    static class RegexMatcher extends TypeSafeMatcher<String> {
        Pattern p;
        public RegexMatcher(String regex){p=Pattern.compile(regex);}
        @Override public boolean matchesSafely(String s) {return p.matcher(s).matches();}
        @Override public void describeTo(Description description){description.appendValue(p);}
    }

    @Test
    public void startContainer() throws Exception {
        System.out.println("\u26a0 jk\"");
        l.error("uns");
        l.info("\u26a0 jk");
        l.warn("jaja");
        l.debug("hola");
//        DeployServiceHelper dsh = new DeployServiceHelper();
//        JsonObject jo = (JsonObject) dsh.stringToJson("{\"command\":\"echo jolo\"}");
//        String cid = dsh.startContainer(jo);
    }

}