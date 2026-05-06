package Project2UI2.Common;

import java.util.List;

public class QAPayload extends Payload {
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