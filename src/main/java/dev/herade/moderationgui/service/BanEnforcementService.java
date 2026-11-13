package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.PunishmentRepository;

import java.sql.SQLException;
import java.util.Optional;

/** Безопасная проверка активного BAN перед допуском игрока на сервер. */
public final class BanEnforcementService {
    private BanEnforcementService() { }

    public static Optional<PunishmentRepository.Punishment> findActiveBan(
            PunishmentRepository punishments, String playerUuid, long now) throws SQLException {
        return punishments.findActiveForPlayer(playerUuid, now).stream()
                .filter(punishment -> punishment.type() == PunishmentRepository.Type.BAN)
                .findFirst();
    }
}
