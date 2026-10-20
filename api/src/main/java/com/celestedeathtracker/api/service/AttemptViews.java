package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.model.Attempt;
import com.celestedeathtracker.api.model.Death;
import com.celestedeathtracker.api.model.RoomEntry;
import com.celestedeathtracker.api.repository.DeathRepository;
import com.celestedeathtracker.api.repository.RoomEntryRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Charge les morts et les entrées de room des tentatives, et les classe selon les règles de
 * docs/arborescence-pages.md. Partagé par les statistiques d'une face et par le live de session.
 * <ul>
 *   <li><b>tentative significative</b> : au moins une mort, une entrée de room, ou une fin « completed » ;</li>
 *   <li><b>deathless réussi</b> : terminée (completed) sans aucune mort ;</li>
 *   <li><b>room où s'arrête une tentative</b> : celle de la première mort ; à défaut, sa room de fin ;</li>
 *   <li><b>durée d'une tentative morte</b> : le temps de partie à la première mort (le temps tenu).</li>
 * </ul>
 * Les identifiants passés doivent venir de tentatives déjà filtrées par propriétaire.
 */
@Component
public class AttemptViews {

    // « end » marque la fin de la map dans les données du jeu : ce n'est pas une room à afficher.
    public static final Set<String> NOT_A_ROOM = Set.of("end");

    private final DeathRepository deaths;
    private final RoomEntryRepository roomEntries;

    public AttemptViews(DeathRepository deaths, RoomEntryRepository roomEntries) {
        this.deaths = deaths;
        this.roomEntries = roomEntries;
    }

    /** Une tentative avec ses morts et ses entrées de room, avant tout classement. */
    public record Loaded(Attempt attempt, List<Death> deaths, List<RoomEntry> entries) {

        public boolean completed() {
            return "completed".equals(attempt.getEndReason());
        }

        /** Les tentatives de quelques secondes sans rien d'autre (recommencer tout de suite) sont ignorées. */
        public boolean significant() {
            return !deaths.isEmpty() || !entries.isEmpty() || completed();
        }

        /** Dernier signe de vie de la tentative : son départ, une entrée de room, une mort ou sa fin. */
        public long lastActivity() {
            long last = attempt.getStartedAt();
            if (attempt.getEndedAt() != null) {
                last = Math.max(last, attempt.getEndedAt());
            }
            for (Death d : deaths) {
                last = Math.max(last, d.getTimestamp());
            }
            for (RoomEntry e : entries) {
                last = Math.max(last, e.getEnteredAt());
            }
            return last;
        }
    }

    /** Une tentative classée : issue, durée, room d'arrêt. result : success | death | abandoned | ongoing. */
    public record View(
            Attempt attempt,
            List<Death> deaths,
            List<String> rooms,      // rooms visitées dans l'ordre, départ compris
            String result,
            Long durationMs,
            String endRoom) {

        public boolean success() {
            return "success".equals(result);
        }
    }

    public List<Loaded> load(List<Attempt> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Attempt::getId).toList();
        Map<Long, List<Death>> deathsByAttempt = deaths.findByAttemptIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(Death::getAttemptId));
        Map<Long, List<RoomEntry>> entriesByAttempt = roomEntries.findByAttemptIdInOrderByEnteredAtAsc(ids).stream()
                .collect(Collectors.groupingBy(RoomEntry::getAttemptId));

        List<Loaded> loaded = new ArrayList<>();
        for (Attempt a : list) {
            loaded.add(new Loaded(a, deathsByAttempt.getOrDefault(a.getId(), List.of()),
                    entriesByAttempt.getOrDefault(a.getId(), List.of())));
        }
        return loaded;
    }

    /** Les tentatives significatives, classées, dans l'ordre reçu. */
    public List<View> significantViews(List<Attempt> list) {
        return load(list).stream().filter(Loaded::significant).map(this::view).toList();
    }

    public View view(Loaded l) {
        Attempt a = l.attempt();
        Set<String> rooms = new LinkedHashSet<>();
        if (a.getStartRoom() != null && !NOT_A_ROOM.contains(a.getStartRoom())) {
            rooms.add(a.getStartRoom());
        }
        for (RoomEntry r : l.entries()) {
            if (!NOT_A_ROOM.contains(r.getRoom())) {
                rooms.add(r.getRoom());
            }
        }
        List<String> visited = new ArrayList<>(rooms);

        if (!l.deaths().isEmpty()) {
            Death first = l.deaths().get(0);
            // La première mort met fin au deathless : c'est là que la tentative s'arrête.
            return new View(a, l.deaths(), visited, "death", first.getTimeInRoomMs(), first.getRoom());
        }
        if (l.completed()) {
            return new View(a, l.deaths(), visited, "success", a.getSessionTimeMs(), null);
        }
        if (a.getEndReason() == null) {
            String last = visited.isEmpty() ? a.getStartRoom() : visited.get(visited.size() - 1);
            return new View(a, l.deaths(), visited, "ongoing", a.getSessionTimeMs(), last);
        }
        return new View(a, l.deaths(), visited, "abandoned", a.getSessionTimeMs(), a.getEndRoom());
    }
}
