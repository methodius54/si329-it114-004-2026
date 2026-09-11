package Project2UI.Client.Interfaces;

import java.util.List;

public interface IGameQuestionEvents extends IClientEvents {
    void onQuestionReceived(String category, String question, List<String> options);
}