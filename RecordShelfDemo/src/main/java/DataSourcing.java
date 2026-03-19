import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

public class DataSourcing {
    //API keys
    private static final String SPOTIFY_CLIENT_ID = "a7df04b3f2eb4138bdf8cdff694d751c";
    private static final String SPOTIFY_CLIENT_SECRET = "f3d2b32f7c19462c99d0f1597f4cb0af";
    private static final String GENIUS_ACCESS_TOKEN = "nWxr8IdQBwdPzcK53obFQsa0Z94NzZld0dYoxsc2g60Rj-DPIxLgbOti7edVn2lP";

    public static void main(String[] args) {
        List<Song> songs = fetchSongsFromSpotify("rock", 10);

        for (Song song : songs) {
            song.lyrics = fetchLyricsFromGenius(song.title, song.artist);
        }

        saveSongsToCSV(songs, "enriched_songs.csv");
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

    // Stub: Fetch songs from Spotify
    static List<Song> fetchSongsFromSpotify(String genre, int limit) {
        List<Song> songs = new ArrayList<>();
        try {
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(SPOTIFY_CLIENT_ID)
                .setClientSecret(SPOTIFY_CLIENT_SECRET)
                .build();

            try {
                // Get access token
                final String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);

                SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks("genre:" + genre).limit(limit).build();
                Track[] tracks = searchTracksRequest.execute().getItems();

                System.out.println("Fetched " + tracks.length + " tracks from Spotify.");

                for (Track track : tracks) {
                    Song song = new Song();
                    song.title = track.getName();
                    song.artist = track.getArtists()[0].getName();
                    song.album = track.getAlbum().getName();
                    song.genre = genre;
                    if (track.getAlbum().getImages().length > 0) {
                        song.albumArtUrl = track.getAlbum().getImages()[0].getUrl();
                    }
                    songs.add(song);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
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

    // Fetch album art from Spotify
    public static String fetchAlbumArtFromSpotify(String title, String artist) {
        try {
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(SPOTIFY_CLIENT_ID)
                .setClientSecret(SPOTIFY_CLIENT_SECRET)
                .build();

            try {
                // Get access token
                final String accessToken = spotifyApi.clientCredentials().build().execute().getAccessToken();
                spotifyApi.setAccessToken(accessToken);

                String query = title + " " + artist;
                SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks(query).limit(1).build();
                Track[] tracks = searchTracksRequest.execute().getItems();

                if (tracks.length > 0 && tracks[0].getAlbum().getImages().length > 0) {
                    return tracks[0].getAlbum().getImages()[0].getUrl();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
