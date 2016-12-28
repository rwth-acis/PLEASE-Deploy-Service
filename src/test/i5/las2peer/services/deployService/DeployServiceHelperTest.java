package i5.las2peer.services.deployService;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import sun.rmi.runtime.Log;

import javax.json.JsonObject;

import java.util.logging.*;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by adabru on 26.12.16.
 */
public class DeployServiceHelperTest {
    static class RegexMatcher extends TypeSafeMatcher<String> {
        Pattern p;
        public RegexMatcher(String regex){p=Pattern.compile(regex);}
        @Override public boolean matchesSafely(String s) {return p.matcher(s).matches();}
        @Override public void describeTo(Description description){description.appendValue(p);}
    }

    @Test
    public void startContainer() throws Exception {
        Logger l = MyLogger.getLogger();
        System.out.println("\u26a0 jk\"");
        l.info("\u26a0 jk");
//        DeployServiceHelper dsh = new DeployServiceHelper();
//        JsonObject jo = (JsonObject) dsh.stringToJson("{\"command\":\"echo jolo\"}");
//        String cid = dsh.startContainer(jo);
    }

}