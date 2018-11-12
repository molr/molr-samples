package org.molr.mole.core.tree;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.molr.commons.domain.Block;
import org.molr.commons.domain.Result;
import org.molr.commons.domain.RunState;
import org.molr.commons.domain.Strand;
import org.molr.commons.domain.StrandCommand;
import org.molr.mole.core.tree.exception.RejectedCommandException;
import org.molr.mole.core.tree.exception.StrandExecutorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;
import static org.molr.commons.domain.RunState.FINISHED;
import static org.molr.commons.domain.RunState.PAUSED;
import static org.molr.commons.domain.RunState.RUNNING;
import static org.molr.commons.domain.StrandCommand.PAUSE;
import static org.molr.commons.domain.StrandCommand.RESUME;
import static org.molr.commons.domain.StrandCommand.SKIP;
import static org.molr.commons.domain.StrandCommand.STEP_INTO;
import static org.molr.commons.domain.StrandCommand.STEP_OVER;
import static org.molr.mole.core.utils.ThreadFactories.namedThreadFactory;

/**
 * Concurrent (non-blocking) implementation of a {@link StrandExecutor}. Internally all the operations run on a separate
 * thread avoiding to block the {@link #instruct(StrandCommand)} method (or any other for that matter).
 * <p>
 * TODO #1 concurrency should be ok
 * TODO #2 check atomicity of public operations
 */
