package io.molr.samples.demo.coredemo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.molr.commons.domain.MissionParameter;
import io.molr.commons.domain.Placeholder;
import io.molr.commons.domain.Placeholders;
import io.molr.gui.fx.MolrGuiBaseConfiguration;
import io.molr.mole.core.api.Mole;
import io.molr.mole.core.local.LocalSuperMole;
import io.molr.mole.core.runnable.RunnableLeafsMission;
import io.molr.mole.core.runnable.RunnableLeafsMole;
import io.molr.mole.core.runnable.lang.RunnableLeafsMissionSupport;
import io.molr.mole.core.single.SingleNodeMission;
import io.molr.mole.core.single.SingleNodeMole;
import io.molr.mole.junit5.mole.JUnit5Mission;
import io.molr.mole.junit5.mole.JUnit5Mole;
import io.molr.mole.remote.rest.RestRemoteMole;
import io.molr.samples.demo.coredemo.junit5tests.MarsRoverAcceptanceTest;
import org.minifx.workbench.MiniFx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static io.molr.commons.domain.MissionParameter.required;

@Configuration
@Import(MolrGuiBaseConfiguration.class)
public class GsiDemo {

    @Bean
    public Mole mole(Set<SingleNodeMission<?>> singleNodeMissions, Set<RunnableLeafsMission> runnableLeafsMissions, Set<JUnit5Mission> junitMissions) {
        return new LocalSuperMole(ImmutableList.of(new SingleNodeMole(singleNodeMissions), new RunnableLeafsMole(runnableLeafsMissions), new JUnit5Mole(junitMissions), new RestRemoteMole("http://localhost:8800")));
    }

    @Bean
    public JUnit5Mission allTests() {
        return JUnit5Mission.fromNameAndPackage("All Tests", MarsRoverAcceptanceTest.class.getPackage().getName());
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
        Placeholder<String> name = Placeholder.aString("namer");
        return SingleNodeMission.from((in) -> System.out.println("Hello " + in.get(name)))
                .withName("Hello World")
                .withParameters(required(name));
    }

    public static void main(String... args) {
        MiniFx.launcher(GsiDemo.class).launch(args);
    }
}
