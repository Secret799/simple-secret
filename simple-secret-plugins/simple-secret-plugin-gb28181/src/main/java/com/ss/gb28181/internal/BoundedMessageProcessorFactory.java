package com.ss.gb28181.internal;

import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MessageProcessorFactory;
import gov.nist.javax.sip.stack.OIOMessageProcessorFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.UDPMessageProcessor;

import javax.sip.ListeningPoint;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;

/** Creates the RI processors with a bounded queue in front of UDP parsing workers. */
public final class BoundedMessageProcessorFactory implements MessageProcessorFactory {

    private static final int UDP_QUEUE_CAPACITY = 256;

    private final OIOMessageProcessorFactory delegate = new OIOMessageProcessorFactory();
    private final int udpQueueCapacity;

    public BoundedMessageProcessorFactory() {
        this(UDP_QUEUE_CAPACITY);
    }

    BoundedMessageProcessorFactory(int udpQueueCapacity) {
        if (udpQueueCapacity <= 0) {
            throw new IllegalArgumentException("UDP queue capacity must be positive");
        }
        this.udpQueueCapacity = udpQueueCapacity;
    }

    @Override
    public MessageProcessor createMessageProcessor(SIPTransactionStack sipStack, InetAddress ipAddress,
                                                   int port, String transport) throws IOException {
        if (!ListeningPoint.UDP.equalsIgnoreCase(transport)) {
            if (ListeningPoint.TCP.equalsIgnoreCase(transport)) {
                return new DirectTcpMessageProcessor(ipAddress, sipStack, port);
            }
            return delegate.createMessageProcessor(sipStack, ipAddress, port, transport);
        }
        if (sipStack.getStackCongestionControlTimeout() > 0) {
            throw new IllegalStateException(
                    "gov.nist.javax.sip.CONGESTION_CONTROL_TIMEOUT must be 0 for the bounded UDP queue");
        }
        UDPMessageProcessor processor = (UDPMessageProcessor) delegate.createMessageProcessor(
                sipStack, ipAddress, port, transport);
        try {
            // RI 1.3.0-91 exposes no queue hook. OIO creation is retained because it also
            // sets the package-private udpFlag used to send replies from the listening socket.
            Field queue = UDPMessageProcessor.class.getDeclaredField("messageQueue");
            queue.setAccessible(true);
            queue.set(processor, new ArrayBlockingQueue<>(udpQueueCapacity));
            return processor;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            closeBeforeStart(processor);
            throw new IOException("JAIN-SIP RI UDP queue adaptation is incompatible", exception);
        }
    }

    private static void closeBeforeStart(UDPMessageProcessor processor) {
        try {
            processor.stop();
        } catch (RuntimeException ignored) {
            // RI closes the socket before touching its not-yet-initialized channel list.
        }
    }
}
