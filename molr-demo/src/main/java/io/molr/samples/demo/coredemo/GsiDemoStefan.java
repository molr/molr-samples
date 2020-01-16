package io.molr.samples.demo.coredemo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.molr.commons.domain.MissionParameter;
import io.molr.commons.domain.Placeholder;
import io.molr.commons.util.MissionParameters;
import io.molr.gui.fx.MolrGuiBaseConfiguration;
import io.molr.mole.core.api.Mole;
import io.molr.mole.core.local.LocalSuperMole;
import io.molr.mole.core.runnable.RunnableLeafsMission;
import io.molr.mole.core.runnable.RunnableLeafsMole;
import io.molr.mole.core.runnable.lang.RunnableLeafsMissionSupport;
import io.molr.mole.core.single.SingleNodeMission;
import io.molr.mole.core.single.SingleNodeMissions;
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

@Configuration
@Import(MolrGuiBaseConfiguration.class)
public class GsiDemoStefan {

    @Bean
    public Mole mole() {
        return new RestRemoteMole("http://localhost:8889");
    }

    public static void main(String... args) {
        MiniFx.launcher(GsiDemoStefan.class).launch(args);
    }
}
