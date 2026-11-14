package dev.along.spotifylyrics;

import dev.along.spotifylyrics.backend.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.net.http.HttpResponse;
import java.util.List;

public class SpotifyLyricsClient implements ClientModInitializer {

    private static String previousLyric = "";
    private static String currentLyric = "Spotify Lyrics (Android Ready)";
    private static String nextLyric = "";

    private static final int MARGIN = 15;
    private static final int LINE_SPACING = 18;
    private static LyricsLine currentLine;
    private static MusicManager spotifyManager;
    private static LyricsManager lyricsManager;
    private static List<LyricsLine> lyrics;
    private static String currentTrack = "";
    private static boolean isFetchingLyrics = false;

    @Override
    public void onInitializeClient() {
        try {
            spotifyManager = new AndroidSpotifyManager();
            lyricsManager = new LyricsManager();
        } catch (Exception e) {
            System.err.println("Spotify Lyrics: failed to initialize");
            spotifyManager = null;
            lyricsManager = new LyricsManager();
        }

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(
                        SpotifyLyrics.MOD_ID,
                        "lyrics"
                ),
                SpotifyLyricsClient::renderLyrics
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (spotifyManager == null) return;

            String artist;
            String title;
            try {
                artist = spotifyManager.getArtist();
                title = spotifyManager.getTitle();
            } catch (Exception e) {
                clearLyrics();
                currentTrack = "";
                return;
            }

            if (artist == null || artist.isBlank() || title == null || title.isBlank()) {
                return;
            }

            String track = artist + " - " + title;

            if (!track.equals(currentTrack)) {
                currentTrack = track;
                currentLine = null;
                lyrics = null;
                clearLyrics();
                setCurrentLyric("Mencari lirik: " + title + "...");

                if (!isFetchingLyrics) {
                    isFetchingLyrics = true;
                    final String fArtist = artist;
                    final String fTitle = title;
                    final String fAlbum = spotifyManager.getAlbum();
                    final long fDuration = spotifyManager.getDuration();

                    new Thread(() -> {
                        try {
                            HttpResponse<String> response = lyricsManager.fetchLyrics(
                                    fArtist, fTitle, fAlbum, fDuration
                            );
                            String syncedLyrics = lyricsManager.getSyncedLyrics(response);
                            List<LyricsLine> parsed = lyricsManager.parseLyrics(syncedLyrics);
                            lyrics = parsed;
                            if (parsed == null || parsed.isEmpty()) {
                                setCurrentLyric("Lirik tidak ditemukan");
                            } else {
                                clearLyrics();
                            }
                        } catch (Exception e) {
                            lyrics = null;
                            setCurrentLyric("Gagal memuat lirik");
                        } finally {
                            isFetchingLyrics = false;
                        }
                    }).start();
                }
                return;
            }

            if (lyrics == null || lyrics.isEmpty()) {
                return;
            }

            long position = spotifyManager.getPosition();
            int currentIndex = lyricsManager.getCurrentLineIndex(lyrics, position);

            if (currentIndex == -1) {
                return;
            }

            LyricsLine current = lyrics.get(currentIndex);

            if (currentLine == null || current.getTimeStamp() != currentLine.getTimeStamp()) {
                currentLine = current;

                String previous = currentIndex > 0 ? lyrics.get(currentIndex - 1).getText() : "";
                String next = currentIndex < lyrics.size() - 1 ? lyrics.get(currentIndex + 1).getText() : "";

                setLyrics(previous, current.getText(), next);
            }
        });
    }

    private static void renderLyrics(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        int x = MARGIN;
        int y = MARGIN;

        if (!previousLyric.isEmpty()) {
            graphics.text(minecraft.font, previousLyric, x, y, 0x99FFFFFF, true);
        }

        graphics.text(minecraft.font, currentLyric, x, y + LINE_SPACING, 0xFFFFFFFF, true);

        if (!nextLyric.isEmpty()) {
            graphics.text(minecraft.font, nextLyric, x, y + LINE_SPACING * 2, 0x99FFFFFF, true);
        }
    }

    public static void setLyrics(String previous, String current, String next) {
        previousLyric = previous;
        currentLyric = current;
        nextLyric = next;
    }

    public static void setCurrentLyric(String lyric) {
        currentLyric = lyric;
    }

    public static void clearLyrics() {
        previousLyric = "";
        currentLyric = "";
        nextLyric = "";
            }
}
