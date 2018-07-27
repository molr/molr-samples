package cern.molr.server;

import cern.molr.commons.api.request.server.SupervisorsInfoRequest;
import cern.molr.commons.api.response.SupervisorInfo;
import cern.molr.commons.web.DataProcessorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * WebSocket Spring Handler which handles websoscket requests for getting the supervisors info stream, it uses WebFlux.
 *
 * @author yassine-kr
 */
@Component
public class SupervisorsInfoHandler implements WebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisorsInfoHandler.class);

    private final ServerExecutionService service;

    public SupervisorsInfoHandler(ServerExecutionService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        LOGGER.info("session created for a request received from the client: {}", session.getHandshakeInfo().getUri());

        return session.send(new DataProcessorBuilder<SupervisorsInfoRequest, SupervisorInfo>(SupervisorsInfoRequest.class)
                .setPreInput(session.receive().map(WebSocketMessage::getPayloadAsText))
                .setGenerator((request) -> service.getSupervisorsInfoStream())
                .setGeneratorExceptionHandler(null)
                .build().map(session::textMessage));
    }
}
