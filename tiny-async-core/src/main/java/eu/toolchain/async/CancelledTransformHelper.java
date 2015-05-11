package eu.toolchain.async;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CancelledTransformHelper<T> implements FutureDone<T> {
    private final LazyTransform<Void, ? extends T> transform;
    private final ResolvableFuture<T> target;

    @Override
    public void failed(Throwable e) throws Exception {
        target.fail(e);
    }

    @Override
    public void resolved(T result) throws Exception {
        target.resolve(result);
    }

    @Override
    public void cancelled() throws Exception {
        final AsyncFuture<? extends T> future;

        try {
            future = transform.transform(null);
        } catch (Exception e) {
            target.fail(e);
            return;
        }

        future.on(new FutureDone<T>() {
            @Override
            public void failed(Throwable e) throws Exception {
                target.fail(e);
            }

            @Override
            public void resolved(T result) throws Exception {
                target.resolve(result);
            }

            @Override
            public void cancelled() throws Exception {
                target.cancel();
            }
        });
    }
}