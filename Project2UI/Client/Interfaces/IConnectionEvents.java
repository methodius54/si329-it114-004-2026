package Project2UI.Client.Interfaces;

import Project2UI.Common.User;

public interface IConnectionEvents extends IClientEvents {
    void onConnected(User localUser);

    void onDisconnected();
}
