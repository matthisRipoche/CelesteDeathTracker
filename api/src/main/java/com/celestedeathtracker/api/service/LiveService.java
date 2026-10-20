package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.LiveDtos.CruxRoom;
import com.celestedeathtracker.api.dto.LiveDtos.CurrentAttempt;
import com.celestedeathtracker.api.dto.LiveDtos.FaceCount;
import com.celestedeathtracker.api.dto.LiveDtos.LiveAttempt;
import com.celestedeathtracker.api.dto.LiveDtos.LiveSnapshot;
import com.celestedeathtracker.api.dto.LiveDtos.SessionStats;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.service.AttemptViews.Loaded;
import com.celestedeathtracker.api.service.AttemptViews.View;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcule l'état du live : la tentative en cours et les statistiques de la session de jeu.
 *
 * <p><b>Une session</b> est une suite de tentatives dont deux voisines ne sont pas séparées de plus de
 * {@value #SESSION_GAP_MIN} minutes d'inactivité, toutes faces confondues. Le jeu n'envoie rien pendant
 * une pause : tant que la dernière tentative est ouverte (le joueur n'a pas quitté la map), la session
 * reste donc ouverte, jusqu'à {@value #PAUSE_TOLERANCE_H} h sans nouvelle. Au-delà, on la tient pour
 * abandonnée (jeu fermé sans passer par la sortie de la map).
 */
@Service
@Transactional(readOnly = true)
public class LiveService {

    static final int SESSION_GAP_MIN = 30;
    static final int PAUSE_TOLERANCE_H = 3;
    private static final long SESSION_GAP_MS = SESSION_GAP_MIN * 60_000L;
    private static final long PAUSE_TOLERANCE_MS = PAUSE_TOLERANCE_H * 3_600_000L;
    private static final long LOOKBACK_MS = 24 * 3_600_000L;
    private static final int RECENT_MAX = 8;
    private static final int CRUX_MIN_ENDINGS = 2;

    private final AttemptRepository attempts;
    private final AttemptViews attemptViews;

    public LiveService(AttemptRepository attempts, AttemptViews attemptViews) {
        this.attempts = attempts;
        this.attemptViews = attemptViews;
    }

    private record FaceKey(String chapter, String side) {
    }

    private record RoomKey(String chapter, String side, String room) {
    }

    /** L'état du live pour ce compte à l'instant nowMs (passé en paramètre pour pouvoir le tester). */
    public LiveSnapshot snapshot(Long userId, long nowMs) {
        List<Loaded> recent = attemptViews.load(
                attempts.findByUserIdAndStartedAtGreaterThanEqualOrderByStartedAtAsc(userId, nowMs - LOOKBACK_MS));
        if (recent.isEmpty()) {
            return new LiveSnapshot(false, null, null, nowMs);
        }

        List<Loaded> session = lastSession(recent);
        Loaded last = session.get(session.size() - 1);
        long lastActivity = session.stream().mapToLong(Loaded::lastActivity).max().orElse(0);

        // « Dans la map » : la dernière tentative n'a pas été fermée et le jeu a donné signe de vie récemment.
        boolean open = last.attempt().getEndedAt() == null && nowMs - last.lastActivity() < PAUSE_TOLERANCE_MS;
        boolean active = open || nowMs - lastActivity <= SESSION_GAP_MS;

        return new LiveSnapshot(active, stats(session, lastActivity), open ? current(last) : null, nowMs);
    }

    /** La dernière session : on remonte tant que l'écart avec la tentative précédente reste sous le seuil. */
    private List<Loaded> lastSession(List<Loaded> chronological) {
        int start = chronological.size() - 1;
        while (start > 0) {
            long gap = chronological.get(start).attempt().getStartedAt() - chronological.get(start - 1).lastActivity();
            if (gap > SESSION_GAP_MS) {
                break;
            }
            start--;
        }
        return chronological.subList(start, chronological.size());
    }

    private CurrentAttempt current(Loaded l) {
        View v = attemptViews.view(l);
        String room = v.rooms().isEmpty() ? l.attempt().getStartRoom() : v.rooms().get(v.rooms().size() - 1);
        return new CurrentAttempt(l.attempt().getId(), l.attempt().getChapter(), l.attempt().getSide(),
                l.attempt().getMapName(), room, l.attempt().getStartedAt(), l.lastActivity(),
                l.deaths().size(), v.rooms().size());
    }

    private SessionStats stats(List<Loaded> session, long lastActivity) {
        List<View> views = session.stream().filter(Loaded::significant).map(attemptViews::view).toList();

        long activeMs = 0;
        int deaths = 0;
        for (Loaded l : session) {
            long end = l.attempt().getEndedAt() != null ? l.attempt().getEndedAt() : l.lastActivity();
            activeMs += Math.max(0, end - l.attempt().getStartedAt());
            deaths += l.deaths().size();
        }

        Long bestClear = views.stream().filter(View::success).map(View::durationMs)
                .filter(d -> d != null).min(Long::compare).orElse(null);
        View longest = views.stream().filter(v -> v.durationMs() != null)
                .max(Comparator.comparingLong(View::durationMs)).orElse(null);

        return new SessionStats(
                session.get(0).attempt().getStartedAt(), lastActivity, activeMs,
                views.size(), (int) views.stream().filter(View::success).count(), deaths,
                bestClear,
                longest == null ? null : longest.durationMs(),
                longest == null ? null : longest.endRoom(),
                crux(views), faces(views), recent(views));
    }

    /** La room (d'une face donnée) où le plus de tentatives de la session se sont arrêtées. */
    private CruxRoom crux(List<View> views) {
        Map<RoomKey, Integer> endings = new LinkedHashMap<>();
        for (View v : views) {
            if (!v.success() && v.endRoom() != null && !AttemptViews.NOT_A_ROOM.contains(v.endRoom())) {
                endings.merge(new RoomKey(v.attempt().getChapter(), v.attempt().getSide(), v.endRoom()), 1, Integer::sum);
            }
        }
        return endings.entrySet().stream()
                .filter(e -> e.getValue() >= CRUX_MIN_ENDINGS)
                .max(Map.Entry.comparingByValue())
                .map(e -> new CruxRoom(e.getKey().chapter(), e.getKey().side(), e.getKey().room(), e.getValue()))
                .orElse(null);
    }

    private List<FaceCount> faces(List<View> views) {
        Map<FaceKey, List<View>> byFace = new LinkedHashMap<>();
        for (View v : views) {
            byFace.computeIfAbsent(new FaceKey(v.attempt().getChapter(), v.attempt().getSide()), k -> new ArrayList<>()).add(v);
        }
        return byFace.entrySet().stream().map(e -> {
            String name = e.getValue().stream().map(v -> v.attempt().getMapName())
                    .filter(n -> n != null).reduce((a, b) -> b).orElse(null);
            return new FaceCount(e.getKey().chapter(), e.getKey().side(), name, e.getValue().size(),
                    (int) e.getValue().stream().filter(View::success).count());
        }).toList();
    }

    private List<LiveAttempt> recent(List<View> views) {
        List<LiveAttempt> out = new ArrayList<>();
        for (int i = views.size() - 1; i >= 0 && out.size() < RECENT_MAX; i--) {
            View v = views.get(i);
            out.add(new LiveAttempt(v.attempt().getId(), v.attempt().getChapter(), v.attempt().getSide(),
                    v.attempt().getMapName(), v.attempt().getStartedAt(), v.result(), v.durationMs(),
                    v.endRoom(), v.deaths().size()));
        }
        return out;
    }
}
