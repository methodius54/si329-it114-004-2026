package Project2UI.Client.Interfaces;

import java.util.Map;

import Project2UI.Common.User;

public interface IPlayerEvents extends IClientEvents {
    void onPlayersUpdated(Map<Long, User> players);
}