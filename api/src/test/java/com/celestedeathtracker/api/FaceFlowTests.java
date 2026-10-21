package com.celestedeathtracker.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Statistiques par face : on ingère des tentatives comme le mod, puis on lit ce que le site affichera. */
@SpringBootTest
@AutoConfigureMockMvc
class FaceFlowTests {

    private static final String SID = "Celeste/1-ForsakenCity";

    @Autowired MockMvc mvc;

    private record Player(MockHttpSession session, String bearer) {}

    private Player newPlayer() throws Exception {
        String body = "{\"email\":\"%s@test.fr\",\"password\":\"motdepasse123\"}".formatted(UUID.randomUUID());
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
        return new Player(session, "Bearer " + m.group(1));
    }

    private void event(Player p, String type, String id, String room, long ts, String extra) throws Exception {
        String body = "{\"type\":\"%s\",\"attemptId\":\"%s\",\"chapter\":\"%s\",\"side\":\"B\",\"saveSlot\":0,\"room\":\"%s\",\"timestamp\":%d%s}"
                .formatted(type, id, SID, room, ts, extra);
        mvc.perform(post("/api/ingest/events").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
    }

    private void death(Player p, String id, String room, long ts, long sessionMs) throws Exception {
        deathAt(p, id, room, ts, sessionMs, 1, 2);
    }

    private void deathAt(Player p, String id, String room, long ts, long sessionMs, int x, int y) throws Exception {
        String body = ("{\"timestamp\":%d,\"chapter\":\"%s\",\"room\":\"%s\",\"deathX\":%d,\"deathY\":%d,"
                + "\"totalDeaths\":1,\"sessionDeaths\":1,\"timeInRoomMs\":%d,\"attemptId\":\"%s\",\"side\":\"B\",\"saveSlot\":0}")
                .formatted(ts, SID, room, x, y, sessionMs, id);
        mvc.perform(post("/api/ingest/deaths").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    /** Une réussite, deux morts, un abandon et une tentative parasite sur Forsaken City, face B. */
    private Player playerWithHistory() throws Exception {
        Player p = newPlayer();
        String a1 = UUID.randomUUID().toString(), a2 = UUID.randomUUID().toString(), a3 = UUID.randomUUID().toString();
        String a4 = UUID.randomUUID().toString(), a5 = UUID.randomUUID().toString();

        // 1. deathless réussi en 1:23
        event(p, "attempt_start", a1, "00", 1_000, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a1, "01", 2_000, ",\"sessionTimeMs\":5000");
        event(p, "room_enter", a1, "02", 3_000, ",\"sessionTimeMs\":9000");
        event(p, "attempt_end", a1, "end", 4_000, ",\"sessionTimeMs\":83000,\"reason\":\"completed\"");

        // 2. morte en room 02 après 41 s
        event(p, "attempt_start", a2, "00", 10_000, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a2, "01", 11_000, ",\"sessionTimeMs\":4000");
        event(p, "room_enter", a2, "02", 12_000, ",\"sessionTimeMs\":9000");
        death(p, a2, "02", 13_000, 41_000);
        event(p, "attempt_end", a2, "02", 14_000, ",\"sessionTimeMs\":42000,\"reason\":\"restart\"");

        // 3. morte en room 01 après 20 s, sans entrée de room
        event(p, "attempt_start", a3, "00", 20_000, ",\"startedFromBeginning\":true");
        death(p, a3, "01", 21_000, 20_000);
        event(p, "attempt_end", a3, "01", 22_000, ",\"sessionTimeMs\":21000,\"reason\":\"restart\"");

        // 4. parasite : recommencée aussitôt, ni mort ni room (doit être ignorée)
        event(p, "attempt_start", a4, "00", 30_000, ",\"startedFromBeginning\":true");
        event(p, "attempt_end", a4, "00", 31_000, ",\"sessionTimeMs\":2000,\"reason\":\"restart\"");

        // 5. abandonnée en room 01
        event(p, "attempt_start", a5, "00", 40_000, ",\"startedFromBeginning\":true");
        event(p, "room_enter", a5, "01", 41_000, ",\"sessionTimeMs\":4000");
        event(p, "attempt_end", a5, "01", 42_000, ",\"sessionTimeMs\":10000,\"reason\":\"give_up\"");
        return p;
    }

    @Test
    void faceDetailComputesTheDeathlessStatistics() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/faces/detail").session(p.session()).param("sid", SID).param("side", "B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapName").value("Forsaken City"))
                .andExpect(jsonPath("$.attempts").value(4))           // la tentative parasite est ignorée
                .andExpect(jsonPath("$.clears").value(1))
                .andExpect(jsonPath("$.clearRate").value(0.25))
                .andExpect(jsonPath("$.bestClearMs").value(83_000))
                .andExpect(jsonPath("$.longestMs").value(83_000))
                // rooms dans l'ordre de la tentative réussie, sans la pseudo-room « end »
                .andExpect(jsonPath("$.rooms", hasSize(3)))
                .andExpect(jsonPath("$.rooms[0].room").value("00"))
                .andExpect(jsonPath("$.rooms[0].entries").value(4))
                .andExpect(jsonPath("$.rooms[1].room").value("01"))
                .andExpect(jsonPath("$.rooms[1].entries").value(3))
                .andExpect(jsonPath("$.rooms[1].endings").value(2))   // morte en 01 + abandon en 01
                .andExpect(jsonPath("$.rooms[1].deaths").value(1))
                .andExpect(jsonPath("$.rooms[2].room").value("02"))
                .andExpect(jsonPath("$.rooms[2].endings").value(1))
                .andExpect(jsonPath("$.rooms[2].deaths").value(1))
                // évolution dans l'ordre chronologique ; durée d'une morte = temps tenu avant la mort
                .andExpect(jsonPath("$.evolution", hasSize(4)))
                .andExpect(jsonPath("$.evolution[0].result").value("success"))
                .andExpect(jsonPath("$.evolution[1].result").value("death"))
                .andExpect(jsonPath("$.evolution[1].durationMs").value(41_000))
                // dernières tentatives : la plus récente d'abord
                .andExpect(jsonPath("$.recent", hasSize(4)))
                .andExpect(jsonPath("$.recent[0].result").value("abandoned"))
                .andExpect(jsonPath("$.recent[0].endRoom").value("01"))
                .andExpect(jsonPath("$.recent[0].roomsCleared").value(1))
                .andExpect(jsonPath("$.recent[3].result").value("success"))
                .andExpect(jsonPath("$.recent[3].roomsCleared").value(3));
    }

    @Test
    void roomDetailCountsEntriesStopsAndOutcomes() throws Exception {
        Player p = playerWithHistory();

        // Room 01 : atteinte par 4 tentatives (la morte sans entrée enregistrée comprise), 2 s'y arrêtent
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "B").param("room", "01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapName").value("Forsaken City"))
                .andExpect(jsonPath("$.room").value("01"))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.roomCount").value(3))
                .andExpect(jsonPath("$.previousRoom").value("00"))
                .andExpect(jsonPath("$.nextRoom").value("02"))
                .andExpect(jsonPath("$.entries").value(4))
                .andExpect(jsonPath("$.stops").value(2))               // morte en 01 + abandon en 01
                .andExpect(jsonPath("$.stopRate").value(0.5))
                .andExpect(jsonPath("$.deathsTotal").value(1))
                .andExpect(jsonPath("$.lastDeathAt").value(21_000))
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].x").value(1))
                .andExpect(jsonPath("$.points[0].y").value(2))
                .andExpect(jsonPath("$.points[0].first").value(true))
                // ordre chronologique : réussie, morte en 02 (franchit 01), morte en 01, abandonnée en 01
                .andExpect(jsonPath("$.outcomes", hasSize(4)))
                .andExpect(jsonPath("$.outcomes[0].outcome").value("cleared"))
                .andExpect(jsonPath("$.outcomes[1].outcome").value("cleared"))
                .andExpect(jsonPath("$.outcomes[2].outcome").value("died"))
                .andExpect(jsonPath("$.outcomes[3].outcome").value("left"));

