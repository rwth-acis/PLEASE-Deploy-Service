package i5.las2peer.services.deployService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by adabru on 13.02.17.
 */
public class ResourceDistribution {
    private int user_limit_memory;
    private int user_limit_disk;
    private int user_limit_cpu;
    private DatabaseManager dm;

    public Map<String,Integer> parse(Map<String,String> m) {
        Map<String, Integer> res = new HashMap<>();
        for (Map.Entry<String, String> entry : m.entrySet())
            res.put(entry.getKey(), parse(entry.getValue()));
        return res;
    }
    private int parse(String size) {
        int factor = 1;
        switch(size.charAt(size.length()-1)) {
            case 'k': factor=1024; break;
            case 'K': factor=1000; break;
            case 'm': factor=1024*1024; break;
            case 'M': factor=1000*1000; break;
            case 'g': factor=1024*1024*1024; break;
            case 'G': factor=1000*1000*1000; break;
        }
        if (factor != 1) size = size.substring(0, size.length()-1);
        return (int)(factor * Double.parseDouble(size));
    }
    public ResourceDistribution(DatabaseManager dm, String user_limit_memory, String user_limit_disk, String user_limit_cpu) {
        this.dm = dm;
        this.user_limit_memory = parse(user_limit_memory);
        this.user_limit_disk = parse(user_limit_disk);
        this.user_limit_cpu = parse(user_limit_cpu);
    }

    public boolean checkUserAffordable(Map<String,Integer> limit, String userId) throws SQLException {
        Map<String,Integer> acc = account(userId);
        return limit.get("memory") + acc.get("usage_memory") <= acc.get("max_memory")
            && limit.get("disk") + acc.get("usage_disk") <= acc.get("max_disk")
            && limit.get("cpu") + acc.get("usage_cpu") <= acc.get("max_cpu");
    }
    public Map<String,Integer> account(String userId) throws SQLException {
        Map<String,Integer> acc = new HashMap<>();
        ResultSet rs = dm.query("SELECT COALESCE(SUM(memory),0) AS memory, COALESCE(SUM(disk),0) AS disk, COALESCE(SUM(cpu),0) AS cpu FROM deployments WHERE creator=?", userId);
        rs.next();
        acc.put("max_memory", user_limit_memory);
        acc.put("max_disk", user_limit_disk);
        acc.put("max_cpu", user_limit_cpu);
        acc.put("usage_memory", rs.getInt("memory"));
        acc.put("usage_disk", rs.getInt("disk"));
        acc.put("usage_cpu", rs.getInt("cpu"));
        return acc;
    }
}
