package dev.herade.moderationgui.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Серверное разрешение прав. Индивидуальные правила имеют приоритет над рангами;
 * внутри рангов более конкретный узел важнее wildcard, затем учитывается приоритет ранга.
 */
public final class PermissionRepository {
    private final Database database;

    public PermissionRepository(Database database) {
        this.database = database;
    }

    public void setRankPermission(long rankId, String node, boolean allowed) throws SQLException {
        String normalized = normalizeNode(node, true);
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO rank_permissions(rank_id, permission_node, allowed)
                VALUES (?, ?, ?)
                ON CONFLICT(rank_id, permission_node) DO UPDATE SET allowed = excluded.allowed
                """)) {
            statement.setLong(1, rankId);
            statement.setString(2, normalized);
            statement.setInt(3, allowed ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void setPlayerPermission(String playerUuid, String node, boolean allowed, String assignedBy)
            throws SQLException {
        String uuid = requireText(playerUuid, "playerUuid", 36);
        String actor = requireText(assignedBy, "assignedBy", 36);
        String normalized = normalizeNode(node, true);
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_permissions(player_uuid, permission_node, allowed, assigned_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, permission_node) DO UPDATE SET
                    allowed = excluded.allowed,
                    assigned_by = excluded.assigned_by
                """)) {
            statement.setString(1, uuid);
            statement.setString(2, normalized);
            statement.setInt(3, allowed ? 1 : 0);
            statement.setString(4, actor);
            statement.executeUpdate();
        }
    }

    public boolean removePlayerPermission(String playerUuid, String node) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM player_permissions WHERE player_uuid = ? AND permission_node = ?")) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            statement.setString(2, normalizeNode(node, true));
            return statement.executeUpdate() > 0;
        }
    }

    public boolean hasPermission(String playerUuid, String requestedNode) throws SQLException {
        String uuid = requireText(playerUuid, "playerUuid", 36);
        String requested = normalizeNode(requestedNode, false);

        Candidate personal = findPlayerCandidate(uuid, requested);
        if (personal != null) return personal.allowed();

        Candidate ranked = findRankCandidate(uuid, requested);
        return ranked != null && ranked.allowed();
    }

    private Candidate findPlayerCandidate(String uuid, String requested) throws SQLException {
        Candidate best = null;
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT permission_node, allowed FROM player_permissions WHERE player_uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String pattern = result.getString("permission_node");
                    if (!matches(pattern, requested)) continue;
                    Candidate candidate = new Candidate(specificity(pattern), 0, Integer.MAX_VALUE,
                            result.getInt("allowed") == 1);
                    best = choose(best, candidate);
                }
            }
        }
        return best;
    }

    private Candidate findRankCandidate(String uuid, String requested) throws SQLException {
        Candidate best = null;
        try (PreparedStatement assigned = database.connection().prepareStatement("""
                SELECT r.id, r.priority
                FROM player_ranks pr JOIN ranks r ON r.id = pr.rank_id
                WHERE pr.player_uuid = ?
                ORDER BY r.priority DESC, r.id ASC
                """)) {
            assigned.setString(1, uuid);
            try (ResultSet ranks = assigned.executeQuery()) {
                while (ranks.next()) {
                    long rankId = ranks.getLong("id");
                    int rankPriority = ranks.getInt("priority");
                    int depth = 0;
                    while (rankId > 0) {
                        if (++depth > 128) {
                            throw new SQLException("Rank inheritance chain exceeds 128 levels");
                        }
                        try (PreparedStatement permissions = database.connection().prepareStatement(
                                "SELECT permission_node, allowed FROM rank_permissions WHERE rank_id = ?")) {
                            permissions.setLong(1, rankId);
                            try (ResultSet rules = permissions.executeQuery()) {
                                while (rules.next()) {
                                    String pattern = rules.getString("permission_node");
                                    if (!matches(pattern, requested)) continue;
                                    Candidate candidate = new Candidate(specificity(pattern), depth - 1,
                                            rankPriority, rules.getInt("allowed") == 1);
                                    best = choose(best, candidate);
                                }
                            }
                        }
                        try (PreparedStatement parent = database.connection().prepareStatement(
                                "SELECT parent_rank_id FROM ranks WHERE id = ?")) {
                            parent.setLong(1, rankId);
                            try (ResultSet result = parent.executeQuery()) {
                                if (!result.next()) break;
                                rankId = result.getLong(1);
                                if (result.wasNull()) rankId = 0;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static Candidate choose(Candidate current, Candidate next) {
        if (current == null) return next;
        if (next.specificity() > current.specificity()) return next;
        if (next.specificity() < current.specificity()) return current;
        if (next.inheritanceDepth() < current.inheritanceDepth()) return next;
        if (next.inheritanceDepth() > current.inheritanceDepth()) return current;
        if (next.rankPriority() > current.rankPriority()) return next;
        if (next.rankPriority() < current.rankPriority()) return current;
        // При равной специфичности и приоритете явный запрет безопаснее разрешения.
        return current.allowed() && !next.allowed() ? next : current;
    }

    private static boolean matches(String pattern, String requested) {
        if (pattern.equals("*")) return true;
        if (pattern.equals(requested)) return true;
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return requested.equals(prefix) || requested.startsWith(prefix + ".");
        }
        return false;
    }

    private static int specificity(String pattern) {
        return pattern.equals("*") ? 0 : pattern.endsWith(".*")
                ? pattern.length() - 2 : 1000 + pattern.length();
    }

    private static String normalizeNode(String node, boolean wildcardAllowed) {
        if (node == null || node.isBlank()) throw new IllegalArgumentException("Permission node cannot be blank");
        String normalized = node.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128) throw new IllegalArgumentException("Permission node cannot exceed 128 characters");
        String expression = wildcardAllowed
                ? "(\\*|[a-z0-9_-]+(\\.[a-z0-9_-]+)*(\\.\\*)?)"
                : "[a-z0-9_-]+(\\.[a-z0-9_-]+)*";
        if (!normalized.matches(expression)) throw new IllegalArgumentException("Invalid permission node: " + node);
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        return normalized;
    }

    private record Candidate(int specificity, int inheritanceDepth, int rankPriority, boolean allowed) { }
}
