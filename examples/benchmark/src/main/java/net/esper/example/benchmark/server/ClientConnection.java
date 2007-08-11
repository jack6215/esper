package net.esper.example.benchmark.server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import net.esper.example.benchmark.MarketData;

/**
 * The ClientConnection handles unmarshalling from the connected client socket and delegates the event to
 * the underlying ESP/CEP engine by using/or not the executor policy.
 * Each ClientConnection manages a throughput statistic (evt/10s) over a 10s batched window
 *
 * @See Server
 */
public class ClientConnection extends Thread {

    static Map<Integer, ClientConnection> CLIENT_CONNECTIONS = Collections.synchronizedMap(new HashMap<Integer, ClientConnection>());

    public static void dumpStats(int statSec) {
        long totalCount = 0;
        int cnx = 0;
        ClientConnection any = null;
        for (ClientConnection m : CLIENT_CONNECTIONS.values()) {
            cnx++;
            totalCount += m.countForStatSecLast;
            any = m;
        }
        if (any != null) {
            System.out.printf("Throughput %.0f (active %d pending %d cnx %d)\n",
                    (float) totalCount / statSec,
                    any.executor == null ? 0 : any.executor.getCorePoolSize(),
                    any.executor == null ? 0 : any.executor.getQueue().size(),
                    cnx
            );
        }
    }

    private SocketChannel socketChannel;
    private CEPProvider.ICEPProvider cepProvider;
    private ThreadPoolExecutor executor;//this guy is shared
    private final int statSec;
    private long countForStatSec = 0;
    private long countForStatSecLast = 0;
    private long lastThroughputTick = System.currentTimeMillis();
    private int myID;
    private static int ID = 0;

    public ClientConnection(SocketChannel socketChannel, ThreadPoolExecutor executor, CEPProvider.ICEPProvider cepProvider, int statSec) {
        super("EsperServer-cnx-" + ID++);
        this.socketChannel = socketChannel;
        this.executor = executor;
        this.cepProvider = cepProvider;
        this.statSec = statSec;
        myID = ID - 1;

        CLIENT_CONNECTIONS.put(myID, this);
    }

    public void run() {
        try {
            ByteBuffer packet = ByteBuffer.allocateDirect(MarketData.SIZE / 8);
            do {
                if (socketChannel.read(packet) < 0) {
                    System.err.println("Error receiving data from client (got null). Did client disconnect?");
                    break;
                }
                if (packet.hasRemaining()) {
                    ;//System.err.println("partial packet");
                } else {
                    packet.flip();
                    final MarketData event = MarketData.fromByteBuffer(packet);
                    if (executor == null) {
                        long ns = System.nanoTime();
                        cepProvider.sendEvent(event);
                        long nsDone = System.nanoTime();
                        long msDone = System.currentTimeMillis();
                        StatsHolder.getEngine().update(nsDone - ns);
                        StatsHolder.getEndToEnd().update(msDone - event.getTime());
                    } else {
                        executor.execute(new Runnable() {
                            public void run() {
                                long ns = System.nanoTime();
                                cepProvider.sendEvent(event);
                                long nsDone = System.nanoTime();
                                long msDone = System.currentTimeMillis();
                                StatsHolder.getEngine().update(nsDone - ns);
                                StatsHolder.getServer().update(nsDone - event.getInTime());
                                StatsHolder.getEndToEnd().update(msDone - event.getTime());
                            }
                        });
                    }
                    //stats
                    countForStatSec++;
                    if (System.currentTimeMillis() - lastThroughputTick > statSec * 1E3) {
                        countForStatSecLast = countForStatSec;
                        countForStatSec = 0;
                        lastThroughputTick = System.currentTimeMillis();
                    }
                    packet.clear();
                }
            } while (true);
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("Error receiving data from client. Did client disconnect?");
        } finally {
            CLIENT_CONNECTIONS.remove(myID);
            StatsHolder.remove(StatsHolder.getEngine());
            StatsHolder.remove(StatsHolder.getServer());
            StatsHolder.remove(StatsHolder.getEndToEnd());
        }
    }
}
