package eu.toolchain.async;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Callable;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;

public class DelayedCollectCoordinatorTest {
    private AsyncCaller caller;
    private StreamCollector<Object, Object> collector;
    private TinySemaphore mutex;
    private ResolvableFuture<Object> future;
    private Callable<AsyncFuture<Object>> callable;
    private Callable<AsyncFuture<Object>> callable2;
    private AsyncFuture<Object> f;
    private AsyncFuture<Object> f2;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        caller = mock(AsyncCaller.class);
        collector = mock(StreamCollector.class);
        mutex = mock(TinySemaphore.class);
        future = mock(ResolvableFuture.class);
        callable = mock(Callable.class);
        callable2 = mock(Callable.class);

        f = mock(AsyncFuture.class);
        f2 = mock(AsyncFuture.class);

        when(callable.call()).thenReturn(f);
        when(callable2.call()).thenReturn(f2);
    }

    @Test
    public void testCallFutureDoneMethods() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of();

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object result = new Object();
        final Throwable cause = new Throwable();

        coordinator.cancelled();
        coordinator.resolved(result);
        coordinator.failed(cause);

        verify(mutex, times(3)).release();
        verify(caller).cancelStreamCollector(collector);
        verify(caller).resolveStreamCollector(collector, result);
        verify(caller).failStreamCollector(collector, cause);
    }

    @Test
    public void testEmptyCallables() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of();

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object reference = new Object();

        when(collector.end(0, 0, 0)).thenReturn(reference);

        coordinator.run();

        verify(f, never()).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(0, 0, 0);
        verify(mutex, never()).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future).resolve(reference);
        verify(future, never()).fail(any(Throwable.class));
        verify(future, never()).cancel();

        verify(caller, never()).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testInterrupted() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable);

        final InterruptedException e = new InterruptedException();

        doThrow(e).when(mutex).acquire();

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        coordinator.run();

        verify(f, never()).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector, never()).end(any(Integer.class), any(Integer.class), any(Integer.class));

        verify(future).on(any(FutureCancelled.class));
        verify(future, never()).resolve(any());
        verify(future).fail(e);
        verify(future, never()).cancel();

        verify(caller, never()).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testInterruptedOnCancelWait() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final InterruptedException e = new InterruptedException();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                Object[] args = invocation.getArguments();
                final FutureCancelled cancel = (FutureCancelled) args[0];
                cancel.cancelled();
                return null;
            }
        }).when(future).on(any(FutureCancelled.class));

        doThrow(e).when(mutex).acquire();

        final Object reference = new Object();

        coordinator.run();

        verify(f, never()).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector, never()).end(any(Integer.class), any(Integer.class), any(Integer.class));
        verify(mutex, times(1)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future).fail(e);
        verify(future, never()).resolve(any());
        verify(future, never()).cancel();

        verify(caller, times(1)).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testCancelled() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable, callable);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        doNothing().when(mutex).acquire();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                Object[] args = invocation.getArguments();
                final FutureCancelled cancel = (FutureCancelled) args[0];
                cancel.cancelled();
                return null;
            }
        }).when(future).on(any(FutureCancelled.class));

        coordinator.run();

        verify(f, never()).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(0, 0, 2);
        verify(mutex, times(2)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future, never()).fail(any(Throwable.class));
        verify(future).resolve(any());
        verify(future, never()).cancel();

        verify(caller, times(2)).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testSuccessful() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object reference = new Object();

        when(collector.end(1, 0, 0)).thenReturn(reference);
        when(callable.call()).thenReturn(f);

        coordinator.run();

        verify(f).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(1, 0, 0);
        verify(mutex, times(1)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future, never()).fail(any(Throwable.class));
        verify(future).resolve(reference);
        verify(future, never()).cancel();

        verify(caller, never()).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testFailSecond() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable, callable2);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object reference = new Object();

        final Exception e = new Exception();

        when(collector.end(1, 1, 0)).thenReturn(reference);
        when(callable.call()).thenReturn(f);
        when(callable2.call()).thenThrow(e);

        coordinator.run();

        verify(f).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(1, 1, 0);
        verify(mutex, times(2)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future, never()).fail(any(Throwable.class));
        verify(future).resolve(reference);
        verify(future, never()).cancel();

        verify(caller, never()).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, times(1)).failStreamCollector(eq(collector), any(Throwable.class));
    }

    /**
     * If the first fails, should cancel the second.
     */
    @Test
    public void testFailFirst() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable, callable2);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object reference = new Object();

        final Exception e = new Exception();

        when(collector.end(0, 1, 1)).thenReturn(reference);
        when(callable2.call()).thenReturn(f);
        when(callable.call()).thenThrow(e);

        coordinator.run();

        verify(f, never()).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(0, 1, 1);
        verify(mutex, times(2)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future, never()).fail(any(Throwable.class));
        verify(future).resolve(reference);
        verify(future, never()).cancel();

        verify(caller, times(1)).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, times(1)).failStreamCollector(eq(collector), any(Throwable.class));
    }

    @Test
    public void testCollectodEndThrows() throws Exception {
        final List<Callable<AsyncFuture<Object>>> callables = ImmutableList.of(callable);

        final DelayedCollectCoordinator<Object, Object> coordinator = new DelayedCollectCoordinator<Object, Object>(
                caller, callables, collector, mutex, future);

        final Object reference = new Object();
        final Exception e = new Exception();

        when(collector.end(1, 0, 0)).thenThrow(e);
        when(callable.call()).thenReturn(f);

        coordinator.run();

        verify(f).on(coordinator);
        verify(f2, never()).on(coordinator);

        verify(collector).end(1, 0, 0);
        verify(mutex, times(1)).acquire();

        verify(future).on(any(FutureCancelled.class));
        verify(future).fail(any(Throwable.class));
        verify(future, never()).resolve(reference);
        verify(future, never()).cancel();

        verify(caller, never()).cancelStreamCollector(eq(collector));
        verify(caller, never()).resolveStreamCollector(eq(collector), any(Object.class));
        verify(caller, never()).failStreamCollector(eq(collector), any(Throwable.class));
    }
}