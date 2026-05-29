package com.orgzly.android.git;

import org.eclipse.jgit.api.TransportCommand;

public interface GitTransportSetter {
    <C extends TransportCommand<?, ?>> C setTransport(C tc);
}
