import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourcingTest {

    @Test
    void resolveDbPath_followsExpectedFallbackOrder() {
        Path parent = Paths.get("..", "music_shelf.db").toAbsolutePath().normalize();
        Path local = Paths.get("music_shelf.db").toAbsolutePath().normalize();

        String resolved = DataSourcing.resolveDbPath();

        if (Files.exists(parent)) {
            assertEquals(parent.toString(), resolved);
        } else if (Files.exists(local)) {
            assertEquals(local.toString(), resolved);
        } else {
            assertEquals(parent.toString(), resolved);
        }
    }

    @Test
    void saveAndLoadCsv_preservesBasicSongFields() {
        String fileName = "test_songs.csv";

        DataSourcing.Song song = new DataSourcing.Song();
        song.title = "Song A";
        song.artist = "Artist A";
        song.album = "Album A";
        song.genre = "Rock";
        song.albumArtUrl = "http://example.com/a.jpg";
        song.lyrics = "hello world";

        DataSourcing.saveSongsToCSV(Collections.singletonList(song), fileName);
        List<DataSourcing.Song> loaded = DataSourcing.loadSongsFromCSV(fileName);

        assertEquals(1, loaded.size());
        assertEquals("Song A", loaded.get(0).title);
        assertEquals("Artist A", loaded.get(0).artist);
        assertEquals("Album A", loaded.get(0).album);
        assertEquals("Rock", loaded.get(0).genre);

        Path csv = Paths.get(fileName);
        try {
            Files.deleteIfExists(csv);
        } catch (Exception ignored) {
        }
    }

    @Test
    void saveSongsToDatabase_acceptsMockedListAndInsertsRows() throws Exception {
        String uniqueTitle = "TEST-" + UUID.randomUUID();

        DataSourcing.Song song = new DataSourcing.Song();
        song.title = uniqueTitle;
        song.artist = "Mock Artist";
        song.album = "Mock Album";
        song.genre = "Mock Genre";
        song.albumArtUrl = "";
        song.lyrics = "";

        List<DataSourcing.Song> mockedSongs = new AbstractList<DataSourcing.Song>() {
            @Override
            public DataSourcing.Song get(int index) {
                if (index != 0) {
                    throw new IndexOutOfBoundsException(String.valueOf(index));
                }
                return song;
            }

            @Override
            public int size() {
                return 1;
            }
        };

        DataSourcing.saveSongsToDatabase(mockedSongs);

        String url = "jdbc:sqlite:" + DataSourcing.resolveDbPath();
        int count;
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM songs WHERE title = ?")) {
            stmt.setString(1, uniqueTitle);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                count = rs.getInt(1);
            }
        }

        assertTrue(count >= 1);

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement cleanup = conn.prepareStatement("DELETE FROM songs WHERE title = ?")) {
            cleanup.setString(1, uniqueTitle);
            cleanup.executeUpdate();
        }
    }

    @Test
    void loadSongsFromCsv_missingFileReturnsEmptyList() {
        List<DataSourcing.Song> loaded = DataSourcing.loadSongsFromCSV("missing-file-does-not-exist.csv");
        assertFalse(loaded == null);
        assertTrue(loaded.isEmpty());
    }
}
