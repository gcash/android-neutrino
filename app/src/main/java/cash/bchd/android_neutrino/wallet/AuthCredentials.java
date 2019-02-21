package cash.bchd.android_neutrino.wallet;

import io.grpc.*;

import java.util.concurrent.Executor;

import static io.grpc.CallCredentials.ATTR_SECURITY_LEVEL;

public class AuthCredentials implements CallCredentials {
    private final String token;

    public AuthCredentials(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(MethodDescriptor<?, ?> methodDescriptor, Attributes attributes, Executor executor, MetadataApplier metadataApplier) {
        String authority = attributes.get(ATTR_AUTHORITY);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Metadata headers = new Metadata();
                    Metadata.Key<String> key = Metadata.Key.of("AuthenticationToken", Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(key, token);
                    metadataApplier.apply(headers);
                } catch (Throwable e) {
                    metadataApplier.fail(Status.UNAUTHENTICATED.withCause(e));
                }
            }
        });
    }

    @Override public void thisUsesUnstableApi() {
    }
}