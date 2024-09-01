package org.pidu;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultithreadedMockitoTest {
    private static final int RUN_FOR_SECONDS = 15;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ValueReturner object = Mockito.spy(new ValueReturner());

    @Test
    public void testNotSafe() throws InterruptedException {
        runInAnotherThread(() -> {  // run in background thread, continously use the mocked method
            while (true) {
                System.out.println(object.getValue());
            }
        });
        Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).toSeconds() < RUN_FOR_SECONDS) {
            Mockito.doAnswer(ans -> 13).when(object).getValue();  // try to mock the method, finally clash will occur with background thread
        }
    }


    @Test
    public void testSafe() throws InterruptedException {
        runInAnotherThread(() -> {  // run in background thread, continously use the mocked method
            while (true) {
                System.out.println(object.getValue());
            }
        });
        Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).toSeconds() < RUN_FOR_SECONDS) {
            MockitoSynchronizer.runSafely(() -> {
                Mockito.doAnswer(ans -> 13).when(object).getValue();  // try to mock the method, finally clash will occur with background thread
            }, object);
        }
    }

    private void runInAnotherThread(Runnable runnable) {
        executorService.execute(runnable);
    }
}
