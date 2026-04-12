package com.elgooners.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Iteration1DoneColumnTest {

    @Test
    @DisplayName("EL-22 Create an account / signup-login: signup rejects passwords shorter than 6")
    void el22_signupValidationRejectsShortPassword() {
        AuthController controller = new AuthController();
        controller.db = mock(Database.class);

        ResponseEntity<?> response = controller.signup(Map.of("username", "newuser", "password", "123"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("EL-14 Star ratings: rating values outside 1-5 are rejected")
    void el14_ratingRejectsOutOfRangeStars() {
        RatingController controller = new RatingController();
        controller.db = mock(Database.class);

        ResponseEntity<?> response = controller.rateAlbum(1L, Map.of("stars", 6), null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("EL-11 Search bar: empty query returns empty result set with zero meta counts")
    void el11_emptySearchReturnsEmptyCollections() {
        AlbumController controller = new AlbumController();

        Map<String, Object> response = controller.search("   ");

        Map<?, ?> meta = assertInstanceOf(Map.class, response.get("meta"));
        assertEquals(0, meta.get("albumCount"));
        assertEquals(0, meta.get("songCount"));
    }

    @Test
    @DisplayName("EL-24 View User Profile Page: profile endpoint returns user info and ratings")
    void el24_profileResponseContainsPublicFieldsAndRatings() {
        Database db = mock(Database.class);

        Map<String, Object> userRow = new HashMap<>();
        userRow.put("id", 7L);
        userRow.put("username", "demo");
        userRow.put("displayName", "Demo User");
        userRow.put("avatarColor", "#FF6B6B");
        userRow.put("bio", "collector");
        userRow.put("createdAt", "2026-03-25");
        userRow.put("isAdmin", false);
        userRow.put("isActive", true);
        when(db.findUser("demo")).thenReturn(userRow);

        when(db.getRatingsByUser(7L)).thenReturn(List.of(
            Map.of("albumId", 10L, "stars", 4),
            Map.of("albumId", 11L, "stars", 5)
        ));

        ProfileController controller = new ProfileController();
        controller.db = db;

        ResponseEntity<?> response = controller.getProfile("demo");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("demo", body.get("username"));
        assertEquals(2, body.get("ratingCount"));
    }
}


