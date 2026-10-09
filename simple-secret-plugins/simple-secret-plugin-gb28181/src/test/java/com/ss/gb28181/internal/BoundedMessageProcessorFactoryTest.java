package com.ss.gb28181.internal;

import gov.nist.javax.sip.stack.DatagramQueuedMessageDispatch;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.UDPMessageProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedMessageProcessorFactoryTest {

    @Test
    void udpProcessorDropsThe257thQueuedDatagram() throws Exception {
        ManagedSipStack stack = stack(0);
        UDPMessageProcessor processor = null;
        try {
            processor = assertInstanceOf(UDPMessageProcessor.class,
                    new BoundedMessageProcessorFactory().createMessageProcessor(
                            stack, InetAddress.getLoopbackAddress(), 0, "UDP"));
            BlockingQueue<DatagramQueuedMessageDispatch> queue = queue(processor);

            assertInstanceOf(ArrayBlockingQueue.class, queue);
            assertTrue(udpEnabled(stack));

            for (int index = 0; index < 256; index++) {
                assertTrue(queue.offer(datagram()), "datagram " + index + " should fit");
            }
            assertFalse(queue.offer(datagram()));
            assertEquals(256, queue.size());
        } finally {
            closeSocket(processor);
            stack.stop();
        }
    }

    @Test
    void refusesToReplaceQueueWhenRiCongestionAuditorIsEnabled() {
        ManagedSipStack stack = stack(1);

        try {
            assertThrows(IllegalStateException.class, () -> new BoundedMessageProcessorFactory()
                    .createMessageProcessor(stack, InetAddress.getLoopbackAddress(), 0, "UDP"));
        } finally {
            stack.stop();
        }
    }

    @Test
    void createsAClosableTcpProcessorWithTheTcpContract() throws Exception {
        ManagedSipStack stack = stack(0);
        MessageProcessor processor = null;
        try {
            processor = new BoundedMessageProcessorFactory().createMessageProcessor(
                    stack, InetAddress.getLoopbackAddress(), 0, "TCP");
            assertTrue("TCP".equalsIgnoreCase(processor.getTransport()));
        } finally {
            if (processor != null) {
                processor.stop();
            }
            stack.stop();
        }
    }

    private static DatagramQueuedMessageDispatch datagram() {
        return new DatagramQueuedMessageDispatch(new DatagramPacket(new byte[0], 0),
                System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private static BlockingQueue<DatagramQueuedMessageDispatch> queue(UDPMessageProcessor processor)
            throws Exception {
        Field field = UDPMessageProcessor.class.getDeclaredField("messageQueue");
        field.setAccessible(true);
        return (BlockingQueue<DatagramQueuedMessageDispatch>) field.get(processor);
    }

    private static boolean udpEnabled(SIPTransactionStack stack) throws Exception {
        Field field = SIPTransactionStack.class.getDeclaredField("udpFlag");
        field.setAccessible(true);
        return field.getBoolean(stack);
    }

    private static void closeSocket(UDPMessageProcessor processor) throws Exception {
        if (processor == null) {
            return;
        }
        Field field = UDPMessageProcessor.class.getDeclaredField("sock");
        field.setAccessible(true);
        ((DatagramSocket) field.get(processor)).close();
    }

    private static ManagedSipStack stack(int congestionTimeout) {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "bounded-processor-test-" + UUID.randomUUID());
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        properties.setProperty("gov.nist.javax.sip.CONGESTION_CONTROL_TIMEOUT",
                Integer.toString(congestionTimeout));
        try {
            return new ManagedSipStack(properties);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create test SIP stack", exception);
        }
    }
}
