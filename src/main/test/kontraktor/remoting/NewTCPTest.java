package kontraktor.remoting;

import kontraktor.remoting.helpers.ServerTestFacade;
import org.junit.Assert;
import org.junit.Test;
import org.nustaq.kontraktor.*;
import org.nustaq.kontraktor.impl.SimpleScheduler;
import org.nustaq.kontraktor.remoting.tcp.TCPActorClient;
import org.nustaq.kontraktor.remoting.tcp.NIOActorPublisher;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ruedi on 07/05/15.
 */
public class NewTCPTest {

    static AtomicInteger lostCount = new AtomicInteger();
    public static class NIOTA extends Actor<NIOTA> {

        public IPromise $test() {
            try {

                ServerTestFacade run = TCPActorClient.Connect(ServerTestFacade.class, "localhost", 8787).await();
//                ServerTestFacade run = NIOTCPActorClient.Connect(ServerTestFacade.class, "localhost", 8787).await();
                Assert.assertTrue(run.$benchMark1(0, null).await(1000).equals("ok"));

                Promise p = new Promise();
                p.timeoutIn(5000);
                long time = System.currentTimeMillis();
                run.$testCallWithCB(time, (r,e) -> {
                    if (Actor.isFinal(e) ) {
                        System.out.println("DONE "+r);
                        p.resolve(r);
                    } else {
                        System.out.println("received:" + r);
                    }
                });

                ArrayList<Integer> sporeResult = new ArrayList<>();
                Promise sporeP = new Promise();
                run.$sporeTest(new Spore<Integer, Integer>() {
                    @Override
                    public void remote(Integer input) {
                        stream(input);
                    }
                }.forEach((res, e) -> {
                    System.out.println("spore res " + res);
                    sporeResult.add(res);
                }).onFinish(() -> {
                    System.out.println("Finish");
                    sporeP.complete();
                }));

                Assert.assertTrue(p.await().equals(new Date(time).toString()));
                Assert.assertTrue(run.$futureTest("A").await().equals("A A"));
                sporeP.await();
                Assert.assertTrue(sporeResult.size() == 4);

                System.out.println("one way performance");
                for ( int i = 0; i < 4_000_000; i++ ) {
                    run.$benchMark(13, null);
                    yield();
                }
                System.out.println("two way performance");
                for ( int i = 0; i < 20_000_000; i++ ) {
                    if ( i%1_000_000==0 )
                        System.out.println("sent "+i);
                    if ( run.isStopped() )
                    {
                        lostCount.incrementAndGet();
                        System.out.println("connection lost "+lostCount.get());
                        break;
                    }
                    run.$benchMark1BigResult(13, null);
                    yield();
                }
            } catch (Throwable t) {
                return reject(t);
            }
            return resolve(null);
        }

    }

    @Test
    public void test1() throws Exception {
        ServerTestFacade server = Actors.AsActor(ServerTestFacade.class,256000);
        NIOActorPublisher.Publish(server, 8787).await();
        Thread.sleep(10000000);
    }

    @Test
    public void test() throws Exception {
//        ServerTestFacade server = Actors.AsActor(ServerTestFacade.class,128000);
//        NIOTCPActorPublisher.Publish(server, 8787).await();
//        TCPActorServerAdapter.Publish(server, 8787);

        SimpleScheduler sched = new SimpleScheduler();
        for ( int i = 0; i < 1; i++ ) {
            NIOTA niota = Actors.AsActor(NIOTA.class);
            niota.$test();//.await(1000000);
        }
//        NIOTA niota = new NIOTA();
        Thread.sleep(20000000);
    }

}
