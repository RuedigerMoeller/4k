package kontraktor.remoting;

import kontraktor.remoting.helpers.ServerTestFacade;
import org.junit.Assert;
import org.junit.Test;
import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.Promise;
import org.nustaq.kontraktor.Spore;
import org.nustaq.kontraktor.remoting.tcp.TCPActorClient;
import org.nustaq.kontraktor.remoting.tcp.TCPActorPublisher;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by ruedi on 07/05/15.
 */
public class NewTCPTest {

    @Test
    public void test() throws Exception {
        ServerTestFacade server = Actors.AsActor(ServerTestFacade.class);
        TCPActorPublisher.publish(server, 8080).await();

        ServerTestFacade run = TCPActorClient.Connect(ServerTestFacade.class, "localhost", 8080).await();

        Assert.assertTrue(run.$benchMark1(0, null).await(1000000).equals("ok"));

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
        run.$sporeTest( new Spore<Integer,Integer>() {
            @Override
            public void remote(Integer input) {
                stream(input);
            }
        }.forEach( (res,e) -> {
            System.out.println("spore res "+res);
            sporeResult.add(res);
        }).onFinish( () -> {
            System.out.println("Finish");
            sporeP.complete();
        }));

        Assert.assertTrue(p.await().equals(new Date(time).toString()));
        Assert.assertTrue(run.$futureTest("A").await().equals("A A"));
        sporeP.await();
        Assert.assertTrue(sporeResult.size() == 4);

        System.out.println("one way performance");
        for ( int i = 0; i < 10_000_000; i++ ) {
            run.$benchMark(13,null);
        }
        System.out.println("two way performance");
        for ( int i = 0; i < 10_000_000; i++ ) {
            run.$benchMark1(13, null);
        }
        Thread.sleep(10000);
    }

}
