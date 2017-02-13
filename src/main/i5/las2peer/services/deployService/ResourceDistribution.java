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
        return factor * (int)Double.parseDouble(size);
    }
    public ResourceDistribution(DatabaseManager dm, String user_limit_memory, String user_limit_disk, String user_limit_cpu) {
        this.dm = dm;
        this.user_limit_memory = parse(user_limit_memory);
        this.user_limit_disk = parse(user_limit_disk);
        this.user_limit_cpu = parse(user_limit_cpu);
    }

    public boolean checkUserAffordable(Map<String,String> limit, String userId) throws SQLException {
        ResultSet rs = dm.query("SELECT SUM(memory) AS memory, SUM(disk) AS disk, SUM(cpu) AS cpu FROM deployments WHERE user=?", userId);
        return parse(limit.get("memory")) <= user_limit_memory - rs.getInt("memory")
            && parse(limit.get("disk")) <= user_limit_disk - rs.getInt("disk")
            && parse(limit.get("cpu")) <= user_limit_cpu - rs.getInt("cpu");
    }
    public Map<String,Integer> account(String userId) throws SQLException {
        Map<String,Integer> acc = new HashMap<>();
        ResultSet rs = dm.query("SELECT SUM(memory) AS memory, SUM(disk) AS disk, SUM(cpu) AS cpu FROM resources WHERE user=?", userId);
        acc.put("max_memory", user_limit_memory);
        acc.put("max_disk", user_limit_disk);
        acc.put("max_cpu", user_limit_cpu);
        acc.put("usage_memory", rs.getInt("memory"));
        acc.put("usage_disk", rs.getInt("disk"));
        acc.put("usage_cpu", rs.getInt("cpu"));
        return acc;
    }
}
