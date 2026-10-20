package com.celestedeathtracker.api.service;

import com.celestedeathtracker.api.dto.LiveDtos.LiveSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pousse l'état du live aux navigateurs connectés (Server-Sent Events), un flux par onglet ouvert.
 *
 * <p>Chaque nouvel événement du mod (départ, room, mort, fin de tentative) déclenche {@link #publish} :
 * le serveur recalcule l'état UNE fois pour le compte et l'envoie à tous ses onglets. Un message de
 * silence (« ping ») part toutes les 15 s pour que les proxys ne ferment pas la connexion, et l'état est
 * renvoyé chaque minute pour que « session terminée » apparaisse sans nouvel événement.
 *
 * <p>Les connexions sont gardées en mémoire de cette instance : une seule instance d'API (comme pour la
 * limitation de débit). Passer à plusieurs instances demanderait un bus de messages (Redis, base…).
 */
@Component
public class LiveHub {

    private static final Logger log = LoggerFactory.getLogger(LiveHub.class);

    // Le navigateur se reconnecte tout seul (EventSource) et reçoit alors l'état complet.
    private static final long TIMEOUT_MS = 60 * 60_000L;
    // Un compte n'a pas besoin de dizaines d'onglets : au-delà, on ferme les plus anciens.
    private static final int MAX_PER_USER = 5;

    private final LiveService live;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private int ticks;

    public LiveHub(LiveService live) {
        this.live = live;
    }

    /** Ouvre un flux pour ce compte et lui envoie tout de suite l'état courant. */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        while (list.size() > MAX_PER_USER) {
            SseEmitter oldest = list.remove(0);
            oldest.complete();
        }
        send(userId, emitter, live.snapshot(userId, System.currentTimeMillis()));
        return emitter;
    }

    /** À appeler après chaque événement reçu du mod : met à jour tous les onglets de ce compte. */
    public void publish(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return; // personne n'écoute : on ne calcule rien
        }
        try {
            LiveSnapshot snapshot = live.snapshot(userId, System.currentTimeMillis());
            for (SseEmitter emitter : list) {
                send(userId, emitter, snapshot);
            }
        } catch (RuntimeException e) {
            // Ne jamais faire échouer l'enregistrement d'une mort à cause du live.
            log.warn("Live : impossible de publier l'état du compte {}", userId, e);
        }
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        ticks++;
        boolean refresh = ticks % 4 == 0; // toutes les minutes
        emitters.forEach((userId, list) -> {
            if (list.isEmpty()) {
                return;
            }
            if (refresh) {
                publish(userId);
            } else {
                for (SseEmitter emitter : list) {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException e) {
                        remove(userId, emitter);
                    }
                }
            }
        });
    }

    private void send(Long userId, SseEmitter emitter, LiveSnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter); // le navigateur est parti
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    /** Nombre de flux ouverts pour ce compte (pour les tests). */
    int connections(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list == null ? 0 : list.size();
    }
}
