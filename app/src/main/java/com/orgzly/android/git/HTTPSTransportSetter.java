package com.orgzly.android.git;

import androidx.annotation.NonNull;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class HTTPSTransportSetter implements GitTransportSetter {
    private String username;
    private String password;

    public HTTPSTransportSetter(@NonNull String username, @NonNull String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public <C extends TransportCommand<?, ?>> C setTransport(C tc) {
        tc.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
        return tc;
    }
}
