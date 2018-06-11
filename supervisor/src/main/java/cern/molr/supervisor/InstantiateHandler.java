package cern.molr.supervisor;

import cern.molr.commons.events.MissionExceptionEvent;
import cern.molr.commons.mission.AnnotatedMissionMaterializer;
import cern.molr.commons.mission.Mission;
import cern.molr.commons.mission.MissionMaterializer;
import cern.molr.commons.request.server.InstantiationRequest;
import cern.molr.commons.response.MissionEvent;
import cern.molr.commons.web.DataExchangeBuilder;
import cern.molr.supervisor.impl.supervisor.MoleSupervisorService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * WebSocket Spring Handler which handles websoscket requests for instantiating the MoleRunner. It returns a flux as
 * response
 *
 * @author yassine-kr
 */
@Component
public class InstantiateHandler implements WebSocketHandler {

    private final MoleSupervisorService supervisor;

    public InstantiateHandler(MoleSupervisorService supervisor) {
        this.supervisor = supervisor;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        return session.send(new DataExchangeBuilder<>
                (InstantiationRequest.class, MissionEvent.class)
                .setPreInput(session.receive().map(WebSocketMessage::getPayloadAsText))
                .setGenerator((request) -> {
                    MissionMaterializer materializer = new AnnotatedMissionMaterializer();
                    Mission mission = materializer.materialize(request.getMissionName());
                    return supervisor.instantiate(mission, request.getArgs(), request.getMissionExecutionId());
                })
                .setGeneratorExceptionHandler(MissionExceptionEvent::new).build().map(session::textMessage));

    }
}
