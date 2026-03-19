import java.io.*;
import java.net.*;
import java.util.*;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

public class DataSourcing {
    //API keys
    private static final String SPOTIFY_CLIENT_ID = "a7df04b3f2eb4138bdf8cdff694d751c";
    private static final String SPOTIFY_CLIENT_SECRET = "f3d2b32f7c19462c99d0f1597f4cb0af";
    private static final String GENIUS_ACCESS_TOKEN = "nWxr8IdQBwdPzcK53obFQsa0Z94NzZld0dYoxsc2g60Rj-DPIxLgbOti7edVn2lP";

    public static void main(String[] args) {
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(SPOTIFY_CLIENT_ID)
            .setClientSecret(SPOTIFY_CLIENT_SECRET)
            .build();

        try {
            // Authenticate
            spotifyApi.clientCredentials().build().execute();

            // Search for tracks in a genre
            SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks("genre:rock").limit(10).build();
            Track[] tracks = searchTracksRequest.execute().getItems();

            for (Track track : tracks) {
                System.out.println("Song: " + track.getName());
                System.out.println("Artist: " + track.getArtists()[0].getName());
                System.out.println("Album: " + track.getAlbum().getName());
                System.out.println("Album Art: " + track.getAlbum().getImages()[0].getUrl());
            }
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

    // Stub: Fetch songs from Spotify
    static List<Song> fetchSongsFromSpotify(String genre, int limit) {
        // TODO: Implement Spotify API call
        return new ArrayList<>();
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
}
