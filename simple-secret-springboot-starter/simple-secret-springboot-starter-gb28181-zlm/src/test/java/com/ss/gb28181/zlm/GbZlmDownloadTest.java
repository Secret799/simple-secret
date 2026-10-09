package com.ss.gb28181.zlm;

import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbRtpTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GbZlmDownloadTest {
    private static final String DEVICE = "34020000001320000001";
    private static final String CHANNEL = "34020000001320000002";
    private static final GbPlaybackRange RANGE = new GbPlaybackRange(
            Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-15T01:00:00Z"));

    private static final GbDownloadRequest REQUEST = new GbDownloadRequest(RANGE, 8);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void downloadAllocatesBeforeInviteAndReleasesOnDownloadEndOrOffline(boolean offline) throws Exception {
        var f = fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.TCP_PASSIVE)) {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertFalse(opening.isDone());
            assertEquals(1, f.resources.size());
            assertEquals(1, f.request.get().getTcpMode());
            assertEquals(new GbRtpTarget("192.0.2.1", 50000, GbRtpTarget.Transport.TCP_PASSIVE), f.target.get());
            f.invite.complete(f.session);
            GbZlmPlaySession session = opening.get(2, TimeUnit.SECONDS);
            assertSame(f.session, session.sipSession());
            assertEquals("rtp", session.app());
            assertTrue(f.resources.contains(session.stream()));
            if (offline) f.ended.completeExceptionally(new IllegalStateException("device offline"));
            else f.ended.complete(null);
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertTrue(f.resources.isEmpty());
            if (offline) assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            else session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(f.server, never()).play(anyString(), anyString(), any());
            verify(f.server, never()).playback(anyString(), anyString(), any(), any());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void downloadCancellationCancelsInviteAndReleasesReceiver() throws Exception {
        var f = fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertTrue(opening.cancel(false));
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertTrue(f.invite.isCancelled());
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void liveAndDownloadShareReceiverCapacityAndCloseCancelsDownload() throws Exception {
        var f = fixture();
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> player.play(DEVICE, CHANNEL).get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> player.download(DEVICE, CHANNEL, REQUEST).get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> player.playback(DEVICE, CHANNEL, RANGE).get(2, TimeUnit.SECONDS));
            player.close();
            assertTrue(opening.isCompletedExceptionally());
            assertTrue(f.invite.isCancelled());
            assertTrue(f.resources.isEmpty());
            assertThrows(ExecutionException.class,
                    () -> player.download(DEVICE, CHANNEL, REQUEST).get(2, TimeUnit.SECONDS));
        } finally { player.close(); }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void downloadAllocationFailureNeverInvitesAndInvalidArgumentsNeverAllocate() throws Exception {
        var f = fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            assertThrows(NullPointerException.class, () -> player.download(DEVICE, CHANNEL, null));
            assertThrows(IllegalArgumentException.class, () -> player.download("bad", CHANNEL, REQUEST));
            assertThrows(IllegalArgumentException.class, () -> player.download(DEVICE, "bad", REQUEST));
            verify(f.media, never()).openRtpServer(any());
            doReturn(-1).when(f.media).openRtpServer(any());
            assertThrows(ExecutionException.class,
                    () -> player.download(DEVICE, CHANNEL, REQUEST).get(2, TimeUnit.SECONDS));
            verify(f.server, never()).download(anyString(), anyString(), any(), any());
            verify(f.server, never()).play(anyString(), anyString(), any());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void rejectedDownloadInviteReleasesReceiverBeforeReturningFailure() throws Exception {
        var f = fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            IllegalStateException rejection = new IllegalStateException("download rejected");
            f.invite.completeExceptionally(rejection);
            assertSame(rejection, assertThrows(ExecutionException.class,
                    () -> opening.get(2, TimeUnit.SECONDS)).getCause());
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void lateDownloadSuccessAfterCloseStopsSipWithoutReopeningReceiver() throws Exception {
        var f = fixture();
        CompletableFuture<GbPlaySession> uncancellable = new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
        when(f.server.download(eq(DEVICE), eq(CHANNEL), any(), same(REQUEST))).thenAnswer(call -> {
            f.invited.countDown();
            return uncancellable;
        });
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            player.close();
            assertTrue(opening.isCompletedExceptionally());
            uncancellable.complete(f.session);
            assertTrue(f.stopped.get());
            assertTrue(f.resources.isEmpty());
            assertEquals(1, f.releaseCount.get());
        } finally { player.close(); }
    }

    @Test
    void livePlayStillUsesLiveSignaling() throws Exception {
        var f = fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var opening = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.complete(f.session);
            assertSame(f.session, opening.get(2, TimeUnit.SECONDS).sipSession());
            verify(f.server, never()).download(anyString(), anyString(), any(), any());
        }
        assertTrue(f.resources.isEmpty());
    }

    @Test
    void closeStopsEstablishedDownloadWithoutAwaitingBye() throws Exception {
        var f = fixture();
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var opening = player.download(DEVICE, CHANNEL, REQUEST);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.complete(f.session);
            opening.get(2, TimeUnit.SECONDS);
            player.close();
            assertTrue(f.stopped.get());
            assertTrue(f.resources.isEmpty());
            assertFalse(f.ended.isDone());
        } finally { player.close(); }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void historyStillUsesPlaybackSignaling() throws Exception {
        var f = fixture();
        when(f.server.playback(eq(DEVICE), eq(CHANNEL), any(), same(RANGE))).thenAnswer(call -> {
            f.invited.countDown();
            return f.invite;
        });
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var opening = player.playback(DEVICE, CHANNEL, RANGE);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.complete(f.session);
            assertSame(f.session, opening.get(2, TimeUnit.SECONDS).sipSession());
            verify(f.server, never()).download(anyString(), anyString(), any(), any());
            verify(f.server, never()).play(anyString(), anyString(), any());
        }
        assertTrue(f.resources.isEmpty());
    }

    private static GbZlmPlayerTest.Fixture fixture() {
        var f = new GbZlmPlayerTest.Fixture();
        when(f.server.download(eq(DEVICE), eq(CHANNEL), any(), same(REQUEST))).thenAnswer(call -> {
            assertEquals(1, f.resources.size(), "RTP must exist before download INVITE");
            f.target.set(call.getArgument(2));
            f.invited.countDown();
            return f.invite;
        });
        return f;
    }
}
