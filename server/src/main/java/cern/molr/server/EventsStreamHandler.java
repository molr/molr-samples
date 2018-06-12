package cern.molr.server;

import cern.molr.commons.api.response.MissionEvent;
import cern.molr.commons.events.MissionExceptionEvent;
import cern.molr.commons.web.DataExchangeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * WebSocket Spring Handler which handles websoscket requests for getting the events stream concerning a mission
 * execution, it uses WebFlux
 *
 * @author yassine-kr
 */
@Component
public class EventsStreamHandler implements WebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventsStreamHandler.class);

    private final ServerRestExecutionService service;

    public EventsStreamHandler(ServerRestExecutionService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        LOGGER.info("session created for a request received from the client: {}", session.getHandshakeInfo().getUri());

        return session.send(new DataExchangeBuilder<>
                (String.class, MissionEvent.class)
                .setPreInput(session.receive().map(WebSocketMessage::getPayloadAsText))
                .setGenerator(service::getEventsStream)
                .setGeneratorExceptionHandler(MissionExceptionEvent::new)
                .build().map(session::textMessage));
    }
}