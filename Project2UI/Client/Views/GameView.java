package Project2UI.Client.Views;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;

import Project2UI.Client.Client;
import Project2UI.Client.Interfaces.IConnectionEvents;
import Project2UI.Client.Interfaces.IPlayerEvents;
import Project2UI.Client.Interfaces.IPlayerStatusEvents;
import Project2UI.Common.Constants;
import Project2UI.Common.Phase;
import Project2UI.Common.User;
import Project2UI.Exceptions.ValidationException;

/**
 * Main gameplay panel that shows phase-aware status, cards, grid actions, and game events.
 */
public class GameView extends JPanel implements IConnectionEvents, IPlayerEvents, IPlayerStatusEvents, IGameFlowEvents, IGameTimerEvents {
    
    private final Client client;
    private final JLabel statusLabel = new JLabel("Connect to the server to receive game data.");
    private final JPanel readyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JButton readyButton = new JButton("Mark Ready");
    private final GameEventsView gameEventsView = new GameEventsView();
    private final JPanel phaseContentPanel = new JPanel(new CardLayout());
    private final JPanel evaluationPanel = createCenteredPhasePanel("Evaluating round results...");

    // trivia fields
    private final JLabel categoryLabel = new JLabel("Category: ");
    private final JLabel questionLabel = new JLabel("Question will appear here.");
    private final JButton[] answerButtons = new JButton[4];
    private final JLabel timerLabel = new JLabel("Time: --");


    // Main game panel: status line + phase-aware center content.
    // READY and IN_PROGRESS use play content; EVALUATION uses evaluation content.
    public GameView(Client client) {
        super(new BorderLayout(6, 6));
        this.client = client;
        setBorder(BorderFactory.createTitledBorder("Game"));

        JPanel status = new JPanel();
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        // Single-line status/instruction area shown above the board.
        status.add(selectionLabel);
        readyButton.addActionListener(event -> {
            try {
                client.sendReadySignal();
            } catch (ValidationException e) {
                selectionLabel.setText(e.getMessage());
            }
        });
        readyPanel.add(readyButton);
        status.add(readyPanel);

        //Question section
        JPanel questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        questionPanel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        categoryLabel.setFont(categoryLabel.getFont.deriveFont(Font.BOLD, 13f));
        questionPanel.add(categoryLabel);
        questionPanel.add(questionLabel);
        questionPanel.add(timerLabel);

        //answer section
        JPanel answersPanel = new JPanel(new GridLayout(2,2,6,6));
        String[] labels = {"A","B","C","D"};
        for (int i = 0; i < 4; i++) {
            final String choice = labels[i];
            answerButtons[i] = new JButton(choice);
            answerButtons[i].setEnabled(false);
            answersButtons[i].addActionListener(event -> {
                try {
                    client.setAnswerSignal(choice);
                    lockInAnswer(choice);
                }
                catch (ValidationException e) {
                    statusLabel.setText(e.getMessage());
                }
        });
        answersPanel.add(answerButtons[i]);
    }

        JPanel playPanel = new JPanel(new BorderLayout(6, 6));
        playPanel.add(questionPanel, BorderLayout.NORTH);
        playPanel.add(answersPanel, BorderLayout.CENTER);
        
        phaseContentPanel.add(playPanel, CARD_PLAY);
        phaseContentPanel.add(evaluationPanel, CARD_EVALUATION);

        JSplitPane gameSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, phaseContentPanel, gameEventsView);
        gameSplit.setResizeWeight(0.75);
        gameSplit.setDividerLocation(0.75);

