package com.celestedeathtracker.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.repository.AttemptRepository;
import com.jayway.jsonpath.JsonPath;
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

/** Historique des tentatives : on ingère comme le mod, puis on lit la liste et le détail comme le site. */
@SpringBootTest
@AutoConfigureMockMvc
class AttemptHistoryFlowTests {

    private static final String SID = "Celeste/1-ForsakenCity";

    @Autowired MockMvc mvc;
    @Autowired AttemptRepository attempts;

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
        String body = ("{\"timestamp\":%d,\"chapter\":\"%s\",\"room\":\"%s\",\"deathX\":7,\"deathY\":9,"
                + "\"totalDeaths\":1,\"sessionDeaths\":1,\"timeInRoomMs\":%d,\"attemptId\":\"%s\",\"side\":\"B\",\"saveSlot\":0}")
                .formatted(ts, SID, room, sessionMs, id);
        mvc.perform(post("/api/ingest/deaths").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    /**
     * Quatre tentatives significatives et une parasite, de la plus ancienne (t = 1 s) à la plus récente :
     * réussite, mort en room 02, mort en room 01, abandon en room 01.
     */
    private Player playerWithHistory() throws Exception {
        Player p = newPlayer();
        String a1 = UUID.randomUUID().toString(), a2 = UUID.randomUUID().toString(), a3 = UUID.randomUUID().toString();
        String a4 = UUID.randomUUID().toString(), a5 = UUID.randomUUID().toString();

        event(p, "attempt_start", a1, "00", 1_000, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a1, "01", 2_000, ",\"sessionTimeMs\":5000");
        event(p, "room_enter", a1, "02", 3_000, ",\"sessionTimeMs\":9000");
        event(p, "attempt_end", a1, "end", 4_000, ",\"sessionTimeMs\":83000,\"reason\":\"completed\"");

        event(p, "attempt_start", a2, "00", 10_000, ",\"mapName\":\"Forsaken City\",\"startedFromBeginning\":true");
        event(p, "room_enter", a2, "01", 11_000, ",\"sessionTimeMs\":4000");
        event(p, "room_enter", a2, "02", 12_000, ",\"sessionTimeMs\":9000");
        death(p, a2, "02", 13_000, 41_000);
        event(p, "attempt_end", a2, "02", 14_000, ",\"sessionTimeMs\":42000,\"reason\":\"restart\"");

        event(p, "attempt_start", a3, "00", 20_000, ",\"startedFromBeginning\":true");
        death(p, a3, "01", 21_000, 20_000);
        event(p, "attempt_end", a3, "01", 22_000, ",\"sessionTimeMs\":21000,\"reason\":\"restart\"");

        // parasite : recommencée aussitôt, ni mort ni room (doit être ignorée)
        event(p, "attempt_start", a4, "00", 30_000, ",\"startedFromBeginning\":true");
        event(p, "attempt_end", a4, "00", 31_000, ",\"sessionTimeMs\":2000,\"reason\":\"restart\"");

        event(p, "attempt_start", a5, "00", 40_000, ",\"startedFromBeginning\":true");
        event(p, "room_enter", a5, "01", 41_000, ",\"sessionTimeMs\":4000");
        event(p, "attempt_end", a5, "01", 42_000, ",\"sessionTimeMs\":10000,\"reason\":\"give_up\"");
        return p;
    }

    @Test
    void listReturnsSignificantAttemptsMostRecentFirst() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/attempts").session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))             // la tentative parasite est ignorée
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].result").value("abandoned"))
                .andExpect(jsonPath("$.items[0].endRoom").value("01"))
                .andExpect(jsonPath("$.items[0].sid").value(SID))
                .andExpect(jsonPath("$.items[0].side").value("B"))
                .andExpect(jsonPath("$.items[1].result").value("death"))
                .andExpect(jsonPath("$.items[1].durationMs").value(20_000))
                .andExpect(jsonPath("$.items[1].deaths").value(1))
                .andExpect(jsonPath("$.items[3].result").value("success"))
                .andExpect(jsonPath("$.items[3].mapName").value("Forsaken City"))
                .andExpect(jsonPath("$.items[3].durationMs").value(83_000))
                .andExpect(jsonPath("$.items[3].deaths").value(0));
    }

    @Test
    void listCanBeFilteredAndPaged() throws Exception {
        Player p = playerWithHistory();

        mvc.perform(get("/api/attempts").session(p.session()).param("result", "death"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)));

        mvc.perform(get("/api/attempts").session(p.session()).param("sid", SID).param("side", "B"))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/attempts").session(p.session()).param("sid", SID).param("side", "A"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/attempts").session(p.session()).param("sid", "Celeste/2-OldSite"))
                .andExpect(jsonPath("$.total").value(0));

        // l'historique daté de 1970 sort des périodes prédéfinies, mais pas de « all »
        mvc.perform(get("/api/attempts").session(p.session()).param("period", "7d"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/attempts").session(p.session()).param("period", "all"))
                .andExpect(jsonPath("$.total").value(4));

        // pages de 3 : la seconde contient la dernière tentative, la plus ancienne
        mvc.perform(get("/api/attempts").session(p.session()).param("size", "3").param("page", "0"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/attempts").session(p.session()).param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].result").value("success"));
        mvc.perform(get("/api/attempts").session(p.session()).param("size", "3").param("page", "9"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void periodKeepsRecentAttempts() throws Exception {
        Player p = newPlayer();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        event(p, "attempt_start", id, "00", now - 60_000, ",\"startedFromBeginning\":true");
        event(p, "room_enter", id, "01", now - 50_000, ",\"sessionTimeMs\":4000");
        event(p, "attempt_end", id, "01", now - 40_000, ",\"sessionTimeMs\":10000,\"reason\":\"give_up\"");

        mvc.perform(get("/api/attempts").session(p.session()).param("period", "7d"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/attempts").session(p.session()).param("period", "30d"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void invalidParametersAreRefusedWithA400() throws Exception {
        Player p = newPlayer();
        for (String[] bad : new String[][] {
                {"side", "Z"}, {"result", "won"}, {"period", "1y"}, {"page", "-1"}, {"size", "0"}, {"size", "101"}}) {
            mvc.perform(get("/api/attempts").session(p.session()).param(bad[0], bad[1]))
                    .andExpect(status().isBadRequest());
        }
        // la borne haute est acceptée
        mvc.perform(get("/api/attempts").session(p.session()).param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void detailGivesTheRouteAndTheDeaths() throws Exception {
        Player p = playerWithHistory();
        long id = attemptIdAt(p, 2); // la mort en room 02, après 41 s

        mvc.perform(get("/api/attempts/" + id).session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sid").value(SID))
                .andExpect(jsonPath("$.side").value("B"))
                .andExpect(jsonPath("$.mapName").value("Forsaken City"))
                .andExpect(jsonPath("$.result").value("death"))
                .andExpect(jsonPath("$.durationMs").value(41_000))
                .andExpect(jsonPath("$.endReason").value("restart"))
                .andExpect(jsonPath("$.startRoom").value("00"))
                .andExpect(jsonPath("$.endRoom").value("02"))
                .andExpect(jsonPath("$.route", hasSize(3)))
                .andExpect(jsonPath("$.route[0].room").value("00"))
                .andExpect(jsonPath("$.route[0].enteredAt").value(10_000))   // départ de la tentative
                .andExpect(jsonPath("$.route[0].sessionTimeMs").doesNotExist())
                .andExpect(jsonPath("$.route[1].room").value("01"))
                .andExpect(jsonPath("$.route[1].sessionTimeMs").value(4_000))
                .andExpect(jsonPath("$.route[2].room").value("02"))
                .andExpect(jsonPath("$.route[2].sessionTimeMs").value(9_000))
                .andExpect(jsonPath("$.deaths", hasSize(1)))
                .andExpect(jsonPath("$.deaths[0].room").value("02"))
                .andExpect(jsonPath("$.deaths[0].x").value(7))
                .andExpect(jsonPath("$.deaths[0].y").value(9))
                .andExpect(jsonPath("$.deaths[0].timeInRoomMs").value(41_000))
                .andExpect(jsonPath("$.deaths[0].first").value(true));
    }

    @Test
    void detailOfASuccessHasNoDeaths() throws Exception {
        Player p = playerWithHistory();
        long id = attemptIdAt(p, 3); // la plus ancienne : le deathless réussi

        mvc.perform(get("/api/attempts/" + id).session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("success"))
                .andExpect(jsonPath("$.durationMs").value(83_000))
                .andExpect(jsonPath("$.endReason").value("completed"))
                .andExpect(jsonPath("$.deaths", hasSize(0)))
                .andExpect(jsonPath("$.route", hasSize(3)));
    }

    @Test
    void otherAccountsAndUnknownIdsGetTheSame404() throws Exception {
        Player owner = playerWithHistory();
        Player other = newPlayer();
        long id = attemptIdAt(owner, 0);

        mvc.perform(get("/api/attempts/" + id).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/attempts/999999999").session(owner.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/attempts").session(other.session()))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anAttemptWithoutAnythingSignificantIsNotFound() throws Exception {
        Player p = playerWithHistory();
        // la parasite est la seule tentative qui n'apparaît pas dans la liste : on la retrouve par son id
        long parasite = attemptIdStartedAt(30_000);
        mvc.perform(get("/api/attempts/" + parasite).session(p.session())).andExpect(status().isNotFound());
    }

    @Test
    void historyIsReservedToAConnectedSessionNotToAModToken() throws Exception {
        Player p = newPlayer();
        mvc.perform(get("/api/attempts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attempts").header("Authorization", p.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attempts/1")).andExpect(status().isUnauthorized());
    }

    /** L'identifiant de la n-ième tentative de la liste (0 = la plus récente). */
    private long attemptIdAt(Player p, int index) throws Exception {
        String body = mvc.perform(get("/api/attempts").session(p.session()).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.items[" + index + "].id")).longValue();
    }

    /** L'identifiant d'une tentative, même non significative (absente de la liste) : on la lit en base. */
    private long attemptIdStartedAt(long startedAt) {
        // La base est partagée entre les tests : la plus récemment créée est celle du test en cours.
        return attempts.findAll().stream()
                .filter(a -> a.getStartedAt() == startedAt)
                .mapToLong(a -> a.getId())
                .max().orElseThrow();
    }
}
