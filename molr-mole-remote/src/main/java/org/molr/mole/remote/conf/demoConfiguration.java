package org.molr.mole.remote.conf;

import org.molr.mole.core.api.Mole;
import org.molr.mole.remote.rest.RestRemoteMole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class demoConfiguration {

    @Bean
    public Mole mole(){
        return new RestRemoteMole("http://localhost:8800");
    }
}
