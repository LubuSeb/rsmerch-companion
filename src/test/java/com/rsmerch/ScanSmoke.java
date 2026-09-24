package com.rsmerch;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit network check of the native Java scanner used by the Scan button. */
public final class ScanSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch complete=new CountDownLatch(1); AtomicReference<String> error=new AtomicReference<>();
        okhttp3.OkHttpClient http=new okhttp3.OkHttpClient();
        try (ScanRunner runner=new ScanRunner(new WikiClient(http))) {
            if (!runner.start(args[0],1_000_000,System.out::println,failure -> { error.set(failure); complete.countDown(); })) { throw new IllegalStateException("Scan did not start"); }
            if (runner.start(args[0],1_000_000,s -> {},s -> {})) { throw new IllegalStateException("Duplicate scan started"); }
            if (!complete.await(6,TimeUnit.MINUTES)) { throw new IllegalStateException("Scan did not return"); }
            if (error.get()!=null) { throw new IllegalStateException(error.get()); }
            Research research=Research.read(args[0],"",new Book(),System.currentTimeMillis(),1_000_000);
            if (!research.fresh) { throw new IllegalStateException(research.status+" "+research.problem); }
            System.out.println("Fresh scan loaded: "+research.candidates.size()+" opportunities");
            for (Research.Candidate c:research.candidates) { System.out.println(c.name+": "+c.buy+" / "+c.sell+"; test "+c.testQuantity); }
        } finally { http.connectionPool().evictAll(); http.dispatcher().executorService().shutdown(); }
    }
}
