package il.ac.tau.cs.hanukcoin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A program to choe current status of HanukCoin netwrk and block-chain
 */
public class ShowChain {
    public static final int BEEF_BEEF = 0xbeefBeef;
    public static final int DEAD_DEAD = 0xdeadDead;
    public static void log(String fmt, Object... args) {
        println(fmt, args);
    }
    public static void println(String fmt, Object... args) {
        System.out.format(fmt + "\n", args);
    }


    static class NodeInfo {
        // FRANJI: Discussion - public members - pro/cons. What is POJO
        public String name;
        public String host;
        public int port;
        public int lastSeenTS;
        public boolean isNew = true;

        public String toString(){
            return "host: " + host + " port: " + port;
        }

        public NodeInfo(String name, String host, int port, int lastSeenTS){
            this.name = name;
            this.host = host;
            this.port = port;
            this.lastSeenTS = lastSeenTS;
        }
        public NodeInfo(){

        }
        // TODO(students): add more fields you may need such as number of connection attempts failed
        //  last time connection was attempted, if this node is new ot alive etc.

        public void modify(){
            this.isNew = false;
            synchronized (this) {
                ConnectionsList.activeNodes.add(this);
            }
        }

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
            n.port = dis.readUnsignedShort();
            n.lastSeenTS =dis.readInt();
            // TODO(students): update extra fields
            return n;
        }

        public void writeInfo(DataOutputStream dataOutputStream) throws IOException {
            dataOutputStream.writeByte(this.name.length());
            dataOutputStream.write(this.name.getBytes(StandardCharsets.UTF_8));
            dataOutputStream.writeByte(this.host.length());
            dataOutputStream.write(this.host.getBytes(StandardCharsets.UTF_8));
            dataOutputStream.writeShort(port);
            dataOutputStream.writeInt(lastSeenTS);
        }
    }


    static class ClientConnection {
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
                parseMessage(dataInput);

            } catch (IOException e) {
                throw new RuntimeException("send/recieve error", e);
            }
        }

        public void parseMessage(DataInputStream dataInput) throws IOException  {
            int cmd = dataInput.readInt(); // skip command field

            int beefBeef = dataInput.readInt();
            if (beefBeef != BEEF_BEEF) {
                throw new IOException("Bad message no BeefBeef");
            }
            int nodesCount = dataInput.readInt();
            System.out.println("node count: " + nodesCount);
            // FRANJI: discussion - create a new list in memory or update global list?
            ArrayList<NodeInfo> receivedNodes =  new ArrayList<>();
            for (int ni = 0; ni < nodesCount; ni++) {
                NodeInfo newInfo = NodeInfo.readFrom(dataInput);
                receivedNodes.add(newInfo);
            }
            int deadDead = dataInput.readInt();
            if (deadDead != DEAD_DEAD) {
                throw new IOException("Bad message no DeadDead");
            }
            int blockCount = dataInput.readInt();
            // FRANJI: discussion - create a new list in memory or update global list?
            ArrayList<Block> receivedBlocks =  new ArrayList<>();
            for (int bi = 0; bi < blockCount; bi++) {
                Block newBlock = Block.readFrom(dataInput);
                receivedBlocks.add(newBlock);
            }
            printMessage(receivedNodes, receivedBlocks);
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

        private void sendRequest(int cmd, DataOutputStream dos) throws IOException {
            dos.writeInt(cmd);
            dos.writeInt(BEEF_BEEF);
            int activeNodes = ConnectionsList.activeNodes.size();
            // TODO(students): calculate number of active (not new) nodes
            dos.writeInt(activeNodes);
            // TODO(students): sendRequest data of active (not new) nodes
            dos.writeInt(DEAD_DEAD);
            int blockChain_size = 0;
            dos.writeInt(blockChain_size);
            // TODO(students): sendRequest data of blocks
        }
    }


    public static void sendReceive(String host, int port){
        try {
            log("INFO - Sending request message to %s:%d", host, port);
            Socket soc = new Socket(host, port);
            ClientConnection connection = new ClientConnection(soc);
            connection.sendReceive();
        } catch (IOException e) {
            log("WARN - open socket exception connecting to %s:%d: %s", host, port, e.toString());
        }
    }

    public static void main(String argv[]) {
        for (String server: argv){
            if(!server.contains(":")){
                println("ERROR - please provide HOST:PORT");
                return;
            }
            String[] parts = server.split(":");
            String addr = parts[0];
            int port = Integer.parseInt(parts[1]);
            sendReceive(addr, port);
        }
    }
}
