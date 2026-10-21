package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.AttemptDtos.AttemptDeath;
import com.celestedeathtracker.api.dto.AttemptDtos.AttemptDetail;
import com.celestedeathtracker.api.dto.AttemptDtos.AttemptPage;
import com.celestedeathtracker.api.dto.AttemptDtos.AttemptRow;
import com.celestedeathtracker.api.dto.AttemptDtos.RouteStep;
import com.celestedeathtracker.api.model.Attempt;
import com.celestedeathtracker.api.model.Death;
import com.celestedeathtracker.api.model.RoomEntry;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.service.AttemptViews.Loaded;
import com.celestedeathtracker.api.service.AttemptViews.View;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historique des tentatives d'UN compte. Le classement (tentative significative, résultat, durée) vient de
 * {@link AttemptViews}. Il dépend des morts et des entrées de room : il se calcule donc en Java, pas en SQL.
 * On charge les tentatives du compte (filtrées par face et période) puis on pagine en mémoire : suffisant tant
 * qu'un compte compte quelques milliers de tentatives.
 */
@Service
@Transactional(readOnly = true)
public class AttemptHistoryService {

    private final AttemptRepository attempts;
    private final AttemptViews attemptViews;

    public AttemptHistoryService(AttemptRepository attempts, AttemptViews attemptViews) {
        this.attempts = attempts;
        this.attemptViews = attemptViews;
    }

    /** Filtres de la liste. Tous facultatifs ; periodDays vide = depuis toujours. */
    public record Filter(String sid, String side, String result, Integer periodDays) {
    }

    public AttemptPage list(Long userId, Filter filter, int page, int size) {
        List<Attempt> candidates = filter.periodDays() == null
                ? attempts.findByUserIdOrderByStartedAtAsc(userId)
                : attempts.findByUserIdAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                        userId, System.currentTimeMillis() - Duration.ofDays(filter.periodDays()).toMillis());

        // On filtre d'abord sur les colonnes de la tentative : on évite de charger les morts des autres faces.
        List<Attempt> onFace = candidates.stream()
                .filter(a -> filter.sid() == null || filter.sid().equals(a.getChapter()))
                .filter(a -> filter.side() == null || filter.side().equals(a.getSide()))
                .toList();

        List<View> views = new ArrayList<>(attemptViews.significantViews(onFace).stream()
                .filter(v -> filter.result() == null || filter.result().equals(v.result()))
                .toList());
        views.sort(Comparator.comparingLong((View v) -> v.attempt().getStartedAt()).reversed());

        long total = views.size();
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min(from + (long) size, total);
        List<AttemptRow> items = views.subList(from, to).stream().map(AttemptHistoryService::row).toList();
        return new AttemptPage(items, page, size, total);
    }

    /** Le détail d'une tentative ; vide si elle n'existe pas, n'est pas à ce compte ou n'est pas significative. */
    public Optional<AttemptDetail> detail(Long userId, long attemptId) {
        return attempts.findById(attemptId)
                // Même réponse qu'une tentative inexistante : on ne révèle pas qu'elle existe chez un autre compte.
                .filter(a -> userId.equals(a.getUserId()))
                .flatMap(a -> attemptViews.load(List.of(a)).stream().findFirst())
                .filter(Loaded::significant)
                .map(l -> detail(l, attemptViews.view(l)));
    }

    private static AttemptRow row(View v) {
        Attempt a = v.attempt();
        return new AttemptRow(a.getId(), a.getStartedAt(), a.getChapter(), a.getSide(), a.getMapName(),
                v.result(), v.durationMs(), v.endRoom(), v.deaths().size());
    }

    private static AttemptDetail detail(Loaded l, View v) {
        Attempt a = l.attempt();

        List<RouteStep> route = new ArrayList<>();
        for (String room : v.rooms()) {
            Optional<RoomEntry> entry = l.entries().stream().filter(e -> room.equals(e.getRoom())).findFirst();
            // La room de départ n'a pas d'événement d'entrée : on la date du départ de la tentative.
            route.add(new RouteStep(room,
                    entry.map(RoomEntry::getEnteredAt).orElse(a.getStartedAt()),
                    entry.map(RoomEntry::getSessionTimeMs).orElse(null)));
        }

        List<AttemptDeath> deaths = new ArrayList<>();
        for (Death d : l.deaths()) {
            deaths.add(new AttemptDeath(d.getId(), d.getRoom(), d.getDeathX(), d.getDeathY(),
                    d.getTimestamp(), d.getTimeInRoomMs(), d == l.deaths().get(0)));
        }

        return new AttemptDetail(a.getId(), a.getStartedAt(), a.getEndedAt(), a.getChapter(), a.getSide(),
                a.getMapName(), v.result(), v.durationMs(), a.getEndReason(), a.getStartRoom(), v.endRoom(),
                route, deaths);
    }
}
