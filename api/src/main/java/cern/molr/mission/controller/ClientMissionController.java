/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.mission.controller;

import cern.molr.mole.supervisor.MoleExecutionCommand;
import cern.molr.mole.supervisor.MoleExecutionEvent;
import cern.molr.mole.supervisor.MoleExecutionCommandResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller used to control a mission execution
 * @author yassine-kr
 */
public interface ClientMissionController {

     /**
      * A method which gets an events flux generated by the supervisor where the mission is being executed
      * @return
      */
     Flux<MoleExecutionEvent> getFlux();


     /**
      * A method which send a command to the supervisor where the mission is being executed
      * @param command
      * @return a future response which says whether the command was accepted or not
      */
     Mono<MoleExecutionCommandResponse> instruct(MoleExecutionCommand command);
}
