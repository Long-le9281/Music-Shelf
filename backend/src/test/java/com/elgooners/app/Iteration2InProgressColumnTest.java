package com.elgooners.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("DataFlowIssue")
class Iteration2InProgressColumnTest {

    @Test
    @DisplayName("EL-17 Record shelf UI design: album detail includes songs for the selected record")
    void el17_albumDetailsIncludeSongsPayload() {
        Database db = mock(Database.class);
        Map<String, Object> albumRow = new HashMap<>();
        albumRow.put("id", 4L);
        albumRow.put("title", "Demo Album");
        when(db.getAlbumById(4L)).thenReturn(albumRow);

        Map<String, Object> song1 = new HashMap<>();
        song1.put("id", 40L);
        song1.put("title", "Track One");
        Map<String, Object> song2 = new HashMap<>();
        song2.put("id", 41L);
        song2.put("title", "Track Two");
        when(db.getSongsForAlbum(4L)).thenReturn(List.of(song1, song2));

        AlbumController controller = new AlbumController();
        controller.db = db;

        ResponseEntity<?> response = controller.getOneAlbum(4L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertNotNull(body.get("songs"));
    }

    @Test
    @DisplayName("EL-13 Create custom albums or playlists: create playlist succeeds with default category")
    void el13_createPlaylistReturnsCreatedPayload() {
        Database db = mock(Database.class);
        when(db.findUser("demo")).thenReturn(Map.of("id", 77L, "username", "demo"));
        when(db.createPlaylist(77L, "Road Trip", "Driving mix", "Custom")).thenReturn(501L);

        PlaylistController controller = new PlaylistController();
        controller.db = db;

        UserDetails principal = User.withUsername("demo").password("x").roles("USER").build();
        ResponseEntity<?> response = controller.createPlaylist(
            Map.of("name", "Road Trip", "description", "Driving mix"),
            principal
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(501L, body.get("id"));
        assertEquals("Custom", body.get("category"));
    }

    @Test
    @DisplayName("EL-2 Database and data sourcing: songs endpoint returns sourced song list")
    void el2_getAllSongsReturnsDatabasePayload() {
        Database db = mock(Database.class);
        when(db.getAllSongs()).thenReturn(List.of(
            Map.of("id", 1L, "title", "Seed Song")
        ));

        AlbumController controller = new AlbumController();
        controller.db = db;

        List<Map<String, Object>> songs = controller.getAllSongs();

        assertEquals(1, songs.size());
        assertEquals("Seed Song", songs.get(0).get("title"));
    }

    @Test
    @DisplayName("EL-3 User Authentication and Roles: admin can update a target user's role")
    void el3_adminRoleUpdateReturnsOk() {
        Database db = mock(Database.class);
        Map<String, Object> targetUser = new HashMap<>();
        targetUser.put("id", 9L);
        targetUser.put("username", "targetUser");
        targetUser.put("isAdmin", false);
        when(db.findUser("targetUser")).thenReturn(targetUser);
        when(db.setUserAdminByUsername("targetUser", true)).thenReturn(true);

        AdminController controller = new AdminController();
        controller.db = db;

        UserDetails principal = User.withUsername("adminUser").password("x").roles("ADMIN").build();
        ResponseEntity<?> response = controller.setRole("targetUser", Map.of("isAdmin", true), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("targetUser", body.get("username"));
        assertEquals(true, body.get("isAdmin"));
    }
}


