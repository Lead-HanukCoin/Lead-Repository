package il.ac.tau.cs.hanukcoin;
import java.io.IOException;
// note - we use mostly java.nio API in this server
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;


/**
 * Simple request-response server that uses non-blocking TCP API
 */
class ServerSimpleNonBlocking {
    protected static int accepPort = 8080;

    class ClientConnection {
        private SocketChannel connectionSocket;
        private ByteBuffer inBuf = null;
        private boolean isActive = true;
        public ClientConnection(SocketChannel connectionSocket) {
            this.connectionSocket = connectionSocket;
        }

        /**
         * Attempt to read more bytes from the Channel
         * @throws IOException
         */
        protected void readMore() throws IOException {
            // See https://howtodoinjava.com/java7/nio/java-nio-2-0-working-with-buffers/#buffer_attributes
            if (inBuf == null) {
                inBuf = ByteBuffer.allocate(32 * 1024);
            } else {
                // append to end of buf
                inBuf.compact();
                inBuf.position(inBuf.limit()).limit(inBuf.capacity());
            }
            connectionSocket.read(inBuf);
            inBuf.flip();
        }

        /**
         * Try reading a full text line from what's in the buffer
         * @return a line read (String) or null if no line in buffer
         * @throws IOException
         */
        protected String readLine() throws IOException {
            int start = inBuf.position();
            int end = inBuf.limit();
            int eol_index = -1;
            int next_line_index = -1;
            final int CR = '\r';
            final int NL = '\n';

            for (int i = start; i < end; i++) {
                byte b = inBuf.get(i);
                if (b == CR || b == NL) {
                    eol_index = i;
                    next_line_index = i + 1;
                    if (b == CR) {
                        // assume '\n' follows
                        next_line_index += 1;
                    }
                    break;
                }
            }
            if (eol_index < 0) {
                return null;
            }
            inBuf.position(next_line_index);
            return new String(inBuf.array(), start, eol_index - start, "utf-8");
        }

        private void handleHttp() {
            try {
                boolean firstLine = true;
                while(true) {
                    readMore();
                    String line = readLine();
                    if (line == null) {
                        // note line == null and line == "" is not the same
                        return;   // not enough data in buffer
                    }
                    if (firstLine) {
                        firstLine = false;
                        if (!line.startsWith("GET / ")) {
                            connectionSocket.close();
                            isActive = false;
                            return;
                        }
                    }
                    System.out.println(String.format("Http header: %s", line));
                    if (line.length() == 0) {
                        break;  // in HTTP empty line ends the header part
                    }
                }
                sendHtml(); // send response - assume this is a "GET"
            } catch (IOException e) {
                throw new RuntimeException(e); ///e.printStackTrace();
            }
        }

        protected void sendHtml() throws IOException {
            String html = String.format("<html><body>I am alive<br/>" +
                    "I am non-blocking server connection:%s" +
                    "</body></html>\r\n", this.toString());
            int contentLen = html.length();
            HashMap<String, String> header = new HashMap<>();
            header.put("Content-Length", new Integer(contentLen).toString());
            header.put("Content-Type", "text/html");
            header.put("Connection", "Closed");
            String responseLine = "HTTP/1.1 200 OK\r\n";
            String headerText = header.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\r\n"));
            String headerFull = responseLine + headerText + "\r\n\r\n";
            String response = headerFull + html;
            System.out.println(headerFull);
            connectionSocket.write(ByteBuffer.wrap(response.getBytes()));
        }
    }

    private ArrayList<ClientConnection> connections = new ArrayList<>();

    public void runServer() throws InterruptedException {
        ServerSocketChannel acceptSocket = null;
        try {
            acceptSocket = ServerSocketChannel.open();
            acceptSocket.configureBlocking(false);  // non-blocking to allow loop to handle other things
            acceptSocket.socket().bind(new InetSocketAddress(accepPort));
        } catch (IOException e) {
            System.out.println(String.format("ERROR accepting at port %d", accepPort));
            return;
        }

        while (true) {
            SocketChannel connectionSocket = null;
            try {
                connectionSocket = acceptSocket.accept();
                if (connectionSocket != null) {
                    connections.add(new ClientConnection(connectionSocket));
                }
            } catch (IOException e) {
                System.out.println(String.format("ERROR accept:\n  %s", e.toString()));
            }
            handleConnections();
            Thread.sleep(50);  // prevent tight loop
        }
    }

    /**
     * go over all live connections hand see if there is data to handle.
     */
    private void handleConnections() {
        for (ClientConnection conn : connections) {
            if (conn.isActive) {
                conn.handleHttp();
            }
            // TODO(franji): if !conn.isAlive - remove from connections[]
        }
    }


    public static void main(String argv[]) {
        if (argv.length > 0) {
            // allow changing accept port
            accepPort = Integer.parseInt(argv[0]);
        }
        ServerSimpleNonBlocking server = new ServerSimpleNonBlocking();
        try {
            server.runServer();
        } catch (InterruptedException e) {
            // Exit - Ctrl -C pressed
        }
    }
}