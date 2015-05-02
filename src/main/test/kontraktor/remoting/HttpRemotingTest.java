package kontraktor.remoting;

import junit.framework.Assert;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.junit.Test;
import org.nustaq.kontraktor.*;
import org.nustaq.kontraktor.annotations.Register;
import org.nustaq.kontraktor.remoting.RemoteCallEntry;
import org.nustaq.kontraktor.remoting.http.*;
import org.nustaq.kontraktor.undertow.http.KUndertowHttpServerAdapter;
import org.nustaq.kontraktor.undertow.Knode;
import org.nustaq.kontraktor.util.PromiseLatch;
import org.nustaq.kontraktor.util.RateMeasure;
import org.nustaq.kson.Kson;
import org.nustaq.kson.KsonDeserializer;
import org.nustaq.kson.KsonStringCharInput;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ruedi on 04.04.2015.
 */
public class HttpRemotingTest {

    public static class Pojo {
        String name;
        int id;
        Set<String> follower = new HashSet<>();

        public Pojo() { // required for json !!
        }

        public Pojo(String name, int id, Set<String> follower) {
            this.name = name;
            this.id = id;
            this.follower = follower;
        }

        public String getName() {
            return name;
        }

        public int getId() {
            return id;
        }

        public Set<String> getFollower() {
            return follower;
        }
    }

    static AtomicInteger callCount = new AtomicInteger(0);
    static AtomicInteger successCount = new AtomicInteger(0);

    @Register(Pojo.class)
    public static class HttpTestService extends Actor<HttpTestService> {

        //http://localhost:8080/api/test/$test/hello
        public void $test(String s) {
            System.out.println(s);
            callCount.incrementAndGet();
        }

        public void $bench(String s) {
        }

        //http://localhost:8080/api/test/$hello/1/2/3/4/5/'Guten%20Tag%20!'
        public void $hello( byte b, short s, int i, long l, char c, String str ) {
            System.out.println("byte "+b+", short "+s+", int "+i+", long "+l+", char "+c+", String "+str);
            successCount.incrementAndGet();
        }

        //http://localhost:8080/api/test/$helloBig/1/2/3/4/5/'Guten%20Tag%20!'
        public void $helloBig( Byte b, Short s, Integer i, Long l, Character c, String str ) {
            System.out.println("byte "+b+", short "+s+", int "+i+", long "+l+", char "+c+", String "+str);
            successCount.incrementAndGet();
        }

        //http://localhost:8080/api/test/$callback/
        public void $callback( String dummy, Callback cb ) {
            callCount.incrementAndGet();
            delayed( 500, () -> cb.stream("A") );
            delayed( 1000, () -> cb.stream("B") );
            delayed( 1500, () -> cb.stream("C") );
            delayed( 2000, () -> cb.finish() );
        }

        //FAILS !!
        public void $hello1( byte b[], short s[], int i[], long l[], char c[], String str[] ) {
            System.out.println("byte "+b+", short "+s+", int "+i+", long "+l+", char "+c+", String "+str);
            successCount.incrementAndGet();
        }

        public IPromise $promise(String s) {
            callCount.incrementAndGet();
            return new Promise(s+" "+s);
        }

        public IPromise<Pojo> $clonePojo(Pojo pojo) {
            callCount.incrementAndGet();
            return new Promise<>(pojo);
        }

        public void $testList0( List l, int len ) {
            if (l.size() == len) {
                successCount.incrementAndGet();
            }
        }

        public void $testList1( ArrayList l, int len ) {
            if (l.size() == len) {
                successCount.incrementAndGet();
            }
        }

        public void $testMap0( Map<String,String> l, int len ) {
            if (l.size() == len) {
                successCount.incrementAndGet();
            }
        }

        public void $testMap1( HashMap<String,String> l, int len ) {
            if (l.size() == len) {
                successCount.incrementAndGet();
            }
        }

    }

    @Test
    public void benchKson() throws Exception {
        Kson kson = new Kson()
            .map("call", RemoteCallEntry.class)
            .map("calls", RemoteCallEntry[].class)
            .map("rcb", HttpRemotedCB.class)
            .map(Pojo.class);
        kson.getMapper().setUseSimplClzName(false);
        String ksonString = "[{\n" +
                          "  receiverKey: 1\n" +
                          "  method: complete\n" +
                          "  args:\n" +
                          "    [ \n" +
                          "      Pojo {\n" +
                          "        name: bla\n" +
                          "        id: 14\n" +
                          "        follower:\n" +
                          "          [ \n" +
                          "            X\n" +
                          "            Y\n" +
                          "            \"Z Z\"\n" +
                          "          ]\n" +
                          "      }\n" +
                          "      null\n" +
                          "    ]\n" +
                          "  queue: 1\n" +
                          "}\n" +
                          "]";
        deser(kson, ksonString);
        deser(kson, ksonString);
        deser(kson, ksonString);
        deser(kson, ksonString);
        deser(kson, ksonString);

        System.out.println("------------ ser ------------------");
        KsonStringCharInput in = new KsonStringCharInput(ksonString);
        KsonDeserializer deserializer = new KsonDeserializer(in, kson.getMapper());
        Object o = deserializer.readObject(RemoteCallEntry[].class,String.class,null);

        ser(kson, o);
        ser(kson, o);
        ser(kson, o);

    }

    protected void ser(Kson kson, Object o) throws Exception {RateMeasure m = new RateMeasure("serialize");
        for ( int i = 0; i < 1000000; i++ ) {
            kson.writeObject(o);
            m.count();
        }
    }

