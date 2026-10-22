package com.celestedeathtracker.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Tableau de bord : on ingère comme le mod, puis on lit GET /api/summary comme le site. */
@SpringBootTest
@AutoConfigureMockMvc
class SummaryFlowTests {

    private static final String CITY = "Celeste/1-ForsakenCity";
    private static final String SITE = "Celeste/2-OldSite";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

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

    private void event(Player p, String sid, String type, String id, String room, long ts, String extra) throws Exception {
        String body = "{\"type\":\"%s\",\"attemptId\":\"%s\",\"chapter\":\"%s\",\"side\":\"A\",\"saveSlot\":0,\"room\":\"%s\",\"timestamp\":%d%s}"
                .formatted(type, id, sid, room, ts, extra);
        mvc.perform(post("/api/ingest/events").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
    }

    private void death(Player p, String sid, String id, String room, long ts, long sessionMs) throws Exception {
        String body = ("{\"timestamp\":%d,\"chapter\":\"%s\",\"room\":\"%s\",\"deathX\":7,\"deathY\":9,"
                + "\"totalDeaths\":1,\"sessionDeaths\":1,\"timeInRoomMs\":%d,\"attemptId\":\"%s\",\"side\":\"A\",\"saveSlot\":0}")
                .formatted(ts, sid, room, sessionMs, id);
        mvc.perform(post("/api/ingest/deaths").header("Authorization", p.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    /** Un deathless réussi : 00 → 01 → 02 → fin. */
    private void clear(Player p, String sid, long ts, long durationMs) throws Exception {
        String id = UUID.randomUUID().toString();
        event(p, sid, "attempt_start", id, "00", ts, ",\"mapName\":\"Map\",\"startedFromBeginning\":true");
        event(p, sid, "room_enter", id, "01", ts + 1_000, ",\"sessionTimeMs\":5000");
        event(p, sid, "room_enter", id, "02", ts + 2_000, ",\"sessionTimeMs\":9000");
        event(p, sid, "attempt_end", id, "end", ts + 3_000, ",\"sessionTimeMs\":%d,\"reason\":\"completed\"".formatted(durationMs));
    }

    /** Une tentative morte dans la room 01, après heldMs. */
    private void diesIn01(Player p, String sid, long ts, long heldMs) throws Exception {
        String id = UUID.randomUUID().toString();
        event(p, sid, "attempt_start", id, "00", ts, ",\"startedFromBeginning\":true");
        event(p, sid, "room_enter", id, "01", ts + 1_000, ",\"sessionTimeMs\":4000");
        death(p, sid, id, "01", ts + 2_000, heldMs);
        event(p, sid, "attempt_end", id, "01", ts + 3_000, ",\"sessionTimeMs\":%d,\"reason\":\"restart\"".formatted(heldMs + 1_000));
    }

    @Test
    void summaryGivesResumeTodayRecentAndActivity() throws Exception {
        Player p = newPlayer();
        long now = System.currentTimeMillis();
        long threeDaysAgo = now - Duration.ofDays(3).toMillis();

        // il y a trois jours : un deathless sur Forsaken City
        clear(p, CITY, threeDaysAgo, 83_000);
        // aujourd'hui (il y a quelques secondes) : deux morts sur Old Site, la dernière face jouée
        diesIn01(p, SITE, now - 20_000, 20_000);
        diesIn01(p, SITE, now - 10_000, 30_000);

        LocalDate today = LocalDate.now(PARIS);
        mvc.perform(get("/api/summary").session(p.session()).param("tz", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resume.sid").value(SITE))
                .andExpect(jsonPath("$.resume.side").value("A"))
                .andExpect(jsonPath("$.resume.attempts").value(2))
                .andExpect(jsonPath("$.resume.clears").value(0))
                .andExpect(jsonPath("$.resume.bestRooms").value(2))     // 00 puis morte en 01
                .andExpect(jsonPath("$.resume.knownRooms").value(2))
                .andExpect(jsonPath("$.today.attempts").value(2))
                .andExpect(jsonPath("$.today.clears").value(0))
                .andExpect(jsonPath("$.today.bestAttemptMs").value(30_000))
                .andExpect(jsonPath("$.today.mostPlayed.sid").value(SITE))
                .andExpect(jsonPath("$.today.mostPlayed.attempts").value(2))
                .andExpect(jsonPath("$.recent", hasSize(1)))           // sans la face de « reprendre »
                .andExpect(jsonPath("$.recent[0].sid").value(CITY))
                .andExpect(jsonPath("$.recent[0].clears").value(1))
                .andExpect(jsonPath("$.recent[0].bestRooms").value(3))
                .andExpect(jsonPath("$.recent[0].knownRooms").value(3))
                .andExpect(jsonPath("$.activity", hasSize(14)))
                .andExpect(jsonPath("$.activity[13].day").value(today.toString()))
                .andExpect(jsonPath("$.activity[13].attempts").value(2))
                .andExpect(jsonPath("$.activity[10].day").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.activity[10].attempts").value(1))
                .andExpect(jsonPath("$.activity[10].clears").value(1))
                .andExpect(jsonPath("$.activity[0].attempts").value(0));
    }

    @Test
    void aNewAccountGetsAnEmptySummary() throws Exception {
        Player p = newPlayer();
        mvc.perform(get("/api/summary").session(p.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andExpect(jsonPath("$.today.attempts").value(0))
                .andExpect(jsonPath("$.today.bestAttemptMs").doesNotExist())
                .andExpect(jsonPath("$.today.mostPlayed").doesNotExist())
                .andExpect(jsonPath("$.recent", hasSize(0)))
                .andExpect(jsonPath("$.activity", hasSize(14)));
    }

    @Test
    void summaryOnlyCountsTheConnectedAccount() throws Exception {
        Player owner = newPlayer();
        Player other = newPlayer();
        diesIn01(owner, CITY, System.currentTimeMillis() - 60_000, 10_000);

        mvc.perform(get("/api/summary").session(other.session()))
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andExpect(jsonPath("$.today.attempts").value(0));
    }

    @Test
    void anUnknownTimeZoneIsRefusedWithA400() throws Exception {
        Player p = newPlayer();
        mvc.perform(get("/api/summary").session(p.session()).param("tz", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryIsReservedToAConnectedSessionNotToAModToken() throws Exception {
        Player p = newPlayer();
        mvc.perform(get("/api/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/summary").header("Authorization", p.bearer())).andExpect(status().isUnauthorized());
    }
}
