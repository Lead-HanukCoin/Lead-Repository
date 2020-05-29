package il.ac.tau.cs.hanukcoin;

import javafx.util.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.stream.Collectors;

import il.ac.tau.cs.hanukcoin.ShowChain.NodeInfo;
import org.omg.CORBA.ShortSeqHelper;

public class ServerAnswer {
	public static int accepPort;
	public static String TeamName;
	public static final int BEEF_BEEF = 0xbeefBeef;
	public static final int DEAD_DEAD = 0xdeadDead;
	public static ArrayList<ShowChain.NodeInfo> waitingList = new ArrayList<>();
	public static int lastChange = (int) (System.currentTimeMillis() / 1000);
	public static String host;
	public static int port;

	public static void log(String fmt, Object... args) {
		println(fmt, args);
	}

	public static void println(String fmt, Object... args) {
		System.out.format(fmt + "\n", args);
	}

	private ShowChain.NodeInfo[] get3RandomNodes() {
		NodeInfo[] arr = new NodeInfo[3];
		System.out.println();
		ArrayList<ShowChain.NodeInfo> nodes = new ArrayList<>();
		for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.getValuesIterator(); it.hasNext();) {
			ShowChain.NodeInfo node = it.next();
			if ((!node.host.equals(ServerAnswer.host) || node.port != ServerAnswer.port)
					&& !ServerAnswer.waitingList.contains(node)) {
				nodes.add(node);
			}
		}
		int cnt = 0;
		while (cnt < 3 && nodes.size() != 0) {
			int idx = (int) (Math.random() * nodes.size());
			arr[cnt] = nodes.remove(idx);
			cnt++;
		}
		return arr;
	}

	private void tryConnection() {
		ShowChain.NodeInfo[] nodeArray = get3RandomNodes();
		synchronized (this) {
			for (ShowChain.NodeInfo node : nodeArray) {
				if (node != null) {
					if (node.port == ServerAnswer.port && node.host.equals(ServerAnswer.host)) {
						System.out.println("ERROR, ME RETURNED");
					}
					sendReceive(node.host, node.port);
				}
			}
		}
	}

	public void sendReceive(String host, int port) {
		try {
			log("INFO - Sending request message to %s:%d", host, port);
			Socket soc = new Socket(host, port);
			ClientConnection connection = new ClientConnection(soc, false);
			connection.sendReceive();
		} catch (IOException e) {
			log("WARN - open socket exception connecting to %s:%d: %s", host, port, e.toString());
		}
	}

	class ClientConnection {
		private DataInputStream dataInput;
		private DataOutputStream dataOutput;
		private boolean isIncomming = false;
		Socket connectionSocket;
		public String host;
		public int port;

		public ClientConnection(Socket connectionSocket, boolean incomming) {
			String addr = connectionSocket.getRemoteSocketAddress().toString();
			host = addr.substring(1).split(":")[0];
			port = Integer.parseInt(addr.substring(1).split(":")[1]);
			isIncomming = incomming;
			this.connectionSocket = connectionSocket;
			try {
				dataInput = new DataInputStream(connectionSocket.getInputStream());
				dataOutput = new DataOutputStream(connectionSocket.getOutputStream());
			} catch (IOException e) {
				// connectionThread would fail and kill thread
			}
		}

		public void sendReceive() {
			try {
				send(1, dataOutput);
				parseMessage(dataInput);

			} catch (IOException e) {
				throw new RuntimeException("send/recieve error", e);
			}
		}

		public void runInThread() {
			new Thread(new Runnable() {
				@Override
				public void run() {
					// Note - in here we are in:
					// class ServerSimpleThread (parent)
					// class ClientConnection (dynamic inner class)
					// class annonymous_runnable (dynamic inner child of ClientConnection)
					// Here I call my parent instance of ClientConnection
					try {
						ClientConnection.this.connectionThread();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
		}

		private void send(int cmd, DataOutputStream dos) throws IOException {

			dos.writeInt(cmd);
			dos.writeInt(BEEF_BEEF);
			if (!ConnectionsList.hmap.isEmpty()) {
				synchronized (this) {
					// ConnectionsList.hmap.get(new Pair(host, port)).lastSeenTS =
					// (int)(System.currentTimeMillis() / 1000);
					dos.writeInt(ConnectionsList.activeNodes.size());
					for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.activeNodes.listIterator(); it.hasNext();) {
						ShowChain.NodeInfo node = it.next();
						if (node.port == ServerAnswer.port && node.host.equals(ServerAnswer.host)) {
							node.lastSeenTS = (int) (System.currentTimeMillis() / 1000);
						}
						node.writeInfo(dos);
					}
				}
			} else {
				dos.writeInt(0);
			}
			dos.writeInt(DEAD_DEAD);
			int blockChain_size = 0;
			dos.writeInt(blockChain_size);
			// TODO(students): sendRequest data of blocks
			ServerAnswer.waitingList.add(ConnectionsList.hmap.get(new Pair(this.host, this.port)));
		}

		public void parseMessage(DataInputStream dataInput) throws IOException {
			System.out.println("<----> got new message!!");
			int cmd = dataInput.readInt(); // skip command field
			if (cmd != 1 && cmd != 2) {
				throw new IOException("Bad message bad cmd");
			}

			int beefBeef = dataInput.readInt();
			if (beefBeef != BEEF_BEEF) {
				throw new IOException("Bad message no BeefBeef");
			}
			int nodesCount = dataInput.readInt();
			// FRANJI: discussion - create a new list in memory or update global list?
			ArrayList<NodeInfo> receivedNodes = new ArrayList<>();
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
			ArrayList<Block> receivedBlocks = new ArrayList<>();
			for (int bi = 0; bi < blockCount; bi++) {
				Block newBlock = Block.readFrom(dataInput);
				receivedBlocks.add(newBlock);
			}

			// update ConnectionsList.hmap
			boolean changed = false;
			synchronized (this) {
				for (NodeInfo node : receivedNodes) {
					if (!ConnectionsList.hmap.containsKey(new Pair(node.host, node.port))) {
						changed = true;
						ConnectionsList.add(node); // add a new node to hmap. isNew = true;
					} else {
						NodeInfo originalNode = ConnectionsList.hmap.get(new Pair<>(node.host, node.port));
						originalNode.lastSeenTS = Math.max(originalNode.lastSeenTS, node.lastSeenTS); // the time stamp
																										// is the
																										// maximum
																										// between the
																										// one we have
																										// and the new
																										// one
					}
				}
			}

			if (cmd == 2) {
				synchronized (this) {
					Pair p = new Pair(this.host, this.port);
					if (ConnectionsList.hmap.containsKey(p)) {
						NodeInfo n = ConnectionsList.hmap.get(p); // sender
						if (waitingList.contains(n)) {
							waitingList.remove(n);
							if (n.isNew) {
								n.modify();
							}
						}
					}
				}
			} else if (cmd == 1) {
				System.out.println("<----> send back");
				send(2, dataOutput);
			}

			if (changed) {
				lastChange = (int) (System.currentTimeMillis() / 1000);
				System.out.println("<----> try to connect to 3 nodes");
				tryConnection();
			}

		}

		private void connectionThread() throws IOException {
			// This function runs in a separate thread to handle the connection
			// send ConnectionsList
			parseMessage(dataInput);
			dataInput.close();
			dataOutput.close();
			connectionSocket.close();
		}
	}

	public void runServer() throws InterruptedException {
		ServerSocketChannel acceptSocket = null;
		try {
			acceptSocket = ServerSocketChannel.open();
			acceptSocket.socket().bind(new InetSocketAddress(accepPort));
		} catch (IOException e) {
			System.out.println(String.format("ERROR accepting at port %d", accepPort));
			return;
		}

		while (true) {
			SocketChannel connectionSocket = null;
			try {
				connectionSocket = acceptSocket.accept(); // this blocks
				if (connectionSocket != null) {
					System.out.println("here : " + connectionSocket.toString());
					new ServerAnswer.ClientConnection(connectionSocket.socket(), true).runInThread();
				}
			} catch (IOException e) {
				System.out.println(String.format("ERROR accept:\n  %s", e.toString()));
				continue;
			}
		}
	}

	public static void main(String[] args) {
		ServerAnswer.TeamName = args[2];
		ServerAnswer.accepPort = Integer.parseInt(args[1]);
		ServerAnswer.port = ServerAnswer.accepPort;
		ServerAnswer server = new ServerAnswer();
		ServerAnswer.host = "80.179.243.230";
//        try {
//            ServerAnswer.host = InetAddress.getLocalHost().getHostAddress();
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        }
		NodeInfo me = new NodeInfo(ServerAnswer.TeamName, ServerAnswer.host, ServerAnswer.port,
				(int) (System.currentTimeMillis() / 1000));
		System.out.println(me);
		me.isNew = false;
		ConnectionsList.add(me);
		ConnectionsList.activeNodes.add(me);

//		args = new String[] {"80.179.243.230:2005", "2007"};

		String[] parts = args[0].split(":");
		String addrTal = parts[0];
		int portTal = Integer.parseInt(parts[1]);

		ConnectionsList.add(new NodeInfo("Earth" , addrTal, portTal,0));

		Thread firstMassage = new Thread( new Runnable() {
			public void run() {
				server.sendReceive(addrTal, portTal);
			}
		});
		firstMassage.start();

		Thread fiveMin = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(5 * 60 - ((int) (System.currentTimeMillis() / 1000) - server.lastChange));
						for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.getValuesIterator(); it.hasNext();) {
							ShowChain.NodeInfo node = it.next();
							// delete nodes that weren't active in 30 min
							if (((int) (System.currentTimeMillis() / 1000 - node.lastSeenTS) >= 30 * 60)) {
								ConnectionsList.hmap.remove(node);
								if (!node.isNew) {
									ConnectionsList.activeNodes.remove(node);
								}
								if (ServerAnswer.waitingList.contains(node)) {
									ServerAnswer.waitingList.remove(node);
								}
							}
						}
						if ((int) (System.currentTimeMillis() / 1000) - server.lastChange >= 5 * 60) {
							server.tryConnection();
							server.lastChange = (int) (System.currentTimeMillis() / 1000);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		fiveMin.start();
//        ConnectionsList.add(new ShowChain.NodeInfo("Lead", "or", 50, 60));
//        ConnectionsList.add(new ShowChain.NodeInfo("Lead1", "or1", 501, 601));
//        ConnectionsList.add(new ShowChain.NodeInfo("Lead2", "or2", 502, 601));
//        ConnectionsList.add(new ShowChain.NodeInfo("Lead3", "or3", 503, 601));
		try {
			server.runServer();
		} catch (InterruptedException e) {
			// Exit - Ctrl -C pressed
		}
	}
}
