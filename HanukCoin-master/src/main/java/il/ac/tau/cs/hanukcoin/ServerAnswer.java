package il.ac.tau.cs.hanukcoin;

import javafx.util.Pair;

import java.io.*;
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
	ClientConnection fileConnection;
	public static BlocksList blocksList = new BlocksList(new ArrayList<>());
	public static Block genesis;
	public static int walletCode;

	public static void log(String fmt, Object... args) {
		println(fmt, args);
	}

	public static void println(String fmt, Object... args) {
		System.out.format(fmt + "\n", args);
	}

	private synchronized void readFile(DataInputStream dis, DataOutputStream dos){
		
		ClientConnection fileConnection = new ClientConnection(dis, dos);
		this.fileConnection = fileConnection;
		try {
			fileConnection.parseMessage(dis);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private synchronized void saveFile(){
		//System.out.println("*");
		System.out.println("--------------------------------------------------------------------------- ");
//		File file = new File("connectionList.txt");
//		file.delete();

		try {
			//System.out.println("--------------------------------------------------------------------------- " + );
			try {
				File file = new File("connectionList.txt");
				file.delete();
//				File newFile = new File("connectionList.txt");
				DataOutputStream FileDataOut = new DataOutputStream(new FileOutputStream(file));
				DataInputStream FileDataIn = new DataInputStream(new FileInputStream(file));
				ClientConnection fileConnection = new ClientConnection(FileDataIn, FileDataOut);
				this.fileConnection = fileConnection;
				FileDataIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			fileConnection.sendToFileOrNode(2, fileConnection.dataOutput, true);
//			//System.out.println("***");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private ShowChain.NodeInfo[] get3RandomNodes() {
		NodeInfo[] arr = new NodeInfo[3];
		ArrayList<ShowChain.NodeInfo> nodes = new ArrayList<>();
		for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.getValuesIterator(); it.hasNext();) {
			ShowChain.NodeInfo node = it.next();
			if ((!node.host.equals(ServerAnswer.host) || node.port != ServerAnswer.port) && !ServerAnswer.waitingList.contains(node)) {
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
		int errorCounter = 0;

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
		public ClientConnection(DataInputStream dis, DataOutputStream dos){ // for files only
			dataInput = dis;
			dataOutput = dos;
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
			sendToFileOrNode(cmd,dos,false);
		}
		private void sendToFileOrNode(int cmd, DataOutputStream dos, boolean file) throws IOException {
//			System.out.println("is file: " + file);
//			System.out.println("connections list hmap: " + ConnectionsList.hmap);
//			System.out.println("connections list active nodes: " + ConnectionsList.activeNodes);
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
			}
			else {
				dos.writeInt(0);
			}
			dos.writeInt(DEAD_DEAD);
			synchronized (this) {
				dos.writeInt(blocksList.blist.size());
				for (Iterator<Block> it = blocksList.getBlocksIterator(); it.hasNext(); ) {
					Block block = it.next();
					block.writeInfo(dos);
				}
			}
			if (file){
				DataInputStream dis = new DataInputStream(new FileInputStream(new File("connectionList.txt")));
				//System.out.println("line");
				parseMessage(dis);

			}
			if (!file)
				ServerAnswer.waitingList.add(ConnectionsList.hmap.get(new Pair(this.host, this.port)));
		}

		public void parseMessage(DataInputStream dataInput) throws IOException {
//			if (dataInput.available() == 0)
//				return;
			System.out.println("<----> got new message!!");
//			try {
//				File myObj = new File("connectionList.txt");
//				Scanner myReader = new Scanner(myObj);
//				while (myReader.hasNextLine()) {
//					String data = myReader.nextLine();
//					System.out.println(data);
//				}
//				myReader.close();
//			} catch (FileNotFoundException e) {
//				System.out.println("An error occurred.");
//				e.printStackTrace();
//			}



//			int cmd = 0;
//			synchronized (this) {
//				if (errorCounter > 5) {
//					return;
//				}
//				try {
//					cmd = dataInput.readInt(); // skip command field
//				} catch (EOFException e) {
//					errorCounter++;
//					return;
//				}
//			}
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

			if (blocksList.blist.size() < receivedBlocks.size()){
				if (receivedBlocks.get(0).equals(genesis)) {
					BlocksList received = new BlocksList(receivedBlocks);
					if (received.checkValid())
						blocksList = received;
				}
			}

			// update ConnectionsList.hmap
			boolean changed = false;
			synchronized (this) {
				for (NodeInfo node : receivedNodes) {
					if (!ConnectionsList.hmap.containsKey(new Pair(node.host, node.port))) {
						if (((int) (System.currentTimeMillis() / 1000 - node.lastSeenTS) < 30 * 60)) {
							changed = true;
							ConnectionsList.add(node); // add a new node to hmap. isNew = true;
						}
					}
					else {
						NodeInfo originalNode = ConnectionsList.hmap.get(new Pair<>(node.host, node.port));
						originalNode.lastSeenTS = Math.max(originalNode.lastSeenTS, node.lastSeenTS); // the time stamp							// one
					}
				}
			}

			if (cmd == 2 && host != null) {
				synchronized (this) {
					Pair p = new Pair(this.host, this.port);
					System.out.println("host: " + host + " port: " + port);
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
				System.out.println("<----> got new node!");
				System.out.println("<----> try to connect to 3 nodes");
				tryConnection();
			}

		}

		private void connectionThread() throws IOException {
			// This function runs in a separate thread to handle the connection
			// send ConnectionsList
			parseMessage(dataInput);
//			dataInput.close();
//			dataOutput.close();
//			connectionSocket.close();
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

	static private byte[] parseByteStr(String s) {
		ArrayList<Byte> a = new ArrayList<Byte>();
		for (String hex : s.split("\\s+")) {
			byte b = (byte) Integer.parseInt(hex, 16);
			a.add(b);
		}
		byte[] result = new byte[a.size()];
		for(int i = 0; i < a.size(); i++) {
			result[i] = a.get(i);
		}
		return result;
	}

	public static Block createBlock0forTestStage() {
		Block g = new Block();
		g.data = parseByteStr(
				"00 00 00 00  00 00 00 00  \n" +
						"54 45 53 54  5F 52 30 32  \n" +
						"A8 F5 DA 01  49 47 DF C1  \n" +
						"F7 45 41 20  32 F2 88 C9  \n" +
						"D8 22 0D CB \n");
		return g;
	}


	public static void main(String[] args) {
		ServerAnswer.TeamName = args[1];
		ServerAnswer.accepPort = Integer.parseInt(args[3]);
		ServerAnswer.port = ServerAnswer.accepPort;
		ServerAnswer server = new ServerAnswer();
		ServerAnswer.host = args[2];
		ServerAnswer.walletCode = HanukCoinUtils.walletCode(args[1]);
		//ServerAnswer.walletCode = HanukCoinUtils.walletCode("Lead");
		ServerAnswer.genesis = createBlock0forTestStage();

		System.out.println("wallet: " + Integer.toHexString(ServerAnswer.walletCode));
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

				try {
					File file = new File("connectionList.txt");
					DataOutputStream FileDataOut = new DataOutputStream(new FileOutputStream(file));
					DataInputStream FileDataIn = new DataInputStream(new FileInputStream(file));
					server.readFile(FileDataIn, FileDataOut);
					FileDataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				server.sendReceive(addrTal, portTal);
			}
		});
		firstMassage.start();

		Thread fiveMin = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						//System.out.println("<----> start 5 minutes sleep");
						Thread.sleep(60000*5 - 1000*((int) (System.currentTimeMillis() / 1000) - server.lastChange));
						//System.out.println("<----> 5 minutes sleep ended");
						server.saveFile();
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
							System.out.println("<----> 5 minutes since last call");
							System.out.println("<----> try to connect to 3 nodes");
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

		Thread mining = new Thread(new Runnable() {
			@Override
			public void run() {
				ServerAnswer.blocksList.blist.add(genesis);
				Block newBlock = null;
				boolean weAreLast = false;
				while(true){
					synchronized (this) {
						weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
					}
					while (weAreLast) {
						try {
							Thread.sleep(1000);
							synchronized (this) {
								weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					while (newBlock == null){
						newBlock = HanukCoinUtils.mineCoinAtteempt(ServerAnswer.walletCode, ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size()-1), 1000000);
					}
					System.out.println("DONE MINING!!!");
					System.out.println("block chain size: " + ServerAnswer.blocksList.blist.size());
					server.tryConnection();
					synchronized (this) {
						ServerAnswer.blocksList.blist.add(newBlock);
					}
					newBlock = null;
				}
			}
		});
		mining.start();

		try {
			server.runServer();
		} catch (InterruptedException e) {
			// Exit - Ctrl -C pressed
		}
	}
}
