package com.ss.gb28181;

import com.ss.gb28181.internal.DigestAuthenticator;
import com.ss.gb28181.internal.GbXml;
import com.ss.gb28181.internal.ManagedSipStack;
import com.ss.gb28181.internal.ResponseSourceGuard;
import gov.nist.javax.sip.RequestEventExt;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Platform-side GB registration, heartbeat, device queries, PTZ and playback signaling.
 * Owns its SIP stack and maintenance thread; always close the server.
 * Future continuations must not block SIP/maintenance threads; use thenApplyAsync for business work.
 */
public final class Gb28181Server implements AutoCloseable, SipListener {
    private static final System.Logger LOG = System.getLogger(Gb28181Server.class.getName());
    private final GbServerOptions options;
    private final DigestAuthenticator authenticator;
    private final HeaderFactoryImpl headers = new HeaderFactoryImpl();
    private final AddressFactoryImpl addresses = new AddressFactoryImpl();
    private final MessageFactoryImpl messages = new MessageFactoryImpl();
    private final Map<String, SipProvider> providers = new HashMap<>();
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Map<Integer, PendingRequest<?>> pending = new HashMap<>();
    private final GbPlayback playback;
    private final GbSubscriptions<GbAlarmSubscriptionRequest, GbAlarm, GbAlarmSubscription> alarmSubscriptions;
    private final GbSubscriptions<GbCatalogSubscriptionRequest, GbCatalogNotification, GbCatalogSubscription> catalogSubscriptions;
    private final Map<GbCatalogEvent, BooleanSupplier> catalogEligibility = new IdentityHashMap<>();
    private final CallbackDispatcher<GbCatalogEvent> catalogDispatcher;
    private int pendingCatalogNotifications;
    private final GbSubscriptions<GbMobilePositionSubscriptionRequest, GbMobilePositionNotification,
            GbMobilePositionSubscription> mobilePositionSubscriptions;
    private final Map<GbMobilePositionEvent, BooleanSupplier> mobilePositionEligibility = new IdentityHashMap<>();
    private final CallbackDispatcher<GbMobilePositionEvent> mobilePositionDispatcher;
    private int pendingMobilePositionNotifications;
    private final Map<GbAlarmEvent, BooleanSupplier> alarmEligibility = new IdentityHashMap<>();
    private final ResponseSourceGuard responseGuard;
    private final CallbackDispatcher<GbAlarmEvent> alarmDispatcher;
    private SipStackImpl stack;
    private ScheduledExecutorService maintenance;
    private volatile boolean closed;
    private int nextSn;
    private int pendingAlarms;

