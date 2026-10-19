package com.celestedeathtracker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.repository.ApiTokenRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
class TokenFlowTests {

    private static final String DEATH = """
            {"timestamp":1779300000000,"chapter":"Celeste/1-ForsakenCity","room":"%s","deathX":10,"deathY":20,
             "totalDeaths":5,"sessionDeaths":1,"timeInRoomMs":1234}""";

    @Autowired MockMvc mvc;
    @Autowired ApiTokenRepository tokenRepository;

    /** Crée un compte, le connecte, renvoie sa session. */
    private MockHttpSession newLoggedInUser() throws Exception {
        String body = "{\"email\":\"%s@test.fr\",\"password\":\"motdepasse123\"}"
                .formatted(UUID.randomUUID());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        return session;
    }

    private record Created(String id, String token) {}

    private Created createToken(MockHttpSession session, String label) throws Exception {
        String json = mvc.perform(post("/api/tokens").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"%s\"}".formatted(label)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Created(extract(json, "\"id\":(\\d+)"), extract(json, "\"token\":\"(cdt_[^\"]+)\""));
    }

    private static String extract(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        assertTrue(m.find(), "introuvable : " + regex + " dans " + json);
        return m.group(1);
    }

    @Test
    void tokenIngestsDeathsForItsOwnerOnly() throws Exception {
        MockHttpSession alice = newLoggedInUser();
        MockHttpSession bob = newLoggedInUser();
        Created token = createToken(alice, "PC fixe");

        // Le mod : pas de session, pas de CSRF, juste le Bearer
        mvc.perform(post("/api/ingest/deaths").header("Authorization", "Bearer " + token.token())
                        .contentType(MediaType.APPLICATION_JSON).content(DEATH.formatted("room-alice")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/deaths").session(alice))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("room-alice")));
        // Isolation : Bob ne voit rien
        mvc.perform(get("/api/deaths").session(bob))
                .andExpect(status().isOk()).andExpect(content().string("[]"));
    }

    @Test
    void ingestRejectsMissingOrInvalidToken() throws Exception {
        mvc.perform(post("/api/ingest/deaths").contentType(MediaType.APPLICATION_JSON)
                .content(DEATH.formatted("x"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ingest/deaths").header("Authorization", "Bearer cdt_nimportequoi")
                .contentType(MediaType.APPLICATION_JSON).content(DEATH.formatted("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserSessionDoesNotGrantIngestAndTokenDoesNotGrantReading() throws Exception {
        MockHttpSession session = newLoggedInUser();
        Created token = createToken(session, "t");

        mvc.perform(post("/api/ingest/deaths").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DEATH.formatted("x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deaths").header("Authorization", "Bearer " + token.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenStopsWorking() throws Exception {
        MockHttpSession session = newLoggedInUser();
        Created token = createToken(session, "à révoquer");

        mvc.perform(delete("/api/tokens/" + token.id()).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/ingest/deaths").header("Authorization", "Bearer " + token.token())
                .contentType(MediaType.APPLICATION_JSON).content(DEATH.formatted("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotRevokeSomeoneElsesToken() throws Exception {
        MockHttpSession alice = newLoggedInUser();
        MockHttpSession mallory = newLoggedInUser();
        Created token = createToken(alice, "t");

        mvc.perform(delete("/api/tokens/" + token.id()).with(csrf()).session(mallory))
                .andExpect(status().isNotFound());
        // Le token d'Alice fonctionne toujours
        mvc.perform(post("/api/ingest/deaths").header("Authorization", "Bearer " + token.token())
                .contentType(MediaType.APPLICATION_JSON).content(DEATH.formatted("x")))
                .andExpect(status().isCreated());
    }

    @Test
    void tokenIsStoredHashedAndNeverListed() throws Exception {
        MockHttpSession session = newLoggedInUser();
        Created token = createToken(session, "secret");

        assertFalse(tokenRepository.findAll().stream().anyMatch(t -> t.getTokenHash().equals(token.token())),
                "le token en clair ne doit jamais être en base");
        assertTrue(tokenRepository.findAll().stream().anyMatch(t -> t.getTokenHash().length() == 64));

        String listing = mvc.perform(get("/api/tokens").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(listing.contains(token.token()));
        assertFalse(listing.contains("tokenHash"));
        assertEquals(1, listing.split("\"label\"", -1).length - 1);
    }

    @Test
    void attemptEventsAreAccepted() throws Exception {
        MockHttpSession session = newLoggedInUser();
        Created token = createToken(session, "t");
        mvc.perform(post("/api/ingest/events").header("Authorization", "Bearer " + token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"attempt_start\",\"attemptId\":\"11111111-1111-1111-1111-111111111111\",\"chapter\":\"a/b\",\"side\":\"A\",\"room\":\"r\",\"timestamp\":1}"))
                .andExpect(status().isAccepted());
    }
}
