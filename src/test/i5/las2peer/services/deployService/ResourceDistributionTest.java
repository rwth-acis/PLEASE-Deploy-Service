package i5.las2peer.services.deployService;

import org.junit.Test;

import javax.json.JsonStructure;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by adabru on 14.02.17.
 */
public class ResourceDistributionTest {
    private JsonStructure json(Object s) { return JsonHelper.parse(((String)s).replaceAll("'","\"")); }
    private Map<String,Object> map(String s) { return (Map) JsonHelper.toCollection(json(s)); }

    @Test
    public void all() throws Exception {
        DatabaseManager dm = new DatabaseManager("sa", "",
                "jdbc:h2:mem:resourcedistributiontest_1;DB_CLOSE_DELAY=-1", "testSchema",
                "./etc/db_migration", "./database");
        ResourceDistribution rd = new ResourceDistribution(dm, "10k", "10K", "1000");
        String userId = "max";
        dm.update("INSERT INTO deployments VALUES (7,7,7,'max',2000,4000,333)");
        dm.update("INSERT INTO deployments VALUES (8,8,8,'max',3000,5000,444)");

        assertEquals(map("{'max_memory':10240,'max_disk':10000,'max_cpu':1000,'usage_memory':5000,'usage_disk':9000,'usage_cpu':777}"), rd.account("max"));
        assertEquals(true, rd.checkUserAffordable((Map)map("{'memory':5240,'disk':1000,'cpu':223}"), "max"));
        assertEquals(false, rd.checkUserAffordable((Map)map("{'memory':5241,'disk':1000,'cpu':223}"), "max"));
        assertEquals(false, rd.checkUserAffordable((Map)map("{'memory':5240,'disk':1001,'cpu':223}"), "max"));
        assertEquals(false, rd.checkUserAffordable((Map)map("{'memory':5240,'disk':1000,'cpu':224}"), "max"));
        assertEquals(true, rd.checkUserAffordable((Map)map("{'memory':5241,'disk':1001,'cpu':224}"), "maria"));
    }
}