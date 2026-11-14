package dev.along.spotifylyrics.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class AndroidSpotifyManager extends MusicManager {
    private final HttpClient client = HttpClient.newHttpClient();
    private String artist = "";
    private String title = "";
    private String album = "";
    private long duration = 0;
    private long basePosition = 0;
    private long lastUpdateMs = 0;
    private boolean playing = false;

    public AndroidSpotifyManager() {
        Thread poller = new Thread(() -> {
            while (true) {
                try {
                    fetchCurrentTrack();
                    Thread.sleep(2000);
                } catch (Exception e) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
        poller.setDaemon(true);
        poller.start();
    }

    private String getToken() {
        try {
            File tokenFile = new File("spotify_token.txt");
            if (!tokenFile.exists()) {
                Files.writeString(Path.of("spotify_token.txt"), "TEMPEL_TOKEN_SPOTIFY_DISINI");
                return "";
            }
            return Files.readString(Path.of("spotify_token.txt")).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void fetchCurrentTrack() {
        String token = getToken();
        if (token.isEmpty() || token.equals("TEMPEL_TOKEN_SPOTIFY_DISINI")) {
            playing = false;
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://spotify.com"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                boolean isPlayingNow = json.has("is_playing") && json.get("is_playing").getAsBoolean();
                long progressMs = json.has("progress_ms") && !json.get("progress_ms").isJsonNull()
                        ? json.get("progress_ms").getAsLong() : 0;

                if (json.has("item") && !json.get("item").isJsonNull()) {
                    JsonObject item = json.getAsJsonObject("item");
                    String newTitle = item.get("name").getAsString();
                    long newDuration = item.get("duration_ms").getAsLong() / 1000;
                    String newAlbum = item.getAsJsonObject("album").get("name").getAsString();

                    JsonArray artists = item.getAsJsonArray("artists");
                    String newArtist = artists.size() > 0
                            ? artists.get(0).getAsJsonObject().get("name").getAsString() : "";

                    this.title = newTitle;
                    this.artist = newArtist;
                    this.album = newAlbum;
                    this.duration = newDuration;
                    this.basePosition = progressMs;
                    this.lastUpdateMs = System.currentTimeMillis();
                    this.playing = isPlayingNow;
                }
            } else {
                this.playing = false;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getAlbum() {
        return album;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public long getPosition() {
        if (!playing) {
            return basePosition;
        }
        return basePosition + (System.currentTimeMillis() - lastUpdateMs);
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }
                               }
