package Project2UI.Client.Interfaces;

import Project2UI.Common.TimerType;

/**
 * Receives server-synced timer updates for game flow timers.
 */
public interface IGameTimerEvents extends IGameEvents {
    /**
     * @param timerType timer channel being updated
     * @param secondsRemaining remaining seconds; negative means inactive/not running
     */
    void onGameTimerUpdated(TimerType timerType, int secondsRemaining);
}
