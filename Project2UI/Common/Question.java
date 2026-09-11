package Project2UI.Common;

import java.util.List;

public class Question {
    // This is a bit strange, but each different class, Question, QuestionPayload, QAPayload each serve a respective purpose
    // QAPayload does the job of handling server-toclient
    // QuestionPayload does the job of handling client-toserver
    // Question does the job of handling server-side
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