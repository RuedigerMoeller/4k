package kontraktor.remoting;

import kontraktor.remoting.helpers.ServerTestFacade;
import org.junit.Assert;
import org.junit.Test;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.remoting.tcp.TCPActorClient;
import org.nustaq.kontraktor.remoting.tcp.TCPActorPublisher;

/**
 * Created by ruedi on 07/05/15.
 */
public class NewTCPTest {

    @Test
    public void test() throws Exception {
        ServerTestFacade server = Actors.AsActor(ServerTestFacade.class);
        TCPActorPublisher.publish(server, 8080).await();

        ServerTestFacade client = TCPActorClient.Connect(ServerTestFacade.class, "localhost", 8080).await();
//        client.$benchMark1(0, null).then( (r,e) -> System.out.println(r+" e:"+e) );
        Assert.assertTrue(client.$benchMark1(0, null).await(1000000).equals("ok"));
        System.out.println("one way performance");
        for ( int i = 0; i < 10_000_000; i++ ) {
            client.$benchMark(13,null);
        }
        System.out.println("two way performance");
        for ( int i = 0; i < 10_000_000; i++ ) {
            client.$benchMark1(13, null);
        }
        Thread.sleep(10000);
    }

}
