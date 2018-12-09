package cern.molr.sample.conf;

import cern.molr.sample.mission.*;
import io.molr.mole.core.single.SingleNodeMission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.molr.commons.domain.Placeholder.anInteger;

@Configuration
public class DemoSingleNodeMissionConfiguration {


    @Bean
    public SingleNodeMission<Integer> fibonacciMission() {
        return SingleNodeMission.from(Integer.class, new Fibonacci(), anInteger("input")).withName("fibonacci");
    }

    @Bean
    public SingleNodeMission<Integer> intDoublerMission() {
        return SingleNodeMission.from(Integer.class, new IntDoubler(), anInteger("input")).withName("intDoubler");
    }

    @Bean
    public SingleNodeMission<Void> invocationTargetExceptionMission() {
        return SingleNodeMission.from(new InvocationTargetExceptionMission()).withName("invocationTargetException");
    }

    @Bean
    public SingleNodeMission<Void> runnableExceptionMission() {
        return SingleNodeMission.from(new RunnableExceptionMission()).withName("runnableException");
    }

    @Bean
    public SingleNodeMission<Void> runnableHelloWriterMission() {
        return SingleNodeMission.from(new RunnableHelloWriter()).withName("runnableHelloWriter");
    }

}
