package org.pidu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MockitoSynchronizer {
    private static final Duration LOCKS_TIMEOUT = Duration.ofSeconds(10);
    private static final int SINGLE_LOCKING_TIMEOUT_MILLIS = 2;

    public static void runSafely(Runnable runnable, Object... mocks) {
        List<ReentrantReadWriteLock.WriteLock> writeLocks = Arrays.stream(mocks)
                .map(SynchronizedMockMaker::getOrCreateLock)
                .map(ReentrantReadWriteLock::writeLock)
                .toList();
        boolean locked = tryLock(writeLocks);
        if (!locked) {
            throw new IllegalStateException("Couldn't acquire locks for objects: %s in time: %s".formatted(
                    Arrays.stream(mocks).map(obj -> obj.getClass().getName()).collect(Collectors.joining(",")),
                    SINGLE_LOCKING_TIMEOUT_MILLIS));
        }
        try {
            runnable.run();
        } finally {
            writeLocks.forEach(ReentrantReadWriteLock.WriteLock::unlock);
        }
    }

    /**
     * We don't want to wait too much for a lock here, because deadlocks will never be resolved. <br>
     * We try for a while and if we don't succeed, we stop and try again to lock. <br>
     * Thanks to that other operations that are holding read locks can finish and we may finally find a slot where locks can be obtained.
     */
    private static boolean tryLock(List<ReentrantReadWriteLock.WriteLock> writeLocks) {
        List<ReentrantReadWriteLock.WriteLock> toBeLocked = new ArrayList<>(writeLocks);
        Instant startTime = Instant.now();
        while (isNotTimeouted(startTime)
                && !toBeLocked.isEmpty()) { // all locks locked
            List<ReentrantReadWriteLock.WriteLock> lockedLocks = toBeLocked.stream()
                    .filter(MockitoSynchronizer::tryLock)
                    .toList();
            toBeLocked.removeAll(lockedLocks);
        }
        return toBeLocked.isEmpty();
    }

    private static boolean tryLock(ReentrantReadWriteLock.WriteLock writeLock) {
        try {
            return writeLock.tryLock()
                    || writeLock.tryLock(MockitoSynchronizer.SINGLE_LOCKING_TIMEOUT_MILLIS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isNotTimeouted(Instant startTime) {
        return Duration.between(startTime, Instant.now()).compareTo(LOCKS_TIMEOUT) < 0;
    }
}
