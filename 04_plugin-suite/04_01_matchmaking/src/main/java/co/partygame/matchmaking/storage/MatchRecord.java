package co.partygame.matchmaking.storage;

import co.partygame.common.mysql.MySQLManager;
import co.partygame.common.mysql.tables.DatabaseTables;
import co.partygame.common.util.JsonUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores matchmaking records locally with MySQL synchronization.
 * Tracks match history, statistics, and performance metrics.
 */
public class MatchRecord {

    private static final Logger LOGGER = Logger.getLogger(MatchRecord.class.getName());

    private final MySQLManager mysql;

    public MatchRecord(MySQLManager mysql) {
        this.mysql = Objects.requireNonNull(mysql);
        ensureTables();
    }

    /**
     * Creates a new matchmaking record and saves it.
     *
     * @param playerId  the player's UUID
     * @param gameId    the game identifier
     * @param gameType  the game type
     * @param backend   the backend server name
     * @param result    the result (SUCCESS, TIMEOUT, CANCELLED)
     * @param teammates list of teammate UUIDs
     * @param joinTime  the time the player joined the queue
     * @param startTime the time the game started
     * @param endTime   the time the game ended
     * @return the created record
     */
    public MatchRecordEntry createRecord(UUID playerId, String gameId, String gameType,
                                          String backend, String result,
                                          List<String> teammates,
                                          long joinTime, long startTime, long endTime) {
        UUID recordId = UUID.randomUUID();
        MatchRecordEntry record = new MatchRecordEntry(
                recordId, playerId, gameId, gameType, backend, result,
                teammates, joinTime, startTime, endTime);
        record.save();
        return record;
    }

