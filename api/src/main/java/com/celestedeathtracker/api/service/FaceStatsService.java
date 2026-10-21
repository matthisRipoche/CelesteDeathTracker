package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.FaceDtos.DeathPoint;
import com.celestedeathtracker.api.dto.FaceDtos.EvolutionPoint;
import com.celestedeathtracker.api.dto.FaceDtos.FaceDetail;
import com.celestedeathtracker.api.dto.FaceDtos.FaceSummary;
import com.celestedeathtracker.api.dto.FaceDtos.RecentAttempt;
import com.celestedeathtracker.api.dto.FaceDtos.RoomDetail;
import com.celestedeathtracker.api.dto.FaceDtos.RoomOutcome;
import com.celestedeathtracker.api.dto.FaceDtos.RoomStat;
import com.celestedeathtracker.api.model.Death;
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
    private static final int POINTS_MAX = 500;
    private static final int OUTCOMES_MAX = 100;

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

    /**
     * Le détail d'une room ; vide si aucune tentative significative de la face n'y est entrée. Une room
     * s'identifie par (sid, side, room) : un même nom existe dans d'autres maps.
     */
    public Optional<RoomDetail> room(Long userId, String sid, String side, String room) {
        List<View> views = attemptViews.significantViews(
                attempts.findByUserIdAndChapterAndSideOrderByStartedAtAsc(userId, sid, side));
        // Une tentative a atteint la room si elle y est entrée, s'y est arrêtée ou y est morte : un événement
        // d'entrée perdu (envoi sans file d'attente) ne doit pas la faire disparaître, ni dépasser 100 %.
        List<View> entered = views.stream()
                .filter(v -> v.rooms().contains(room) || isStop(v, room) || diedIn(v, room))
                .toList();
        if (entered.isEmpty()) {
            return Optional.empty();
        }

        List<String> order = roomOrder(views);
        int index = order.indexOf(room);

        int stops = (int) entered.stream().filter(v -> isStop(v, room)).count();

        List<DeathPoint> points = new ArrayList<>();
        for (View v : entered) {
            for (Death d : v.deaths()) {
                if (room.equals(d.getRoom())) {
                    points.add(new DeathPoint(d.getId(), v.attempt().getId(), d.getTimestamp(),
                            d.getDeathX(), d.getDeathY(), d == v.deaths().get(0)));
                }
            }
        }
        points.sort(Comparator.comparingLong(DeathPoint::at));
        Long lastDeathAt = points.isEmpty() ? null : points.get(points.size() - 1).at();

        List<RoomOutcome> outcomes = tail(entered, OUTCOMES_MAX).stream()
                .map(v -> new RoomOutcome(v.attempt().getId(), v.attempt().getStartedAt(), outcome(v, room)))
                .toList();

        return Optional.of(new RoomDetail(
                sid, side, mapName(views), room,
                index + 1, order.size(),
                index > 0 ? order.get(index - 1) : null,
                index < order.size() - 1 ? order.get(index + 1) : null,
                entered.size(), stops, (double) stops / entered.size(),
                points.size(), lastDeathAt,
                tail(points, POINTS_MAX), outcomes));
    }

    // ------------------------------------------------------------------ calcul

    /** La tentative s'arrête dans cette room (même règle que RoomStat.endings sur la page de la face). */
    private static boolean isStop(View v, String room) {
        return !v.success() && room.equals(v.endRoom());
    }

    private static boolean diedIn(View v, String room) {
        return v.deaths().stream().anyMatch(d -> room.equals(d.getRoom()));
    }

    private static String outcome(View v, String room) {
        if (diedIn(v, room)) {
            return "died";
        }
        return isStop(v, room) ? "left" : "cleared";
    }

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
