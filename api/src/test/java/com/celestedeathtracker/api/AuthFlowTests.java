package com.celestedeathtracker.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    MockMvc mvc;

    private static final String CREDENTIALS = "{\"email\":\"%s\",\"password\":\"motdepasse123\"}";

    @Test
    void deathsListRequiresLogin() throws Exception {
        mvc.perform(get("/api/deaths")).andExpect(status().isUnauthorized());
    }

    @Test
    void postingDeathsOnTheSessionRouteIsNoLongerPossible() throws Exception {
        // L'ancienne route ouverte du mod a disparu : l'ingestion passe par /api/ingest + token
        mvc.perform(post("/api/deaths").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerLoginMeLogout() throws Exception {
        String body = CREDENTIALS.formatted("Joueur@Example.com");

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("joueur@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("joueur@example.com"));
        mvc.perform(get("/api/deaths").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/deaths").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS.formatted("inconnu@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"court@example.com\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorEndpointIsNotBehindLogin() throws Exception {
        // Régression : MockMvc ne fait pas la redirection interne vers /error, donc on vérifie
        // la règle d'accès directement. Sans permitAll, une erreur 409 devenait un 401.
        mvc.perform(get("/error")).andExpect(result ->
                org.junit.jupiter.api.Assertions.assertNotEquals(401, result.getResponse().getStatus()));
    }

    // .with(csrf()) remplace durablement le dépôt de jetons CSRF du filtre dans le contexte Spring
    // partagé entre les classes de test : le cookie XSRF-TOKEN n'est alors plus posé. Ce test
    // vérifie le vrai comportement de l'application, donc il exige un contexte non pollué
    // (sinon il dépend de l'ordre d'exécution des classes).
    @Test
    @org.springframework.test.annotation.DirtiesContext(
            methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfCookieIsSetOnPlainGet() throws Exception {
        // Sans ce cookie, Angular ne pourrait jamais envoyer X-XSRF-TOKEN sur ses POST
        mvc.perform(get("/api/auth/me"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS.formatted("csrf@example.com")))
                .andExpect(status().isForbidden());
    }
}
