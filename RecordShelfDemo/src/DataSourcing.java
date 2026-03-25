import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * DataSourcing populates the CSV catalog files used by the UI:
 *   - src/albums.csv
 *   - src/songs.csv
 *
 * Data source:
 *   - iTunes Search API (songs + album art + metadata)
 *   - Genius API (lyrics lookup, best effort)
 */
public class DataSourcing {

    // -------------------------------------------------------------------------
    // Genius API token — used to search for lyrics URLs
    // -------------------------------------------------------------------------
    private static final String GENIUS_ACCESS_TOKEN =
            "nWxr8IdQBwdPzcK53obFQsa0Z94NzZld0dYoxsc2g60Rj-DPIxLgbOti7edVn2lP";

    private static final Path ALBUMS_CSV = Paths.get("src", "albums.csv").toAbsolutePath().normalize();
    private static final Path SONGS_CSV = Paths.get("src", "songs.csv").toAbsolutePath().normalize();

    // -------------------------------------------------------------------------
    // Internal data holder
    // -------------------------------------------------------------------------
    static class TrackData {
        String songTitle;
        String artist;
        String albumTitle;
        int    year;
        int    trackNumber;
        int    durationSeconds;
        String genre;
        String artUrl;
        String lyrics = "";
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        String genre = (args.length > 0) ? args[0] : "rock";
        int    limit = (args.length > 1) ? Integer.parseInt(args[1]) : 20;

        ensureCsvFiles();
        Set<String> existingAlbums = readExistingAlbumKeys();
        Set<String> existingSongs = readExistingSongKeys();

        System.out.println("Fetching up to " + limit + " NEW tracks from iTunes for \"" + genre + "\"...");

        List<TrackData> tracks = fetchUniqueFromItunes(genre, limit, existingSongs);
        if (tracks.isEmpty()) {
            System.out.println("No new unique tracks found. Exiting.");
            return;
        }
        System.out.println("Fetched " + tracks.size() + " new unique tracks.");

        // Fetch lyrics for each track (best-effort; failures return "")
        System.out.println("Fetching lyrics from Genius...");
        for (TrackData t : tracks) {
            t.lyrics = fetchLyricsFromGenius(t.songTitle, t.artist);
        }

        int albumsAdded = appendAlbumsCsv(tracks, existingAlbums);
        int songsAdded = appendSongsCsv(tracks, existingSongs);

        System.out.println("Done. Added " + albumsAdded + " new albums and " + songsAdded + " new songs to CSV.");
    }

    static List<TrackData> fetchUniqueFromItunes(String searchTerm, int targetCount, Set<String> existingSongKeys) {
        List<TrackData> uniqueTracks = new ArrayList<>();
        Set<String> seenThisRun = new HashSet<>();

        final int pageSize = 50;
        final int maxPages = 20;

        for (int page = 0; page < maxPages && uniqueTracks.size() < targetCount; page++) {
            int offset = page * pageSize;
            List<TrackData> pageTracks = fetchFromItunesPage(searchTerm, pageSize, offset);
            if (pageTracks.isEmpty()) {
                break;
            }

            for (TrackData t : pageTracks) {
                String key = normalize(t.artist) + "|" + normalize(t.albumTitle) + "|" + normalize(t.songTitle);
                if (existingSongKeys.contains(key) || !seenThisRun.add(key)) {
                    continue;
                }
                uniqueTracks.add(t);
                if (uniqueTracks.size() >= targetCount) {
                    break;
                }
            }
        }

        if (uniqueTracks.size() < targetCount) {
            System.out.println("Only found " + uniqueTracks.size() + " new unique tracks (requested " + targetCount + ").");
        }
        return uniqueTracks;
    }

