package cern.molr.test.server;

import cern.molr.commons.api.request.MissionCommandRequest;
import cern.molr.commons.api.request.client.ServerInstantiationRequest;
import cern.molr.commons.api.response.CommandResponse;
import cern.molr.commons.api.response.InstantiationResponse;
import cern.molr.commons.api.response.MissionEvent;
import cern.molr.commons.commands.Start;
import cern.molr.commons.events.MissionFinished;
import cern.molr.commons.events.MissionStarted;
import cern.molr.commons.events.SessionInstantiated;
import cern.molr.commons.events.SessionTerminated;
import cern.molr.commons.impl.web.MolrWebClientImpl;
import cern.molr.commons.impl.web.MolrWebSocketClientImpl;
import cern.molr.commons.web.MolrConfig;
import cern.molr.sample.mission.Fibonacci;
import cern.molr.server.ServerMain;
import cern.molr.supervisor.RemoteSupervisorMain;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Class for testing the server Api.
 * Each test can fail if the thread finishes before getting all results from impl,
 * in that case sleep duration should be increased.
 */
public class ServerTest {

    private ConfigurableApplicationContext serverContext;
    private ConfigurableApplicationContext supervisorContext;
    private MolrWebClientImpl client = new MolrWebClientImpl("http://localhost", 8000);
    private MolrWebSocketClientImpl clientSocket = new MolrWebSocketClientImpl("http://localhost", 8000);

    @Before
    public void initServers() {
        serverContext = SpringApplication.run(ServerMain.class, new String[]{"--server.port=8000"});

        supervisorContext = SpringApplication.run(RemoteSupervisorMain.class,
                new String[]{"--server.port=8056", "--molr.host=http://localhost", "--molr.port=8000"});
    }

    @After
    public void exitServers() {
        SpringApplication.exit(supervisorContext);
        SpringApplication.exit(serverContext);
    }

    @Test
    public void instantiateTest() throws Exception {

        CountDownLatch instantiateSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(4);

        List<MissionEvent> events = new ArrayList<>();
        List<CommandResponse> commandResponses = new ArrayList<>();

        ServerInstantiationRequest<Integer> request = new ServerInstantiationRequest<>(
                Fibonacci.class.getCanonicalName(), 23);

        InstantiationResponse response = client.post(MolrConfig.INSTANTIATE_PATH, ServerInstantiationRequest.class, request,
                InstantiationResponse.class).block();

        Assert.assertEquals(InstantiationResponse.InstantiationResponseSuccess.class, response.getClass());


        clientSocket.receiveFlux(MolrConfig.EVENTS_STREAM_PATH, MissionEvent.class, response.getResult().getMissionId())
                .doOnError(Throwable::printStackTrace).subscribe(
                (event) -> {
                    System.out.println("event: " + event);
                    events.add(event);
                    endSignal.countDown();

                    if (event instanceof SessionInstantiated) {
                        instantiateSignal.countDown();
                    }
                });


        instantiateSignal.await();
        System.out.println("sending start command");

        clientSocket.receiveMono(MolrConfig.INSTRUCT_PATH, CommandResponse.class, new MissionCommandRequest(response
                .getResult().getMissionId(), new Start())).doOnError
                (Throwable::printStackTrace).subscribe((result) -> {
            System.out.println("response to start: " + result);
            commandResponses.add(result);
        });

        endSignal.await();

        Assert.assertEquals(4, events.size());
        Assert.assertEquals(SessionInstantiated.class, events.get(0).getClass());
        Assert.assertEquals(MissionStarted.class, events.get(1).getClass());
        Assert.assertEquals(MissionFinished.class, events.get(2).getClass());
        Assert.assertEquals(SessionTerminated.class, events.get(3).getClass());
        Assert.assertEquals(1, commandResponses.size());
        Assert.assertEquals(CommandResponse.CommandResponseSuccess.class, commandResponses.get(0).getClass());
    }
}