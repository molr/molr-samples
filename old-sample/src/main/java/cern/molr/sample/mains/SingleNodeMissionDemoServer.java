package cern.molr.sample.mains;

import cern.molr.sample.conf.DemoSingleNodeMissionConfiguration;
import org.molr.mole.core.single.conf.SingleNodeMoleConfiguration;
import org.molr.mole.server.conf.LocalMolrConfiguration;
import org.molr.mole.server.rest.MolrMoleRestService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({MolrMoleRestService.class, SingleNodeMoleConfiguration.class, DemoSingleNodeMissionConfiguration.class, LocalMolrConfiguration.class})
public class SingleNodeMissionDemoServer {

    public static void main(String... args) {
        SpringApplication.run(SingleNodeMissionDemoServer.class);
    }

}
