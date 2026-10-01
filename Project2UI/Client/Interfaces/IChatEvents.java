package Project2UI.Client.Interfaces;

public interface IChatEvents extends IClientEvents {
    void onChatMessageReceived(String message);

    void onSystemMessageReceived(String message);
}