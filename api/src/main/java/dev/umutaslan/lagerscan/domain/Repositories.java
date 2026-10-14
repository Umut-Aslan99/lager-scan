package dev.umutaslan.lagerscan.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

/** Spring Data repositories, all scoped by workspace. */
public final class Repositories {
    private Repositories() {}

    public interface WorkspaceRepo extends JpaRepository<Workspace, UUID> {
        Optional<Workspace> findByJoinToken(String joinToken);

        @Modifying
        @Query("delete from Workspace w where w.id <> :template and w.lastSeenAt < :cutoff")
        int deleteIdle(UUID template, Instant cutoff);

        @Query("select w.id from Workspace w where w.id <> :template order by w.lastSeenAt")
        List<UUID> oldestFirst(UUID template);
    }

    public interface ArticleRepo extends JpaRepository<Article, UUID> {
        List<Article> findByWorkspaceIdOrderByNumber(UUID workspaceId);
        Optional<Article> findByWorkspaceIdAndNumberIgnoreCase(UUID workspaceId, String number);
        Optional<Article> findByWorkspaceIdAndEan(UUID workspaceId, String ean);
    }

    public interface BinRepo extends JpaRepository<Bin, UUID> {
        List<Bin> findByWorkspaceIdOrderByCode(UUID workspaceId);
        Optional<Bin> findByWorkspaceIdAndCodeIgnoreCase(UUID workspaceId, String code);
    }

    public interface StockRepo extends JpaRepository<Stock, UUID> {
        List<Stock> findByWorkspaceId(UUID workspaceId);
        List<Stock> findByWorkspaceIdAndArticleNumberOrderByBinCode(UUID workspaceId, String articleNumber);
        List<Stock> findByWorkspaceIdAndBinCodeOrderByArticleNumber(UUID workspaceId, String binCode);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Stock> findByWorkspaceIdAndArticleNumberAndBinCode(UUID workspaceId, String articleNumber, String binCode);
    }

    public interface BookingRepo extends JpaRepository<Booking, UUID> {
        Optional<Booking> findByWorkspaceIdAndClientId(UUID workspaceId, UUID clientId);
        List<Booking> findByWorkspaceIdOrderByBookedAtDesc(UUID workspaceId, Limit limit);
        List<Booking> findByWorkspaceIdAndArticleNumberOrderByBookedAtDesc(UUID workspaceId, String articleNumber, Limit limit);
        List<Booking> findByWorkspaceIdAndBookedAtAfter(UUID workspaceId, Instant after);
    }

    public interface PhotoRepo extends JpaRepository<Photo, UUID> {
        Optional<Photo> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
        List<Photo> findByWorkspaceIdAndArticleNumberIn(UUID workspaceId, Collection<String> articleNumbers);
    }
}
