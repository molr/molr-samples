package io.molr.samples.demo.coredemo.server;

import com.google.common.collect.ImmutableSet;
import io.molr.commons.domain.Placeholder;
import io.molr.mole.core.api.Mole;
import io.molr.mole.core.local.LocalSuperMole;
import io.molr.mole.core.runnable.RunnableLeafsMission;
import io.molr.mole.core.runnable.RunnableLeafsMole;
import io.molr.mole.core.runnable.lang.RunnableLeafsMissionSupport;
import io.molr.mole.core.single.SingleNodeMission;
import io.molr.mole.core.single.SingleNodeMole;
import io.molr.mole.server.conf.SingleMoleRestServiceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static io.molr.commons.domain.MissionParameter.required;

@SpringBootApplication
@Import(SingleMoleRestServiceConfiguration.class)
public class ServerStefan {

    @Bean
    public Mole mole(Set<SingleNodeMission<?>> singleNodeMissions, Set<RunnableLeafsMission> runnableLeafsMissions) {
        return new LocalSuperMole(ImmutableSet.of(new RunnableLeafsMole(runnableLeafsMissions), new SingleNodeMole(singleNodeMissions)));
    }

    @Bean
    public RunnableLeafsMission landTheFalcon() {
        return new RunnableLeafsMissionSupport() {
            {
                Placeholder<String> landingLocation = Placeholder.aString("landingLocation");

                mandatory(landingLocation);

                sequential("Land Falcon", b -> {
                    b.run("Locate target", (in) -> System.out.println("Locating " + in.get(landingLocation)));
                    b.parallel("land", land -> {
                        land.run("entry burn", () -> System.out.println("Switching on Thrusters for entry burn"));
                        land.run("steer to landing target", (in) -> System.out.println("Steer to " + in.get(landingLocation)));
                    });
                    b.run("final burn", () -> System.out.println("Increasing thrust for final burn"));
                });
            }
        }.build();
    }


    @Bean
    public SingleNodeMission<Void> helloWorldMission() {
        Placeholder<String> name = Placeholder.aString("name");

        return SingleNodeMission.from((in) -> System.out.println("Hello " + in.get(name)))
                .withName("Hello Guy")
                .withParameters(required(name));
    }


    public static void main(String... args) {
        if (System.getProperty("server.port") == null) {
            System.setProperty("server.port", "8889");
        }
        SpringApplication.run(ServerStefan.class);
    }
}
