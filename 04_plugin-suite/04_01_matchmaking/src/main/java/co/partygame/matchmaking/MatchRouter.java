package co.partygame.matchmaking;

import co.partygame.common.protocol.packets.lobby.MatchRequest;
import co.partygame.common.protocol.packets.lobby.PlayerInfo;
import co.partygame.common.protocol.packets.backend.MatchAccepted;
import co.partygame.common.util.BungeeMessenger;
import co.partygame.common.util.ChatUtils;
import co.partygame.matchmaking.backend.BackendManager;
import co.partygame.matchmaking.backend.BackendSelector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes ready player(s) to the appropriate backend server for the matched game session.
 * Handles serialization and transmission of MatchRequest packets, as well as processing
 * MatchAccepted responses from backends.
 */
public class MatchRouter {

    private static final Logger LOGGER = Logger.getLogger(MatchRouter.class.getName());

    private final BungeeMessenger messenger;
    private final BackendManager backendManager;
    private final BackendSelector backendSelector;
    private final Plugin plugin;

    public MatchRouter(BungeeMessenger messenger, BackendManager backendManager,
                       BackendSelector backendSelector, Plugin plugin) {
        this.messenger = Objects.requireNonNull(messenger);
        this.backendManager = Objects.requireNonNull(backendManager);
        this.backendSelector = Objects.requireNonNull(backendSelector);
        this.plugin = Objects.requireNonNull(plugin);
    }

    /**
     * Routes a single MatchRequest to a specific backend server.
     *
     * @param request        the match request containing player list and game details
     * @param backend        the backend server to route to
     */
    public void routePlayers(MatchRequest request, String backend) {
        Objects.requireNonNull(request, "MatchRequest must not be null");
        Objects.requireNonNull(backend, "Backend name must not be null");

        try {
            byte[] data = messenger.serializeMatchRequest(request);
            messenger.sendToServer(backend, data);
            LOGGER.info("MatchRequest " + request.getSessionId() + " sent to " + backend);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to route match request to " + backend, e);
        }
    }

    /**
     * Routes a list of players to an appropriate backend server.
     * Builds the matchmaking request, selects the best backend, and sends it.
     *
     * @param players       the list of players to match
     * @param sessionId     unique session identifier
     * @param gameId        the game identifier (e.g., "survival_001")
     * @param gameType      the game type (e.g., "partygame")
     * @param customOptions custom game options
     * @param partyId       the party UUID if matching as a party, null for solo
     */
    public void routePlayers(List<Player> players, String sessionId, String gameId,
                             String gameType, Map<String, Object> customOptions, UUID partyId) {
        Objects.requireNonNull(players, "Players list must not be null");

        if (players.isEmpty()) {
            LOGGER.warning("Cannot route players: player list is empty");
            return;
        }

        List<PlayerInfo> playerInfos = new ArrayList<>();
        for (Player p : players) {
            playerInfos.add(new PlayerInfo(p.getUniqueId(), p.getName()));
        }

        String sourceServer = plugin.getServer().getName();
        MatchRequest request = new MatchRequest(sessionId, gameId, gameType, playerInfos,
                customOptions, partyId, sourceServer);

        String selectedBackend = selectBackend(gameType);
        if (selectedBackend == null) {
            LOGGER.warning("No available backend for game type: " + gameType);
            for (Player p : players) {
                p.sendMessage(ChatUtils.colorize("&cNo backend server available. Please try again later."));
            }
            return;
        }

        routePlayers(request, selectedBackend);

        for (Player p : players) {
            p.sendMessage(ChatUtils.colorize("&aWaiting for match... &7(a backend server has been assigned)"));
        }
    }

    /**
     * Gets the list of currently available backend servers.
     *
     * @return list of backend server names
     */
    public List<String> getAvailableBackends() {
        List<BackendManager.BackendInfo> backends = backendManager.getOnlineBackends();
        List<String> names = new ArrayList<>();
        for (BackendManager.BackendInfo info : backends) {
            if (info.isOnline()) {
                names.add(info.getName());
            }
        }
        return names;
    }

    /**
     * Handles a MatchAccepted response from a backend server.
     * Notifies players on the lobby side to teleport to the assigned backend.
     *
     * @param accepted the MatchAccepted notification from backend
     */
    public void handleMatchAccepted(MatchAccepted accepted) {
        Objects.requireNonNull(accepted, "MatchAccepted must not be null");

        String server = accepted.getServer();
        List<PlayerInfo> players = accepted.getPlayers();

        if (players == null || players.isEmpty()) {
            LOGGER.warning("MatchAccepted has no players: " + accepted.getSessionId());
            return;
        }

        LOGGER.info("Match accepted for session " + accepted.getSessionId() +
                " -> backend: " + server + ", players: " + players.size());

        for (PlayerInfo playerInfo : players) {
            Player player = Bukkit.getPlayer(playerInfo.getUuid());
            if (player != null && player.isOnline()) {
                teleportPlayer(player, server);
                player.sendMessage(ChatUtils.colorize("&a&lMatch Found! &7Teleporting to &e" + server));
            } else {
                LOGGER.warning("Player not online during teleport: " + playerInfo.getName());
            }
        }
    }

    /**
     * Teleports a player to the specified backend server via BungeeCord/Velocity.
     *
     * @param player     the player to teleport
     * @param serverName the target server name
     */
    public void teleportPlayer(Player player, String serverName) {
        Objects.requireNonNull(player, "Player must not be null");
        Objects.requireNonNull(serverName, "Server name must not be null");

        try {
            player.connect(Bukkit.getServer().getServerName(serverName));
            LOGGER.info("Teleporting " + player.getName() + " to " + serverName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to teleport " + player.getName() + " to " + serverName, e);
            player.sendMessage(ChatUtils.colorize("&cFailed to connect to game server. Please try again."));
        }
    }

    private String selectBackend(String gameType) {
        try {
            return backendSelector.getBackend(gameType);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Backend selection failed for " + gameType, e);
            return null;
        }
    }
}
