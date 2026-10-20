package com.celestedeathtracker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.dto.LiveDtos.LiveSnapshot;
import com.celestedeathtracker.api.repository.UserRepository;
import com.celestedeathtracker.api.service.LiveService;
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
import org.springframework.test.web.servlet.MvcResult;

/** Live de session : session automatique, pause tolérée dans la map, flux en temps réel. */
@SpringBootTest
@AutoConfigureMockMvc
class LiveFlowTests {

    private static final String SID = "Celeste/1-ForsakenCity";
    private static final long MIN = 60_000L;
    // Une date fixe dans le passé récent : les tests passent un « maintenant » simulé au service.
    private static final long T0 = 1_800_000_000_000L;

    @Autowired MockMvc mvc;
    @Autowired LiveService live;
    @Autowired UserRepository users;

    private record Player(Long userId, MockHttpSession session, String bearer) {}

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
        return new Player(users.findByEmail(email).orElseThrow().getId(), session, "Bearer " + m.group(1));
    }

    private void event(Player p, String type, String id, String room, long ts, String extra) throws Exception {
        String body = "{\"type\":\"%s\",\"attemptId\":\"%s\",\"chapter\":\"%s\",\"side\":\"B\",\"saveSlot\":0,\"room\":\"%s\",\"timestamp\":%d%s}"
                .formatted(type, id, SID, room, ts, extra);
        mvc.perform(post("/api/ingest/events").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
    }

    private void death(Player p, String id, String room, long ts, long sessionMs) throws Exception {
        String body = ("{\"timestamp\":%d,\"chapter\":\"%s\",\"room\":\"%s\",\"deathX\":1,\"deathY\":2,"
                + "\"totalDeaths\":1,\"sessionDeaths\":1,\"timeInRoomMs\":%d,\"attemptId\":\"%s\",\"side\":\"B\",\"saveSlot\":0}")
                .formatted(ts, SID, room, sessionMs, id);
        mvc.perform(post("/api/ingest/deaths").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    /** Une tentative fermée, morte en `deathRoom` après `heldMs`. */
    private String closedDeath(Player p, long start, String deathRoom, long heldMs) throws Exception {
        String a = id();
        event(p, "attempt_start", a, "00", start, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a, "01", start + 2_000, ",\"sessionTimeMs\":2000");
        death(p, a, deathRoom, start + heldMs, heldMs);
        event(p, "attempt_end", a, deathRoom, start + heldMs + 1_000, ",\"sessionTimeMs\":" + (heldMs + 1_000) + ",\"reason\":\"restart\"");
        return a;
    }

    @Test
    void sessionStatsAndCurrentAttemptAreComputed() throws Exception {
        Player p = newPlayer();
        closedDeath(p, T0, "04", 30_000);
        closedDeath(p, T0 + 2 * MIN, "04", 40_000);
        // 3e tentative encore ouverte : room 05, une mort
        String open = id();
        event(p, "attempt_start", open, "00", T0 + 5 * MIN, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", open, "05", T0 + 5 * MIN + 20_000, ",\"sessionTimeMs\":20000");
        death(p, open, "05", T0 + 5 * MIN + 25_000, 25_000);

        LiveSnapshot s = live.snapshot(p.userId(), T0 + 6 * MIN);

        assertTrue(s.active());
        assertNotNull(s.current());
        assertEquals("05", s.current().room());
        assertEquals(1, s.current().deaths());
        assertEquals("B", s.current().side());
        assertEquals("Forsaken City", s.current().mapName());

        assertEquals(3, s.session().attempts());
        assertEquals(3, s.session().deaths());
        assertEquals(0, s.session().clears());
        assertEquals(T0, s.session().startedAt());
        assertEquals(3, s.session().recent().size());
        assertEquals(s.current().id(), s.session().recent().get(0).id()); // la plus récente d'abord
        // deux tentatives s'arrêtent en room 04 : c'est le passage clé de la session
        assertNotNull(s.session().crux());
        assertEquals("04", s.session().crux().room());
        assertEquals(2, s.session().crux().endings());
        assertEquals(1, s.session().faces().size());
    }

    @Test
    void pauseInsideTheMapKeepsTheSessionOpen() throws Exception {
        Player p = newPlayer();
        String open = id();
        event(p, "attempt_start", open, "00", T0, ",\"startedFromBeginning\":true");
        event(p, "room_enter", open, "01", T0 + 10_000, ",\"sessionTimeMs\":10000");

        // 2 h sans rien : le jeu est en pause, le joueur n'a pas quitté la map
        LiveSnapshot paused = live.snapshot(p.userId(), T0 + 120 * MIN);
        assertTrue(paused.active());
        assertNotNull(paused.current());

        // 4 h sans rien : on considère le jeu fermé sans passer par la sortie de la map
        LiveSnapshot abandoned = live.snapshot(p.userId(), T0 + 240 * MIN);
        assertFalse(abandoned.active());
        assertNull(abandoned.current());
        assertNotNull(abandoned.session()); // le résumé de la dernière session reste lisible
    }

    @Test
    void sessionEndsThirtyMinutesAfterLeavingTheMap() throws Exception {
        Player p = newPlayer();
        closedDeath(p, T0, "03", 20_000); // dernière activité : T0 + 21 s

        assertTrue(live.snapshot(p.userId(), T0 + 29 * MIN).active());

        LiveSnapshot over = live.snapshot(p.userId(), T0 + 31 * MIN);
        assertFalse(over.active());
        assertNull(over.current());
        assertEquals(1, over.session().attempts());
    }

    @Test
    void aLongBreakStartsANewSession() throws Exception {
        Player p = newPlayer();
        closedDeath(p, T0, "03", 20_000);
        closedDeath(p, T0 + 2 * 60 * MIN, "06", 20_000); // 2 h plus tard

        LiveSnapshot s = live.snapshot(p.userId(), T0 + 2 * 60 * MIN + 2 * MIN);

        assertEquals(1, s.session().attempts()); // seule la dernière session compte
        assertEquals(T0 + 2 * 60 * MIN, s.session().startedAt());
    }

    @Test
    void quickRestartsWithoutAnythingDoNotCountAsAttempts() throws Exception {
        Player p = newPlayer();
        String parasite = id();
        event(p, "attempt_start", parasite, "00", T0, ",\"startedFromBeginning\":true");
        event(p, "attempt_end", parasite, "00", T0 + 2_000, ",\"sessionTimeMs\":2000,\"reason\":\"restart\"");
        closedDeath(p, T0 + 10_000, "02", 15_000);

        LiveSnapshot s = live.snapshot(p.userId(), T0 + MIN);

        assertEquals(1, s.session().attempts());
        assertEquals(1, s.session().deaths());
    }

    @Test
    void nothingPlayedRecentlyGivesAnEmptyLive() throws Exception {
        Player p = newPlayer();
        closedDeath(p, T0, "03", 20_000);

        // 3 jours plus tard : hors de la fenêtre des dernières 24 h
        LiveSnapshot s = live.snapshot(p.userId(), T0 + 3 * 24 * 60 * MIN);
        assertFalse(s.active());
        assertNull(s.session());
        assertNull(s.current());
    }

    @Test
    void anotherAccountHasItsOwnLive() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        closedDeath(alice, T0, "03", 20_000);

        assertNull(live.snapshot(bob.userId(), T0 + MIN).session());
        assertNotNull(live.snapshot(alice.userId(), T0 + MIN).session());
    }

    @Test
    void snapshotRouteNeedsALoggedInSession() throws Exception {
        Player p = newPlayer();
        long now = System.currentTimeMillis();
        String a = id();
        event(p, "attempt_start", a, "00", now - 20_000, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a, "02", now - 10_000, ",\"sessionTimeMs\":10000");

        mvc.perform(get("/api/live").session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.current.room").value("02"))
                .andExpect(jsonPath("$.current.mapName").value("Forsaken City"));

        mvc.perform(get("/api/live")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/live").header("Authorization", p.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/live/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void streamPushesAnUpdateAsSoonAsTheGameSendsAnEvent() throws Exception {
        Player p = newPlayer();

        // Le navigateur ouvre le flux : il reçoit tout de suite l'état courant (rien de joué)
        MvcResult stream = mvc.perform(get("/api/live/stream").session(p.session()))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertEquals("no", stream.getResponse().getHeader("X-Accel-Buffering"));
        String first = stream.getResponse().getContentAsString();
        assertThat(first).contains("event:snapshot");
        assertThat(first).contains("\"active\":false");

        // Le jeu envoie un départ de tentative : le flux ouvert reçoit une mise à jour, sans qu'on la demande
        long now = System.currentTimeMillis();
        event(p, "attempt_start", id(), "00", now, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");

        String updated = stream.getResponse().getContentAsString();
        assertThat(updated).contains("\"active\":true");
        assertThat(updated).contains("\"mapName\":\"Forsaken City\"");
        assertEquals(2, updated.split("event:snapshot", -1).length - 1);
    }

    // Assertions de chaîne lisibles sans dépendance supplémentaire.
    private static StringAssert assertThat(String s) {
        return new StringAssert(s);
    }

    private record StringAssert(String actual) {
        void contains(String expected) {
            assertTrue(actual.contains(expected), "« " + expected + " » introuvable dans : " + actual);
        }
    }
}
