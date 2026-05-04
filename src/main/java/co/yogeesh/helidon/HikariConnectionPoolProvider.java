package co.yogeesh.helidon;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.common.config.Config;
import io.helidon.dbclient.jdbc.JdbcConnectionPool;
import io.helidon.dbclient.jdbc.spi.JdbcConnectionPoolProvider;

import java.sql.Connection;
import java.sql.SQLException;

public class HikariConnectionPoolProvider implements JdbcConnectionPoolProvider {

    @Override
    public String configKey() {
        return "hikari";
    }

    @Override
    public JdbcConnectionPool create(Config config, String name) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.get("url").asString().get());
        hikariConfig.setUsername(config.get("username").asString().get());
        hikariConfig.setPassword(config.get("password").asString().get());
        config.get("init-pool-size").asInt().ifPresent(hikariConfig::setMinimumIdle);
        config.get("max-pool-size").asInt().ifPresent(hikariConfig::setMaximumPoolSize);
        config.get("connection-timeout").asLong().ifPresent(hikariConfig::setConnectionTimeout);

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        return new JdbcConnectionPool() {
            @Override
            public Connection connection() {
                try {
                    return dataSource.getConnection();
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to obtain connection from HikariCP pool", e);
                }
            }
        };
    }
}
