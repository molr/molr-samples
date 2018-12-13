package io.molr.samples.demo.coredemo;

import com.google.common.collect.ImmutableSet;
import io.molr.commons.domain.Placeholder;
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

import java.util.Set;

import static io.molr.commons.domain.MissionParameter.required;
import static io.molr.commons.domain.Placeholder.anInteger;

@Configuration
@Import(MolrGuiBaseConfiguration.class)
public class DemoCoreApplication {


    @Bean
    public SingleNodeMission<Void> helloWorld() {
        return SingleNodeMission.from(() -> System.out.println("Hello World")).withName("Hello World");
    }


    @Bean
    public SingleNodeMission<Void> helloYouMission() {
        Placeholder<String> name = Placeholder.aString("name");
        return SingleNodeMission.from((in) -> System.out.println("Hello " + in.get(name)))
                .withParameters(required(name))
                .withName("Hello You!");
    }

    @Bean
    public SingleNodeMission<Void> helloOutput() {
        Placeholder<String> name = Placeholder.aString("name");
        return SingleNodeMission.from(
                (in, out) -> out.emit("message", "Hello " + in.get(name)))
                .withParameters(required(name))
                .withName("Hello Output!");
    }

    @Bean
    public SingleNodeMission<String> helloReturn() {
        Placeholder<String> name = Placeholder.aString("name");
        return SingleNodeMission.from(String.class,
                in -> "Hello " + in.get(name))
                .withParameters(required(name))
                .withName("Hello Return!");
    }


    @Bean
    public SingleNodeMission<Integer> doubleMission() {
        Placeholder<Integer> inValue = anInteger("input");
        return SingleNodeMission.from(Integer.class,
                in -> in.get(inValue) / 0)
                .withParameters(required(inValue))
                .withName("Twice");
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
    public JUnit5Mission marsRoverTests() {
        return JUnit5Mission.fromNameAndClass("Test Mars Rover", MarsRoverAcceptanceTest.class);
    }

    @Bean
    public JUnit5Mission allTests() {
        return JUnit5Mission.fromNameAndPackage("All Tests", MarsRoverAcceptanceTest.class.getPackage().getName());
    }


    @Bean
    public Mole singleNodeMole(Set<SingleNodeMission<?>> missions, Set<RunnableLeafsMission> runnableLeafsMissions, Set<JUnit5Mission> junit5Missions) {
        return new LocalSuperMole(ImmutableSet.of(new SingleNodeMole(missions), new RunnableLeafsMole(runnableLeafsMissions), new JUnit5Mole(junit5Missions), new RestRemoteMole("http://localhost:8800")));
    }


    public static void main(String... args) {
        MiniFx.launcher(DemoCoreApplication.class).launch(args);
    }

}
