import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import java.util.Map;
import java.util.Properties;

/** Read-only SDK check. Run each attempt with a fresh cache and private log directory. */
public class NacosConfigProbe {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        for (String[] binding : new String[][] {
                {"serverAddr", "NACOS_ADDR"}, {"namespace", "NACOS_NAMESPACE"},
                {"username", "NACOS_USERNAME"}, {"password", "NACOS_PASSWORD"}}) {
            String value = System.getenv(binding[1]);
            if (value == null || value.isBlank()) {
                System.err.println("Missing required variable: " + binding[1]);
                System.exit(2);
            }
            properties.setProperty(binding[0], value);
        }
        boolean reject = args.length == 1 && args[0].equals("reject");
        ConfigService config = null;
        NamingService naming = null;
        try {
            config = NacosFactory.createConfigService(properties);
            Map<String, String[]> services = Map.of(
                    "gateway", new String[] {"redis", "mysql", "map", "rabbitmq"},
                    "admin", new String[] {"redis", "caffeine", "mysql", "map", "rabbitmq"},
                    "file", new String[] {},
                    "portal", new String[] {"redis", "caffeine", "mysql"});
            for (var service : services.entrySet()) {
                check(config, "lien-" + service.getKey() + "-prd.yaml");
                for (String shared : service.getValue()) {
                    check(config, "share-" + shared + "-prd.yaml");
                }
            }
            if (reject) {
                throw new IllegalStateException("Invalid credentials were not rejected");
            }
            naming = NacosFactory.createNamingService(properties);
            for (String service : services.keySet()) {
                naming.getAllInstances("lien-" + service, "DEFAULT_GROUP", false);
            }
            System.out.println("PASS: four services' config/shared-config reads and discovery queries");
        } catch (NacosException exception) {
            if (reject && exception.getErrCode() == 403) {
                System.out.println("PASS: invalid credentials rejected (403)");
            } else {
                System.err.println("FAIL: Nacos SDK error code " + exception.getErrCode());
                System.exit(1);
            }
        } finally {
            if (config != null) config.shutDown();
            if (naming != null) naming.shutDown();
        }
    }

    private static void check(ConfigService config, String dataId) throws NacosException {
        String value = config.getConfig(dataId, "DEFAULT_GROUP", 10000);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing config: " + dataId);
        }
    }
}
