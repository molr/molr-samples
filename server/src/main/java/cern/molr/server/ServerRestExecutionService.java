/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.server;

import cern.molr.exception.MissionExecutionNotAccepted;
import cern.molr.exception.NoAppropriateSupervisorFound;
import cern.molr.exception.UnknownMissionException;
import cern.molr.mole.supervisor.*;
import cern.molr.sample.mission.*;
import cern.molr.server.supervisor.RemoteMoleSupervisorImpl;
import cern.molr.mole.supervisor.MissionCommandRequest;
import io.netty.util.internal.ConcurrentSet;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gateway used for communication between server and supervisors
 * @author yassine-kr
 */
@Service
public class ServerRestExecutionService {

    private final ServerState registry = new ServerState();
    private final SupervisorsManager supervisorsManager;


    public ServerRestExecutionService(SupervisorsManager supervisorsManager) {
        //TODO remove this init code after implementing a deployment service

        registry.registerNewMission(RunnableHelloWriter.class.getName());
        registry.registerNewMission(IntDoubler.class.getName());
        registry.registerNewMission(Fibonacci.class.getName());
        //Just for testing, normally missions must be verified before deployment
        registry.registerNewMission(IncompatibleMission.class.getName());
        registry.registerNewMission(RunnableExceptionMission.class.getName());

        this.supervisorsManager=supervisorsManager;
    }


    public <I,O> String instantiate(String missionDefnClassName, I args)
            throws MissionExecutionNotAccepted,NoAppropriateSupervisorFound {
        String missionEId = makeEId();
        missionExists(missionDefnClassName);
        Optional<RemoteMoleSupervisor> optional= supervisorsManager.chooseSupervisor(missionDefnClassName);
        return optional.map((supervisor)->{
                Flux<MoleExecutionEvent> executionEventFlux = supervisor.instantiate(missionDefnClassName, args, missionEId);
                registry.registerNewMissionExecution(missionEId, supervisor, executionEventFlux);
                return missionEId;
        }).orElseThrow(() ->
                new NoAppropriateSupervisorFound("No appropriate supervisor found to execute such mission!"));

    }

    private void missionExists(String missionClassName)throws MissionExecutionNotAccepted{
        if(!registry.missionExists(missionClassName))
            throw new MissionExecutionNotAccepted("Mission not defined in MolR registry");
    }

    public Flux<MoleExecutionEvent> getFlux(String mEId) throws UnknownMissionException{
        Optional<Flux<MoleExecutionEvent>> optionalFlux = registry.getMissionExecutionFlux(mEId);
        return optionalFlux.orElseThrow(() -> new UnknownMissionException("No such mission running"));
    }

    private String makeEId() {
        return UUID.randomUUID().toString();
    }

    public static class ServerState {

        /**
         * Accepted missions
         */
        private ConcurrentSet<String> missionRegistry = new ConcurrentSet<>();
        private ConcurrentMap<String, Flux<MoleExecutionEvent>> missionExecutionRegistry =  new ConcurrentHashMap<>();
        private ConcurrentMap<String, RemoteMoleSupervisor> moleSupervisorRegistry =  new ConcurrentHashMap<>();

        public void registerNewMission(String missionClassName) {
            missionRegistry.add(missionClassName);
        }

        public void registerNewMissionExecution(String missionId,
                                                RemoteMoleSupervisor supervisor, Flux<MoleExecutionEvent> flux) {
            moleSupervisorRegistry.put(missionId, supervisor);
            missionExecutionRegistry.put(missionId, flux);
        }

        public boolean missionExists(String missionClassName) {
            return missionRegistry.contains(missionClassName);
        }

        public Optional<Flux<MoleExecutionEvent>> getMissionExecutionFlux(String missionEId){
            return Optional.ofNullable(missionExecutionRegistry.get(missionEId));
        }

        public Optional<RemoteMoleSupervisor> getMoleSupervisor(String missionExecutionId) {
            return Optional.ofNullable(moleSupervisorRegistry.get(missionExecutionId));
        }

        public void removeMissionExecution(String missionId){
            moleSupervisorRegistry.remove(missionId);
            missionExecutionRegistry.remove(missionId);
        }

    }

    public Mono<MoleExecutionCommandResponse> instruct(MissionCommandRequest commandRequest)
            throws UnknownMissionException {
        Optional<RemoteMoleSupervisor> optionalSupervisor = registry.getMoleSupervisor(commandRequest.getMissionId());
        return optionalSupervisor
                .orElseThrow(() -> new UnknownMissionException("No such mission running"))
                .instruct(commandRequest);
    }

    public String addSupervisor(String host,int port, List<String> missionsAccepted){
        RemoteMoleSupervisor moleSupervisor = new RemoteMoleSupervisorImpl(host,port);
        return supervisorsManager.addSupervisor(moleSupervisor,missionsAccepted);
    }

    public void removeSupervisor(String id){
        supervisorsManager.removeSupervisor(id);
    }

}