    protected void deser(Kson kson, String ksonString) {
        RateMeasure m = new RateMeasure("deserialize");
        for ( int i = 0; i <  1000000; i++ ) {
            try {
                KsonStringCharInput in = new KsonStringCharInput(ksonString);
                KsonDeserializer deserializer = new KsonDeserializer(in, kson.getMapper());
                Object o = deserializer.readObject(RemoteCallEntry[].class,String.class,null);
                m.count();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void startServer() throws InterruptedException {

        HttpTestService service = Actors.AsActor(HttpTestService.class);
        Knode.PublishHttp("api", "test", service, 8080);

        HttpTestService clientProxy = Knode.ConnectHttp(HttpTestService.class,"localhost","/api/test/",8080);

        clientProxy.$test("Hello");

        Set<String> set = new HashSet<>();
        set.add("PAK");
        Pojo p = new Pojo("POK",13,set);
        Pojo p1 = clientProxy.$clonePojo(p).await();
        Assert.assertTrue(p.getName().equals(p1.getName()) && p.getFollower().size() == p1.getFollower().size());


        Assert.assertTrue(clientProxy.$promise("hello").await().equals("hello hello"));
        clientProxy.$callback( "Bla", (r,e) -> {
            System.out.println(r+" - "+e);
            successCount.incrementAndGet();
        });

        clientProxy.$hello((byte)1,(short)22222,222222,1231231231l,(char)44444,"POK");
        clientProxy.$helloBig((byte) 1, (short) 22222, 222222, 1231231231l, (char) 44444, "POK");
//        clientProxy.$hello1(new byte[]{1,2,3},new short[]{22222,33},new int[]{222222,234},new long[]{1231231231l,234},"char".toCharArray(),new String[]{"POK","oij"});

        List testList = new ArrayList<>();
        testList.add(13);
        testList.add("pok");
        testList.add(new int[] {1,2,3,4});
        clientProxy.$testList0(testList, testList.size());
        clientProxy.$testList1((ArrayList) testList, testList.size());


        ///////////// VOID message /////////////////////////////////////////////////////////

        // use plain http post + kson
        try {
            String request = "[ { method: '$test' args: [ 'Hello' ] } ]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .addHeader("Accept", "text/kson")
                .bodyString(request, ContentType.create("text/kson"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        // use plain http post + json
        try {
            String request = "[ { 'method': '$test', 'args': [ 'Hello' ] } ]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .bodyString(request, ContentType.create("text/json"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        ///////////// Callback message /////////////////////////////////////////////////////////

        // use plain http post + kson
        try {
            String request =
                "[ " +
                    "{ method: '$callback' args: [ 'bla', rcb { cbid: 1 } ] } " +
                    "{ method: '$callback' args: [ 'bla', rcb { cbid: 2 } ] } " +
                "]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .addHeader("Accept", "text/kson")
                .bodyString(request, ContentType.create("text/kson"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        // use plain http post + json
        try {
            String request = "[ { method: '$callback', args: [ 'bla', { '_type' : 'rcb', 'cbid' : 1 } ] } ]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .bodyString(request, ContentType.create("text/json"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        ///////////// promise + Pojo /////////////////////////////////////////////////////////

        // use plain http post + kson
        try {
            String request =
            "[ " +
                "{ " +
                    "futureKey: 1 "+
                    "method: '$clonePojo' args: [ " +
                        "Pojo { name: 'bla' id: 14 follower: [ X , Y , 'Z Z' ] }" +
                    "] " +
                "} " +
            "]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .addHeader("Accept", "text/kson")
                .bodyString(request, ContentType.create("text/kson"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        // use plain http post + json
        try {
            String request =
                "[" +
                    "{ " +
                        "futureKey: 1 , " +
                        "method: '$clonePojo', " +
                        "args: [" +
                            " { " +
                                "'_type' : 'Pojo', " +
                                "'name' : 'name', " +
                                "'follower': " +
                                    "[ 'a' , 'b' ]" +
                            " } " +
                        "] " +
                    "}" +
                "]";
            request = URLEncoder.encode(request);

            Content content = Request.Post("http://localhost:8080/api/test")
                .bodyString(request, ContentType.create("text/json"))
                .execute()
                .returnContent();
            System.out.println("Result:");
            System.out.println(content.asString());
            successCount.incrementAndGet();
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        RateMeasure measure = new RateMeasure("plain void req");
        for (int i = 0; i < 2_000_000; i++) {
            clientProxy.$bench(null);
            measure.count();
        }

        clientProxy.$ping().await();
        System.out.println("start future bench");

        RateMeasure pmeasure = new RateMeasure("promise req");
        Promise finished = new Promise();
        PromiseLatch latch = new PromiseLatch(50_000,finished);
        for (int i = 0; i < 50_000; i++) {
            clientProxy.$promise("").then(() -> {
                pmeasure.count();
                latch.countDown();
            });
        }

        finished.await();
        Assert.assertTrue(successCount.get() == 14);

//        callCount.set(0);
//        try {
//            Runtime.getRuntime().exec("firefox "+ new File("./src/main/test/kontraktor/remoting/httptest.html").getCanonicalPath() );
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        Thread.sleep(10000);
//        Assert.assertTrue(callCount.get() == 3);

        System.out.println("Stopping");
        Knode.GetServer(8080).stop();
        Thread.sleep(1000);
    }

    protected RestActorServer getRestActorServer(Knode knode) {
        KUndertowHttpServerAdapter sAdapt = new KUndertowHttpServerAdapter(
            knode.getServer(),
            knode.getPathHandler()
        );

        HttpObjectSocket.DUMP_REQUESTS = false;
        RestActorServer restActorServer = new RestActorServer();
        restActorServer.joinServer("/api", sAdapt);
        return restActorServer;
    }
}
