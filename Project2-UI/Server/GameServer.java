package Project2.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import Project2.Common.Constants;
import Project2.Common.LoggerUtil;
import Project2.Common.Phase;
import Project2.Common.TimedEvent;
import Project2.Common.ValidationUtils;
import Project2.Exceptions.ValidationException;
import Project2.Common.QAPayload;
import Project2.Common.Question;

/**
 * Concrete game session scaffold based on the old GameRoom lifecycle.
 *
 * Commands currently supported from clients:
 * - /ready
 * - /turn <action>
 */
public class GameServer extends BaseGameServer {

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int READY_SECONDS = 30;
    private static final int ROUND_SECONDS = 30;
    private static final int TURN_SECONDS = 20;
    private static final String GAME_TAG = "[Game] ";
    private List<ServerThread> correctResponders = new ArrayList<>();

    private volatile Phase phase = Phase.INACTIVE;
    private volatile TimedEvent readyTimer;
    private volatile TimedEvent roundTimer;
    private volatile TimedEvent turnTimer;
    private volatile Long currentTurnPlayerId;
    private int roundNumber = 0;
    // example data
    private int hiddenNumber = 0;
    // question data
    private List<Question> questions = new ArrayList<>();
    private Question currentQuestion = null;
    private static final String QUESTIONS_FILE = "questions.txt";
    private static final int TOTAL_ROUNDS = 3;

    // start region for lifecycle hook implementations
    @Override
    protected void onSpectatorJoined(ServerThread client) {
        if (client == null) {
            return;
        }
        if (phase == Phase.INACTIVE) {
            return; // nothing to sync if the session isn't active
        }
        LoggerUtil.INSTANCE.info("[GameServer] Spectator joined: " + client.getDisplayName());
        unicastGameMessage(client, "Joined as spectator. Current active players: " + getActivePlayerCount());
        unicastGameStateToJoiner(client);
    }

    @Override
    protected void onPlayerJoined(ServerThread client) {
        if (client == null) {
            return;
        }
        if (phase == Phase.INACTIVE) {
            return; // nothing to sync if the session isn't active
        }
        LoggerUtil.INSTANCE.info("[GameServer] Player joined via ready: " + client.getDisplayName());
        unicastGameMessage(client, "Joined as active player. Waiting room status: "
                + getActivePlayerCount() + "/" + MIN_PLAYERS_TO_START + " ready.");
        if (phase != Phase.READY) {
            unicastGameStateToJoiner(client);
        }
        broadcastGameMessage(client.getDisplayName() + " joined active players.");
        broadcastGameMessage("Active players: " + getActivePlayerCount());
    }

    @Override
    protected void onPlayerLeft(ServerThread client) {
        if (client == null) {
            return;
        }

        LoggerUtil.INSTANCE.info("[GameServer] Player left: " + client.getDisplayName());
        if (getActivePlayerCount() == 0) {
            resetReadyTimer();
            onSessionEnd();
            return;
        }
        if (phase == Phase.IN_PROGRESS && getActivePlayerCount() < MIN_PLAYERS_TO_START) {
            broadcastGameMessage("Not enough active players to continue.");
            onSessionEnd();
        }
    }

    @Override
    protected synchronized void onSessionStart() {
    LoggerUtil.INSTANCE.info("[GameServer] onSessionStart() start");
        loadQuestions();
        if (questions.isEmpty()) {
            broadcastGameMessage("Failed to load questions. Session cannot start.");
            onSessionEnd();
            return;
        }
        resetReadyTimer();
        roundNumber = 0;
        broadcastGameMessage("Session started.");
        LoggerUtil.INSTANCE.info("[GameServer] onSessionStart() end");
        onRoundStart();
    }

