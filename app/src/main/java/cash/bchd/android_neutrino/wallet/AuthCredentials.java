package cash.bchd.android_neutrino.wallet;

import java.util.concurrent.Executor;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

/**
 * AuthCredentials adds the wallet's gRPC authentication token to each call.
 */
public class AuthCredentials extends CallCredentials {
    private static final Metadata.Key<String> TOKEN_KEY =
            Metadata.Key.of("AuthenticationToken", Metadata.ASCII_STRING_MARSHALLER);

    private final String token;

    public AuthCredentials(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        try {
            Metadata headers = new Metadata();
            headers.put(TOKEN_KEY, token);
            applier.apply(headers);
        } catch (Throwable e) {
            applier.fail(Status.UNAUTHENTICATED.withCause(e));
        }
    }
}
