package Project2UI.Common;

import java.util.List;

public class QuestionPayload extends Payload {
    private String questionText;
    private String category;
    private List<String> options;
    private String correctAnswer;

    public QuestionPayload() {
        setPayloadType(PayloadType.ADD_QUESTION);
    }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    @Override
    public String toString() {
        return super.toString() + String.format(" Category[%s] Question[%s] Options[%s] Answer[%s]",
                category, questionText, options, correctAnswer);
    }
}