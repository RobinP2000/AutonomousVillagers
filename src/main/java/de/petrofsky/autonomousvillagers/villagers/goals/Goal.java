package de.petrofsky.autonomousvillagers.villagers.goals;

import net.minecraft.world.entity.npc.Villager;

public abstract class Goal {
    private final Villager villager;
    private boolean started = false;
    private boolean ended = false;
    private boolean paused = false;
    private boolean failed = false;
    private Goal next;

    public Goal(Villager villager) {
        this.villager = villager;
    }

    public final void executeTick() {
        if(! isStarted() || isStopped() || isPaused()) return;
        tick();
    }

    protected abstract void tick();

    public void init() {};

    public void pause() {
        if(!isStarted() || isStopped()) return;
        this.paused = true;
    }

    public void unpause() {
        if(!isStarted() || isStopped()) return;
        this.paused = false;
    }

    public boolean isInProgress() {
        return !isStopped();
    }

    public boolean hasFailed() {
        return this.failed;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public final void start() {
        this.started = true;
        init();
    };

    public final void stop() {
        this.ended = true;
    }

    public final void fail() {
        stop();
        this.failed = true;
    }

    public final void success() {
        stop();
    }

    public final boolean isStopped() {
        return this.ended;
    }

    public final boolean isStarted() {
        return this.started;
    }

    public boolean hasNext() {
        return this.next != null;
    }

    public Goal getNext() {
        return next;
    }

    protected void setNext(Goal nextGoal) {
        this.next = nextGoal;
    }

    public final Villager getVillager() {
        return villager;
    }

}
