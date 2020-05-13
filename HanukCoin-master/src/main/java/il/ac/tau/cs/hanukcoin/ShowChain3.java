package il.ac.tau.cs.hanukcoin;

import javafx.util.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;

/**
 * A program to choe current status of HanukCoin netwםrk and block-chain
 * This program accepts multiple HOST:PORT strings on the command line and access all of them
 * The program uses threads to open concurrent TCP calls.
 * We use SynchronousQueue to coordinate main thread and TCP
 * Look for QUIZ
 */
public class ShowChain3 {
    public static final int BEEF_BEEF = 0xbeefBeef;
    public static final int DEAD_DEAD = 0xdeadDead;
    public static void log(String fmt, Object... args) {
        println(fmt, args);
    }
    public static void println(String fmt, Object... args) {
        System.out.format(fmt + "\n", args);
    }
    public SynchronousQueue<Object> messageQ = new SynchronousQueue<>();

    static class NodeInfo {
        // FRANJI: Discussion - public members - pro/cons. What is POJO
        public String name;
        public String host;
        public int port;
        public int lastSeenTS;
        // TODO(students): add more fields you may need such as number of connection attempts failed
        //  last time connection was attempted, if this node is new ot alive etc.

        public static String readLenStr(DataInputStream dis) throws IOException {
            byte strLen = dis.readByte();
            byte[] strBytes = new byte[strLen];
            dis.read(strBytes, 0, strLen);
            return new String(strBytes, "utf-8");
        }

        public static NodeInfo readFrom(DataInputStream dis) throws IOException {
            NodeInfo n = new NodeInfo();
            n.name = readLenStr(dis);
            n.host = readLenStr(dis);
            // QUIZ: Here is the bug Itai found - how did he found that
            //    I need to used dis.readUnsignedShort(); and not dis.readShort();
            n.port = dis.readUnsignedShort();
            n.lastSeenTS =dis.readInt();
            // TODO(students): update extra fields
            return n;
        }
    }

    class ParsedMessage {
        public ArrayList<NodeInfo> receivedNodes = new ArrayList<>();
        public ArrayList<Block> receivedBlocks =  new ArrayList<>();
    }


    class ClientConnection {
        private DataInputStream dataInput;
        private DataOutputStream dataOutput;

        public ClientConnection(Socket connectionSocket) {
            try {
                dataInput = new DataInputStream(connectionSocket.getInputStream());
                dataOutput = new DataOutputStream(connectionSocket.getOutputStream());

            } catch (IOException e) {
               throw new RuntimeException("FATAL = cannot create data streams", e);
            }
        }

        public void sendReceive() {
            try {
                sendRequest(1, dataOutput);
                ParsedMessage pm = parseMessage(dataInput);
                serverEnqueue(pm);

            } catch (IOException e) {
                // QUIZ: what happens if I throw exception from a thread? who catches it?
                serverEnqueue(new RuntimeException("send/recieve error", e));
            }
        }

        public ParsedMessage parseMessage(DataInputStream dataInput) throws IOException  {
            int cmd = dataInput.readInt(); // skip command field
            ParsedMessage pm  = new ParsedMessage();
            int beefBeef = dataInput.readInt();
            if (beefBeef != BEEF_BEEF) {
                throw new IOException("Bad message no BeefBeef");
            }
            int nodesCount = dataInput.readInt();
            // FRANJI: discussion - create a new list in memory or update global list?
            for (int ni = 0; ni < nodesCount; ni++) {
                NodeInfo newInfo = NodeInfo.readFrom(dataInput);
                pm.receivedNodes.add(newInfo);
            }
            int deadDead = dataInput.readInt();
            if (deadDead != DEAD_DEAD) {
                throw new IOException("Bad message no DeadDead");
            }
            int blockCount = dataInput.readInt();
            // FRANJI: discussion - create a new list in memory or update global list?
            for (int bi = 0; bi < blockCount; bi++) {
                Block newBlock = Block.readFrom(dataInput);
                pm.receivedBlocks.add(newBlock);
            }
            return pm;
        }


        private void sendRequest(int cmd, DataOutputStream dos) throws IOException {
            // QUIZ: Currently this mothods sends an empty request.
            //   in your server - you will need to access the node list and block chain
            //   How would you synchronize?
            // How do you prevent slow stream from locking you?
            // (with Queues)
            dos.writeInt(cmd);
            dos.writeInt(BEEF_BEEF);
            int activeNodes = 0;
            // TODO(students): calculate number of active (not new) nodes
            dos.writeInt(activeNodes);
            // TODO(students): sendRequest data of active (not new) nodes
            dos.writeInt(DEAD_DEAD);
            int blockChain_size = 0;
            dos.writeInt(blockChain_size);
            // TODO(students): sendRequest data of blocks
        }
    }

    public void serverEnqueue(Object o) {
        try {
            messageQ.put(o);
        } catch (InterruptedException e) {
            e.printStackTrace();  // what happened?
        }
    }

    private void printMessage(List<NodeInfo> receivedNodes, List<Block> receivedBlocks) {
        println("==== Nodes ====");
        for (NodeInfo ni : receivedNodes) {
            println("%20s\t%s:%s\t%d",ni.name,  ni.host, ni.port, ni.lastSeenTS);
        }
        println("==== Blocks ====");
        for (Block b : receivedBlocks) {
            println("%5d\t0x%08x\t%s", b.getSerialNumber(), b.getWalletNumber(), b.binDump().replace("\n", "  "));
        }
    }

    public void sendReceive(List<Pair<String, Integer>> hosts) throws InterruptedException {
        for (Pair<String, Integer> hp : hosts) {
            final String host = hp.getKey();
            final int port = hp.getValue();
            new Thread(() -> sendReceive(host, port)).start();
        }
        while(true) {
            // QUIZ: does this loop always end?
            println("waiting on messageQ...");
            Object obj = messageQ.take();
            if (obj instanceof ParsedMessage) {
                // QUIZ: can this cast (ParsedMessage) fail?
                ParsedMessage pm = (ParsedMessage) obj;
                printMessage(pm.receivedNodes, pm.receivedBlocks);
                break;
            } else {
                // another object type - maybe excpetion
                println("Result from thread: %s", obj.toString());
            }
        }
    }

    public void sendReceive(String host, int port){
        try {
            log("INFO - Sending request message to %s:%d", host, port);
            Socket soc = new Socket(host, port);
            ClientConnection connection = new ClientConnection(soc);
            connection.sendReceive();
        } catch (IOException e) {
            serverEnqueue(e);
        }
    }

    public static void main(String argv[]) {
        ArrayList<Pair<String, Integer>> hostPort = new ArrayList<>();
        if (argv.length == 0) {
            println("ERROR - please provide HOST:PORT");
            return;
        }
        for (String hp : argv) {
            if (!hp.contains(":")){
                println("ERROR - please provide HOST:PORT");
                return;
            }
            String[] parts = argv[0].split(":");
            String addr = parts[0];
            int port = Integer.parseInt(parts[1]);
            hostPort.add(new Pair<>(addr, port));

        }
        ShowChain3 app = new ShowChain3();
        try {
            app.sendReceive(hostPort);
        } catch (InterruptedException e) {
            //end program
        }

    }
}


