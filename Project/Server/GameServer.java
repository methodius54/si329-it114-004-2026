package Project.Server;

import java.util.ArrayList;
import java.util.List;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Common.ValidationUtils;
import Project.Exceptions.ValidationException;

public class GameServer extends BaseGameServer {

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int READY_SECONDS = 30;
    private static final int ROUND_SECONDS = 30;
    private static final int TURN_SECONDS = 20;
    private static final String GAME_TAG = "[Game] ";

    private volatile Phase phase = Phase.INACTIVE;
    private volatile TimedEvent readyTimer;
    private volatile TimedEvent roundTimer;
    private volatile TimedEvent turnTimer;
    private volatile Long currentTurnPlayerId;
    private volatile int roundNumber = 0;

    @Override
    protected void onSpectatorJoined(ServerThread client) {
        if (client == null) {
            return;
        }
        if (phase == Phase.INACTIVE) {
            return;
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
            return;
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
        phase = Phase.IN_PROGRESS;
        broadcastCurrentPhase();
        for (ServerThread player : getActivePlayers()) {
            if (!player.isEliminated()) {
                player.setChoice(null);
                player.setTurnTaken(false);
                broadcastTurnStatus(player.getClientId(), false);
            }
        }
        roundNumber++;
        startRoundTimer();
        broadcastGameMessage("Round " + roundNumber + " started. Use /choice <r/p/s> to make your pick. You have " + ROUND_SECONDS + "s.");
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
        ServerThread chosen = snapshot.get(0);
        currentTurnPlayerId = chosen.getClientId();
        startTurnTimer();
        broadcastGameMessage("Turn started for " + chosen.getDisplayName() + ". Use /turn <action> within " + TURN_SECONDS + "s.");
        LoggerUtil.INSTANCE.info("[GameServer] onTurnStart() end");
    }

    @Override
    protected synchronized void onTurnEnd() {
        LoggerUtil.INSTANCE.info("[GameServer] onTurnEnd() start");
        resetTurnTimer();
        currentTurnPlayerId = null;
        LoggerUtil.INSTANCE.info("[GameServer] onTurnEnd() end");
        boolean allTaken = getActivePlayers().stream().filter(p -> !p.isEliminated()).allMatch(ServerThread::isTurnTaken);
        if (allTaken) {
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
        broadcastGameMessage("Round ended. Evaluating choices");

        List<ServerThread> snapshot = new ArrayList<>(getActivePlayers());

        for (ServerThread player : snapshot) {
            if (!player.isEliminated() && !player.isTurnTaken()) {
                player.setEliminated(true);
                broadcastEliminationStatus(player.getClientId(), true);
                broadcastGameMessage(player.getDisplayName() + " was eliminated for not making a choice");
            }
        }

        List<ServerThread> eligiblePlayers = new ArrayList<>();
        for (ServerThread player : snapshot) {
            if (!player.isEliminated() && player.getChoice() != null) {
                eligiblePlayers.add(player);
            }
        }

        for (int i = 0; i < eligiblePlayers.size(); i++) {
            ServerThread attacker = eligiblePlayers.get(i);
            ServerThread defender = eligiblePlayers.get((i + 1) % eligiblePlayers.size());

            if (attacker.isEliminated() || defender.isEliminated()) {
                continue;
            }

            String aChoice = attacker.getChoice();
            String dChoice = defender.getChoice();
            int result = resolveRPS(aChoice, dChoice);
            if (result > 0) {
                attacker.setPoints(attacker.getPoints() + 1);
                broadcastPlayerPoints(attacker);
                defender.setEliminated(true);
                broadcastEliminationStatus(defender.getClientId(), true);
                broadcastGameMessage(attacker.getDisplayName() + " chose " + aChoice + " while " + defender.getDisplayName() + " chose " + dChoice + "; " + attacker.getDisplayName() + " wins!");
            } else if (result < 0) {
                defender.setPoints(defender.getPoints() + 1);
                broadcastPlayerPoints(defender);
                attacker.setEliminated(true);
                broadcastEliminationStatus(attacker.getClientId(), true);
                broadcastGameMessage(defender.getDisplayName() + " chose " + dChoice + " while " + attacker.getDisplayName() + " chose " + aChoice + "; " + defender.getDisplayName() + " wins!");
            } else {
                broadcastGameMessage(attacker.getDisplayName() + " chose " + aChoice + " while " + defender.getDisplayName() + " chose " + dChoice + "; it's a tie!");
            }
        }

        long remaining = snapshot.stream().filter(p -> !p.isEliminated()).count();

        LoggerUtil.INSTANCE.info("[GameServer] onRoundEnd() end");
        if (remaining <= 1) {
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
        phase = Phase.INACTIVE;

        List<ServerThread> snapshot = new ArrayList<>(getActivePlayers());

        List<ServerThread> remaining = new ArrayList<>();
        for (ServerThread p : snapshot) {
            if (!p.isEliminated()) remaining.add(p);
        }

        if (remaining.size() == 1) {
            broadcastGameMessage(remaining.get(0).getDisplayName() + " wins the session!");
        } else {
            broadcastGameMessage("Session ended in a tie!");
        }

        snapshot.sort((a, b) -> Integer.compare(b.getPoints(), a.getPoints()));
        StringBuilder sb = new StringBuilder("Final Scoreboard:\n");
        for (int i = 0; i < snapshot.size(); i++) {
            ServerThread p = snapshot.get(i);
            sb.append(String.format("%d. %s - %d points\n", i + 1, p.getDisplayName(), p.getPoints()));
        }
        broadcastGameMessage(sb.toString());

        for (ServerThread player : snapshot) {
            player.resetGameState();
        }
        broadcastReadyStatus(Constants.DEFAULT_CLIENT_ID, false);
        clearActivePlayers();
        broadcastCurrentPhase();
        broadcastGameMessage("Session ended. Type /ready to join the next session.");
        LoggerUtil.INSTANCE.info("[GameServer] onSessionEnd() end");
    }

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
            broadcastGameMessage("Ready check started. Session begins in " + READY_SECONDS + "s if enough players are ready.");
        }
    }

    private synchronized void resetReadyTimer() {
        if (readyTimer != null) {
            readyTimer.cancel();
            readyTimer = null;
        }
    }

    private synchronized void startRoundTimer() {
        final int capturedRound = roundNumber;
        roundTimer = new TimedEvent(ROUND_SECONDS, () -> {
            if (roundNumber == capturedRound) {
                onRoundEnd();
            }
        });
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

    protected void handleChoice(ServerThread sender, String choice) {
        try {
            ValidationUtils.requireParticipating(isActivePlayer(sender));
            ValidationUtils.requirePhase(phase, Phase.IN_PROGRESS);
            ValidationUtils.requireTrue(!sender.isEliminated(), "You are eliminated! You can't make a choice!");
            ValidationUtils.requireTurnNotTaken(sender.isTurnTaken());
            choice = ValidationUtils.requireValidTurnOption(choice.trim());

            sender.setChoice(choice);
            unicastChoiceConfirmation(sender, choice);
            broadcastGameMessage(sender.getDisplayName() + " has made their choice.");
            sender.setTurnTaken(true);
            broadcastTurnStatus(sender.getClientId(), true);
            onTurnEnd();
        } catch (ValidationException e) {
            LoggerUtil.INSTANCE.warning("[GameServer] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

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

    @Deprecated
    public void handleTurn(ServerThread sender, String action) {
        try {
            ValidationUtils.requireParticipating(isActivePlayer(sender));
            ValidationUtils.requirePhase(phase, Phase.IN_PROGRESS);
            ValidationUtils.requireTurnNotTaken(sender.isTurnTaken());
            String normalizedAction = ValidationUtils.requireValidTurnOption(action);
            sender.setTurnTaken(true);
            broadcastTurnStatus(sender.getClientId(), true);
            onTurnEnd();
        } catch (ValidationException e) {
            LoggerUtil.INSTANCE.warning("[GameServer] " + e.getMessage());
            unicastGameMessage(sender, e.getMessage());
        }
    }

    private void broadcastPointsReset() {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendPlayerPoints(Constants.DEFAULT_CLIENT_ID, 0));
    }

    private void broadcastPlayerPoints(ServerThread player) {
        if (player == null) {
            return;
        }
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendPlayerPoints(player.getClientId(), player.getPoints()));
    }

    private void unicastPlayerPoints(ServerThread target, long clientId, int points) {
        if (target == null) {
            return;
        }
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendPlayerPoints(clientId, points));
    }

    private void unicastChoiceConfirmation(ServerThread target, String choice) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendChoiceConfirmation(choice));
    }

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

