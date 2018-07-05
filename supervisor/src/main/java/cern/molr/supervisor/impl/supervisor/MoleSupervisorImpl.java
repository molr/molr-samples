package cern.molr.supervisor.impl.supervisor;

import cern.molr.commons.api.exception.UnknownMissionException;
import cern.molr.commons.api.mission.Mission;
import cern.molr.commons.api.request.MissionCommandRequest;
import cern.molr.commons.api.response.CommandResponse;
import cern.molr.commons.api.response.MissionEvent;
import cern.molr.commons.api.response.SupervisorState;
import cern.molr.commons.events.MissionExceptionEvent;
import cern.molr.commons.events.MissionControlEvent;
import cern.molr.supervisor.api.session.MissionSession;
import cern.molr.supervisor.api.supervisor.MoleSupervisor;
import cern.molr.supervisor.api.supervisor.SupervisorSessionsManager;
import cern.molr.supervisor.impl.spawner.JVMSpawner;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static cern.molr.commons.events.MissionControlEvent.Event.SESSION_TERMINATED;

/**
 * An Implementation of {@link MoleSupervisor} which manages mission executions which does not manage the state
 *
 * @author yassine-kr
 */
public class MoleSupervisorImpl implements MoleSupervisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoleSupervisorImpl.class);
    protected SupervisorSessionsManager sessionsManager = new SupervisorSessionsManagerImpl();

    /* TODO there is a moment between instantiating and adding the listener to the controller, the session instantiated
     * TODO event could be missed, is this behaviour acceptable?

     */
    @Override
    public <I> Publisher<MissionEvent> instantiate(Mission mission, I missionArguments, String missionId) {
        try {
            MissionSession session;
            JVMSpawner<I> spawner = new JVMSpawner<>();
            session = spawner.spawnMoleRunner(mission, missionArguments);
            sessionsManager.addSession(missionId, session);
            session.getController().addMoleExecutionListener((event) -> {
                if (event instanceof MissionControlEvent && ((MissionControlEvent) event).getEvent().equals(SESSION_TERMINATED)) {
                    sessionsManager.removeSession(session);
                    try {
                        session.getController().close();
                    } catch (IOException error) {
                        LOGGER.error("error while trying to close a session", error);
                    }
                }
            });
            return Flux.create((FluxSink<MissionEvent> emitter) -> {
                session.getController().addMoleExecutionListener((event) -> {
                    emitter.next(event);
                    LOGGER.info("Event Notification from session controller: {}", event);
                    if (event instanceof MissionControlEvent && ((MissionControlEvent) event).getEvent().equals(SESSION_TERMINATED))
                        emitter.complete();
                });
            });
        } catch (Exception error) {
            LOGGER.error("error while trying to spawn the mission on the MoleRunner", error);
            return Flux.just(new MissionExceptionEvent(error));
        }
    }

    @Override
    public Publisher<CommandResponse> instruct(MissionCommandRequest commandRequest) {
        return Mono.just(sessionsManager.getSession(commandRequest.getMissionId()).map((session) -> {
            CommandResponse response = session.getController().sendCommand(commandRequest.getCommand());
            LOGGER.info("Receiving command response from the session controller: {}", response);
            return response;
        }).orElse(new CommandResponse.CommandResponseFailure(new UnknownMissionException("No such mission running"))));
    }

    @Override
    public SupervisorState getSupervisorState() {
        return null;
    }
}