public class ConcurrentStrandExecutor implements StrandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentStrandExecutor.class);
    private static final int EXECUTOR_SLEEP_MS_IDLE = 50;
    private static final int EXECUTOR_SLEEP_MS_DEFAULT = 10;
    private static final int EXECUTOR_SLEEP_MS_WAITING_FOR_CHILDREN = 25;

    private final ExecutorService executor;
    private final LinkedBlockingQueue<StrandCommand> commandQueue;
    private final TreeStructure structure;
    private final Strand strand;
    private final StrandFactory strandFactory;
    private final StrandExecutorFactory strandExecutorFactory;
    private final LeafExecutor leafExecutor;
    private final ReplayProcessor<StrandCommand> lastCommandSink;
    private final Flux<StrandCommand> lastCommandStream;
    private final ReplayProcessor<RunState> stateSink;
    private final Flux<RunState> stateStream;
    private final ReplayProcessor<Block> blockSink;
    private final Flux<Block> blockStream;
    private final EmitterProcessor<Exception> errorSink;
    private final Flux<Exception> errorStream;
    private final AtomicReference<ExecutorState> actualState;
    private final AtomicReference<Block> actualBlock;
    private final AtomicReference<Block> currentStepOverSource;
    private ImmutableList<StrandExecutor> childExecutors;

    public ConcurrentStrandExecutor(Strand strand, Block actualBlock, TreeStructure structure, StrandFactory strandFactory, StrandExecutorFactory strandExecutorFactory, LeafExecutor leafExecutor) {
        requireNonNull(actualBlock, "actualBlock cannot be null");
        this.structure = requireNonNull(structure, "structure cannot be null");
        this.strand = requireNonNull(strand, "strand cannot be null");
        this.strandFactory = requireNonNull(strandFactory, "strandFactory cannot be null");
        this.strandExecutorFactory = requireNonNull(strandExecutorFactory, "strandExecutorFactory cannot be null");
        this.leafExecutor = requireNonNull(leafExecutor, "leafExecutor cannot be null");
        this.childExecutors = ImmutableList.of();

        this.lastCommandSink = ReplayProcessor.cacheLast();
        this.lastCommandStream = lastCommandSink.publishOn(publishingScheduler("last-command"));
        this.errorSink = EmitterProcessor.create();
        this.errorStream = errorSink.publishOn(publishingScheduler("errors"));
        this.stateSink = ReplayProcessor.cacheLast();
        this.stateStream = stateSink.publishOn(publishingScheduler("states"));
        this.blockSink = ReplayProcessor.cacheLast();
        this.blockStream = blockSink.publishOn(publishingScheduler("cursor"));

        this.executor = Executors.newSingleThreadExecutor(namedThreadFactory("strand" + strand.id() + "-exec-%d"));
        this.commandQueue = new LinkedBlockingQueue<>(1);

        this.actualBlock = new AtomicReference<>();
        this.actualState = new AtomicReference<>();
        this.currentStepOverSource = new AtomicReference<>();

        updateActualBlock(actualBlock);
        updateState(ExecutorState.IDLE);

        this.executor.submit(this::lifecycle);
    }

    private void lifecycle() {
        // FIXME refactor in a more maintainable way, after tests are complete!
        while (actualState.get() != ExecutorState.FINISHED) {

            /* remove finished children */
            if (hasChildren() && actualState.get() == ExecutorState.WAITING_FOR_CHILDREN) {
                childExecutors.stream().filter(c -> c.getActualState() == FINISHED).forEach(this::removeChildExecutor);
            }

            /* if has children then the state can only be WAITING or IDLE*/
            if (hasChildren() && actualState.get() != ExecutorState.WAITING_FOR_CHILDREN && actualState.get() != ExecutorState.IDLE) {
                publishError(new StrandExecutorException("[{}] inconsistent state! There are children, so current state can only be IDLE or WAITING FOR CHILDREN, pausing! Current state is {}", strand, actualState.get()));
                updateState(ExecutorState.IDLE);
            }

            if (hasChildren()) {
                boolean allPaused = childExecutors.stream().map(StrandExecutor::getActualState).allMatch(PAUSED::equals);
                if (allPaused && actualState.get() != ExecutorState.IDLE) {
                    LOGGER.debug("[{}] paused because all children are paused", strand);
                    updateState(ExecutorState.IDLE);
                } else if (!allPaused && actualState.get() != ExecutorState.WAITING_FOR_CHILDREN) {
                    LOGGER.debug("[{}] has some non-paused children. Setting the state to waiting", strand);
                    updateState(ExecutorState.WAITING_FOR_CHILDREN);
                }
            }

            StrandCommand commandToExecute = commandQueue.poll();

            if (commandToExecute == StrandCommand.PAUSE) {
                pause();
            }

            if (commandToExecute == StrandCommand.SKIP) {
                if (hasChildren()) {
                    publishError(new RejectedCommandException(commandToExecute, "[{}] has children so skipping is not allowed", strand));
                } else {
                    moveNext();
                }
            }

            if (commandToExecute == StrandCommand.STEP_INTO) {
                if (hasChildren()) {
                    publishError(new RejectedCommandException(commandToExecute, "[{}] has children so step into is not allowed", strand));
                } else {
                    stepInto();
                }
            }

            if (commandToExecute == STEP_OVER && hasChildren()) {
                publishError(new RejectedCommandException(commandToExecute, "[{}] has children so step over is not allowed", strand));
            }

            if (commandToExecute == STEP_OVER) {
                currentStepOverSource.set(actualBlock.get());
            }

            if (commandToExecute == STEP_OVER || commandToExecute == RESUME) {

                if (structure.isParallel(actualBlock.get()) && hasChildren()) {
                    updateState(ExecutorState.WAITING_FOR_CHILDREN);
                    LOGGER.debug("[{}] instructing children to RESUME", strand);
                    childExecutors.forEach(child -> child.instruct(RESUME));
                } else {
                    if (commandToExecute == STEP_OVER) {
                        updateState(ExecutorState.STEPPING_OVER);
                    } else {
                        updateState(ExecutorState.RESUMING);
                    }
                }
            }

            if (actualState.get() == ExecutorState.WAITING_FOR_CHILDREN) {
                if (!hasChildren()) {
                    if (currentStepOverSource.get() != null) {
                        updateState(ExecutorState.STEPPING_OVER);
                    } else {
                        updateState(ExecutorState.RESUMING);
                    }
                    moveNext();
                }
            }

            if (actualState.get() == ExecutorState.STEPPING_OVER) {
                // FIXME potential performance bottleneck #isDescendantOf is very slow
                if (!structure.isDescendantOf(actualBlock.get(), currentStepOverSource.get())) {
                    // Stepping over has finished the subtree of the block that initiate it.. finishing
                    updateState(ExecutorState.IDLE);
                    currentStepOverSource.set(null);
                }
            }

            if (actualState.get() == ExecutorState.RESUMING || actualState.get() == ExecutorState.STEPPING_OVER) {

                if (structure.isLeaf(actualBlock.get())) {
                    LOGGER.debug("[{}] executing {}", strand, actualBlock.get());
                    Result result = leafExecutor.execute(actualBlock.get());
                    if (result == Result.SUCCESS) {
                        moveNext();
                    } else {
                        LOGGER.warn("[{}] execution of {} returned {}. Pausing strand", strand, actualBlock, result);
                        updateState(ExecutorState.IDLE);
                    }
                } else if (structure.isParallel(actualBlock.get())) {
                    for (Block child : structure.childrenOf(actualBlock.get())) {
                        StrandExecutor childExecutor = createChildStrandExecutor(child);
                        childExecutor.instruct(RESUME);
                    }
                    LOGGER.debug("[{}] waiting for children strand to finish", strand);
                    updateState(ExecutorState.WAITING_FOR_CHILDREN);
                } else {
                    // Sequential block, moving into
                    moveInto();
                }
            }

            if (commandToExecute != null) {
                LOGGER.debug("[{}] consumed command {}", strand, commandToExecute);
                lastCommandSink.onNext(commandToExecute);
            }

            cycleSleep();
        }

        LOGGER.debug("Executor for strand {} is finished", strand);
        executor.shutdown();
    }

    /**
     * Tweak this parameters will improve the performances a lot. Possibly to be externalized to be configurable...
     */
    private void cycleSleep() {
        try {
            switch (actualState.get()) {
                case WAITING_FOR_CHILDREN:
                    Thread.sleep(EXECUTOR_SLEEP_MS_WAITING_FOR_CHILDREN);
                    break;
                case IDLE:
                    Thread.sleep(EXECUTOR_SLEEP_MS_IDLE);
                    break;
                default:
                    Thread.sleep(EXECUTOR_SLEEP_MS_DEFAULT);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(strand + " thread interrupted!", e);
        }
    }

    private void publishError(Exception error) {
        LOGGER.error("[{}] {}: {}", strand, error.getClass().getSimpleName(), error.getMessage());
        errorSink.onNext(error);
    }

    private boolean hasChildren() {
        return !childExecutors.isEmpty();
    }


    @Override
    public void instruct(StrandCommand command) {
        if (!commandQueue.offer(command)) {
            LOGGER.warn("Command {} cannot be accepted by strand {} because it is processing another command", command, strand);
        }
    }

    private void pause() {
        if (hasChildren()) {
            LOGGER.debug("[{}] instructing children to pause", strand);
            childExecutors.forEach(child -> child.instruct(PAUSE));
        } else {
            LOGGER.debug("[{}] paused", strand);
            updateState(ExecutorState.IDLE);
        }
    }

    private void stepInto() {
        if (structure.isLeaf(actualBlock.get())) {
            LOGGER.debug("[{}] {} is a leaf, stepping into is not allowed", strand, actualBlock);
            return;
        }

        if (structure.isParallel(actualBlock.get())) {
            for (Block childBlock : structure.childrenOf(actualBlock.get())) {
                StrandExecutor childExecutor = createChildStrandExecutor(childBlock);
                childExecutor.instruct(StrandCommand.PAUSE);
            }

            updateState(ExecutorState.IDLE);
            return;
        }

        moveInto();
        updateState(ExecutorState.IDLE);
    }

    private StrandExecutor createChildStrandExecutor(Block childBlock) {
        Strand childStrand = strandFactory.createChildStrand(strand);
        StrandExecutor childExecutor = strandExecutorFactory.createStrandExecutor(childStrand, structure.substructure(childBlock));
        addChildExecutor(childExecutor);
        LOGGER.debug("[{}] created child strand {}", strand, childStrand);
        return childExecutor;
    }

    private void moveInto() {
        List<Block> children = structure.childrenOf(actualBlock.get());
        if (children.isEmpty()) {
            throw new IllegalStateException("Cannot move into block " + actualBlock + ", no children!");
        }

        Block firstChild = children.get(0);
        updateActualBlock(firstChild);
    }

    private void moveNext() {
        Optional<Block> nextBlock = structure.nextBlock(actualBlock.get());
        if (nextBlock.isPresent()) {
            updateActualBlock(nextBlock.get());
        } else {
            LOGGER.debug("[{}] {} is the last block. Finished", strand, actualBlock);
            updateState(ExecutorState.FINISHED);
            updateActualBlock(null);
        }
    }

    private void addChildExecutor(StrandExecutor childExecutor) {
        childExecutors = ImmutableList.<StrandExecutor>builder().addAll(childExecutors).add(childExecutor).build();
    }

    private void removeChildExecutor(StrandExecutor childExecutor) {
        childExecutors = childExecutors.stream().filter(e -> !e.equals(childExecutor)).collect(toImmutableList());
    }

    private void updateActualBlock(Block newBlock) {
        LOGGER.debug("[{}] block = {}", strand, newBlock);
        // TODO Should we complete the stream if the newBlock is null? (strand execution finished)
        actualBlock.set(newBlock);
        blockSink.onNext(newBlock);
    }

    private void updateState(ExecutorState newState) {
        LOGGER.debug("[{}] state = {}", strand, newState);
        // TODO Should we complete the stream if the new state is FINISHED?
        actualState.set(newState);
        stateSink.onNext(runstateFrom(newState));
    }

    private static RunState runstateFrom(ExecutorState state) {
        switch (state) {
            case RESUMING:
            case RUNNING_LEAF:
            case STEPPING_OVER:
            case WAITING_FOR_CHILDREN:
                return RUNNING;
            case IDLE:
                return PAUSED;
            case FINISHED:
                return FINISHED;
        }
        throw new IllegalArgumentException("Strand state " + state + " cannot be mapped to a RunState");
    }

    @Override
    public Flux<RunState> getStateStream() {
        return stateStream;
    }

    @Override
    public Flux<Block> getBlockStream() {
        return blockStream;
    }

    @Override
    public RunState getActualState() {
        return runstateFrom(actualState.get());
    }

    @Override
    public Block getActualBlock() {
        return actualBlock.get();
    }

    @Override
    public Strand getStrand() {
        return strand;
    }

    @Override
    public Flux<Exception> getErrorsStream() {
        return errorStream;
    }

    @Override
    public Set<StrandCommand> getAllowedCommands() {
        return allowedCommands(runstateFrom(actualState.get()), actualBlock.get());
    }

    private Set<StrandCommand> allowedCommands(RunState state, Block block) {
        if (block == null) {
            return Collections.emptySet();
        }
        boolean hasChildren = hasChildren();

        ImmutableSet.Builder<StrandCommand> builder = ImmutableSet.builder();
        switch (state) {
            case PAUSED:
                builder.add(RESUME);
                if (!hasChildren) {
                    builder.add(STEP_OVER, SKIP);

                    if (!structure.isLeaf(block)) {
                        builder.add(STEP_INTO);
                    }
                }
                break;
            case RUNNING:
                builder.add(PAUSE);
                break;
        }
        return builder.build();
    }

    /**
     * TODO Evaluate if public is necessary
     */
    @Deprecated
    @VisibleForTesting
    public Flux<StrandCommand> getLastCommandStream() {
        return lastCommandStream;
    }

    /**
     * TODO think about a command for this!! A parametrized command will also solve the concurrency issues that the implementation below have
     */
    @Deprecated
    @VisibleForTesting
    public void moveTo(Block block) {
        if (!structure.contains(block)) {
            throw new IllegalArgumentException("Cannot move to " + block + " as is not part of this tree structure");
        }

        updateActualBlock(block);
    }

    @VisibleForTesting
    public Set<StrandExecutor> getChildrenStrandExecutors() {
        return ImmutableSet.copyOf(childExecutors);
    }

    private Scheduler publishingScheduler(String suffix) {
        return Schedulers.newSingle("strand-exec-" + strand.id() + "-" + suffix);
    }

    private enum ExecutorState {
        IDLE,
        STEPPING_OVER,
        RUNNING_LEAF,
        RESUMING,
        FINISHED,
        WAITING_FOR_CHILDREN;

    }
}
