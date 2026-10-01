package Project2UI.Client.Views;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import Project2UI.Client.Client;
import Project2UI.Common.QuestionPayload;

public class AddQuestionDialog extends JDialog {

    private final JTextField categoryField = new JTextField();
    private final JTextField questionField = new JTextField();
    private final JTextField optionAField = new JTextField();
    private final JTextField optionBField = new JTextField();
    private final JTextField optionCField = new JTextField();
    private final JTextField optionDField = new JTextField();
    private final JComboBox<String> correctAnswerBox = new JComboBox<>(
            new String[]{"A", "B", "C", "D"});
    private final JLabel statusLabel = new JLabel(" ");

    public AddQuestionDialog(JFrame parent) {
        super(parent, "Add Question", true);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        form.add(new JLabel("Category:"));
        form.add(categoryField);

        form.add(new JLabel("Question:"));
        form.add(questionField);

        form.add(new JLabel("Option A:"));
        form.add(optionAField);

        form.add(new JLabel("Option B:"));
        form.add(optionBField);

        form.add(new JLabel("Option C (optional):"));
        form.add(optionCField);

        form.add(new JLabel("Option D (optional):"));
        form.add(optionDField);

        form.add(new JLabel("Correct Answer:"));
        form.add(correctAnswerBox);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton submitButton = new JButton("Submit");
        submitButton.addActionListener(e -> handleSubmit());

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(submitButton);
        buttons.add(cancelButton);

        bottom.add(statusLabel);
        bottom.add(buttons);

        add(form, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    private void handleSubmit() {
        String category = categoryField.getText().trim();
        String question = questionField.getText().trim();
        String optA = optionAField.getText().trim();
        String optB = optionBField.getText().trim();
        String optC = optionCField.getText().trim();
        String optD = optionDField.getText().trim();
        String correct = (String) correctAnswerBox.getSelectedItem();

        if (category.isEmpty() || question.isEmpty() || optA.isEmpty() || optB.isEmpty()) {
            statusLabel.setText("Category, question, and at least 2 options are required.");
            return;
        }

        List<String> options = new ArrayList<>();
        options.add(optA);
        options.add(optB);
        if (!optC.isEmpty()) options.add(optC);
        if (!optD.isEmpty()) options.add(optD);

        int correctIndex = correct.charAt(0) - 'A';
        if (correctIndex >= options.size()) {
            statusLabel.setText("Correct answer option doesn't exist.");
            return;
        }

        QuestionPayload payload = new QuestionPayload();
        payload.setCategory(category);
        payload.setQuestionText(question);
        payload.setOptions(options);
        payload.setCorrectAnswer(correct);

        try {
            Client.INSTANCE.sendAddQuestion(payload);
            dispose();
        } catch (Exception e) {
            statusLabel.setText("Failed to send question.");
        }
    }
}