        add(status, BorderLayout.NORTH);
        add(gameContent, BorderLayout.CENTER);
        resetView();
    }

    // ---- Incoming client events ----

    @Override
    public void addNotify() {
        super.addNotify();
        client.registerCallback(this);
    }

    @Override
    public void removeNotify() {
        client.unregisterCallback(this);
        super.removeNotify();
    }

    @Override
    public void onConnected(User localUser) {
        // Connection lifecycle event keeps top status text in sync with session state.
        selectionLabel.setText("Connected. Complete the ready check to join the round.");
        refreshStatusOnly();
    }

    @Override
    public void onDisconnected() {
        // Full reset on disconnect prevents stale in-progress UI state.
        resetView();
    }

    @Override
    public void onPlayersUpdated(Map<Long, User> players) {
        // Join/leave can change the ready-count denominator and numerator without a
        // per-player status event, so refresh the ready label from the latest roster.
        refreshStatusOnly();
    }

    @Override
    public void onPlayerStatusUpdated(User user) {
        // Status updates (ready/turn/points) affect button/grid interactivity.
        refreshStateOnly();
    }

    @Override
    public void onAllPlayerStatusesReset() {
        // Round/session reset also requires control/interactivity refresh.
        refreshStateOnly();
    }

    @Override
    public void onGamePhaseUpdated(Phase phase) {
        // Phase drives which content panel is shown and which controls are active.
        refreshStateOnly();
    }

    @Override
    public void onCurrentTurnUpdated(long currentTurnClientId, String currentTurnDisplayName) {
        refreshStateOnly();
    }

    // ---- View refresh helpers ----

    private void refreshStateOnly() {
        updatePhaseVisibility();
        updateReadyControls();
        updateAnswerButtons();
    }

    private void refreshStatusOnly() {
        updatePhaseVisibility();
        updateReadyControls();

        switch (client.getCurrentGamePhase()) {
            case INACTIVE:
                statusLabel.setText("Use Mark Ready to join the next round.");
                break;
            case READY:
                statusLabel.setText("Waiting for ready check to complete.");
                break;
            case IN_PROGRESS:
                statusLabel.setText("Round in progress. Select an answer!");
                break;
            case EVALUATION:
                statusLabel.setText("Waiting for round evaluation to complete.");
                break;
            default:
                statusLabel.setText("Waiting for game state update.");
                break;
        }

        revalidate();
        repaint();
    }

    private void updatePhaseVisibility() {
        CardLayout layout = (CardLayout) phaseContentPanel.getLayout();
        switch (client.getCurrentGamePhase()) {
            case READY:
            case IN_PROGRESS:
                layout.show(phaseContentPanel, CARD_PLAY);
                break;
            case EVALUATION:
                layout.show(phaseContentPanel, CARD_EVALUATION);
                break;
            default:
                layout.show(phaseContentPanel, CARD_PLAY);
                break;
        }
    }

    private void updateReadyControls() {
        Phase currentPhase = client.getCurrentGamePhase();
        boolean beforeGameplay = currentPhase.ordinal() <= Phase.READY.ordinal();
        boolean localReady = client.isLocalPlayerReady();
        int readyCount = client.getReadyPlayerCount();
        int readyRequired = Constants.REQUIRE_PLAYERS;

        readyPanel.setVisible(beforeGameplay);
        readyButton.setEnabled(beforeGameplay && !localReady);
        String label = localReady ? "Ready" : "Mark Ready";
        readyButton.setText(String.format("%s (%d/%d)", label, readyCount, readyRequired));
    }

    private void updateAnswerButtons() {
        boolean canAnswer = client.getCurrentGamePhase() == Phase.IN_PROGRESS && client.isLocalPlayerReady() && !client.isLocalPlayerTurnTaken();
        for (JButton btn : answerButtons) {
            if(btn.isEnabled() || canAnswer) {
                btn.setEnabled(canAnswer);
            }
        }
    }

    public void onQuestionReceived(String category, String question, List<String> options) {
        categoryLabel.setText("Category: " + category);
        questionLabel.setText("<html>" + question + "/html");
        for (int i = 0; i < answerButtons.length; i++) {
                    answerButtons[i].setBackground(null);
        answerButtons[i].setEnabled(true);
        if (i < options.size()) {
            answerButtons[i].setText(options.get(i));
            answerButtons[i].setVisible(true);
        } else {
            answerButtons[i].setVisible(false);
        }
    }
    timerLabel.setText("Time: --");
    revalidate();
    repaint();
    }

    @Override
    public void onGameTimerUpdated(TimerType TimerType, int secondsRemaining) {
        if(TimerType == TimerType.ROUND) {
            timerLabel.setText(String.format("Time: %s", secondsRemaining));
        }
    }

    private JPanel createCenteredPhasePanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel messageLabel = new JLabel(message, SwingConstants.CENTER);
        panel.add(messageLabel, BorderLayout.CENTER);
        return panel;
    }

    private void resetView() {
        // Local UI reset for disconnect/inactive states.
        selectionLabel.setText("Connect to the server to receive game data.");
        categoryLabel.setText("Category:");
        questionLabel.setText("Question here");
        timerLabel.setText("Time: --");
        for (JButton btn : answerButtons) {
            if (btn != null) {
                btn.setEnabled(false);
                btn.setBackground(null);
            }
        }
        refreshStatusOnly();
    }
    private void lockInAnswer(String choice) 
    {
        for (JButton btn : answerButtons) {
            btn.setEnabled(false);
            if (btn.getText().startsWith(choice)) {
                btn.setBackground(Color.CYAN);
            }
        }
    }
}