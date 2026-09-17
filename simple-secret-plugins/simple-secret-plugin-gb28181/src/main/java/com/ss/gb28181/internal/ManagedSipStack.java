package com.ss.gb28181.internal;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.MessageProcessor;
import javax.sip.PeerUnavailableException;
import java.util.Properties;

/** Ensures one failed listener cannot prevent the remaining stack resources from closing. */
public final class ManagedSipStack extends SipStackImpl {
    public ManagedSipStack(Properties properties) throws PeerUnavailableException { super(properties); }

    @Override public void stop() {
        RuntimeException failure = null;
        // RI 1.3.0-91 adds a TCP processor before binding its socket. If binding fails,
        // TCPMessageProcessor.stop dereferences a null socket and aborts ordinary stack.stop.
        // removeMessageProcessor removes the entry before invoking stop, so process each
        // independently, then let the stack close connections, timers and event scanner.
        for (MessageProcessor processor : getMessageProcessors()) {
            try { removeMessageProcessor(processor); }
            catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        try { super.stop(); }
        catch (RuntimeException e) {
            if (failure == null) failure = e; else failure.addSuppressed(e);
        }
        if (failure != null) throw new IllegalStateException("SIP resource cleanup encountered an error", failure);
    }
}
