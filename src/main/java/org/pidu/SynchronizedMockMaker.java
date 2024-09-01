package org.pidu;

import com.google.common.collect.MapMaker;
import org.mockito.MockedConstruction;
import org.mockito.internal.creation.bytebuddy.InlineByteBuddyMockMaker;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationContainer;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class SynchronizedMockMaker implements MockMaker {
    // we want to use weak hashmap to allow GC of objects from old contexts when using some DI frameworks
    private static final Map<Object, ReentrantReadWriteLock> LOCKS = new MapMaker()
            .concurrencyLevel(4)
            .weakKeys()
            .makeMap();
    private final MockMaker delegate = new InlineByteBuddyMockMaker();

    public static ReentrantReadWriteLock getOrCreateLock(Object object) {
        return LOCKS.computeIfAbsent(object, k -> new ReentrantReadWriteLock(true));
    }

    @Override
    public <T> T createMock(MockCreationSettings<T> mockCreationSettings, MockHandler mockHandler) {
        return delegate.createMock(mockCreationSettings, new SynchronizedMockHandler(mockHandler));
    }

    @Override
    public <T> Optional<T> createSpy(MockCreationSettings<T> settings, MockHandler handler, T instance) {
        return delegate.createSpy(settings, new SynchronizedMockHandler(handler), instance);
    }

    @Override
    public <T> StaticMockControl<T> createStaticMock(Class<T> type, MockCreationSettings<T> settings, MockHandler handler) {
        return delegate.createStaticMock(type, settings, new SynchronizedMockHandler(handler));
    }

    @Override
    public <T> ConstructionMockControl<T> createConstructionMock(Class<T> type, Function<MockedConstruction.Context, MockCreationSettings<T>> settingsFactory, Function<MockedConstruction.Context, MockHandler<T>> handlerFactory, MockedConstruction.MockInitializer<T> mockInitializer) {
        return delegate.createConstructionMock(type,
                settingsFactory,
                ctx -> new SynchronizedMockHandler(handlerFactory.apply(ctx)),
                mockInitializer);
    }

    @Override
    public MockHandler getHandler(Object o) {
        return delegate.getHandler(o);
    }

    @Override
    public void clearAllCaches() {
        delegate.clearAllCaches();
    }

    @Override
    public void resetMock(Object o, MockHandler mockHandler, MockCreationSettings mockCreationSettings) {
        delegate.resetMock(o, mockHandler, mockCreationSettings);
    }

    @Override
    public TypeMockability isTypeMockable(Class<?> aClass) {
        return delegate.isTypeMockable(aClass);
    }

    class SynchronizedMockHandler implements MockHandler {
        private final MockHandler delegate;

        SynchronizedMockHandler(MockHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object handle(Invocation invocation) throws Throwable {
            Object mock = invocation.getMock();
            ReentrantReadWriteLock.ReadLock readLock = getOrCreateLock(mock).readLock();
            if (readLock.tryLock(15, TimeUnit.SECONDS)) {
                try {
                    return delegate.handle(invocation);
                } finally {
                    readLock.unlock();
                }
            }
            throw new IllegalStateException("Couldn't lock %s".formatted(mock.getClass().getName()));
        }

        @Override
        public MockCreationSettings getMockSettings() {
            return delegate.getMockSettings();
        }

        @Override
        public InvocationContainer getInvocationContainer() {
            return delegate.getInvocationContainer();
        }
    }
}
