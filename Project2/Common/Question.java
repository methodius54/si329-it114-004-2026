package Project2.Common;

import java.util.List;

public class Question {
    private String question;
    private String category;
    private List<String> options;
    private String correctAnswer;

    public Question(String question, String category, List<String> options, String correctAnswer) {
        this.question = question;
        this.category = category;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }

    public String getQuestion() {
        return question;
    }
    public String getCategory() {
        return category;
    }
    public List<String> getOptions() {
        return options;
    }
    public String getCorrectAnswer() {
        return correctAnswer;
    }

    @Override
    public String toString() {
    return String.format("[Question] [%s] Category [%s] Options [%s] Answer [%s]", question, category, options, correctAnswer);
    }
}