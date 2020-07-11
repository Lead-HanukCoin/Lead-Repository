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
	public static String pathname;
	public static long avgtime = 0;
	public static int numofcoins = 0;
	public static boolean tryconnection;
    public static boolean readytogo;

	public static void log(String fmt, Object... args) {
		println(fmt, args);
	}

	public static void println(String fmt, Object... args) {
		System.out.format(fmt + "\n", args);
	}

	private synchronized void readFile(DataInputStream dis){
		
		ClientConnection fileConnection = new ClientConnection(dis, null);
		this.fileConnection = fileConnection;
		try {
			fileConnection.parseMessage(dis);
			dis.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private synchronized void saveFile(){
		//System.out.println("*");
//		System.out.println("--------------------------------------------------------------------------- ");
//		File file = new File(ServerAnswer.pathname);
//		file.delete();

			//System.out.println("--------------------------------------------------------------------------- " + );
		try {
			File file = new File(ServerAnswer.pathname);
			file.delete();
//			File newFile = new File(ServerAnswer.pathname);
			DataOutputStream FileDataOut = new DataOutputStream(new FileOutputStream(file));
//			DataInputStream FileDataIn = new DataInputStream(new FileInputStream(file));
			FileDataOut.writeBytes("");
			ClientConnection fileConnection = new ClientConnection(null, FileDataOut);
			this.fileConnection = fileConnection;
//			FileDataIn.close();
			fileConnection.sendToFileOrNode(2, fileConnection.dataOutput, true);
			FileDataOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
//			//System.out.println("***");
	}

	private ShowChain.NodeInfo[] get3RandomNodes() {
		NodeInfo[] arr = new NodeInfo[3];
		ArrayList<ShowChain.NodeInfo> nodes = new ArrayList<>();
		for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.getValuesIterator(); it.hasNext();) {
			ShowChain.NodeInfo node = it.next();
			if ((!node.host.equals(ServerAnswer.host) || node.port != ServerAnswer.port) && (!ServerAnswer.waitingList.contains(node)) && (!node.host.equals("ivory.3utilities.com")) && (!node.host.equals("93.172.201.175"))) {
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

	private void tryConnection(boolean b) {
		if (!b){
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
		else {
			System.out.println("<----> sending massage to all nodes in the connectionlist!!");
			synchronized (this) {
				ArrayList<ShowChain.NodeInfo> nodeArray = ConnectionsList.activeNodes;
				for (ShowChain.NodeInfo node : nodeArray) {
					if (node != null) {
						if (!(node.port == ServerAnswer.port && node.host.equals(ServerAnswer.host))) {
							sendReceive(node.host, node.port);
						}
					}
				}
			}
		}
	}

	public void sendReceive(String host, int port) {
		try {
//		    if (host.equals("ivory.3utilities.com")) {
//		        return;
//            }
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
				DataInputStream dis = new DataInputStream(new FileInputStream(new File(ServerAnswer.pathname)));
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
//				File myObj = new File(ServerAnswer.pathname);
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
				tryConnection(false);
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
			SocketChannel connectionSocket;
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
		g.data = parseByteStr("00 00 00 00  00 00 00 00  \n" +
				"43 4F 4E 54  45 53 54 30  \n" +
				"6C E4 BA AA  70 1C E0 FC  \n" +
				"4B 72 9D 93  A2 28 FB 27  \n" +
				"4D 11 E7 25 ");

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
		ServerAnswer.pathname = args[4];
		ServerAnswer.tryconnection = false;
        ServerAnswer.readytogo = false;


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

		String[] parts = args[0].split(":");
		String addrTal = parts[0];
		int portTal = Integer.parseInt(parts[1]);

//		ConnectionsList.add(new NodeInfo("Earth" , addrTal, portTal,0));

		Thread firstMassage = new Thread( new Runnable() {
			public void run() {
				try {
					File file = new File(ServerAnswer.pathname);
					DataInputStream FileDataIn = new DataInputStream(new FileInputStream(file));
					server.readFile(FileDataIn);
					FileDataIn.close();
					System.out.println("blockchain size: "  + ServerAnswer.blocksList.blist.size());
				} catch (IOException e) {
					e.printStackTrace();
                    //server.sendReceive(addrTal, portTal);
                }
                server.sendReceive(addrTal, portTal);
                //server.tryConnection();
                server.saveFile();
				synchronized (this){
					ServerAnswer.readytogo = true;
				}
			}
		});
		firstMassage.start();



		Thread fiveMin = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						//System.out.println("<----> start 5 minutes sleep");
						Thread.sleep(5 * 60000 - 1000 * ((int) (System.currentTimeMillis() / 1000) - server.lastChange));
						//System.out.println("<----> 5 minutes sleep ended");
						server.saveFile();
						for (Iterator<ShowChain.NodeInfo> it = ConnectionsList.getValuesIterator(); it.hasNext();) {
							ShowChain.NodeInfo node = it.next();
							// delete nodes that weren't active in 30 min
							if ((((int) (System.currentTimeMillis() / 1000 - node.lastSeenTS)) >= 30 * 60)) {
								ConnectionsList.hmap.remove(node);
								if (!node.isNew) {
									ConnectionsList.activeNodes.remove(node);
								}
								if (ServerAnswer.waitingList.contains(node)) {
									ServerAnswer.waitingList.remove(node);
								}
							}
						}
						if (((int) (System.currentTimeMillis() / 1000)) - server.lastChange >= 5 * 60) {
							System.out.println("<----> 5 minutes since last call");
							System.out.println("<----> try to connect to 3 nodes");
							server.tryConnection(false);
							server.lastChange = (int) (System.currentTimeMillis() / 1000.);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		fiveMin.start();

		Thread trytoconmine = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					synchronized (this){
						if (ServerAnswer.tryconnection) {
							ServerAnswer.tryconnection = false;
							server.tryConnection(true);
						}
					}
				}
			}
		});
        trytoconmine.start();

		Thread mining = new Thread(new Runnable() {
			@Override
			public void run() {
				Block newBlock = null;
				boolean weAreLast = false;
				long timeofmine;
				while (true) {
					try {
						synchronized (this) {
							weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
						}
						while (weAreLast) {
							try {
								Thread.sleep(250);
								synchronized (this) {
									weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
								}
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						System.out.println("Thread0 - start mining attempt!!");
						long startime = System.currentTimeMillis() / 1000;
						while (newBlock == null && !weAreLast) {
							synchronized (this) {
								newBlock = HanukCoinUtils.mineCoinAtteempt0(ServerAnswer.walletCode, ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1), 1000000);
								weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
							}
						}
						synchronized (this) {
							if (!(ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode)) {
								ServerAnswer.numofcoins++;
								System.out.println("Thread0 - $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
								System.out.println("Thread0 - block chain size: " + ServerAnswer.blocksList.blist.size());
								timeofmine = Math.round((System.currentTimeMillis() / 1000.) - startime);
								System.out.println("Thread0 - time of mining: " + timeofmine + " seconds");
								ServerAnswer.avgtime += timeofmine;
								System.out.println("Thread0 - average time of mining: " + (ServerAnswer.avgtime / ServerAnswer.numofcoins) + " seconds");
								ServerAnswer.blocksList.blist.add(newBlock);
								ServerAnswer.tryconnection = true;
							} else {
								System.out.println("Thread0 - another thread already mine a coin!");
							}
						}
						newBlock = null;
					}

					catch (ArrayIndexOutOfBoundsException | NullPointerException ignored) {

					}
				}
			}
		});

		Thread mining1 = new Thread(new Runnable() {
			@Override
			public void run() {
				Block newBlock = null;
				boolean weAreLast = false;
				long timeofmine;
				while (true) {
					try {
						synchronized (this) {
							weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
						}
						while (weAreLast) {
							try {
								Thread.sleep(250);
								synchronized (this) {
									weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
								}
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						System.out.println("Thread1 - start mining attempt!!");
						long startime = System.currentTimeMillis() / 1000;
						while (newBlock == null && !weAreLast) {
							synchronized (this) {
								newBlock = HanukCoinUtils.mineCoinAtteempt1(ServerAnswer.walletCode, ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1), 1000000);
								weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
							}
						}
						synchronized (this) {
							if (!(ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode)) {
								ServerAnswer.numofcoins++;
								System.out.println("Thread1 - $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
								System.out.println("Thread1 - block chain size: " + ServerAnswer.blocksList.blist.size());
								timeofmine = Math.round((System.currentTimeMillis() / 1000.) - startime);
								System.out.println("Thread1 - time of mining: " + timeofmine + " seconds");
								ServerAnswer.avgtime += timeofmine;
								System.out.println("Thread1 - average time of mining: " + (ServerAnswer.avgtime / ServerAnswer.numofcoins) + " seconds");
								ServerAnswer.blocksList.blist.add(newBlock);
								ServerAnswer.tryconnection = true;
							} else {
								System.out.println("Thread1 - another thread already mine a coin!");
							}
						}
						newBlock = null;
					}

					catch (ArrayIndexOutOfBoundsException | NullPointerException ignored) {

					}
				}
			}
		});

        Thread mining2 = new Thread(new Runnable() {
            @Override
			public void run() {
				Block newBlock = null;
				boolean weAreLast = false;
				long timeofmine;
				while (true) {
					try {
						synchronized (this) {
							weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
						}
						while (weAreLast) {
							try {
								Thread.sleep(250);
								synchronized (this) {
									weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
								}
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						System.out.println("Thread2 - start mining attempt!!");
						long startime = System.currentTimeMillis() / 1000;
						while (newBlock == null && !weAreLast) {
							synchronized (this) {
								newBlock = HanukCoinUtils.mineCoinAtteempt2(ServerAnswer.walletCode, ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1), 1000000);
								weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
							}
						}
						synchronized (this) {
							if (!(ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode)) {
								ServerAnswer.numofcoins++;
								System.out.println("Thread2 - $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
								System.out.println("Thread2 - block chain size: " + ServerAnswer.blocksList.blist.size());
								timeofmine = Math.round((System.currentTimeMillis() / 1000.) - startime);
								System.out.println("Thread2 - time of mining: " + timeofmine + " seconds");
								ServerAnswer.avgtime += timeofmine;
								System.out.println("Thread2 - average time of mining: " + (ServerAnswer.avgtime / ServerAnswer.numofcoins) + " seconds");
								ServerAnswer.blocksList.blist.add(newBlock);
								ServerAnswer.tryconnection = true;
							} else {
								System.out.println("Thread2 - another thread already mine a coin!");
							}
						}
						newBlock = null;
					}

					catch (ArrayIndexOutOfBoundsException | NullPointerException ignored) {

					}
				}
			}
        });

        Thread mining3 = new Thread(new Runnable() {
            @Override
			public void run() {
				Block newBlock = null;
				boolean weAreLast = false;
				long timeofmine;
				while (true) {
					try {
						synchronized (this) {
							weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
						}
						while (weAreLast) {
							try {
								Thread.sleep(250);
								synchronized (this) {
									weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
								}
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						System.out.println("Thread3 - start mining attempt!!");
						long startime = System.currentTimeMillis() / 1000;
						while (newBlock == null && !weAreLast) {
							synchronized (this) {
								newBlock = HanukCoinUtils.mineCoinAtteempt3(ServerAnswer.walletCode, ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1), 1000000);
								weAreLast = ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode;
							}
						}
						synchronized (this) {
							if (!(ServerAnswer.blocksList.blist.get(ServerAnswer.blocksList.blist.size() - 1).getWalletNumber() == ServerAnswer.walletCode)) {
								ServerAnswer.numofcoins++;
								System.out.println("Thread3 - $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
								System.out.println("Thread3 - block chain size: " + ServerAnswer.blocksList.blist.size());
								timeofmine = Math.round((System.currentTimeMillis() / 1000.) - startime);
								System.out.println("Thread3 - time of mining: " + timeofmine + " seconds");
								ServerAnswer.avgtime += timeofmine;
								System.out.println("Thread3 - average time of mining: " + (ServerAnswer.avgtime / ServerAnswer.numofcoins) + " seconds");
								ServerAnswer.blocksList.blist.add(newBlock);
								ServerAnswer.tryconnection = true;
							} else {
								System.out.println("Thread3 - another thread already mine a coin!");
							}
						}
						newBlock = null;
					}

					catch (ArrayIndexOutOfBoundsException | NullPointerException ignored) {

					}
				}
			}
        });
//        while (true){
//        	if (ServerAnswer.readytogo) {
        		mining.start();
        		mining1.start();
        		mining2.start();
        		mining3.start();
//				break;
//        	}
//        }

        try {
			server.runServer();
		} catch (InterruptedException e) {
			// Exit - Ctrl -C pressed
		}
	}
}
