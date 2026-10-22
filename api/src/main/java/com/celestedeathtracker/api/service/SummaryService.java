package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.SummaryDtos.ActivityDay;
import com.celestedeathtracker.api.dto.SummaryDtos.FaceProgress;
import com.celestedeathtracker.api.dto.SummaryDtos.MostPlayed;
import com.celestedeathtracker.api.dto.SummaryDtos.Summary;
import com.celestedeathtracker.api.dto.SummaryDtos.Today;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.service.AttemptViews.View;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcule le tableau de bord d'UN compte à partir de ses tentatives significatives ({@link AttemptViews}).
 * Comme l'historique, le classement dépend des morts et des entrées de room : il se fait en Java.
 */
@Service
@Transactional(readOnly = true)
public class SummaryService {

    static final int ACTIVITY_DAYS = 14;
    static final int RECENT_MAX = 3;

    private final AttemptRepository attempts;
    private final AttemptViews attemptViews;

    public SummaryService(AttemptRepository attempts, AttemptViews attemptViews) {
        this.attempts = attempts;
        this.attemptViews = attemptViews;
    }

    private record FaceKey(String chapter, String side) {
    }

    public Summary summary(Long userId, ZoneId zone) {
        List<View> views = attemptViews.significantViews(attempts.findByUserIdOrderByStartedAtAsc(userId));

        Map<FaceKey, List<View>> byFace = new LinkedHashMap<>();
        for (View v : views) {
            byFace.computeIfAbsent(key(v), k -> new ArrayList<>()).add(v);
        }
        List<FaceProgress> faces = byFace.entrySet().stream()
                .map(e -> progress(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(FaceProgress::lastPlayedAt).reversed())
                .toList();

        LocalDate today = LocalDate.now(zone);
        return new Summary(
                faces.isEmpty() ? null : faces.get(0),
                today(views.stream().filter(v -> day(v, zone).equals(today)).toList()),
                faces.stream().skip(1).limit(RECENT_MAX).toList(),
                activity(views, zone, today));
    }

    private static FaceProgress progress(FaceKey key, List<View> views) {
        List<String> order = FaceStatsService.roomOrder(views);
        return new FaceProgress(
                key.chapter(),
                key.side(),
                FaceStatsService.mapName(views),
                views.stream().mapToLong(v -> v.attempt().getStartedAt()).max().orElse(0),
                views.size(),
                (int) views.stream().filter(View::success).count(),
                views.stream().mapToInt(v -> FaceStatsService.roomsReached(v, order)).max().orElse(0),
                order.size());
    }

    private static Today today(List<View> views) {
        Long best = views.stream().map(View::durationMs).filter(d -> d != null).max(Long::compare).orElse(null);

        Map<FaceKey, List<View>> byFace = new LinkedHashMap<>();
        for (View v : views) {
            byFace.computeIfAbsent(key(v), k -> new ArrayList<>()).add(v);
        }
        // À égalité, la face jouée le plus récemment l'emporte.
        MostPlayed mostPlayed = byFace.entrySet().stream()
                .max(Comparator.<Map.Entry<FaceKey, List<View>>>comparingInt(e -> e.getValue().size())
                        .thenComparingLong(e -> e.getValue().get(e.getValue().size() - 1).attempt().getStartedAt()))
                .map(e -> new MostPlayed(e.getKey().chapter(), e.getKey().side(),
                        FaceStatsService.mapName(e.getValue()), e.getValue().size()))
                .orElse(null);

        return new Today(views.size(), (int) views.stream().filter(View::success).count(), best, mostPlayed);
    }

    private static List<ActivityDay> activity(List<View> views, ZoneId zone, LocalDate today) {
        LocalDate first = today.minusDays(ACTIVITY_DAYS - 1);
        int[] tries = new int[ACTIVITY_DAYS];
        int[] clears = new int[ACTIVITY_DAYS];
        for (View v : views) {
            LocalDate d = day(v, zone);
            if (d.isBefore(first) || d.isAfter(today)) {
                continue;
            }
            int i = (int) (d.toEpochDay() - first.toEpochDay());
            tries[i]++;
            if (v.success()) {
                clears[i]++;
            }
        }
        List<ActivityDay> days = new ArrayList<>();
        for (int i = 0; i < ACTIVITY_DAYS; i++) {
            days.add(new ActivityDay(first.plusDays(i).toString(), tries[i], clears[i]));
        }
        return days;
    }

    private static FaceKey key(View v) {
        return new FaceKey(v.attempt().getChapter(), v.attempt().getSide());
    }

    /** Le jour (dans le fuseau du joueur) où la tentative a commencé. */
    private static LocalDate day(View v, ZoneId zone) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(v.attempt().getStartedAt()), zone);
    }
}
