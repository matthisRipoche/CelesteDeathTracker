package com.celestedeathtracker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.model.PairingRequest;
import com.celestedeathtracker.api.repository.PairingRequestRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PairingFlowTests {

    private static final String DEATH = """
            {"timestamp":1779300000000,"chapter":"Celeste/1-ForsakenCity","room":"paired","deathX":1,"deathY":2,
             "totalDeaths":3,"sessionDeaths":4,"timeInRoomMs":5}""";

    @Autowired MockMvc mvc;
    @Autowired PairingRequestRepository requests;
    @Autowired JdbcTemplate jdbc;

    private record Started(String deviceCode, String userCode) {}

    private Started start() throws Exception {
        // Comme le mod : pas de session, pas de CSRF
        String json = mvc.perform(post("/api/pairing/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceName\":\"PC-DE-TEST\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Started(find(json, "\"deviceCode\":\"([^\"]+)\""), find(json, "\"userCode\":\"([^\"]+)\""));
    }

    private String poll(String deviceCode) throws Exception {
        return mvc.perform(post("/api/pairing/poll").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"%s\"}".formatted(deviceCode)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession loggedInUser() throws Exception {
        String body = "{\"email\":\"%s@test.fr\",\"password\":\"motdepasse123\"}".formatted(UUID.randomUUID());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/api/auth/login").with(csrf()).session(s)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        return s;
    }

    private static String find(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        assertTrue(m.find(), "introuvable : " + regex + " dans " + json);
        return m.group(1);
    }

    @Test
    void fullPairingFlowDeliversATokenOnceAndItWorks() throws Exception {
        Started s = start();
        assertTrue(s.userCode().matches("[A-Z2-9]{4}-[A-Z2-9]{4}"), s.userCode());

        assertTrue(poll(s.deviceCode()).contains("\"status\":\"pending\""));

        MockHttpSession user = loggedInUser();
        // Le site affiche d'abord qui demande
        mvc.perform(get("/api/pairing/lookup").param("code", s.userCode()).session(user))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pairing/approve").with(csrf()).session(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\"}".formatted(s.userCode().toLowerCase().replace("-", " "))))
                .andExpect(status().isNoContent());

        String delivered = poll(s.deviceCode());
        assertTrue(delivered.contains("\"status\":\"approved\""), delivered);
        String token = find(delivered, "\"token\":\"(cdt_[^\"]+)\"");

        // Le token reçu fonctionne et appartient bien au compte qui a approuvé
        mvc.perform(post("/api/ingest/deaths").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(DEATH)).andExpect(status().isCreated());
        String deaths = mvc.perform(get("/api/deaths").session(user)).andReturn().getResponse().getContentAsString();
        assertTrue(deaths.contains("\"room\":\"paired\""));

        // Il n'est remis qu'UNE fois
        String again = poll(s.deviceCode());
        assertFalse(again.contains("cdt_"), again);
        assertTrue(again.contains("expired"));
    }

    @Test
    void unknownDeviceCodeIsReportedAsExpired() throws Exception {
        assertTrue(poll("cdt_dev_nimportequoi").contains("expired"));
    }

    @Test
    void expiredRequestCannotBeApproved() throws Exception {
        Started s = start();
        jdbc.update("update pairing_requests set expires_at = ? where user_code = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), s.userCode().replace("-", ""));

        MockHttpSession user = loggedInUser();
        mvc.perform(post("/api/pairing/approve").with(csrf()).session(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\"}".formatted(s.userCode())))
                .andExpect(status().isNotFound());
        assertTrue(poll(s.deviceCode()).contains("expired"));
    }

    @Test
    void deniedRequestNeverYieldsAToken() throws Exception {
        Started s = start();
        MockHttpSession user = loggedInUser();
        mvc.perform(post("/api/pairing/deny").with(csrf()).session(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\"}".formatted(s.userCode())))
                .andExpect(status().isNoContent());
        String r = poll(s.deviceCode());
        assertTrue(r.contains("denied"));
        assertFalse(r.contains("cdt_"));
    }

    @Test
    void approvingRequiresALoggedInUser() throws Exception {
        Started s = start();
        mvc.perform(post("/api/pairing/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCode\":\"%s\"}".formatted(s.userCode())))
                .andExpect(status().isUnauthorized());
        assertTrue(poll(s.deviceCode()).contains("pending"));
    }

    @Test
    void userCodeAloneCannotRetrieveATokenAndSecretIsNotStoredInClear() throws Exception {
        Started s = start();
        // Le code court ne sert pas de deviceCode
        assertTrue(poll(s.userCode()).contains("expired"));
        // Rien d'exploitable en base
        for (PairingRequest r : requests.findAll()) {
            assertFalse(r.getDeviceCodeHash().startsWith("cdt_dev_"));
            assertEquals(64, r.getDeviceCodeHash().length());
        }
    }

    @Test
    void malformedCodesAreRejectedWithoutLeakingAnything() throws Exception {
        MockHttpSession user = loggedInUser();
        mvc.perform(get("/api/pairing/lookup").param("code", "pas un code").session(user))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pairing/lookup").param("code", "AAAA-AAAA").session(user))
                .andExpect(status().isNotFound());
    }
}
