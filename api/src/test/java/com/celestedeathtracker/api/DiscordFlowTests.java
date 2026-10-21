package com.celestedeathtracker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.celestedeathtracker.api.model.AppUser;
import com.celestedeathtracker.api.repository.UserRepository;
import com.celestedeathtracker.api.service.DiscordAccountService;
import com.celestedeathtracker.api.service.DiscordAccountService.AlreadyLinkedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Discord est activé ici (identifiants factices) : on ne contacte jamais le vrai Discord. */
@SpringBootTest(properties = {
        "app.discord.client-id=test-client",
        "app.discord.client-secret=test-secret"
})
@AutoConfigureMockMvc
class DiscordFlowTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    DiscordAccountService accounts;
    @Autowired
    UserRepository users;

    @Test
    void providersAnnounceDiscordWhenConfigured() throws Exception {
        mvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discord").value(true));
    }

    @Test
    void startingTheFlowRedirectsToDiscordWithoutAnEmailScope() throws Exception {
        mvc.perform(get("/api/auth/oauth2/discord"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("https://discord.com/oauth2/authorize"),
                                org.hamcrest.Matchers.containsString("client_id=test-client"),
                                org.hamcrest.Matchers.containsString("scope=identify"),
                                org.hamcrest.Matchers.containsString("state="))));
    }

    @Test
    void linkingRequiresALoggedInAccount() throws Exception {
        mvc.perform(post("/api/auth/discord/link").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void linkingNotesTheRequestingAccountInTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String body = "{\"email\":\"lien@example.com\",\"password\":\"motdepasse123\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/discord/link").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(session.getAttribute("discord.link.userId"))
                .isEqualTo(users.findByEmail("lien@example.com").orElseThrow().getId());
    }

    @Test
    void firstDiscordLoginCreatesAnAccountWithoutEmailOrPassword() {
        AppUser user = accounts.resolve("1001", "Madeline", "hash1", null);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getDiscordUsername()).isEqualTo("Madeline");
    }

    @Test
    void secondDiscordLoginFindsTheSameAccountAndRefreshesTheProfile() {
        AppUser first = accounts.resolve("1002", "Ancien", "a", null);
        AppUser second = accounts.resolve("1002", "Nouveau", "b", null);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getDiscordUsername()).isEqualTo("Nouveau");
        assertThat(second.getDiscordAvatar()).isEqualTo("b");
    }

    @Test
    void linkingAttachesDiscordToTheExistingAccountAndKeepsItsData() {
        AppUser existing = users.save(new AppUser("rattache@example.com", "hash"));

        AppUser linked = accounts.resolve("1003", "Badeline", null, existing.getId());

        assertThat(linked.getId()).isEqualTo(existing.getId());
        assertThat(linked.getEmail()).isEqualTo("rattache@example.com");
        assertThat(linked.getDiscordId()).isEqualTo("1003");
        // Une connexion Discord ultérieure ouvre bien CE compte, pas un nouveau
        assertThat(accounts.resolve("1003", "Badeline", null, null).getId()).isEqualTo(existing.getId());
    }

    @Test
    void aDiscordAlreadyUsedByAnotherAccountCannotBeLinked() {
        AppUser discordOwner = accounts.resolve("1004", "Theo", null, null);
        AppUser other = users.save(new AppUser("autre@example.com", "hash"));

        assertThatThrownBy(() -> accounts.resolve("1004", "Theo", null, other.getId()))
                .isInstanceOf(AlreadyLinkedException.class);
        assertThat(users.findByDiscordId("1004").orElseThrow().getId()).isEqualTo(discordOwner.getId());
    }

    @Test
    void anAccountWithAnotherDiscordCannotLinkASecondOne() {
        AppUser user = users.save(new AppUser("deux@example.com", "hash"));
        accounts.resolve("1005", "Premier", null, user.getId());

        assertThatThrownBy(() -> accounts.resolve("1006", "Second", null, user.getId()))
                .isInstanceOf(AlreadyLinkedException.class);
    }

}
