/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.supervisor;

import cern.molr.supervisor.api.address.AddressGetter;
import cern.molr.supervisor.api.web.MolrSupervisorToServer;
import cern.molr.supervisor.impl.address.ConfigurationAddressGetter;
import cern.molr.supervisor.impl.web.MolrSupervisorToServerImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;


/**
 * Remote entry point for the Supervisor
 * When the server is ready, it sends a register request to MolR Server
 *
 * @author nachivpn
 * @author yassine-kr
 */
@SpringBootApplication
public class RemoteSupervisorMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSupervisorMain.class);

    private final AddressGetter addressGetter;
    private final SupervisorConfig config;
    //The supervisor id generated by MolR server
    private String supervisorId;

    public RemoteSupervisorMain(ConfigurationAddressGetter addressGetter, SupervisorConfig config) {
        this.addressGetter = addressGetter;
        this.config = config;
        this.addressGetter.addListener(address -> {
            MolrSupervisorToServer client = new MolrSupervisorToServerImpl(config.getMolrHost(), config.getMolrPort());
            try {
                supervisorId = client.register(address.getHost(), address.getPort(), Arrays.asList(config
                        .getAcceptedMissions()));
            } catch (Exception error) {
                LOGGER.error("error while attempting to register in the MolR server [host: {}, port: {}]",
                        config.getMolrHost(), config.getMolrPort(), error);
            }
        });
    }

    /**
     * In order to specify a supervisor file configuration,
     * the args parameter should contain the element "--supervisor.fileConfig=file_name.properties"
     * If no path specified, the path "supervisor.properties" is used
     * If the used path file does not exist, default configuration values are used
     *
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(RemoteSupervisorMain.class, args);
    }

    @PreDestroy
    public void close() {
        MolrSupervisorToServer client = new MolrSupervisorToServerImpl(config.getMolrHost(), config.getMolrPort());

        try {
            client.unregister(supervisorId);
        } catch (Exception error) {
            LOGGER.error("error while attempting to unregister from MolR server [host: {}, port: {}]",
                    config.getMolrHost(), config.getMolrPort(), error);
        }
    }

    @Configuration
    @PropertySource(value = "classpath:${supervisor.fileConfig:supervisor.properties}",
            ignoreResourceNotFound = true)
    public static class SupervisorConfigurer {

        private final Environment env;

        public SupervisorConfigurer(Environment env) {
            this.env = env;
        }

        @Bean
        public SupervisorConfig getSupervisorConfig() {
            SupervisorConfig config = new SupervisorConfig();
            try {
                config.setMaxMissions(env.getProperty("maxMissions", Integer.class, 1));
            } catch (Exception error) {
                config.setMaxMissions(1);
            }
            //noinspection ConstantConditions
            config.setAcceptedMissions(Optional.ofNullable(env.getProperty("acceptedMissions"))
                    .map((s) -> s.split(",")).orElse(new String[]{}));

            config.setMolrHost(env.getProperty("molr.host", "http://localhost"));
            config.setMolrPort(env.getProperty("molr.port", Integer.class, 8000));

            config.setSupervisorHost(env.getProperty("supervisor.host"));
            config.setSupervisorPort(env.getProperty("supervisor.port", Integer.class, -1));

            config.setHeartbeatInterval(Duration.ofSeconds(env.getProperty("heartbeat.interval", Long.class, 20L)));

            return config;
        }

        @Bean
        public ObjectMapper getMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            return mapper;
        }
    }

}
