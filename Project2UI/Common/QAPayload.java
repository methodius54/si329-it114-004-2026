package Project2UI.Common;

import java.util.List;

public class QAPayload extends Payload {
    // This is a bit strange, but each different class, Question, QuestionPayload, QAPayload each serve a respective purpose
    // QAPayload does the job of handling server-toclient
    // QuestionPayload does the job of handling client-toserver
    // Question does the job of handling server-side
    private String question;
    private String category;
    private List<String> options;

    public QAPayload() {
        setPayloadType(PayloadType.QUESTION);
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
    
    public List<String> getOptions() {
        return this.options;
        }

    public String toString() {
        return super.toString() + String.format(" Category [%s] Question [%s] Options[%s]", category, question, options);
    }
}
