/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.client.api;

import cern.molr.commons.request.MissionCommand;
import cern.molr.commons.response.CommandResponse;
import cern.molr.commons.response.MissionEvent;
import org.reactivestreams.Publisher;

/**
 * Controller used to control a mission execution
 *
 * @author yassine-kr
 */
public interface ClientMissionController {

    /**
     * A method which gets an events stream generated by the supervisor where the mission is being executed
     *
     * @return a stream of events triggered by the mission execution
     */
    Publisher<MissionEvent> getEventsStream();


    /**
     * A method which sends a {@link MissionCommand} to the supervisor where the mission is being executed
     *
     * @param command the command to execute
     * @return a stream of one element which tells whether the command was accepted or not
     */
    Publisher<CommandResponse> instruct(MissionCommand command);
}
