package com.ss.gb28181.zlm;

import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbRtpTarget;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.OpenRtpServerBO;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GbZlmPlayerTest {
    private static final String DEVICE = "34020000001320000001";
    private static final String CHANNEL = "34020000001320000002";

    @Test
    void allocatesBeforeInviteAndReleasesWhenRemoteSessionEnds() throws Exception {
        Fixture f = new Fixture();
        try (GbZlmPlayer player = f.player(2, GbRtpTarget.Transport.TCP_PASSIVE)) {
            CompletableFuture<GbZlmPlaySession> result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            assertEquals(1, f.resources.size());
            assertEquals(1, f.request.get().getTcpMode());
            assertEquals(new GbRtpTarget("192.0.2.1", 50000, GbRtpTarget.Transport.TCP_PASSIVE), f.target.get());
            f.invite.complete(f.session);
            GbZlmPlaySession session = result.get(2, TimeUnit.SECONDS);
            assertEquals("rtp", session.app());
            assertTrue(f.resources.contains(session.stream()));
            assertSame(f.session, session.sipSession());
            f.ended.complete(null);
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void rejectedInviteReleasesPortBeforeReturningFailure() throws Exception {
        Fixture f = new Fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.completeExceptionally(new IllegalStateException("device rejected"));
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void cancellationCancelsInviteAndFreesCapacity() throws Exception {
        Fixture f = new Fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertTrue(result.cancel(false));
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertTrue(f.invite.isCancelled());
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void capacityAndCloseRejectNewRequestsAndReleasePendingPort() throws Exception {
        Fixture f = new Fixture();
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var pending = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> player.play(DEVICE, CHANNEL).get(2, TimeUnit.SECONDS));
            player.close();
            assertTrue(pending.isCompletedExceptionally());
            assertTrue(f.invite.isCancelled());
            assertTrue(f.resources.isEmpty());
            assertThrows(ExecutionException.class, () -> player.play(DEVICE, CHANNEL).get(2, TimeUnit.SECONDS));
        } finally { player.close(); }
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void allocationFailureNeverInvitesAndInvalidInputNeverAllocates() throws Exception {
        Fixture f = new Fixture();
        doReturn(-1).when(f.media).openRtpServer(any());
        try (GbZlmPlayer player = f.player(2, GbRtpTarget.Transport.UDP)) {
            assertThrows(ExecutionException.class, () -> player.play(DEVICE, CHANNEL).get(2, TimeUnit.SECONDS));
            assertEquals(1, f.invited.getCount());
            assertThrows(IllegalArgumentException.class, () -> player.play("bad", CHANNEL));
        }
    }

    @Test
    void closeStopsEstablishedSessionWithoutWaitingForBye() throws Exception {
        Fixture f = new Fixture();
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        var result = player.play(DEVICE, CHANNEL);
        assertTrue(f.invited.await(2, TimeUnit.SECONDS));
        f.invite.complete(f.session);
        result.get(2, TimeUnit.SECONDS);
        player.close();
        assertTrue(f.stopped.get());
        assertTrue(f.resources.isEmpty());
        assertFalse(f.ended.isDone());
        player.close();
        assertEquals(1, f.releaseCount.get());
    }

    @Test
    void cancellingDuringAllocationStillReleasesReturnedPortWithoutInviting() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(call -> {
            OpenRtpServerBO value = call.getArgument(0);
            opening.countDown();
            assertTrue(proceed.await(2, TimeUnit.SECONDS));
            f.resources.add(value.getStream());
            return 50000;
        }).when(f.media).openRtpServer(any());
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(opening.await(2, TimeUnit.SECONDS));
            result.cancel(false);
            proceed.countDown();
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertEquals(1, f.invited.getCount());
            assertTrue(f.resources.isEmpty());
        } finally { proceed.countDown(); }
    }

    @Test
    void lateSuccessAfterCloseIsStoppedWithoutReopeningRtp() throws Exception {
        Fixture f = new Fixture();
        CompletableFuture<GbPlaySession> uncancellable = new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
        when(f.server.play(eq(DEVICE), eq(CHANNEL), any())).thenAnswer(call -> {
            f.invited.countDown();
            return uncancellable;
        });
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            player.close();
            assertTrue(result.isCompletedExceptionally());
            uncancellable.complete(f.session);
            assertTrue(f.stopped.get());
            assertTrue(f.resources.isEmpty());
            assertEquals(1, f.releaseCount.get());
        } finally { player.close(); }
    }

    @Test
    void nativeReleaseFailureIsRetainedAndRetriedOnClose() throws Exception {
        Fixture f = new Fixture();
        java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            if (fail.getAndSet(false)) throw new IllegalStateException("release failed");
            f.releaseCount.incrementAndGet();
            return f.resources.remove(call.getArgument(0));
        }).when(f.media).closeRtpServer(anyString());
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.completeExceptionally(new IllegalStateException("invite failed"));
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertEquals(1, f.resources.size());
            player.close();
            assertTrue(f.resources.isEmpty());
        } finally { player.close(); }
    }

    @Test
    void slowNativeCleanupDoesNotBlockSipCompletionThread() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(call -> {
            cleanupStarted.countDown();
            assertTrue(proceed.await(2, TimeUnit.SECONDS));
            return f.resources.remove(call.getArgument(0));
        }).when(f.media).closeRtpServer(anyString());
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            f.invite.complete(f.session);
            result.get(2, TimeUnit.SECONDS);
            // This call must return even while the worker is waiting inside native cleanup.
            assertTimeout(java.time.Duration.ofSeconds(1), () -> f.ended.complete(null));
            assertTrue(cleanupStarted.await(2, TimeUnit.SECONDS));
            proceed.countDown();
        } finally { proceed.countDown(); }
        assertTrue(f.resources.isEmpty());
    }

    @Test
    void blockedNativeAllocationCannotHoldCloseIndefinitely() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(call -> {
            OpenRtpServerBO value = call.getArgument(0);
            opening.countDown();
            assertTrue(proceed.await(10, TimeUnit.SECONDS));
            f.resources.add(value.getStream());
            return 50000;
        }).when(f.media).openRtpServer(any());
        GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP);
        try {
            player.play(DEVICE, CHANNEL);
            assertTrue(opening.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            assertThrows(IllegalStateException.class, player::close);
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(7));
            assertThrows(ExecutionException.class, () -> player.play(DEVICE, CHANNEL).get(2, TimeUnit.SECONDS));
        } finally {
            proceed.countDown();
            player.close();
        }
        assertTrue(f.resources.isEmpty());
        assertEquals(1, f.invited.getCount());
    }

    @Test
    void callerTimeoutCancelsInviteAndReleasesReceiver() throws Exception {
        Fixture f = new Fixture();
        try (GbZlmPlayer player = f.player(1, GbRtpTarget.Transport.UDP)) {
            var result = player.play(DEVICE, CHANNEL);
            assertTrue(f.invited.await(2, TimeUnit.SECONDS));
            result.completeExceptionally(new TimeoutException("caller deadline"));
            assertTrue(f.released.await(2, TimeUnit.SECONDS));
            assertTrue(f.invite.isCancelled());
            assertTrue(f.resources.isEmpty());
        }
        assertEquals(1, f.releaseCount.get());
    }

    static class Fixture {
        final Gb28181Server server = mock(Gb28181Server.class);
        final GbPlaySession session = mock(GbPlaySession.class);
        final IZlmMediaService media = mock(IZlmMediaService.class);
        final CompletableFuture<GbPlaySession> invite = new CompletableFuture<>();
        final CompletableFuture<Void> ended = new CompletableFuture<>();
        final Set<String> resources = ConcurrentHashMap.newKeySet();
        final CountDownLatch invited = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final AtomicInteger releaseCount = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
        final AtomicReference<OpenRtpServerBO> request = new AtomicReference<>();
        final AtomicReference<GbRtpTarget> target = new AtomicReference<>();
        Fixture() {
            when(media.openRtpServer(any())).thenAnswer(call -> {
                OpenRtpServerBO value = call.getArgument(0);
                request.set(value);
                assertTrue(resources.add(value.getStream()));
                return 50000;
            });
            when(media.closeRtpServer(anyString())).thenAnswer(call -> {
                releaseCount.incrementAndGet();
                boolean removed = resources.remove(call.getArgument(0));
                released.countDown();
                return removed;
            });
            when(server.play(eq(DEVICE), eq(CHANNEL), any())).thenAnswer(call -> {
                assertEquals(1, resources.size(), "RTP must exist before INVITE");
                target.set(call.getArgument(2));
                invited.countDown();
                return invite;
            });
            when(session.completion()).thenReturn(ended);
            when(session.stop()).thenAnswer(call -> { stopped.set(true); return ended; });
        }
        GbZlmPlayer player(int capacity, GbRtpTarget.Transport transport) {
            return new GbZlmPlayer(server, media, "192.0.2.1", transport, capacity);
        }
    }
}