    @Override
    protected synchronized void onRoundStart() {
        LoggerUtil.INSTANCE.info("[GameServer] onRoundStart() start");
        resetRoundTimer();

        int randomIndex = new Random().nextInt(questions.size());
        currentQuestion = questions.remove(randomIndex);

        for (ServerThread player : getActivePlayers()) {
            player.setAnswer(null);
            player.setTurnTaken(false);
            broadcastTurnStatus(player.getClientId(), false);
        }

        roundNumber++;
        phase = Phase.IN_PROGRESS;
        broadcastCurrentPhase();

        QAPayload qa = new QAPayload();
        qa.setCategory(currentQuestion.getCategory());
        qa.setQuestion(currentQuestion.getQuestion());
        qa.setOptions(currentQuestion.getOptions());
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendQuestion(qa));

        correctResponders.clear();

        startRoundTimer();
        broadcastGameMessage("Round " + roundNumber + " started. You have " + ROUND_SECONDS + "s total.");
        LoggerUtil.INSTANCE.info("[GameServer] onRoundStart() end");
    }

    @Override
    protected synchronized void onTurnStart() {
        LoggerUtil.INSTANCE.info("[GameServer] onTurnStart() start");
        resetTurnTimer();

        List<ServerThread> snapshot = new ArrayList<>(getActivePlayers());
        if (snapshot.isEmpty()) {
            onSessionEnd();
            return;
        }
        // TODO: pick next player (covered in a future lesson, below is a temporary
        // scaffold that just picks the first active player)
        ServerThread chosen = snapshot.get(0); // simple scaffold: first active player
        currentTurnPlayerId = chosen.getClientId();

        startTurnTimer();
        broadcastGameMessage("Turn started for " + chosen.getDisplayName() + ". Use /turn <action> within "
                + TURN_SECONDS + "s.");
        LoggerUtil.INSTANCE.info("[GameServer] onTurnStart() end");
    }

    @Override
    protected synchronized void onTurnEnd() {
        LoggerUtil.INSTANCE.info("[GameServer] onTurnEnd() start");
        resetTurnTimer();
        currentTurnPlayerId = null;
        LoggerUtil.INSTANCE.info("[GameServer] onTurnEnd() end");
        // onRoundEnd(); this example doesn't use turns, but this hook is called at the
        // end of handleTurn() and we don't want it to end the round

        // if all players have taken their turn, enter onRoundEnd() early instead of
        // waiting for the turn timer to expire
        boolean allTaken = getActivePlayers().stream().allMatch(ServerThread::isTurnTaken);
        if (allTaken) {
            // NOTE: be careful to not have two closely timed flows both call onRoundEnd()
            // simultaneously
            onRoundEnd();
        }
    }

    @Override
    protected synchronized void onRoundEnd() {
        LoggerUtil.INSTANCE.info("[GameServer] onRoundEnd() start");
        if (phase == Phase.EVALUATION) {
            LoggerUtil.INSTANCE.info("[GameServer] Already in evaluation phase, skipping redundant onRoundEnd() call");
            return;
        }
        phase = Phase.EVALUATION;
        broadcastCurrentPhase();
        resetRoundTimer();
        broadcastGameMessage("Round ended.");
        broadcastCorrectAnswer(currentQuestion.getCorrectAnswer());

        int numCorrect = correctResponders.size();
        for (int i = 0; i < numCorrect; i++) {
            ServerThread player = correctResponders.get(i);
            int pointsAwarded = Math.max(1, 10 - (i * (9 / Math.max(1, numCorrect - 1))));
            player.setPoints(player.getPoints() + pointsAwarded);
            broadcastPlayerPoints(player);
            broadcastGameMessage(String.format("%s answered correctly and was awarded %s points", player.getDisplayName(), pointsAwarded));
        }
        getActivePlayers().stream()
            .sorted((p1, p2) -> Integer.compare(p2.getPoints(), p1.getPoints()))
            .forEach(player -> broadcastGameMessage(
                    String.format("%s: %d points", player.getDisplayName(), player.getPoints())));

    LoggerUtil.INSTANCE.info("[GameServer] onRoundEnd() end");

    if (roundNumber >= TOTAL_ROUNDS || questions.isEmpty()) {
        onSessionEnd();
    } else {
        onRoundStart();
    }
    }

    @Override
    protected synchronized void onSessionEnd() {
        LoggerUtil.INSTANCE.info("[GameServer] onSessionEnd() start");
        resetReadyTimer();
        resetTurnTimer();
        resetRoundTimer();

        currentTurnPlayerId = null;
        currentQuestion = null;
        correctResponders.clear();
        questions.clear();
        phase = Phase.INACTIVE;

        List<ServerThread> snapshot = new ArrayList<>(getActivePlayers());
        broadcastGameMessage("=== Final Scoreboard ===");
        snapshot.stream()
            .sorted((p1, p2) -> Integer.compare(p2.getPoints(), p1.getPoints()))
            .forEach(player -> broadcastGameMessage(
                    String.format("%s: %d points", player.getDisplayName(), player.getPoints())));

    snapshot.stream()
            .max((p1, p2) -> Integer.compare(p1.getPoints(), p2.getPoints()))
            .ifPresentOrElse(winner -> {
                broadcastGameMessage(String.format("Game over! %s wins with %d points!",
                        winner.getDisplayName(), winner.getPoints()));
            }, () -> {
                broadcastGameMessage("Game over! No winner.");
            });

        for (ServerThread player : snapshot) {
            player.resetGameState();
        }
        broadcastReadyStatus(Constants.DEFAULT_CLIENT_ID, false);
        clearActivePlayers();

        broadcastCurrentPhase();
        broadcastGameMessage("Session ended. Type /ready to join the next session.");
        LoggerUtil.INSTANCE.info("[GameServer] onSessionEnd() end");
    }
    // end region for lifecycle hook implementations

    // Start region for timer handlers ===================================

    private synchronized void startReadyTimer(boolean resetOnTry) {
        if (phase != Phase.READY) {
            return;
        }
        if (resetOnTry) {
            resetReadyTimer();
        }
        if (readyTimer == null) {
            readyTimer = new TimedEvent(READY_SECONDS, this::checkReadyStatus);
            readyTimer.setTickCallback(time -> LoggerUtil.INSTANCE.info("[GameServer] Ready timer: " + time));
            broadcastGameMessage(
                    "Ready check started. Session begins in " + READY_SECONDS + "s if enough players are ready.");
        }
    }

    private synchronized void resetReadyTimer() {
        if (readyTimer != null) {
            readyTimer.cancel();
            readyTimer = null;
        }
    }

    private synchronized void startRoundTimer() {
        roundTimer = new TimedEvent(ROUND_SECONDS, this::onRoundEnd);
        roundTimer.setTickCallback(time -> LoggerUtil.INSTANCE.info("[GameServer] Round timer: " + time));
    }

    private synchronized void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private synchronized void startTurnTimer() {
        turnTimer = new TimedEvent(TURN_SECONDS, this::onTurnEnd);
        turnTimer.setTickCallback(time -> LoggerUtil.INSTANCE.info("[GameServer] Turn timer: " + time));
    }

    private synchronized void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    // End region for timer handlers ===================================
    private synchronized void checkReadyStatus() {
        if (phase != Phase.READY) {
            return;
        }
        if (getActivePlayerCount() >= MIN_PLAYERS_TO_START) {
            onSessionStart();
        } else {
            broadcastGameMessage("Ready check expired: not enough ready players.");
            onSessionEnd();
        }
    }

    // start region for handle*() methods called by Server

    protected void handleAnswer(ServerThread sender, String triviaAnswer) {
        try {
            ValidationUtils.requireParticipating(isActivePlayer(sender));
            ValidationUtils.requirePhase(phase, Phase.IN_PROGRESS);
            triviaAnswer = ValidationUtils.requireValidTurnOption(triviaAnswer.trim());

            sender.setAnswer(triviaAnswer);
            broadcastGameMessage(sender.getDisplay() + " locked in their answer.");

            sender.setTurnTaken(true);
            broadcastTurnStatus(sender.getClientId(), true);

            if (triviaAnswer.equalsIgnoreCase(currentQuestion.getCorrectAnswer())) {
                correctResponders.add(sender);
            }

            boolean allAnswered = getActivePlayers().stream().allMatch(ServerThread::isTurnTaken);
            if (allAnswered) {
                onRoundEnd();
            }
        }
        catch(ValidationException e) {
            LoggerUtil.INSTANCE.warning("[Game Server] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

    protected void handleGuess(ServerThread sender, String guess) {
        try {
            ValidationUtils.requireParticipating(isActivePlayer(sender));
            ValidationUtils.requirePhase(phase, Phase.IN_PROGRESS);
            guess = ValidationUtils.requireValidTurnOption(guess.trim());
            // although validation should verify it's a number, I'll see do a try/catch just
            // in case
            // that way if I mistakenly change requireValidTurnOption() in the future and it
            // stops validating properly, I have a fallback to prevent server crashes from
            // NumberFormatException
            try {
                int guessValue = Integer.parseInt(guess);
                // record server local state (used in round end)
                sender.setGuess(guessValue);
                // unicast guess to player for confirmation
                unicastGuessConfirmation(sender, guessValue);
                // NOTE: we won't evaluate here, we'll do it during onRoundEnd()
            } catch (NumberFormatException e) {
                LoggerUtil.INSTANCE.warning("[GameServer] Failed to parse turn action as number: " + guess);
                unicastGameMessage(sender,
                        "Failed to parse your guess as a number. Please enter a valid number between 1 and 10.");
                return;
            }

            // keep the guess hidden from other players in this example
            broadcastGameMessage(sender.getDisplayName() + " made a guess.");
            // Note: technically if your action has data, turnTaken can be derived by
            // whether or not data was recorded, but I'll keep it as a separate property for
            // simplicity and flexibility. In a fuller project, deriving information is more
            // efficient
            sender.setTurnTaken(true);
            broadcastTurnStatus(sender.getClientId(), true);
            onTurnEnd();
        } catch (ValidationException e) {
            LoggerUtil.INSTANCE.warning("[GameServer] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

    /**
     * Handles a player's ready action. Validates the action, registers them as an
     * active player, and starts the ready timer. Sends an error message back to the
     * player on failure.
     */
    public void handleReady(ServerThread sender) {
        try {
            ValidationUtils.requirePhaseAtMost(phase, Phase.READY);
            ValidationUtils.requireNotAlreadyReady(isActivePlayer(sender));

            if (phase == Phase.INACTIVE) {
                phase = Phase.READY;
                broadcastCurrentPhase();
            }

            sender.resetGameState();
            sender.setReady(true);
            if (addActivePlayer(sender)) {
                onPlayerJoined(sender);
            }

            broadcastReadyStatus(sender.getClientId(), true);
            broadcastGameMessage(sender.getDisplayName() + " is ready. (" + getActivePlayerCount() + " active)");
            startReadyTimer(false);
        } catch (ValidationException e) {
            LoggerUtil.INSTANCE.warning("[GameServer] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

    /**
     * Handles a player's turn action. Validates the action, records the turn, and
     * advances the game. Sends an error message back to the player on failure.
     */
    @Deprecated
    public void handleTurn(ServerThread sender, String action) {
        try {
            ValidationUtils.requireParticipating(isActivePlayer(sender));
            ValidationUtils.requirePhase(phase, Phase.IN_PROGRESS);
            ValidationUtils.requireTurnNotTaken(sender.isTurnTaken());
            String normalizedAction = ValidationUtils.requireValidTurnOption(action);

            // TODO: turn logic would go here, in this example we're just marking that we
            // took a turn
            // ValidationUtils.requireCurrentPlayer(currentTurnPlayerId,
            // sender.getClientId());

            sender.setTurnTaken(true);
            broadcastTurnStatus(sender.getClientId(), true);
            onTurnEnd();
        } catch (ValidationException e) {
            LoggerUtil.INSTANCE.warning("[GameServer] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

    // end region for handle*() methods called by Server

    // start region for helper methods to send data to clients

    // This method is a little strange. I wasn't sure how you'd want us to *load* the questions, and in what format we should store the questions because I was originally on RPS
    private void loadQuestions() {
        questions.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(QUESTIONS_FILE))) {
            String line;
            while((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                if (parts.length != 7) {
                    LoggerUtil.INSTANCE.warning("malformed question line skipping: " + line);
                    continue;
                }
                String category = parts[0].trim();
                String questionText = parts[1].trim();
                List<String> options = new ArrayList<>();
                options.add("A) " + parts[2].trim());
                options.add("B) " + parts[3].trim());
                options.add("C) " + parts[4].trim());
                options.add("D) " + parts[5].trim());
                String correctAnswer = parts[6].trim().toUpperCase();
                questions.add(new Question(questionText, category, options, correctAnswer));
                }
                LoggerUtil.INSTANCE.info("GameServer Loaded " + questions.size() + " questions.");
        }
        catch (IOException e) {
        LoggerUtil.INSTANCE.severe("GameServer Failed to load questions file: " + e.getMessage());
        }
        }
    private void broadcastPointsReset() { // optional reset for specific property, but we'll leverage the READY reset as
                                          // a full reset for simplicity in this example
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendPlayerPoints(Constants.DEFAULT_CLIENT_ID, 0));
    }

    private void broadcastPlayerPoints(ServerThread player) {
        if (player == null) {
            return;
        }
        Server.INSTANCE.sendOrDisconnect(
                serverThread -> serverThread.sendPlayerPoints(player.getClientId(), player.getPoints()));
    }

    private void unicastPlayerPoints(ServerThread target, long clientId, int points) {
        if (target == null) {
            return;
        }
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendPlayerPoints(clientId, points));
    }

    private void unicastGuessConfirmation(ServerThread target, int guess) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendGuessConfirmation(guess));
    }

    /**
     * Sends the current phase plus all existing active players' ready, turn, and
     * points state to a newly joined player.
     */
    private void unicastGameStateToJoiner(ServerThread joiner) {
        if (joiner == null) {
            return;
        }
        unicastCurrentPhase(joiner);
        List<ServerThread> snapshot = new ArrayList<>(getActivePlayers());
        for (ServerThread player : snapshot) {
            if (player.getClientId() == joiner.getClientId()) {
                continue;
            }
            unicastReadyStatus(joiner, player.getClientId(), player.isReady());
            unicastTurnStatus(joiner, player.getClientId(), player.isTurnTaken());
            unicastPlayerPoints(joiner, player.getClientId(), player.getPoints());
        }
    }

    /** Sends the current game phase to all connected clients. */
    private void broadcastCurrentPhase() {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendGamePhase(phase));
    }

    /** Sends the current game phase to a single client. */
    private void unicastCurrentPhase(ServerThread target) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendGamePhase(phase));
    }

    /** Notifies all connected clients of a player's ready status. */
    private void broadcastReadyStatus(long clientId, boolean isReady) {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendReadyStatus(clientId, isReady));
    }

    private void broadcastCorrectAnswer(String correctAnswer) {
        broadcastGameMessage("The correct answer was: " + correctAnswer);
    }

    /** Sends a player's ready status to a single client. */
    private void unicastReadyStatus(ServerThread target, long clientId, boolean isReady) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendReadyStatus(clientId, isReady));
    }

    /** Notifies all connected clients of a player's turn-taken status. */
    private void broadcastTurnStatus(long clientId, boolean hasTakenTurn) {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendTurnStatus(clientId, hasTakenTurn));
    }

    /** Sends a player's turn-taken status to a single client. */
    private void unicastTurnStatus(ServerThread target, long clientId, boolean hasTakenTurn) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendTurnStatus(clientId, hasTakenTurn));
    }

    /** Sends a game message to all connected clients. */
    private void broadcastGameMessage(String message) {
        Server.INSTANCE.broadcast(null, GAME_TAG + message);
    }

    /** Sends a game message to a single client. */
    private void unicastGameMessage(ServerThread target, String message) {
        if (target == null) {
            return;
        }
        final String formatted = GAME_TAG + message;
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendMessage(formatted));
    }

    // end region for helper methods to send data to clients
}
