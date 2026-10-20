package com.celestedeathtracker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.model.Attempt;
import com.celestedeathtracker.api.model.Death;
import com.celestedeathtracker.api.repository.AttemptRepository;
import com.celestedeathtracker.api.repository.DeathRepository;
import com.celestedeathtracker.api.repository.RoomEntryRepository;
import com.celestedeathtracker.api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Ingestion des tentatives : événements du mod (Bearer) reconstruits côté serveur. */
@SpringBootTest
@AutoConfigureMockMvc
class AttemptFlowTests {

    private static final String SID = "Celeste/1-ForsakenCity";

    @Autowired MockMvc mvc;
    @Autowired AttemptRepository attempts;
    @Autowired RoomEntryRepository roomEntries;
    @Autowired DeathRepository deaths;
    @Autowired UserRepository users;

    private record Player(Long userId, String bearer) {}

    /** Crée un compte et un token d'API, comme le ferait l'appairage. */
    private Player newPlayer() throws Exception {
        String email = UUID.randomUUID() + "@test.fr";
        String body = "{\"email\":\"%s\",\"password\":\"motdepasse123\"}".formatted(email);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        String json = mvc.perform(post("/api/tokens").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"PC\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"token\":\"(cdt_[^\"]+)\"").matcher(json);
        assertTrue(m.find());
        Long userId = users.findByEmail(email).orElseThrow().getId();
        return new Player(userId, "Bearer " + m.group(1));
    }

    private void event(Player p, String json, int expectedStatus) throws Exception {
        mvc.perform(post("/api/ingest/events").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(expectedStatus));
    }

    private static String ev(String type, String attemptId, String side, String room, long ts, String extra) {
        return "{\"type\":\"%s\",\"attemptId\":\"%s\",\"chapter\":\"%s\",\"side\":\"%s\",\"saveSlot\":2,\"room\":\"%s\",\"timestamp\":%d%s}"
                .formatted(type, attemptId, SID, side, room, ts, extra);
    }

    private void death(Player p, String attemptId, String side, String room, long ts) throws Exception {
        String attempt = attemptId == null ? "" : ",\"attemptId\":\"%s\",\"side\":\"%s\",\"saveSlot\":2".formatted(attemptId, side);
        String body = ("{\"timestamp\":%d,\"chapter\":\"%s\",\"room\":\"%s\",\"deathX\":10,\"deathY\":20,"
                + "\"totalDeaths\":5,\"sessionDeaths\":1,\"timeInRoomMs\":1234%s}").formatted(ts, SID, room, attempt);
        mvc.perform(post("/api/ingest/deaths").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    @Test
    void deathlessAttemptIsRebuiltFromItsEvents() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();

        event(p, ev("attempt_start", id, "B", "a-00", 1000,
                ",\"mapName\":\"Cité oubliée\",\"startedFromBeginning\":true"), 202);
        event(p, ev("room_enter", id, "B", "a-01", 2000, ",\"sessionTimeMs\":900"), 202);
        event(p, ev("room_enter", id, "B", "a-02", 3000, ",\"sessionTimeMs\":1800"), 202);
        event(p, ev("attempt_end", id, "B", "a-02", 4000, ",\"sessionTimeMs\":2500,\"reason\":\"completed\""), 202);

        Attempt a = attempts.findByUserIdAndClientId(p.userId(), id).orElseThrow();
        assertEquals("B", a.getSide());
        assertEquals(2, a.getSaveSlot());
        assertEquals("Cité oubliée", a.getMapName());
        assertEquals(Boolean.TRUE, a.getStartedFromBeginning());
        assertEquals("a-00", a.getStartRoom());
        assertEquals("completed", a.getEndReason());
        assertEquals(2500L, a.getSessionTimeMs());
        assertEquals(2, roomEntries.findByAttemptIdOrderByEnteredAtAsc(a.getId()).size());
        // Aucune mort rattachée + fin "completed" = deathless réussi
        assertTrue(deaths.findAll().stream().noneMatch(d -> a.getId().equals(d.getAttemptId())));
    }

    @Test
    void deathIsLinkedToItsAttempt() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        event(p, ev("attempt_start", id, "A", "a-00", 1000, ""), 202);
        death(p, id, "A", "a-05", 5000);

        Attempt a = attempts.findByUserIdAndClientId(p.userId(), id).orElseThrow();
        List<Death> linked = deaths.findByUserIdOrderByIdAsc(p.userId());
        assertEquals(1, linked.size());
        assertEquals(a.getId(), linked.get(0).getAttemptId());
        assertEquals("A", linked.get(0).getSide());
        assertEquals(2, linked.get(0).getSaveSlot());
    }

    @Test
    void lostStartEventDoesNotBreakTheAttempt() throws Exception {
        // Le mod envoie sans file d'attente : attempt_start peut se perdre. La mort suffit à créer la tentative.
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        death(p, id, "C", "c-03", 5000);
        event(p, ev("attempt_end", id, "C", "c-03", 6000, ",\"reason\":\"restart\""), 202);

        Attempt a = attempts.findByUserIdAndClientId(p.userId(), id).orElseThrow();
        assertEquals("C", a.getSide());
        assertEquals("restart", a.getEndReason());
        assertNull(a.getMapName());
        // Départ estimé : instant de la mort (5000) moins le temps de partie envoyé avec elle (1234)
        assertEquals(5000 - 1234, a.getStartedAt());
        assertEquals(1, deaths.findByUserIdOrderByIdAsc(p.userId()).size());
    }

    @Test
    void oldModDeathWithoutAttemptStillWorks() throws Exception {
        Player p = newPlayer();
        death(p, null, null, "b-05", 5000);

        Death d = deaths.findByUserIdOrderByIdAsc(p.userId()).get(0);
        assertNull(d.getAttemptId());
        assertNull(d.getSide());
        assertNull(d.getSaveSlot());
    }

    @Test
    void sameAttemptIdFromTwoPlayersStaysSeparate() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String id = UUID.randomUUID().toString();
        event(alice, ev("attempt_start", id, "A", "a-00", 1000, ""), 202);
        event(bob, ev("attempt_start", id, "A", "a-00", 1000, ""), 202);

        Attempt a = attempts.findByUserIdAndClientId(alice.userId(), id).orElseThrow();
        Attempt b = attempts.findByUserIdAndClientId(bob.userId(), id).orElseThrow();
        assertNotNull(a.getId());
        assertTrue(!a.getId().equals(b.getId()));
    }

    @Test
    void closingTheGameEndsTheOpenAttempt() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        event(p, ev("attempt_start", id, "A", "a-00", 1000, ""), 202);
        event(p, ev("attempt_end", id, "A", "a-03", 9000, ",\"sessionTimeMs\":8000,\"reason\":\"game_closed\""), 202);

        Attempt a = attempts.findByUserIdAndClientId(p.userId(), id).orElseThrow();
        assertEquals("game_closed", a.getEndReason());
        assertEquals("a-03", a.getEndRoom());
    }

    @Test
    void invalidEventsAreRejected() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        event(p, ev("attempt_start", id, "Z", "a-00", 1000, ""), 400);                       // face inconnue
        event(p, ev("n_importe_quoi", id, "A", "a-00", 1000, ""), 400);                      // type inconnu
        event(p, ev("attempt_end", id, "A", "a-00", 1000, ",\"reason\":\"abandon\""), 400); // motif inconnu
        event(p, ev("attempt_start", "x".repeat(40), "A", "a-00", 1000, ""), 400);          // identifiant trop long
    }
}
