import org.junit.jupiter.api.Test;

import java.awt.HeadlessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordShelfDemoTest {

    private static void setHeadless(boolean value) {
        System.setProperty("java.awt.headless", value ? "true" : "false");
    }

    private static void restoreHeadless(String previous) {
        if (previous == null) {
            System.clearProperty("java.awt.headless");
        } else {
            System.setProperty("java.awt.headless", previous);
        }
    }

    @Test
    void userExists_returnsTrueForKnownUserAndFalseOtherwise() {
        RecordShelfDemo.users.clear();
        RecordShelfDemo.users.add(new RecordShelfDemo.User("alice", "pw"));

        assertTrue(RecordShelfDemo.userExists("alice"));
        assertFalse(RecordShelfDemo.userExists("bob"));
    }

    @Test
    void albumAndSongToString_useExpectedFormat() {
        RecordShelfDemo.Album album = new RecordShelfDemo.Album("Hybrid Theory", "Linkin Park");
        RecordShelfDemo.Song song = new RecordShelfDemo.Song("Numb", "Linkin Park", "Meteora");

        assertEquals("Hybrid Theory - Linkin Park", album.toString());
        assertEquals("Numb - Linkin Park (Meteora)", song.toString());
    }

    @Test
    void saveUsersThenLoadUsers_roundTripsCsvData() throws Exception {
        Path usersFile = Paths.get("users.csv").toAbsolutePath().normalize();
        String original = Files.exists(usersFile) ? new String(Files.readAllBytes(usersFile)) : null;

        try {
            RecordShelfDemo.users.clear();
            RecordShelfDemo.users.add(new RecordShelfDemo.User("testuser", "testpass"));
            RecordShelfDemo.saveUsers();

            RecordShelfDemo.users.clear();
            RecordShelfDemo.loadUsers();

            assertTrue(RecordShelfDemo.userExists("testuser"));
        } finally {
            if (original != null) {
                Files.write(usersFile, original.getBytes());
            } else {
                Files.deleteIfExists(usersFile);
            }
            RecordShelfDemo.users.clear();
            RecordShelfDemo.loadUsers();
        }
    }

    @Test
    void loadSongsFromDatabase_populatesSongsAndAlbums() throws Exception {
        String uniqueTitle = "DB-" + UUID.randomUUID();
        String uniqueArtist = "Artist-" + UUID.randomUUID();
        String uniqueAlbum = "Album-" + UUID.randomUUID();
        String url = "jdbc:sqlite:" + RecordShelfDemo.resolveDbPath();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement insert = conn.prepareStatement(
                     "INSERT INTO songs (title, artist, album, genre, albumArtUrl, lyrics) VALUES (?, ?, ?, '', '', '')")) {
            insert.setString(1, uniqueTitle);
            insert.setString(2, uniqueArtist);
            insert.setString(3, uniqueAlbum);
            insert.executeUpdate();
        }

        try {
            RecordShelfDemo.songs.clear();
            RecordShelfDemo.albums.clear();
            RecordShelfDemo.loadSongsFromDatabase();

            boolean songFound = RecordShelfDemo.songs.stream()
                    .anyMatch(s -> s.title.equals(uniqueTitle) && s.artist.equals(uniqueArtist) && s.album.equals(uniqueAlbum));
            boolean albumFound = RecordShelfDemo.albums.stream()
                    .anyMatch(a -> a.title.equals(uniqueAlbum) && a.artist.equals(uniqueArtist));

            assertTrue(songFound);
            assertTrue(albumFound);
        } finally {
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement cleanup = conn.prepareStatement("DELETE FROM songs WHERE title = ? AND artist = ? AND album = ?")) {
                cleanup.setString(1, uniqueTitle);
                cleanup.setString(2, uniqueArtist);
                cleanup.setString(3, uniqueAlbum);
                cleanup.executeUpdate();
            }
        }
    }

    @Test
    void resolveDbPath_followsExpectedFallbackOrder() {
        Path parent = Paths.get("..", "music_shelf.db").toAbsolutePath().normalize();
        Path local = Paths.get("music_shelf.db").toAbsolutePath().normalize();

        String resolved = RecordShelfDemo.resolveDbPath();

        if (Files.exists(parent)) {
            assertEquals(parent.toString(), resolved);
        } else if (Files.exists(local)) {
            assertEquals(local.toString(), resolved);
        } else {
            assertEquals(parent.toString(), resolved);
        }
    }

    @Test
    void userAndRatingConstructors_setFields() {
        RecordShelfDemo.User user = new RecordShelfDemo.User("sam", "pw1");
        RecordShelfDemo.Album album = new RecordShelfDemo.Album("Album", "Artist");
        RecordShelfDemo.Rating rating = new RecordShelfDemo.Rating(album, 4);

        assertEquals("sam", user.username);
        assertEquals("pw1", user.password);
        assertEquals(album, rating.item);
        assertEquals(4, rating.rating);
    }

    @Test
    void showLogin_throwsInHeadlessMode() {
        String previous = System.getProperty("java.awt.headless");
        try {
            setHeadless(true);
            assertThrows(HeadlessException.class, RecordShelfDemo::showLogin);
        } finally {
            restoreHeadless(previous);
        }
    }

    @Test
    void showMain_throwsInHeadlessMode() {
        String previous = System.getProperty("java.awt.headless");
        try {
            setHeadless(true);
            assertThrows(HeadlessException.class, RecordShelfDemo::showMain);
        } finally {
            restoreHeadless(previous);
        }
    }

    @Test
    void showProfile_throwsInHeadlessMode() {
        String previous = System.getProperty("java.awt.headless");
        try {
            setHeadless(true);
            RecordShelfDemo.currentUser = new RecordShelfDemo.User("headless", "pw");
            assertThrows(HeadlessException.class, () -> RecordShelfDemo.showProfile(null));
        } finally {
            restoreHeadless(previous);
        }
    }

    @Test
    void main_doesNotThrow() {
        String previous = System.getProperty("java.awt.headless");
        try {
            setHeadless(true);
            assertDoesNotThrow(() -> RecordShelfDemo.main(new String[0]));
        } finally {
            restoreHeadless(previous);
        }
    }
}
