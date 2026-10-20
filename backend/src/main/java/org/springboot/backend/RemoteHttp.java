package org.springboot.backend;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounded body subscription: no redirect following, no unbounded buffering, hard end-to-end timeout. */
@Component
class RemoteHttp {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    record Response(int status, byte[] body) { }

    Response send(HttpRequest request, int limit) {
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> new LimitedBody(limit));
        try {
            HttpResponse<byte[]> response = future.get(35, TimeUnit.SECONDS);
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ApiException(502, "remote_error", "Remote request interrupted");
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof ApiException api) throw api;
            throw new ApiException(502, "remote_error", "Remote request failed or timed out");
        }
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > limit) {
                    subscription.cancel();
                    body.completeExceptionally(ZipIngestor.tooLarge("Remote response exceeds its size limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