    private void broadcastCurrentPhase() {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendGamePhase(phase));
    }

    private void unicastCurrentPhase(ServerThread target) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendGamePhase(phase));
    }

    private void broadcastReadyStatus(long clientId, boolean isReady) {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendReadyStatus(clientId, isReady));
    }

    private void unicastReadyStatus(ServerThread target, long clientId, boolean isReady) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendReadyStatus(clientId, isReady));
    }

    private void broadcastTurnStatus(long clientId, boolean hasTakenTurn) {
        Server.INSTANCE.sendOrDisconnect(serverThread -> serverThread.sendTurnStatus(clientId, hasTakenTurn));
    }

    private void unicastTurnStatus(ServerThread target, long clientId, boolean hasTakenTurn) {
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendTurnStatus(clientId, hasTakenTurn));
    }

    private void broadcastGameMessage(String message) {
        Server.INSTANCE.broadcast(null, GAME_TAG + message);
    }

    private void unicastGameMessage(ServerThread target, String message) {
        if (target == null) {
            return;
        }
        final String formatted = GAME_TAG + message;
        Server.INSTANCE.unicast(target, serverThread -> serverThread.sendMessage(formatted));
    }

    private void broadcastEliminationStatus(long clientId, boolean isEliminated) {
        Server.INSTANCE.sendOrDisconnect(st -> st.sendEliminationStatus(clientId, isEliminated));
    }

    private int resolveRPS(String a, String b) {
        if (a.equals(b)) return 0;
        if ((a.equals("rock") && b.equals("scissors")) ||
            (a.equals("scissors") && b.equals("paper")) ||
            (a.equals("paper") && b.equals("rock"))) {
            return 1;
        }
        return -1;
    }
}