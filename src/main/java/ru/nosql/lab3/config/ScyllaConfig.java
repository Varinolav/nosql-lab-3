package ru.nosql.lab3.config;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SessionBuilderConfigurer;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.DataCenterReplication;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ScyllaConfig extends AbstractCassandraConfiguration {
    private final CassandraProperties properties;


    @Override
    protected String getKeyspaceName() {
        return properties.getKeyspaceName();
    }

    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        return List.of(CreateKeyspaceSpecification
                .createKeyspace(properties.getKeyspaceName())
                .ifNotExists()
                .withNetworkReplication(DataCenterReplication.of(properties.getLocalDatacenter(), 3)));
    }
}