        // Dernière room : pas de suivante ; aucune mort ailleurs, la première room n'a pas de précédente
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "B").param("room", "02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextRoom").doesNotExist())
                .andExpect(jsonPath("$.entries").value(2))
                .andExpect(jsonPath("$.stops").value(1))
                .andExpect(jsonPath("$.outcomes[1].outcome").value("died"));
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "B").param("room", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousRoom").doesNotExist())
                .andExpect(jsonPath("$.stops").value(0))
                .andExpect(jsonPath("$.stopRate").value(0.0))
                .andExpect(jsonPath("$.lastDeathAt").doesNotExist())
                .andExpect(jsonPath("$.points", hasSize(0)));
    }

    @Test
    void roomDetailMarksOnlyTheFirstDeathOfAnAttempt() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        event(p, "attempt_start", id, "00", 1_000, ",\"startedFromBeginning\":true");
        event(p, "room_enter", id, "05", 2_000, ",\"sessionTimeMs\":5000");
        deathAt(p, id, "05", 3_000, 6_000, 100, 200);
        deathAt(p, id, "05", 4_000, 9_000, 110, 210);
        event(p, "attempt_end", id, "05", 5_000, ",\"sessionTimeMs\":9500,\"reason\":\"restart\"");

        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "B").param("room", "05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").value(1))
                .andExpect(jsonPath("$.stops").value(1))
                .andExpect(jsonPath("$.stopRate").value(1.0))          // jamais au-dessus de 100 %
                .andExpect(jsonPath("$.deathsTotal").value(2))
                .andExpect(jsonPath("$.points", hasSize(2)))
                .andExpect(jsonPath("$.points[0].x").value(100))
                .andExpect(jsonPath("$.points[0].first").value(true))
                .andExpect(jsonPath("$.points[1].x").value(110))
                .andExpect(jsonPath("$.points[1].first").value(false));
    }

    @Test
    void roomIsIdentifiedByItsFaceNotByItsNameAlone() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "A").param("room", "01"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", "Celeste/2-OldSite").param("side", "B").param("room", "01"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "B").param("room", "inconnue"))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomDetailIsPrivateAndNeedsASession() throws Exception {
        Player p = playerWithHistory();
        Player bob = newPlayer();

        mvc.perform(get("/api/faces/room").session(bob.session())
                        .param("sid", SID).param("side", "B").param("room", "01"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/faces/room").session(p.session())
                        .param("sid", SID).param("side", "Z").param("room", "01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/faces/room").param("sid", SID).param("side", "B").param("room", "01"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/faces/room").header("Authorization", p.bearer())
                        .param("sid", SID).param("side", "B").param("room", "01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listGivesOneLinePerFaceWithItsCounts() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/faces").session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sid").value(SID))
                .andExpect(jsonPath("$[0].side").value("B"))
                .andExpect(jsonPath("$[0].mapName").value("Forsaken City"))
                .andExpect(jsonPath("$[0].attempts").value(4))
                .andExpect(jsonPath("$[0].clears").value(1));
    }

    @Test
    void anotherAccountSeesNothing() throws Exception {
        playerWithHistory();
        Player bob = newPlayer();

        mvc.perform(get("/api/faces").session(bob.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/faces/detail").session(bob.session()).param("sid", SID).param("side", "B"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownFaceIsNotFoundAndBadSideIsRejected() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/faces/detail").session(p.session()).param("sid", SID).param("side", "C"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/faces/detail").session(p.session()).param("sid", SID).param("side", "Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresALoggedInSessionAndTokenDoesNotGiveAccess() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/faces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/faces").header("Authorization", p.bearer())).andExpect(status().isUnauthorized());
    }
}
