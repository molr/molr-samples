package cern.molr.sample.states;

import cern.molr.commons.api.request.MissionCommand;
import cern.molr.commons.api.response.MissionState;
import cern.molr.sample.mole.SequenceMole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A state generated by {@link SequenceMole}
 *
 * @author yassine-kr
 */
public class SequenceMissionState extends MissionState {

    /**
     * The number of the task that the mole is running or waiting for.
     */
    private final int taskNumber;
    private final State state;

    public SequenceMissionState(@JsonProperty("level") Level level, @JsonProperty("status") String status,
                                @JsonProperty("possibleCommands") List<MissionCommand> possibleCommands, @JsonProperty("taskNumber") int
                                        taskNumber, @JsonProperty("state") State state) {
        super(level, status, possibleCommands);
        this.taskNumber = taskNumber;
        this.state = state;
    }

    public SequenceMissionState(String status, List<MissionCommand> possibleCommands, int taskNumber, State state) {
        super(Level.MOLE, status, possibleCommands);
        this.taskNumber = taskNumber;
        this.state = state;
    }

    public int getTaskNumber() {
        return taskNumber;
    }

    public State getState() {
        return state;
    }

    public enum State {
        WAITING,
        TASK_RUNNING,
        TASKS_FINISHED,
        RUNNING_AUTOMATIC
    }
}
