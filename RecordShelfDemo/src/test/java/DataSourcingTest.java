import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourcingTest {

    private static final String MOCK_TRACK = "ZZZ_TEST_TRACK";
    private static final String MOCK_ARTIST = "ZZZ_TEST_ARTIST";
    private static final String MOCK_ALBUM = "ZZZ_TEST_ALBUM";

    @BeforeAll
    static void installMockNetworkLayer() {
        try {
            URL.setURLStreamHandlerFactory(protocol -> {
                if ("https".equals(protocol)) {
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) {
                            String payload;
                            String url = u.toString();
                            if (url.contains("itunes.apple.com/search")) {
                                payload = "{\"results\":[{\"trackName\":\"" + MOCK_TRACK
                                        + "\",\"artistName\":\"" + MOCK_ARTIST
                                        + "\",\"collectionName\":\"" + MOCK_ALBUM
                                        + "\",\"primaryGenreName\":\"Rock\",\"artworkUrl100\":\"https://img/100x100bb.jpg\"}]}";
                            } else {
                                payload = "{\"response\":{\"hits\":[]}}";
                            }
                            return new MockHttpURLConnection(u, payload);
                        }
                    };
                }
                return null;
            });
        } catch (Error ignored) {
            // URLStreamHandlerFactory can only be set once per JVM.
        }
    }

    static class MockHttpURLConnection extends HttpURLConnection {
        private final String payload;

        protected MockHttpURLConnection(URL url, String payload) {
            super(url);
            this.payload = payload;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        }
    }

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

    @Test
    void fetchSongsFromItunes_parsesMockedResponse() {
        List<DataSourcing.Song> songs = DataSourcing.fetchSongsFromItunes("rock", 1);

        assertEquals(1, songs.size());
        DataSourcing.Song first = songs.get(0);
        assertEquals(MOCK_TRACK, first.title);
        assertEquals(MOCK_ARTIST, first.artist);
        assertEquals(MOCK_ALBUM, first.album);
        assertEquals("Rock", first.genre);
        assertTrue(first.albumArtUrl.contains("600x600bb"));
    }

    @Test
    void fetchLyricsFromGenius_returnsPlaceholderWithMockedResponse() {
        String result = DataSourcing.fetchLyricsFromGenius("anything", "anyone");
        assertEquals("Lyrics not implemented yet", result);
    }

    @Test
    void main_runsEndToEndAndWritesToDatabase() throws Exception {
        assertDoesNotThrow(() -> DataSourcing.main(new String[0]));

        String url = "jdbc:sqlite:" + DataSourcing.resolveDbPath();
        int count;
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM songs WHERE title = ? AND artist = ? AND album = ?")) {
            stmt.setString(1, MOCK_TRACK);
            stmt.setString(2, MOCK_ARTIST);
            stmt.setString(3, MOCK_ALBUM);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                count = rs.getInt(1);
            }
        }

        assertTrue(count >= 1);

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement cleanup = conn.prepareStatement("DELETE FROM songs WHERE title = ? AND artist = ? AND album = ?")) {
            cleanup.setString(1, MOCK_TRACK);
            cleanup.setString(2, MOCK_ARTIST);
            cleanup.setString(3, MOCK_ALBUM);
            cleanup.executeUpdate();
        }
    }
}