    static List<TrackData> fetchFromItunesPage(String searchTerm, int limit, int offset) {
        List<TrackData> tracks = new ArrayList<>();
        try {
            String query = URLEncoder.encode(searchTerm, "UTF-8");
            String urlStr = "https://itunes.apple.com/search?term=" + query
                    + "&media=music&entity=song&limit=" + limit + "&offset=" + offset;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONArray results = json.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (!item.has("trackName") || !item.has("artistName") || !item.has("collectionName")) {
                    continue;
                }

                TrackData t = new TrackData();
                t.songTitle = safe(item.optString("trackName", ""));
                t.artist = safe(item.optString("artistName", ""));
                t.albumTitle = safe(item.optString("collectionName", ""));
                t.genre = safe(item.optString("primaryGenreName", searchTerm));
                t.trackNumber = item.optInt("trackNumber", 0);
                t.durationSeconds = item.optInt("trackTimeMillis", 0) / 1000;

                String releaseDate = item.optString("releaseDate", "");
                t.year = (releaseDate != null && releaseDate.length() >= 4)
                        ? parseIntSafe(releaseDate.substring(0, 4))
                        : 0;

                t.artUrl = safe(item.optString("artworkUrl100", ""))
                        .replace("100x100bb", "600x600bb");

                if (!t.songTitle.isEmpty() && !t.artist.isEmpty()) {
                    tracks.add(t);
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching from iTunes: " + e.getMessage());
        }
        return tracks;
    }

    static void ensureCsvFiles() {
        try {
            if (!Files.exists(ALBUMS_CSV)) {
                Files.createDirectories(ALBUMS_CSV.getParent());
                Files.write(ALBUMS_CSV, Collections.singletonList("artist,album_title,year,track_count,art_url"));
            }
            if (!Files.exists(SONGS_CSV)) {
                Files.createDirectories(SONGS_CSV.getParent());
                Files.write(SONGS_CSV, Collections.singletonList("artist,album_title,year,track_number,song_title,duration_seconds"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to create CSV files", e);
        }
    }

    static Set<String> readExistingAlbumKeys() {
        Set<String> keys = new HashSet<>();
        try {
            List<String> lines = Files.readAllLines(ALBUMS_CSV);
            for (int i = 1; i < lines.size(); i++) {
                List<String> parts = parseCsvLine(lines.get(i));
                if (parts.size() < 2) continue;
                keys.add(normalize(parts.get(0)) + "|" + normalize(parts.get(1)));
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    static Set<String> readExistingSongKeys() {
        Set<String> keys = new HashSet<>();
        try {
            List<String> lines = Files.readAllLines(SONGS_CSV);
            for (int i = 1; i < lines.size(); i++) {
                List<String> parts = parseCsvLine(lines.get(i));
                if (parts.size() < 5) continue;
                keys.add(normalize(parts.get(0)) + "|" + normalize(parts.get(1)) + "|" + normalize(parts.get(4)));
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    static int appendAlbumsCsv(List<TrackData> tracks, Set<String> existingAlbumKeys) {
        int added = 0;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(ALBUMS_CSV.toFile(), true), "UTF-8"))) {
            Set<String> batch = new HashSet<>();
            for (TrackData t : tracks) {
                String key = normalize(t.artist) + "|" + normalize(t.albumTitle);
                if (existingAlbumKeys.contains(key) || !batch.add(key)) {
                    continue;
                }
                out.println(csvRow(t.artist, t.albumTitle, String.valueOf(t.year), "0", t.artUrl));
                existingAlbumKeys.add(key);
                added++;
            }
        } catch (Exception e) {
            System.out.println("Error writing albums.csv: " + e.getMessage());
        }
        return added;
    }

    static int appendSongsCsv(List<TrackData> tracks, Set<String> existingSongKeys) {
        int added = 0;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(SONGS_CSV.toFile(), true), "UTF-8"))) {
            Set<String> batch = new HashSet<>();
            for (TrackData t : tracks) {
                String key = normalize(t.artist) + "|" + normalize(t.albumTitle) + "|" + normalize(t.songTitle);
                if (existingSongKeys.contains(key) || !batch.add(key)) {
                    continue;
                }
                out.println(csvRow(
                        t.artist,
                        t.albumTitle,
                        String.valueOf(t.year),
                        String.valueOf(t.trackNumber),
                        t.songTitle,
                        String.valueOf(t.durationSeconds)
                ));
                existingSongKeys.add(key);
                added++;
            }
        } catch (Exception e) {
            System.out.println("Error writing songs.csv: " + e.getMessage());
        }
        return added;
    }

    // -------------------------------------------------------------------------
    // Genius API — lyrics fetch (search + page scrape)
    // -------------------------------------------------------------------------
    /**
     * Searches Genius for a song and attempts to scrape its lyrics.
     * Returns an empty string if the search or scrape fails.
     *
     * Note: lyrics scraping from Genius is done on a best-effort basis.
     * Not all pages expose server-rendered lyric containers.
     */
    static String fetchLyricsFromGenius(String title, String artist) {
        try {
            // Step 1: search the Genius API
            String query = URLEncoder.encode(title + " " + artist, "UTF-8");
            URL url = new URL("https://api.genius.com/search?q=" + query);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + GENIUS_ACCESS_TOKEN);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JSONObject response = new JSONObject(sb.toString())
                    .getJSONObject("response");
            JSONArray hits = response.getJSONArray("hits");
            if (hits.length() == 0) return "";

            String songUrl = hits.getJSONObject(0)
                    .getJSONObject("result")
                    .getString("url");

            // Step 2: scrape the lyrics page with Jsoup
            Document doc = Jsoup.connect(songUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                               "AppleWebKit/537.36 (KHTML, like Gecko) " +
                               "Chrome/120.0 Safari/537.36")
                    .timeout(10_000)
                    .get();

            StringBuilder lyrics = new StringBuilder();
            for (Element container : doc.select("[data-lyrics-container=true]")) {
                // Replace <br> tags with a placeholder before extracting text
                container.select("br").after("\\n");
                String text = container.text().replace("\\n", "\n");
                lyrics.append(text).append("\n\n");
            }

            String result = lyrics.toString().trim();
            if (!result.isEmpty()) {
                System.out.println("  Lyrics found for: " + title);
            }
            return result;

        } catch (Exception e) {
            // Lyrics fetch is best-effort — failures are non-fatal
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------
    static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String csvEscape(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    static String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            row.append(csvEscape(values[i]));
        }
        return row.toString();
    }

    static List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        if (line == null) return values;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }
}