    private Gb28181Server(GbServerOptions options, DeviceCredentials credentials, GbAlarmListener alarmListener,
                          GbCatalogListener catalogListener, GbMobilePositionListener mobilePositionListener) {
        this.options = Objects.requireNonNull(options);
        authenticator = new DigestAuthenticator(options.realm(), Math.max(256, options.maxDevices() * 2), credentials);
        responseGuard = new ResponseSourceGuard(options.maxPendingQueries() + options.maxPlaySessions() * 4
                + options.maxAlarmSubscriptions() * 2 + options.maxCatalogSubscriptions() * 2
                + options.maxMobilePositionSubscriptions() * 2);
        playback = new GbPlayback(this, options, providers, responseGuard);
        alarmSubscriptions = new GbSubscriptions<>(this, options, providers, responseGuard, options.maxAlarmSubscriptions(),
                GbSubscriptionTypes.ALARM, this::acceptSubscriptionAlarm);
        alarmDispatcher = alarmListener == null ? null : new CallbackDispatcher<>(options.maxPendingAlarms(), "alarm",
                event -> deliverAlarm(alarmListener, event));
        catalogSubscriptions = new GbSubscriptions<>(this, options, providers, responseGuard, options.maxCatalogSubscriptions(),
                GbSubscriptionTypes.CATALOG, this::acceptSubscriptionCatalog);
        catalogDispatcher = catalogListener == null ? null : new CallbackDispatcher<>(options.maxPendingCatalogNotifications(),
                "catalog", event -> deliverCatalog(catalogListener, event));
        mobilePositionSubscriptions = new GbSubscriptions<>(this, options, providers, responseGuard,
                options.maxMobilePositionSubscriptions(), GbSubscriptionTypes.MOBILE_POSITION,
                this::acceptSubscriptionMobilePosition);
        mobilePositionDispatcher = mobilePositionListener == null ? null
                : new CallbackDispatcher<>(options.maxPendingMobilePositionNotifications(), "mobile-position",
                event -> deliverMobilePosition(mobilePositionListener, event));
    }
    /** Starts both UDP and TCP. Partial startup failure releases listeners already opened. */
    public static Gb28181Server open(GbServerOptions options, DeviceCredentials credentials) {
        return start(options, credentials, null, null, null);
    }
    /** Starts both transports with ordered, bounded asynchronous Alarm delivery. */
    public static Gb28181Server open(GbServerOptions options, DeviceCredentials credentials,
                                    GbAlarmListener alarmListener) {
        Objects.requireNonNull(alarmListener, "Alarm listener");
        return start(options, credentials, alarmListener, null, null);
    }
    /** Starts both transports with independently optional Alarm and Catalog listeners. */
    public static Gb28181Server open(GbServerOptions options, DeviceCredentials credentials,
                                    GbAlarmListener alarmListener, GbCatalogListener catalogListener) {
        return start(options, credentials, alarmListener, catalogListener, null);
    }
    /** Starts both transports with independently optional Alarm, Catalog and MobilePosition listeners. */
    public static Gb28181Server open(GbServerOptions options, DeviceCredentials credentials,
                                     GbAlarmListener alarmListener, GbCatalogListener catalogListener,
                                     GbMobilePositionListener mobilePositionListener) {
        return start(options, credentials, alarmListener, catalogListener, mobilePositionListener);
    }
    private static Gb28181Server start(GbServerOptions options, DeviceCredentials credentials,
                                      GbAlarmListener alarmListener, GbCatalogListener catalogListener,
                                      GbMobilePositionListener mobilePositionListener) {
        Gb28181Server server = new Gb28181Server(options, Objects.requireNonNull(credentials), alarmListener,
                catalogListener, mobilePositionListener);
        try { server.start(); return server; }
        catch (Exception | LinkageError e) {
            try { server.close(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw new IllegalStateException("Cannot start GB28181 SIP server", e);
        }
    }
    private void start() throws Exception {
        Properties p = new Properties();
        p.setProperty("javax.sip.STACK_NAME", "simple-secret-gb-" + UUID.randomUUID());
        p.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        // Managed subscriptions validate dialogs themselves; avoid RI acquiring a pending SUBSCRIBE
        // semaphore while delivering NOTIFY, which can outlive an explicitly retired transaction.
        p.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
        p.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        p.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        p.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "4");
        p.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "0");
        p.setProperty("gov.nist.javax.sip.MAX_CONNECTIONS", Integer.toString(options.maxDevices()));
        p.setProperty("gov.nist.javax.sip.MAX_SERVER_TRANSACTIONS", Integer.toString(Math.max(256, options.maxPendingQueries() * 4)));
        p.setProperty("gov.nist.javax.sip.MAX_CLIENT_TRANSACTIONS", Integer.toString(options.maxPendingQueries()
                + options.maxPlaySessions() * 4 + options.maxAlarmSubscriptions() * 2
                + options.maxCatalogSubscriptions() * 2 + options.maxMobilePositionSubscriptions() * 2));
        p.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", Integer.toString(options.maxMessageBytes()));
        p.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "true");
        p.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "true");
        p.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "10000");
        p.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", "gov.nist.javax.sip.stack.CongestionControlMessageValve");
        p.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", "com.ss.gb28181.internal.BoundedMessageProcessorFactory");
        p.setProperty("gov.nist.javax.sip.CONGESTION_CONTROL_TIMEOUT", "0");
        stack = new ManagedSipStack(p);
        responseGuard.init(stack);
        stack.sipMessageValves.add(responseGuard);
        for (String transport : List.of("TCP", "UDP")) {
            ListeningPoint point = stack.createListeningPoint(options.bindAddress(), options.port(), transport);
            SipProvider provider = stack.createSipProvider(point);
            provider.addSipListener(this);
            providers.put(transport, provider);
        }
        stack.start();
        maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "simple-secret-gb-maintenance"); thread.setDaemon(true); return thread;
        });
        maintenance.scheduleWithFixedDelay(this::maintain, 50, 50, TimeUnit.MILLISECONDS);
    }
    /** Current online registration snapshots. Offline/expired records are not retained. */
    public synchronized List<RegisteredDevice> devices() {
        expireDevices();
        return bindings.values().stream().map(b -> b.device).sorted(Comparator.comparing(RegisteredDevice::deviceId)).toList();
    }
    /** Begins a bounded query. A SIP 200 alone does not complete the returned future. */
    public synchronized CompletableFuture<List<CatalogItem>> queryCatalog(String deviceId) {
        CatalogAccumulator accumulator = new CatalogAccumulator(options);
        return sendMessage(deviceId, deviceId, "Catalog", sn -> GbXml.catalogQuery(deviceId, sn), accumulator::accept);
    }
    /** Queries recording metadata for a host-authorized channel using device-local time.
     * Results retain first-seen order and include whole files; SIP receipt alone does not complete the query.
     */
    public synchronized CompletableFuture<List<RecordItem>> queryRecordInfo(String ownerDeviceId, String channelId,
                                                                           RecordQuery query) {
        Objects.requireNonNull(query, "Record query");
        RecordAccumulator accumulator = new RecordAccumulator(options, channelId);
        return sendMessage(ownerDeviceId, channelId, "RecordInfo", sn -> GbXml.recordQuery(channelId, sn, query), accumulator::accept);
    }
    /** Queries manufacturer, model, firmware and optional channel count. Missing optional fields are null. */
    public synchronized CompletableFuture<DeviceInfo> queryDeviceInfo(String deviceId) {
        return sendMessage(deviceId, deviceId, "DeviceInfo", sn -> GbXml.query("DeviceInfo", deviceId, sn), GbXml.Message::deviceInfo);
    }
    /** Queries the device's reported state; this does not change registration/heartbeat liveness. */
    public synchronized CompletableFuture<DeviceStatus> queryDeviceStatus(String deviceId) {
        return sendMessage(deviceId, deviceId, "DeviceStatus", sn -> GbXml.query("DeviceStatus", deviceId, sn), GbXml.Message::deviceStatus);
    }
    /** Queries the device home-position configuration. */
    public synchronized CompletableFuture<HomePosition> queryHomePosition(String deviceId) {
        return queryHomePosition(deviceId, deviceId);
    }
    /** Queries home-position configuration on a host-authorized device or channel. */
    public synchronized CompletableFuture<HomePosition> queryHomePosition(String ownerDeviceId, String targetId) {
        return sendMessage(ownerDeviceId, targetId, "HomePositionQuery",
                sn -> GbXml.query("HomePositionQuery", targetId, sn), GbXml.Message::homePosition);
    }
    /** Queries precise PTZ position for a host-authorized device or channel. */
    public synchronized CompletableFuture<PtzPosition> queryPtzPosition(String ownerDeviceId, String targetId) {
        return sendMessage(ownerDeviceId, targetId, "PTZPosition",
                sn -> GbXml.query("PTZPosition", targetId, sn), GbXml.Message::ptzPosition);
    }
    public synchronized CompletableFuture<PtzPosition> queryPtzPosition(String deviceId) {
        return queryPtzPosition(deviceId, deviceId);
    }
    /** Queries up to 255 presets for a host-authorized channel, retaining first-seen order and exact IDs.
     * SIP receipt alone does not complete the query; all declared unique presets must arrive.
     */
    public synchronized CompletableFuture<List<PresetItem>> queryPresets(String deviceId, String channelId) {
        PresetAccumulator accumulator = new PresetAccumulator();
        return sendMessage(deviceId, channelId, "PresetQuery", sn -> GbXml.presetQuery(channelId, sn), accumulator::accept);
    }
    /** Sets, invokes or removes a preset on a host-authorized channel. Completion means SIP receipt,
     * not physical completion. Cancellation only ends waiting and does not reverse a sent control.
     */
    public synchronized CompletableFuture<Void> preset(String deviceId, String channelId, PresetCommand command) {
        Objects.requireNonNull(command, "Preset command");
        return sendMessage(deviceId, channelId, "DeviceControl", sn -> GbXml.ptzControl(channelId, sn, command.hex()), null);
    }
    /** Sends PTZ movement/zoom/stop to a host-authorized channel. Completion means SIP receipt,
     * not mechanical completion. Cancellation only ends waiting; send PtzCommand.stop() explicitly to stop motion.
     */
    public synchronized CompletableFuture<Void> ptz(String deviceId, String channelId, PtzCommand command) {
        Objects.requireNonNull(command, "PTZ command");
        return sendMessage(deviceId, channelId, "DeviceControl", sn -> GbXml.ptzControl(channelId, sn, command.hex()), null);
    }
    /** Resets explicitly selected alarm state on a host-authorized device or channel.
     * Completion means SIP receipt, not confirmation that device alarm state changed.
     */
    public synchronized CompletableFuture<Void> resetAlarm(String deviceId, String targetId,
                                                            GbAlarmResetCommand command) {
        Objects.requireNonNull(command, "Alarm reset command");
        return sendMessage(deviceId, targetId, "DeviceControl",
                sn -> GbXml.alarmReset(targetId, sn, command), null, true);
    }
    /** Requests an IDR frame from a host-authorized channel. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> requestKeyFrame(String deviceId, String channelId) {
        return sendMessage(deviceId, channelId, "DeviceControl",
                sn -> GbXml.keyFrameRequest(channelId, sn), null);
    }
    /** Requests a remote device reboot. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> teleBoot(String deviceId) {
        return sendMessage(deviceId, deviceId, "DeviceControl",
                sn -> GbXml.teleBoot(deviceId, sn), null);
    }
    /** Starts or stops recording on a host-authorized channel. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> controlRecording(String deviceId, String channelId,
                                                                 GbRecordControlCommand command) {
        Objects.requireNonNull(command, "Record control command");
        return sendMessage(deviceId, channelId, "DeviceControl",
                sn -> GbXml.recordControl(channelId, sn, command), null);
    }
    /** Sets or resets alarm guard on a host-authorized target. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> controlGuard(String deviceId, String targetId,
                                                             GbGuardCommand command) {
        Objects.requireNonNull(command, "Guard command");
        return sendMessage(deviceId, targetId, "DeviceControl",
                sn -> GbXml.guard(targetId, sn, command), null);
    }
    /** Requests drag-box zoom in on a host-authorized channel. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> dragZoomIn(String deviceId, String channelId,
                                                            GbDragZoomCommand command) {
        Objects.requireNonNull(command, "Drag zoom command");
        return sendMessage(deviceId, channelId, "DeviceControl",
                sn -> GbXml.dragZoomIn(channelId, sn, command), null);
    }
    /** Requests drag-box zoom out on a host-authorized channel. Completion means SIP receipt only. */
    public synchronized CompletableFuture<Void> dragZoomOut(String deviceId, String channelId,
                                                             GbDragZoomCommand command) {
        Objects.requireNonNull(command, "Drag zoom command");
        return sendMessage(deviceId, channelId, "DeviceControl",
                sn -> GbXml.dragZoomOut(channelId, sn, command), null);
    }
    private <T> CompletableFuture<T> sendMessage(String device, String target, String command,
                                                IntFunction<byte[]> body, Function<GbXml.Message, T> decoder) {
        return sendMessage(device, target, command, body, decoder, false);
    }
    private <T> CompletableFuture<T> sendMessage(String device, String target, String command,
                                                IntFunction<byte[]> body, Function<GbXml.Message, T> decoder,
                                                boolean allowAlarmCenter) {
        requireOpen();
        if (device == null || !device.matches("[0-9]{20}") || target == null
                || !(target.matches("[0-9]{20}") || (allowAlarmCenter && target.matches("[0-9]{10}"))))
            throw new IllegalArgumentException("Device must contain 20 digits and target 20 digits (or 10-digit alarm center)");
        expireDevices();
        Binding binding = bindings.get(device);
        if (binding == null) throw new IllegalStateException("Device is not registered and online");
        for (var entry : List.copyOf(pending.entrySet())) {
            if (entry.getValue().result.isDone()) { pending.remove(entry.getKey()); terminate(entry.getValue()); }
        }
        if (pending.size() >= options.maxPendingQueries()) throw new IllegalStateException("Pending MESSAGE capacity exceeded");
        int sn = reserveSn();
        PendingRequest<T> query = new PendingRequest<>(device, target, command, binding.endpoint,
                System.nanoTime() + options.queryTimeout().toNanos(), decoder);
        pending.put(sn, query);
        try {
            sendPendingMessage(query, sn, body.apply(sn));
        } catch (Exception e) {
            pending.remove(sn);
            terminate(query);
            query.result.completeExceptionally(new IllegalStateException("Cannot send " + command + " request", e));
        }
        return query.result;
    }
    /** Starts live PS/RTP playback for a host-authorized channel belonging to an online device.
     * The host must start the RTP receiver before calling and release it when the session ends.
     * Completion means SIP/SDP establishment, not media availability.
     */
    public synchronized CompletableFuture<GbPlaySession> play(String deviceId, String channelId, GbRtpTarget target) {
        return play(deviceId, channelId, target, null, null);
    }
    /** Starts historical PS/RTP playback for an explicit UTC interval and host-owned RTP receiver. */
    public synchronized CompletableFuture<GbPlaySession> playback(String deviceId, String channelId,
                                                                  GbRtpTarget target, GbPlaybackRange range) {
        return play(deviceId, channelId, target, Objects.requireNonNull(range, "Playback range"), null);
    }
    /** Requests a recording interval as a download RTP stream to a host-owned receiver.
     * Completion means SIP/SDP establishment; the host owns media reception and file storage.
     */
    public synchronized CompletableFuture<GbPlaySession> download(String deviceId, String channelId,
                                                                  GbRtpTarget target, GbDownloadRequest request) {
        Objects.requireNonNull(request, "Download request");
        return play(deviceId, channelId, target, request.range(), request);
    }
    private CompletableFuture<GbPlaySession> play(String deviceId, String channelId, GbRtpTarget target,
                                                 GbPlaybackRange range, GbDownloadRequest downloadRequest) {
        requireOpen();
        if (deviceId == null || !deviceId.matches("[0-9]{20}") || channelId == null || !channelId.matches("[0-9]{20}"))
            throw new IllegalArgumentException("Playback device and channel must contain 20 digits");
        Objects.requireNonNull(target, "RTP target");
        expireDevices();
        Binding binding = bindings.get(deviceId);
        if (binding == null) throw new IllegalStateException("Device is not registered and online");
        return playback.play(deviceId, channelId, target, range, downloadRequest,
                new GbPlayback.Peer(binding.endpoint.host, binding.endpoint.port, binding.endpoint.transport));
    }
    /** Subscribes to a host-authorized Alarm device/center under an online registered IPC/NVR.
     * Requires an Alarm listener. Completion means SIP acceptance; notifications may arrive before it.
     * The server renews automatically. Stop the returned handle when the subscription is no longer needed.
     */
    public synchronized CompletableFuture<GbAlarmSubscription> subscribeAlarms(String deviceId, String targetId,
                                                                               GbAlarmSubscriptionRequest request) {
        requireOpen();
        Objects.requireNonNull(request, "Alarm subscription request");
        if (deviceId == null || !deviceId.matches("[0-9]{20}")
                || targetId == null || !targetId.matches("(?:[0-9]{10}|[0-9]{20})"))
            throw new IllegalArgumentException("Alarm owner must contain 20 digits and target 10 or 20 digits");
        if (alarmDispatcher == null) throw new IllegalStateException("Alarm subscription requires an Alarm listener");
        expireDevices();
        Binding binding = bindings.get(deviceId);
        if (binding == null) throw new IllegalStateException("Device is not registered and online");
        return alarmSubscriptions.subscribe(deviceId, targetId, request,
                new GbPlayback.Peer(binding.endpoint.host, binding.endpoint.port, binding.endpoint.transport));
    }
    /** Subscribes to the whole catalog of an online registered IPC/NVR. Requires a Catalog listener.
     * Completion means SIP acceptance; notifications may arrive earlier. The server renews automatically.
     */
    public synchronized CompletableFuture<GbCatalogSubscription> subscribeCatalog(String deviceId,
                                                                                  GbCatalogSubscriptionRequest request) {
        requireOpen();
        Objects.requireNonNull(request, "Catalog subscription request");
        if (deviceId == null || !deviceId.matches("[0-9]{20}"))
            throw new IllegalArgumentException("Catalog owner must contain 20 digits");
        if (catalogDispatcher == null) throw new IllegalStateException("Catalog subscription requires a Catalog listener");
        expireDevices();
        Binding binding = bindings.get(deviceId);
        if (binding == null) throw new IllegalStateException("Device is not registered and online");
        return catalogSubscriptions.subscribe(deviceId, deviceId, request,
                new GbPlayback.Peer(binding.endpoint.host, binding.endpoint.port, binding.endpoint.transport));
    }
    /** Subscribes to MobilePosition reports from an online registered IPC/NVR. Requires a listener. */
    public synchronized CompletableFuture<GbMobilePositionSubscription> subscribeMobilePosition(
            String deviceId, GbMobilePositionSubscriptionRequest request) {
        requireOpen();
        Objects.requireNonNull(request, "MobilePosition subscription request");
        if (deviceId == null || !deviceId.matches("[0-9]{20}"))
            throw new IllegalArgumentException("MobilePosition owner must contain 20 digits");
        if (mobilePositionDispatcher == null)
            throw new IllegalStateException("MobilePosition subscription requires a MobilePosition listener");
        expireDevices();
        Binding binding = bindings.get(deviceId);
        if (binding == null) throw new IllegalStateException("Device is not registered and online");
        return mobilePositionSubscriptions.subscribe(deviceId, deviceId, request,
                new GbPlayback.Peer(binding.endpoint.host, binding.endpoint.port, binding.endpoint.transport));
    }
    @Override public void processRequest(RequestEvent event) {
        try {
            synchronized (this) {
                if (closed) return;
                Request request = event.getRequest();
                SipProvider provider = (SipProvider) event.getSource();
                if (!(event instanceof RequestEventExt ext) || ext.getRemoteIpAddress() == null) return;
                Endpoint endpoint = new Endpoint(ext.getRemoteIpAddress(), ext.getRemotePort(),
                        provider.getListeningPoint().getTransport().toUpperCase(Locale.ROOT));
                if (Request.ACK.equals(request.getMethod())) return;
                ServerTransaction transaction = event.getServerTransaction();
                if (transaction == null) transaction = provider.getNewServerTransaction(request);
                try {
                    if (request.getRawContent() != null && request.getRawContent().length > options.maxMessageBytes()) {
                        reply(transaction, request, 413); return;
                    }
                    if (!(request.getRequestURI() instanceof SipURI target) || !options.serverId().equals(target.getUser())
                            || !isLocalTarget(target.getHost())) {
                        reply(transaction, request, 404); return;
                    }
                    if (Request.BYE.equals(request.getMethod())) {
                        playback.incomingBye(request, transaction, new GbPlayback.Peer(endpoint.host, endpoint.port, endpoint.transport));
                        return;
                    }
                    if (Request.NOTIFY.equals(request.getMethod())) {
                        expireDevices();
                        var peer = new GbPlayback.Peer(endpoint.host, endpoint.port, endpoint.transport);
                        if (alarmSubscriptions.owns(request)) alarmSubscriptions.notify(request, transaction, peer);
                        else if (catalogSubscriptions.owns(request)) catalogSubscriptions.notify(request, transaction, peer);
                        else if (mobilePositionSubscriptions.owns(request))
                            mobilePositionSubscriptions.notify(request, transaction, peer);
                        else reply(transaction, request, 481);
                        return;
                    }
                    FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
                    if (from == null || !(from.getAddress().getURI() instanceof SipURI identity)
                            || identity.getUser() == null || !identity.getUser().matches("[0-9]{20}")) {
                        reply(transaction, request, 400); return;
                    }
                    if (Request.REGISTER.equals(request.getMethod())) register(transaction, request, identity.getUser(), endpoint);
                    else if (Request.MESSAGE.equals(request.getMethod())) message(transaction, request, identity.getUser(), endpoint);
                    else reply(transaction, request, 405);
                } catch (IllegalArgumentException e) { reply(transaction, request, 400); }
                catch (IllegalStateException e) { reply(transaction, request, 503); }
            }
        } catch (TransactionAlreadyExistsException ignored) {
            // SIP stack owns retransmitted server transactions and cached responses.
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "GB SIP request processing failed: {0}", e.getClass().getSimpleName());
        }
    }
    private void register(ServerTransaction tx, Request request, String device, Endpoint endpoint) throws Exception {
        AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        if (authenticator.requiresChallenge(auth)) {
            Response response = response(request, 401);
            WWWAuthenticateHeader challenge = headers.createWWWAuthenticateHeader("Digest");
            challenge.setRealm(options.realm()); challenge.setAlgorithm("MD5"); challenge.setQop("auth");
            challenge.setNonce(authenticator.issue(device, endpoint.toString()));
            if (auth != null) challenge.setStale(true);
            response.addHeader(challenge); tx.sendResponse(response); return;
        }
        if (!authenticator.authenticate(device, endpoint.toString(), request.getMethod(), request.getRequestURI().toString(), auth)) {
            reply(tx, request, 403); return;
        }
        ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        ExpiresHeader expires = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        int seconds = contact != null && contact.getExpires() >= 0 ? contact.getExpires()
                : expires == null ? (int) options.registrationTtl().toSeconds() : expires.getExpires();
        if (contact == null || seconds < 0 || (contact.isWildCard() && seconds != 0)) {
            reply(tx, request, 400); return;
        }
        expireDevices();
        if (closed) return;
        String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
        long cseq = ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber();
        Binding old = bindings.get(device);
        if (old != null && old.callId.equals(callId) && cseq <= old.cseq) { reply(tx, request, 400); return; }
        Response response = response(request, 200);
        seconds = Math.min(seconds, (int) options.registrationTtl().toSeconds());
        response.addHeader(headers.createExpiresHeader(seconds));
        ContactHeader accepted = (ContactHeader) contact.clone();
        if (!accepted.isWildCard()) accepted.setExpires(seconds);
        response.addHeader(accepted);
        if (seconds == 0) {
            bindings.remove(device); failDeviceQueries(device, "Device unregistered");
        } else {
            if (old == null && bindings.size() >= options.maxDevices()) { reply(tx, request, 503); return; }
            Instant now = Instant.now();
            Header version = request.getHeader("X-GB-Ver");
            String protocol = version == null ? "unknown" : version.toString().substring(version.toString().indexOf(':') + 1).trim();
            if (!List.of("1.0", "1.1", "2.0", "3.0", "unknown").contains(protocol)) protocol = "unknown";
            if (old != null && !old.endpoint.equals(endpoint)) {
                // Futures may synchronously reenter public APIs. Hide the old endpoint before retiring its work.
                bindings.remove(device);
                failDeviceQueries(device, "Device signaling endpoint changed");
                if (closed) return;
            }
            bindings.put(device, new Binding(new RegisteredDevice(device, protocol, endpoint.transport,
                    now.plusSeconds(seconds), now), endpoint, callId, cseq));
        }
        if (!closed) tx.sendResponse(response);
    }
    private void message(ServerTransaction tx, Request request, String device, Endpoint endpoint) throws Exception {
        expireDevices();
        // In-dialog notifications can use a channel in From; authenticate the dialog before owner lookup.
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        if (to != null && to.getTag() != null && playback.incomingMessage(request, tx,
                new GbPlayback.Peer(endpoint.host, endpoint.port, endpoint.transport))) return;
        Binding binding = bindings.get(device);
        if (binding == null || !binding.endpoint.equals(endpoint)) { reply(tx, request, 403); return; }
        ContentTypeHeader contentType = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
        if (contentType == null || !"application".equalsIgnoreCase(contentType.getContentType())
                || !"MANSCDP+xml".equalsIgnoreCase(contentType.getContentSubType())) { reply(tx, request, 415); return; }
        GbXml.Message content = GbXml.parse(request.getRawContent(), options.maxMessageBytes(),
                options.maxCatalogItems(), options.maxRecordItems(), options.maxMobilePositionItems());
        if ("Alarm".equals(content.command()) && "Notify".equals(content.kind())) {
            acceptAlarm(tx, request, device, binding, content);
            return;
        }
        if ("Keepalive".equals(content.command()) && "Notify".equals(content.kind())) {
            if (!device.equals(content.deviceId())) { reply(tx, request, 403); return; }
            if (!"OK".equals(content.status())) { reply(tx, request, 400); return; }
            bindings.put(device, new Binding(new RegisteredDevice(device, binding.device.protocolVersion(), endpoint.transport,
                    binding.device.registeredUntil(), Instant.now()), endpoint, binding.callId, binding.cseq));
            reply(tx, request, 200); return;
        }
        if (!"Response".equals(content.kind())) { reply(tx, request, 400); return; }
        PendingRequest<?> query = pending.get(content.sn());
        if (query == null) {
            // Optional control and late channel query responses only acknowledge receipt and never mutate state.
            reply(tx, request, device.equals(content.deviceId()) || "DeviceControl".equals(content.command())
                || "RecordInfo".equals(content.command()) || "PresetQuery".equals(content.command())
                || "HomePositionQuery".equals(content.command()) ? 200 : 403);
            return;
        }
        if (!query.device.equals(device) || !query.target.equals(content.deviceId()) || !query.endpoint.equals(endpoint)) {
            reply(tx, request, 403); return;
        }
        if (!query.command.equals(content.command())) { reply(tx, request, 400); return; }
        if (query.result.isDone()) { pending.remove(content.sn()); terminate(query); reply(tx, request, 200); return; }
        if (System.nanoTime() >= query.deadline) {
            fail(content.sn(), new TimeoutException(query.command + " request timed out")); reply(tx, request, 200); return;
        }
        if (query.receiptOnly()) { reply(tx, request, 200); return; }
        if ("ERROR".equals(content.result())) {
            reply(tx, request, 200);
            fail(content.sn(), new IllegalStateException(query.command + " returned ERROR"));
            return;
        }
        try {
            query.accept(content, request.getRawContent().length, options);
        } catch (IllegalArgumentException e) {
            fail(content.sn(), new IllegalArgumentException("Inconsistent or oversized " + query.command + " response"));
            reply(tx, request, 400); return;
        }
        reply(tx, request, 200);
        if (query.value != null) {
            pending.remove(content.sn()); terminate(query);
            query.complete();
        }
    }
    private void acceptAlarm(ServerTransaction tx, Request request, String sourceDevice, Binding binding,
                             GbXml.Message content) throws Exception {
        removeCompletedPending();
        if (pending.size() >= options.maxPendingQueries()) { reply(tx, request, 503); return; }
        acceptAlarm(tx, request, sourceDevice, content.alarm(), () -> {
            try { sendAlarmResponse(sourceDevice, binding, content.alarm()); }
            catch (RuntimeException sendFailure) {
                LOG.log(System.Logger.Level.WARNING, "GB Alarm response send failed: {0}", sendFailure.getClass().getSimpleName());
            }
        }, null);
    }
    private void acceptSubscriptionAlarm(ServerTransaction tx, Request request, String sourceDevice, String callId, GbAlarm alarm,
                                         Runnable accepted, BooleanSupplier eligible) throws Exception {
        acceptAlarm(tx, request, sourceDevice, alarm, accepted, eligible);
    }
    private void acceptAlarm(ServerTransaction tx, Request request, String sourceDevice, GbAlarm alarm,
                             Runnable accepted, BooleanSupplier eligible) throws Exception {
        if (alarmDispatcher == null || pendingAlarms >= options.maxPendingAlarms()) {
            reply(tx, request, 503); return;
        }
        pendingAlarms++;
        GbAlarmEvent event = new GbAlarmEvent(sourceDevice, Instant.now(), alarm);
        try { reply(tx, request, 200); }
        catch (Exception receiptFailure) { releaseAlarm(); throw receiptFailure; }
        accepted.run();
        // A completion continuation can synchronously close the server while committing a terminal NOTIFY.
        if (closed || eligible != null && !eligible.getAsBoolean()) { releaseAlarm(); return; }
        if (eligible != null) alarmEligibility.put(event, eligible);
        if (!alarmDispatcher.dispatch(event, this::releaseAlarm)) {
            alarmEligibility.remove(event); releaseAlarm();
        }
    }
    private void acceptSubscriptionCatalog(ServerTransaction tx, Request request, String sourceDevice, String callId,
                                           GbCatalogNotification notification, Runnable accepted,
                                           BooleanSupplier eligible) throws Exception {
        if (catalogDispatcher == null || pendingCatalogNotifications >= options.maxPendingCatalogNotifications()) {
            reply(tx, request, 503); return;
        }
        pendingCatalogNotifications++;
        GbCatalogEvent event = new GbCatalogEvent(sourceDevice, callId, Instant.now(), notification);
        try { reply(tx, request, 200); }
        catch (Exception receiptFailure) { releaseCatalog(); throw receiptFailure; }
        accepted.run();
        // Completion callbacks may synchronously close or invalidate this subscription.
        if (closed || !eligible.getAsBoolean()) { releaseCatalog(); return; }
        catalogEligibility.put(event, eligible);
        if (!catalogDispatcher.dispatch(event, this::releaseCatalog)) {
            catalogEligibility.remove(event); releaseCatalog();
        }
    }
    private void deliverCatalog(GbCatalogListener listener, GbCatalogEvent event) {
        synchronized (this) {
            BooleanSupplier eligible = catalogEligibility.remove(event);
            if (closed || eligible != null && !eligible.getAsBoolean()) return;
        }
        listener.onCatalog(event);
    }
    private synchronized void releaseCatalog() {
        if (pendingCatalogNotifications > 0) pendingCatalogNotifications--;
    }
    private void acceptSubscriptionMobilePosition(ServerTransaction tx, Request request, String sourceDevice,
                                                  String callId, GbMobilePositionNotification notification,
                                                  Runnable accepted, BooleanSupplier eligible) throws Exception {
        if (mobilePositionDispatcher == null
                || pendingMobilePositionNotifications >= options.maxPendingMobilePositionNotifications()) {
            reply(tx, request, 503); return;
        }
        pendingMobilePositionNotifications++;
        GbMobilePositionEvent event = new GbMobilePositionEvent(sourceDevice, callId, Instant.now(), notification);
        try { reply(tx, request, 200); }
        catch (Exception receiptFailure) { releaseMobilePosition(); throw receiptFailure; }
        accepted.run();
        if (closed || !eligible.getAsBoolean()) { releaseMobilePosition(); return; }
        mobilePositionEligibility.put(event, eligible);
        if (!mobilePositionDispatcher.dispatch(event, this::releaseMobilePosition)) {
            mobilePositionEligibility.remove(event); releaseMobilePosition();
        }
    }
    private void deliverMobilePosition(GbMobilePositionListener listener, GbMobilePositionEvent event) {
        synchronized (this) {
            BooleanSupplier eligible = mobilePositionEligibility.remove(event);
            if (closed || eligible != null && !eligible.getAsBoolean()) return;
        }
        listener.onMobilePosition(event);
    }
    private synchronized void releaseMobilePosition() {
        if (pendingMobilePositionNotifications > 0) pendingMobilePositionNotifications--;
    }
    private void sendAlarmResponse(String owner, Binding binding, GbAlarm alarm) {
        int sn = reserveSn();
        PendingRequest<Void> acknowledgment = new PendingRequest<>(owner, owner, "Alarm", binding.endpoint,
                System.nanoTime() + options.queryTimeout().toNanos(), null, true);
        pending.put(sn, acknowledgment);
        try {
            sendPendingMessage(acknowledgment, sn, GbXml.alarmResponse(alarm.deviceId(), alarm.sn()));
        } catch (Exception failure) {
            pending.remove(sn);
            terminate(acknowledgment);
            throw new IllegalStateException("Cannot send Alarm response", failure);
        }
    }
    private void sendPendingMessage(PendingRequest<?> pendingRequest, int sn, byte[] body) throws Exception {
        Endpoint endpoint = pendingRequest.endpoint;
        SipProvider provider = providers.get(endpoint.transport);
        SipURI uri = addresses.createSipURI(pendingRequest.target, endpoint.host);
        uri.setPort(endpoint.port);
        uri.setTransportParam(endpoint.transport.toLowerCase(Locale.ROOT));
        var from = headers.createFromHeader(addresses.createAddress("sip:" + options.serverId() + "@" + options.realm()), tag());
        var to = headers.createToHeader(addresses.createAddress("sip:" + pendingRequest.target + "@" + options.realm()), null);
        ViaHeader via = headers.createViaHeader(options.advertisedAddress(), options.port(), endpoint.transport, null);
        via.setRPort();
        Request outgoing = messages.createRequest(uri, Request.MESSAGE, provider.getNewCallId(),
                headers.createCSeqHeader(sn, Request.MESSAGE), from, to, new ArrayList<>(List.of(via)),
                headers.createMaxForwardsHeader(70));
        outgoing.addHeader(headers.createHeader("X-GB-Ver", "3.0"));
        outgoing.setContent(body, headers.createContentTypeHeader("Application", "MANSCDP+xml"));
        ClientTransaction transaction = provider.getNewClientTransaction(outgoing);
        pendingRequest.transaction = transaction;
        transaction.setApplicationData(sn);
        responseGuard.expect(outgoing, endpoint.host, endpoint.port, endpoint.transport);
        transaction.sendRequest();
    }
    private int reserveSn() {
        int sn;
        do { nextSn = nextSn == Integer.MAX_VALUE ? 1 : nextSn + 1; sn = nextSn; } while (pending.containsKey(sn));
        return sn;
    }
    private void removeCompletedPending() {
        for (var entry : List.copyOf(pending.entrySet())) {
            if (entry.getValue().result.isDone()) {
                pending.remove(entry.getKey());
                terminate(entry.getValue());
            }
        }
    }
    private void deliverAlarm(GbAlarmListener listener, GbAlarmEvent event) {
        synchronized (this) {
            BooleanSupplier eligible = alarmEligibility.remove(event);
            if (closed || eligible != null && !eligible.getAsBoolean()) return;
        }
        listener.onAlarm(event);
    }
    private synchronized void releaseAlarm() {
        if (pendingAlarms > 0) pendingAlarms--;
    }
    @Override public synchronized void processResponse(ResponseEvent event) {
        if (closed || alarmSubscriptions.response(event) || catalogSubscriptions.response(event)
                || mobilePositionSubscriptions.response(event) || playback.response(event)) return;
        if (event.getClientTransaction() == null
                || !(event.getClientTransaction().getApplicationData() instanceof Integer sn)) return;
        PendingRequest<?> query = pending.get(sn);
        if (query == null || !(event instanceof ResponseEventExt ext)
                || !query.endpoint.host.equals(ext.getRemoteIpAddress()) || query.endpoint.port != ext.getRemotePort()) return;
        if (query.result.isDone()) { pending.remove(sn); terminate(query); return; }
        if (System.nanoTime() >= query.deadline) {
            fail(sn, new TimeoutException(query.command + " request timed out")); return;
        }
        int status = event.getResponse().getStatusCode();
        if (status >= 300) {
            if (query.alarmResponse) logAlarmResponseFailure("rejected");
            fail(sn, new IllegalStateException(query.command + " SIP request rejected: " + status));
        }
        else if (status >= 200 && query.receiptOnly()) {
            pending.remove(sn); terminate(query); query.complete();
        }
    }
    @Override public synchronized void processTimeout(TimeoutEvent event) {
        alarmSubscriptions.timeout(event); catalogSubscriptions.timeout(event); mobilePositionSubscriptions.timeout(event);
        playback.timeout(event);
        if (!event.isServerTransaction() && event.getClientTransaction() != null
                && event.getClientTransaction().getApplicationData() instanceof Integer sn) {
            PendingRequest<?> query = pending.get(sn);
            if (query != null && query.alarmResponse) logAlarmResponseFailure("timed out");
            fail(sn, new TimeoutException("MESSAGE SIP transaction timed out"));
        }
    }
    @Override public void processIOException(IOExceptionEvent event) {
        // The bounded query/heartbeat timers remain authoritative; an old connection's event must
        // not remove a newer registration at the same endpoint.
    }
    @Override public void processTransactionTerminated(TransactionTerminatedEvent event) { }
    @Override public void processDialogTerminated(DialogTerminatedEvent event) { }
    private boolean isLocalTarget(String host) {
        if (options.realm().equalsIgnoreCase(host)) return true;
        String numeric = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (!numeric.matches("[0-9a-fA-F:.]+")
                || (!numeric.contains(":") && !numeric.matches("[0-9]+(?:\\.[0-9]+){3}"))) return false;
        try {
            var target = java.net.InetAddress.getByName(numeric);
            return target.equals(java.net.InetAddress.getByName(options.advertisedAddress()))
                    || target.equals(java.net.InetAddress.getByName(options.bindAddress()));
        } catch (java.net.UnknownHostException e) { return false; }
    }
    private Response response(Request request, int status) throws Exception {
        Response response = messages.createResponse(status, request);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        if (to.getTag() == null) to.setTag(tag());
        if (Request.REGISTER.equals(request.getMethod())) response.addHeader(headers.createHeader("X-GB-Ver", "3.0"));
        return response;
    }
    private void reply(ServerTransaction tx, Request request, int status) throws Exception { tx.sendResponse(response(request, status)); }
    private static String tag() { return UUID.randomUUID().toString().replace("-", ""); }
    private void requireOpen() { if (closed) throw new IllegalStateException("GB28181 server is closed"); }
    private synchronized void maintain() {
        if (closed) return;
        try {
            expireDevices(); authenticator.expire(); playback.maintain(); alarmSubscriptions.maintain();
            catalogSubscriptions.maintain(); mobilePositionSubscriptions.maintain();
            long now = System.nanoTime();
            for (var entry : List.copyOf(pending.entrySet())) {
                if (entry.getValue().result.isDone()) { pending.remove(entry.getKey()); terminate(entry.getValue()); }
                else if (now >= entry.getValue().deadline) {
                    if (entry.getValue().alarmResponse) logAlarmResponseFailure("timed out");
                    fail(entry.getKey(), new TimeoutException("MESSAGE request timed out"));
                }
            }
        } catch (RuntimeException e) { LOG.log(System.Logger.Level.WARNING, "GB maintenance failed: {0}", e.getClass().getSimpleName()); }
    }
    private void expireDevices() {
        Instant now = Instant.now();
        for (Binding binding : List.copyOf(bindings.values())) {
            if (!now.isBefore(binding.device.registeredUntil()) || !now.isBefore(binding.device.lastHeartbeat()
                    .plus(options.heartbeatInterval().multipliedBy(options.heartbeatMisses())))) {
                bindings.remove(binding.device.deviceId()); failDeviceQueries(binding.device.deviceId(), "Device is offline");
            }
        }
    }
    private void failDeviceQueries(String device, String reason) {
        alarmSubscriptions.failDevice(device, reason); catalogSubscriptions.failDevice(device, reason);
        mobilePositionSubscriptions.failDevice(device, reason);
        playback.failDevice(device, reason);
        for (var entry : List.copyOf(pending.entrySet())) {
            if (entry.getValue().device.equals(device)) {
                if (entry.getValue().alarmResponse) logAlarmResponseFailure("abandoned after registration change");
                fail(entry.getKey(), new IllegalStateException(reason));
            }
        }
    }
    private void fail(int sn, Exception error) {
        PendingRequest<?> query = pending.remove(sn);
        if (query != null) { terminate(query); query.result.completeExceptionally(error); }
    }
    private void terminate(PendingRequest<?> query) {
        if (query.transaction != null) {
            responseGuard.forgetCall(((CallIdHeader) query.transaction.getRequest().getHeader(CallIdHeader.NAME)).getCallId());
            if (query.transaction.getState() != TransactionState.TERMINATED)
                try { query.transaction.terminate(); } catch (ObjectInUseException ignored) { }
        }
    }
    private void logAlarmResponseFailure(String reason) {
        LOG.log(System.Logger.Level.WARNING, "GB Alarm response {0}", reason);
    }
    /** Idempotently stops listeners, transactions and timers and fails outstanding requests. */
    @Override public void close() {
        SipStackImpl stopping;
        ScheduledExecutorService timer;
        synchronized (this) {
            if (closed) return;
            closed = true;
            stopping = stack; timer = maintenance;
            if (alarmDispatcher != null) alarmDispatcher.close();
            if (catalogDispatcher != null) catalogDispatcher.close();
            pendingCatalogNotifications = 0; catalogEligibility.clear();
            if (mobilePositionDispatcher != null) mobilePositionDispatcher.close();
            pendingMobilePositionNotifications = 0; mobilePositionEligibility.clear();
            pendingAlarms = 0; alarmEligibility.clear();
            alarmSubscriptions.close(); catalogSubscriptions.close(); mobilePositionSubscriptions.close();
            playback.close();
            for (Integer sn : List.copyOf(pending.keySet())) fail(sn, new IllegalStateException("GB28181 server closed"));
            bindings.clear(); authenticator.clear();
            responseGuard.clear();
        }
        if (timer != null) timer.shutdownNow();
        if (stopping != null) stopping.stop();
    }
    private record Endpoint(String host, int port, String transport) { }
    private record Binding(RegisteredDevice device, Endpoint endpoint, String callId, long cseq) { }
    private static final class PendingRequest<T> {
        final String device, target, command;
        final Endpoint endpoint;
        final long deadline;
        final CompletableFuture<T> result = new CompletableFuture<>();
        final Function<GbXml.Message, T> decoder;
        ClientTransaction transaction;
        T value;
        long bytes;
        final boolean alarmResponse;
        PendingRequest(String device, String target, String command, Endpoint endpoint, long deadline,
                       Function<GbXml.Message, T> decoder) {
            this(device, target, command, endpoint, deadline, decoder, false);
        }
        PendingRequest(String device, String target, String command, Endpoint endpoint, long deadline,
                       Function<GbXml.Message, T> decoder, boolean alarmResponse) {
            this.device = device; this.target = target; this.command = command; this.endpoint = endpoint;
            this.deadline = deadline; this.decoder = decoder; this.alarmResponse = alarmResponse;
        }
        boolean receiptOnly() { return decoder == null; }
        void accept(GbXml.Message message, int bodyBytes, GbServerOptions options) {
            bytes += bodyBytes;
            if (bytes > (long) options.maxMessageBytes() * 16) throw new IllegalArgumentException();
            value = decoder.apply(message);
        }
        void complete() { result.complete(value); }
    }
    private static final class RecordAccumulator {
        final Map<RecordKey, RecordItem> records = new LinkedHashMap<>();
        final GbServerOptions options;
        final String channel;
        int total = -1;
        RecordAccumulator(GbServerOptions options, String channel) { this.options = options; this.channel = channel; }
        List<RecordItem> accept(GbXml.Message message) {
            if (message.total() > options.maxRecordItems() || (total != -1 && total != message.total()))
                throw new IllegalArgumentException();
            total = message.total();
            for (RecordItem item : message.records()) {
                if (!channel.equals(item.deviceId())) throw new IllegalArgumentException();
                RecordKey key = new RecordKey(item.deviceId(), item.filePath(), item.startTime(), item.endTime(),
                        item.type(), item.recorderId());
                RecordItem old = records.putIfAbsent(key, item);
                if (old != null && !old.equals(item)) throw new IllegalArgumentException();
            }
            if (records.size() > total) throw new IllegalArgumentException();
            return records.size() == total ? List.copyOf(records.values()) : null;
        }
        private record RecordKey(String deviceId, String filePath, String startTime, String endTime,
                                 String type, String recorderId) { }
    }
    private static final class CatalogAccumulator {
        final Map<String, CatalogItem> items = new LinkedHashMap<>();
        final GbServerOptions options;
        int total = -1;
        CatalogAccumulator(GbServerOptions options) { this.options = options; }
        List<CatalogItem> accept(GbXml.Message message) {
            if (message.total() > options.maxCatalogItems() || (total != -1 && total != message.total()))
                throw new IllegalArgumentException();
            total = message.total();
            for (CatalogItem item : message.items()) {
                CatalogItem old = items.putIfAbsent(item.deviceId(), item);
                if (old != null && !old.equals(item)) throw new IllegalArgumentException();
            }
            if (items.size() > total) throw new IllegalArgumentException();
            return items.size() == total ? List.copyOf(items.values()) : null;
        }
    }
}
