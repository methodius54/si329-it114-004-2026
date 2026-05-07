package Project2UI.Client.Interfaces;

import Project2UI.Exceptions.ValidationException;

public interface IClientCommands {
    boolean connectToServer(String host, int port, String name);

    void disconnectFromServer();

    void sendChatMessage(String text);

    void sendReadySignal() throws ValidationException;

    void sendAnswerSignal(String choice) throws ValidationException;
    
    void setDisplayName(String name);

    void sendAwayToggle() throws ValidationException;
}