    /**
     * Gets all matchmaking records for a specific player.
     *
     * @param playerId the player's UUID
     * @return list of match records
     */
    public List<MatchRecordEntry> getRecordsForPlayer(UUID playerId) {
        List<MatchRecordEntry> results = new ArrayList<>();
        String sql = "SELECT * FROM pg_match_records WHERE player_uuid = ? ORDER BY join_time DESC LIMIT 50";
        try {
            mysql.query(sql, rs -> {
                try {
                    results.add(parseRecord(rs));
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to parse record", e);
                }
            }, playerId.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to query records for " + playerId, e);
        }
        return results;
    }

    /**
     * Gets match statistics for a specific player.
     *
     * @param playerId the player's UUID
     * @return map of statistic name to value
     */
    @Deprecated
    public Map<String, Integer> getStats(UUID playerId) {
        Integer total = extractInt(executeQuerySingle("SELECT COUNT(*) FROM pg_match_records WHERE player_uuid = ?",
                playerId.toString()));
        Integer wins = extractInt(executeQuerySingle("SELECT COUNT(*) FROM pg_match_records WHERE player_uuid = ? AND result = 'SUCCESS'",
                playerId.toString()));

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("total", total != null ? total : 0);
        stats.put("wins", wins != null ? wins : 0);
        stats.put("losses", (total != null ? total : 0) - (wins != null ? wins : 0));
        return stats;
    }

    /**
     * Gets the player's average wait time across all successful matches.
     *
     * @param playerId the player's UUID
     * @return average wait time in milliseconds, or 0 if no data
     */
    @Deprecated
    public long getAverageWaitTime(UUID playerId) {
        String result = executeQuerySingle(
                "SELECT AVG(end_time - join_time) FROM pg_match_records WHERE player_uuid = ? AND result = 'SUCCESS'",
                playerId.toString());
        if (result == null) return 0;
        try {
            return Double.valueOf(result).longValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets the total number of matches for this player.
     *
     * @param playerId the player's UUID
     * @return total match count
     */
    public int getTotalMatches(UUID playerId) {
        return extractInt(executeQuerySingle("SELECT COUNT(*) FROM pg_match_records WHERE player_uuid = ?",
                playerId.toString())) + 0;
    }

    /**
     * Gets the player's win rate (wins / total matches) as a double.
     *
     * @param playerId the player's UUID
     * @return win rate as a double (0.0 to 1.0)
     */
    public double getWinRate(UUID playerId) {
        Integer total = extractInt(executeQuerySingle("SELECT COUNT(*) FROM pg_match_records WHERE player_uuid = ?",
                playerId.toString()));
        if (total == null || total == 0) return 0.0;

        Integer wins = extractInt(executeQuerySingle("SELECT COUNT(*) FROM pg_match_records WHERE player_uuid = ? AND result = 'SUCCESS'",
                playerId.toString()));

        return wins != null ? (double) wins / total : 0.0;
    }

    /**
     * Gets the player's current winning streak.
     *
     * @param playerId the player's UUID
     * @return number of consecutive wins
     */
    public int getStreak(UUID playerId) {
        int streak = 0;
        String sql = "SELECT result FROM pg_match_records WHERE player_uuid = ? ORDER BY join_time DESC LIMIT 20";
        mysql.query(sql, rs -> {
            try {
                String result = rs.getString("result");
                if ("SUCCESS".equals(result)) {
                    streak++;
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to parse streak record", e);
            }
        }, playerId.toString());
        return streak;
    }

    /**
     * Ensures the match records table exists.
     */
    private void ensureTables() {
        try {
            DatabaseTables.initTables(mysql);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize database tables", e);
        }
    }

    private MatchRecordEntry parseRecord(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String gameId = rs.getString("game_id");
        String gameType = rs.getString("game_type");
        String backend = rs.getString("backend");
        String result = rs.getString("result");

        List<String> teammates = new ArrayList<>();
        Object teammatesObj = rs.getObject("teammates");
        if (teammatesObj instanceof String) {
            teammates = JsonUtils.parseStringList((String) teammatesObj);
        }

        long joinTime = toLong(rs.getLong("join_time"));
        long startTime = toLong(rs.getLong("start_time"));
        long endTime = toLong(rs.getLong("end_time"));

        return new MatchRecordEntry(id, playerId, gameId, gameType, backend,
                result, teammates, joinTime, startTime, endTime);
    }

    private List<MatchRecordEntry> queryRecords(String sql, String... params) {
        List<MatchRecordEntry> results = new ArrayList<>();
        try {
            mysql.query(sql, rs -> {
                try {
                    results.add(parseRecord(rs));
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to parse record", e);
                }
            }, params);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Query failed: " + sql, e);
        }
        return results;
    }

    private List<Map<String, Object>> query(String sql, String... params) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            mysql.query(sql, rs -> {
                try {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
                        map.put(rs.getMetaData().getColumnName(i + 1), rs.getObject(i + 1));
                    }
                    results.add(map);
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to parse row", e);
                }
            }, params);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Query failed: " + sql, e);
        }
        return results;
    }

    private String executeQuerySingle(String sql, String... params) {
        return mysql.querySingle(sql, params);
    }

    @Deprecated
    public List<Map<String, Object>> getTopGames(UUID playerId) {
        return query("SELECT game_id, game_type, COUNT(*) as count "
                + "FROM pg_match_records WHERE player_uuid = ? "
                + "GROUP BY game_id, game_type ORDER BY count DESC LIMIT 5",
                playerId.toString());
    }

    private int extractInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long toLong(long val) {
        return val;
    }

    /**
     * Entry representing a single matchmaking record.
     */
    public static class MatchRecordEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public final UUID id;
        public final UUID playerId;
        public final String gameId;
        public final String gameType;
        public final String backend;
        public final String result;
        public final List<String> teammates;
        public final long joinTime;
        public final long startTime;
        public final long endTime;

        public MatchRecordEntry(UUID id, UUID playerId, String gameId, String gameType,
                                String backend, String result, List<String> teammates,
                                long joinTime, long startTime, long endTime) {
            this.id = Objects.requireNonNull(id);
            this.playerId = Objects.requireNonNull(playerId);
            this.gameId = Objects.requireNonNull(gameId);
            this.gameType = Objects.requireNonNull(gameType);
            this.backend = Objects.requireNonNull(backend);
            this.result = Objects.requireNonNull(result);
            this.teammates = teammates != null ? teammates : new ArrayList<>();
            this.joinTime = joinTime;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        /**
         * Gets the match duration in milliseconds.
         *
         * @return duration, or 0 if not available
         */
        public long getDuration() {
            if (startTime == 0 || endTime == 0) return 0;
            return endTime - startTime;
        }

        /**
         * Gets the wait time in milliseconds (time spent in queue).
         *
         * @return wait time, or 0 if not available
         */
        public long getWaitTime() {
            if (startTime == 0) return 0;
            return startTime - joinTime;
        }

        /**
         * Saves this record to the database.
         *
         * @return true if save was successful
         */
        public boolean save() {
            String jsonTeammates;
            try {
                jsonTeammates = JsonUtils.toJson(teammates);
            } catch (Exception ignored) {
                jsonTeammates = "[]";
            }

            String sql = "INSERT INTO pg_match_records "
                    + "(id, player_uuid, game_id, game_type, backend, result, teammates, "
                    + "  join_time, start_time, end_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try {
                mysql.update(sql,
                        id.toString(), playerId.toString(), gameId, gameType, backend,
                        result, jsonTeammates, joinTime, startTime, endTime);
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save match record", e);
                return false;
            }
        }
    }
}
