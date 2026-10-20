package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.FaceDtos.EvolutionPoint;
import com.celestedeathtracker.api.dto.FaceDtos.FaceDetail;
import com.celestedeathtracker.api.dto.FaceDtos.FaceSummary;
import com.celestedeathtracker.api.dto.FaceDtos.RecentAttempt;
import com.celestedeathtracker.api.dto.FaceDtos.RoomStat;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.service.AttemptViews.View;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcule les statistiques d'une face à partir des tentatives classées par {@link AttemptViews}
 * (les règles — tentative significative, deathless réussi, room d'arrêt, durée — y sont décrites).
 * Tout est lu pour UN compte : on ne passe jamais un identifiant qui ne vient pas de ses tentatives.
 */
@Service
@Transactional(readOnly = true)
public class FaceStatsService {

    private static final int EVOLUTION_MAX = 200;
    private static final int RECENT_MAX = 8;

    private final AttemptRepository attempts;
    private final AttemptViews attemptViews;

    public FaceStatsService(AttemptRepository attempts, AttemptViews attemptViews) {
        this.attempts = attempts;
        this.attemptViews = attemptViews;
    }

    private record FaceKey(String chapter, String side) {
    }

    /** « Mes maps » : une ligne par face jouée, la plus récente d'abord. */
    public List<FaceSummary> faces(Long userId) {
        Map<FaceKey, List<View>> byFace = new LinkedHashMap<>();
        for (View v : attemptViews.significantViews(attempts.findByUserIdOrderByStartedAtAsc(userId))) {
            byFace.computeIfAbsent(new FaceKey(v.attempt().getChapter(), v.attempt().getSide()),
                    k -> new ArrayList<>()).add(v);
        }
        return byFace.entrySet().stream()
                .map(e -> new FaceSummary(
                        e.getKey().chapter(),
                        e.getKey().side(),
                        mapName(e.getValue()),
                        e.getValue().size(),
                        (int) e.getValue().stream().filter(View::success).count(),
                        e.getValue().stream().mapToLong(v -> v.attempt().getStartedAt()).max().orElse(0)))
                .sorted(Comparator.comparingLong(FaceSummary::lastPlayedAt).reversed())
                .toList();
    }

    /** Le détail d'une face ; vide si le compte n'y a aucune tentative significative. */
    public Optional<FaceDetail> face(Long userId, String sid, String side) {
        List<View> views = attemptViews.significantViews(
                attempts.findByUserIdAndChapterAndSideOrderByStartedAtAsc(userId, sid, side));
        if (views.isEmpty()) {
            return Optional.empty();
        }

        int clears = (int) views.stream().filter(View::success).count();
        Long bestClear = views.stream().filter(View::success).map(View::durationMs)
                .filter(d -> d != null).min(Long::compare).orElse(null);
        View longest = views.stream().filter(v -> v.durationMs() != null)
                .max(Comparator.comparingLong(View::durationMs)).orElse(null);

        List<String> order = roomOrder(views);

        List<RoomStat> rooms = order.stream().map(room -> new RoomStat(
                room,
                (int) views.stream().filter(v -> v.rooms().contains(room)).count(),
                (int) views.stream().filter(v -> !v.success() && room.equals(v.endRoom())).count(),
                (int) views.stream().flatMap(v -> v.deaths().stream()).filter(d -> room.equals(d.getRoom())).count()))
                .toList();

        List<EvolutionPoint> evolution = tail(views, EVOLUTION_MAX).stream()
                .map(v -> new EvolutionPoint(v.attempt().getId(), v.attempt().getStartedAt(), v.durationMs(), v.result()))
                .toList();

        List<RecentAttempt> recent = new ArrayList<>();
        for (int i = views.size() - 1; i >= 0 && recent.size() < RECENT_MAX; i--) {
            View v = views.get(i);
            recent.add(new RecentAttempt(v.attempt().getId(), v.attempt().getStartedAt(), v.result(),
                    v.durationMs(), v.endRoom(), roomsCleared(v, order), v.deaths().size()));
        }

        return Optional.of(new FaceDetail(
                sid, side, mapName(views), views.size(), clears,
                (double) clears / views.size(), bestClear,
                longest == null ? null : longest.durationMs(),
                longest == null ? null : longest.endRoom(),
                rooms, evolution, recent));
    }

    // ------------------------------------------------------------------ calcul

    /**
     * L'ordre des rooms de la map, déduit des tentatives : celui de la tentative qui est allée le plus
     * loin (de préférence terminée), puis les rooms vues ailleurs dans l'ordre où on les rencontre.
     */
    private List<String> roomOrder(List<View> views) {
        View reference = views.stream()
                .max(Comparator.<View>comparingInt(v -> "completed".equals(v.attempt().getEndReason()) ? 1 : 0)
                        .thenComparingInt(v -> v.rooms().size()))
                .orElseThrow();
        Set<String> order = new LinkedHashSet<>(reference.rooms());
        for (View v : views) {
            order.addAll(v.rooms());
            if (v.endRoom() != null && !AttemptViews.NOT_A_ROOM.contains(v.endRoom())) {
                order.add(v.endRoom());
            }
        }
        return new ArrayList<>(order);
    }

    private int roomsCleared(View v, List<String> order) {
        if (v.success()) {
            return order.size();
        }
        int i = v.endRoom() == null ? -1 : order.indexOf(v.endRoom());
        return i >= 0 ? i : v.rooms().size();
    }

    private String mapName(Collection<View> views) {
        String name = null;
        for (View v : views) {
            if (v.attempt().getMapName() != null) {
                name = v.attempt().getMapName(); // la plus récente l'emporte
            }
        }
        return name;
    }

    private static <T> List<T> tail(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(list.size() - max, list.size());
    }
}
