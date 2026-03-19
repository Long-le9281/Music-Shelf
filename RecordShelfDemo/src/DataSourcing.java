import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataSourcing {
    private static final String GENIUS_ACCESS_TOKEN = "nWxr8IdQBwdPzcK53obFQsa0Z94NzZld0dYoxsc2g60Rj-DPIxLgbOti7edVn2lP";

    public static void main(String[] args) {
        List<Song> songs = fetchSongsFromItunes("rock", 10);
        if (songs.isEmpty()) {
            System.out.println("No songs fetched from iTunes. Database was not modified.");
            return;
        }
        for (Song song : songs) {
            song.lyrics = fetchLyricsFromGenius(song.title, song.artist);
        }
        // Save to SQLite database
        saveSongsToDatabase(songs);
    }
    // SQLite database file
    private static final String DB_FILE = "music_shelf.db";

    static String resolveDbPath() {
        // Canonical location is the repo root (one level up from RecordShelfDemo/)
        Path parent = Paths.get("..", DB_FILE).toAbsolutePath().normalize();
        if (Files.exists(parent)) {
            return parent.toString();
        }

        Path local = Paths.get(DB_FILE).toAbsolutePath().normalize();
        if (Files.exists(local)) {
            return local.toString();
        }

        // Default: create at repo root
        return parent.toString();
    }

    // Create songs table and insert songs
    static void saveSongsToDatabase(List<Song> songs) {
        String url = "jdbc:sqlite:" + resolveDbPath();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Create table if not exists
            String createTable = "CREATE TABLE IF NOT EXISTS songs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "artist TEXT," +
                "album TEXT," +
                "genre TEXT," +
                "albumArtUrl TEXT," +
                "lyrics TEXT" +
                ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTable);
            }
            // Insert songs
            String insertSql = "INSERT INTO songs (title, artist, album, genre, albumArtUrl, lyrics) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (Song song : songs) {
                    pstmt.setString(1, song.title);
                    pstmt.setString(2, song.artist);
                    pstmt.setString(3, song.album);
                    pstmt.setString(4, song.genre);
                    pstmt.setString(5, song.albumArtUrl);
                    pstmt.setString(6, song.lyrics);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            System.out.println("Songs saved to database: " + songs.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Example Song class
    static class Song {
        String title;
        String artist;
        String album;
        String genre;
        String albumArtUrl;
        String lyrics;
    }

    // Fetch songs from iTunes Search API (free, no credentials needed)
    static List<Song> fetchSongsFromItunes(String genre, int limit) {
        List<Song> songs = new ArrayList<>();
        try {
            String query = URLEncoder.encode(genre, "UTF-8");
            String urlStr = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=" + limit;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONArray results = json.getJSONArray("results");
            System.out.println("Fetched " + results.length() + " tracks from iTunes.");

            for (int i = 0; i < results.length(); i++) {
                JSONObject track = results.getJSONObject(i);
                Song song = new Song();
                song.title = track.optString("trackName", "(unknown)");
                song.artist = track.optString("artistName", "(unknown)");
                song.album = track.optString("collectionName", "(unknown)");
                song.genre = track.optString("primaryGenreName", genre);
                // Replace thumbnail size 100x100 with 600x600 for better quality
                song.albumArtUrl = track.optString("artworkUrl100", "").replace("100x100bb", "600x600bb");
                songs.add(song);
            }
        } catch (Exception e) {
            System.out.println("Error fetching from iTunes: " + e.getMessage());
            e.printStackTrace();
        }
        return songs;
    }

    // Save songs to CSV
    static void saveSongsToCSV(List<Song> songs, String filename) {
        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("Title,Artist,Album,Genre,AlbumArtUrl,Lyrics");
            for (Song song : songs) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    song.title, song.artist, song.album, song.genre, song.albumArtUrl, song.lyrics.replace("\"", "'"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Fetch lyrics from Genius
    public static String fetchLyricsFromGenius(String songTitle, String artist) {
        try {
            String query = songTitle + " " + artist;
            String urlStr = "https://api.genius.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + GENIUS_ACCESS_TOKEN);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            // Parse JSON response to get lyrics URL (use a JSON library like org.json or Gson)
            // Then scrape the lyrics from the Genius page (requires HTML parsing, e.g., Jsoup)

            return "Lyrics not implemented yet"; // Placeholder
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // Load songs from CSV
    static List<Song> loadSongsFromCSV(String filename) {
        List<Song> songs = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename));
            for (String line : lines.subList(1, lines.size())) { // Skip header
                String[] parts = line.split(",");
                Song song = new Song();
                song.title = parts[0].replace("\"", "");
                song.artist = parts[1].replace("\"", "");
                song.album = parts[2].replace("\"", "");
                song.genre = parts[3].replace("\"", "");
                // Add more fields as needed
                songs.add(song);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

}
