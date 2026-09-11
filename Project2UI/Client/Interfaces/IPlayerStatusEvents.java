package Project2UI.Client.Interfaces;

import Project2UI.Common.User;

public interface IPlayerStatusEvents extends IClientEvents {
    void onPlayerStatusUpdated(User user);

    void onAllPlayerStatusesReset();
}