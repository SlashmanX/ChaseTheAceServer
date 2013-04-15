package com.slashmanx.chasetheaceserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;

public class ServerService extends Service {
	// default ip
	public static String SERVERIP = "10.0.2.15";

	// designate a port
	public static final int SERVERPORT = 8080;

	private Handler handler = new Handler();

	private ServerSocket serverSocket;
	
	private ArrayList<Player> players;
	
	private ArrayList<Integer> usedCards;
	
	private Player theDealer;
	
	private boolean gameRunning = false;
	
	private ArrayList<Integer> playerOrder;
	
	private String[] cardNos = {"Ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King"};
	
	private String[] suits = {"Hearts", "Diamonds", "Spades", "Clubs"};


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
	@Override
    public void onCreate() {
    	
    	SERVERIP = getLocalIpAddress();
    	
    	players = new ArrayList<Player>();
    	
    	usedCards = new ArrayList<Integer>();
    	
		SERVERIP = getLocalIpAddress();
		
		Thread fst = new Thread(new ServerThread());
        fst.start();
		
    }
    
    public class ServerThread implements Runnable {

        public void run() {
            try {
                if (SERVERIP != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                        	logToServer("Listening on IP: " + SERVERIP);
                        }
                    });
                    serverSocket = new ServerSocket(SERVERPORT);
                    while (true) {
                        // listen for incoming clients
                    	Socket client = serverSocket.accept();
        				ClientThread c = new ClientThread(client);
        				c.start();
                    }
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                        	logToServer("Couldn't detect internet connection.");
                        }
                    });
                }
            } catch (Exception e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                    	logToServer("Error");
                    }
                });
                e.printStackTrace();
            }
        }
    }
    
    public class ClientThread extends Thread {
        private Socket socket = null;

        public ClientThread(Socket socket) {
        	super("ClientThread");
        	this.socket = socket;
        }

        public void run() {
	    	try {
	    	    BufferedReader in = new BufferedReader(
	    				    new InputStreamReader(
	    				    socket.getInputStream()));
	
	    	    String line = null;
	
	    	    while ((line = in.readLine()) != null) {
					final Command cmd = new Command(this.socket, line);
					handler.post(new Runnable() {
						@Override
						public void run() {
							cmd.tick();
						}
					});
	    	    }
	    	    in.close();
	    	    socket.close();
	
	    	} catch (IOException e) {
	    	    e.printStackTrace();
	    	}
        }
    }

    @Override
    public void onDestroy() {
		try {
			// make sure you close the socket upon exiting
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    @Override
    public void onStart(Intent intent, int startid) {
    }
    
	public String getLocalIpAddress() {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ipAddress = wifiInfo.getIpAddress();
		return String.format("%d.%d.%d.%d", (ipAddress & 0xff),
				(ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
				(ipAddress >> 24 & 0xff));
	}
	
	public class Player {
		private int id;
		private String name;
		private int card;
		private boolean isDealer;
		private Socket client;
		
		public Player(String name, int card, boolean isDealer, Socket client) {
			super();
			this.name = name;
			this.card = card;
			this.isDealer = isDealer;
			this.client = client;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public int getCard() {
			return card;
		}
		public void setCard(int card) {
			this.card = card;
			logToServer(this.getName() +" has: "+ cardNos[card % 13] +" of "+ suits[(int) (card / 13)]);
			this.sendCmdToClient("SETCARD:"+card);
		}
		public boolean isDealer() {
			return isDealer;
		}
		public void setDealer(boolean isDealer) {
			this.isDealer = isDealer;
		}
		public Socket getClient() {
			return client;
		}
		public void setClient(Socket client) {
			this.client = client;
		}
		
	    public void sendCmdToClient(String cmd) {
	    	PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.client.getOutputStream())), true);

		    	out.println(cmd);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	    }
		
	}
	
	public Player findPlayerByClient(Socket client){
		for(int i = 0; i < players.size(); i++) {
			if(players.get(i).getClient().equals(client)) {
				return players.get(i);
			}
		}
		return null;
	}
	
	public int dealCard() {
		boolean gotCard = false;
		
		while(!gotCard)
		{
			int card = new Random().nextInt(52);
			if(usedCards.indexOf(card) == -1)
			{
				usedCards.add(card);
				return card;
			}
		}
		return -1;
	}
	
	public class Command {
		String cmd;
		String txt;
		Player thePlayer;
		Socket client;
		public Command(Socket client, String cmd) {
			String[] tmp = cmd.split(":");
			this.cmd = tmp[0];
			if(tmp.length > 1)
				this.txt = tmp[1];
			this.client = client;
			this.thePlayer = findPlayerByClient(client);
		}
		
		public void tick() {
			Log.d("SERVICE", "Ticking: "+ this.cmd);
			if(this.cmd.equalsIgnoreCase("NEWPLAYER"))
			{
				Player tmp = new Player(this.txt, -1, false, this.client);
				players.add(tmp);
				logToServer(txt +" Connected");
			}
			
			if(this.cmd.equalsIgnoreCase("DEALCARD"))
			{
				int card = dealCard();
				thePlayer.setCard(card);
			}
			if(this.cmd.equalsIgnoreCase("NEWDEAL"))
			{
				if(thePlayer.isDealer()){
					usedCards.clear();
					for(int i = 0; i < players.size(); i++)
					{
						int card = dealCard();
						players.get(i).setCard(card);
					}
				}
			}
			if(this.cmd.equalsIgnoreCase("SWAPCARD")) {
				if(!thePlayer.isDealer()) {
					int tmp = thePlayer.getCard();
					Player nextPlayer = players.get((players.indexOf(thePlayer) + 1) % players.size());
					thePlayer.setCard(nextPlayer.getCard());
					nextPlayer.setCard(tmp);
					nextPlayer.sendCmdToClient("YOURTURN");
				}
				else {
					int card = dealCard();
					thePlayer.setCard(card);
					endGame();
				}
			}
			if(this.cmd.equalsIgnoreCase("STICK")) {
				if(!thePlayer.isDealer()) {
					Player nextPlayer = players.get((players.indexOf(thePlayer) + 1) % players.size());
					nextPlayer.sendCmdToClient("YOURTURN");
				}
				else {
					endGame();
				}
			}
			if(this.cmd.equalsIgnoreCase("DISCONNECT")){
				try {
					players.remove(thePlayer);
					thePlayer.getClient().close();
					thePlayer.setClient(null);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(this.cmd.equalsIgnoreCase("NEWDEAL")) {
				if(thePlayer.isDealer()) {
					theDealer = players.get((players.indexOf(theDealer) + 1) % players.size());
					startGame();
				}
			}
			if(this.cmd.equalsIgnoreCase("STARTNEWGAME")) {
				theDealer = players.get(0);
				for(Player p: players) {
					p.sendCmdToClient("STARTNEWGAME");
				}
				startGame();
			}
		}
	}
	
	public void endGame() {
		try {
			Thread.sleep(1000);
			Player topPlayer = theDealer;
			int topCard = theDealer.getCard() % 4;
			Player bottomPlayer = theDealer;
			int bottomCard = theDealer.getCard() % 4;
			for(Player p : players) {
				if(p.getCard() % 4 > topCard){
					topPlayer = p;
					topCard = p.getCard() % 4;
				}
				else if (p.getCard() % 4 < bottomCard) {
					bottomPlayer = p;
					bottomCard = p.getCard() % 4;
				}
			}
			
			for(Player p : players)
			{
				p.sendCmdToClient("ENDGAME");
				p.sendCmdToClient("WINNER:"+ topPlayer.getName());
				p.sendCmdToClient("LOSER:"+ bottomPlayer.getName());
			}
		} 
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void startGame() {
		theDealer.sendCmdToClient("SETDEALER");
		theDealer.setDealer(true);
		for(Player p : players){
			if(!p.isDealer()) {
				p.sendCmdToClient("UNSETDEALER");
			}
			p.setCard(dealCard());
		}
		Player nextPlayer = players.get((players.indexOf(theDealer) + 1) % players.size() );
		nextPlayer.sendCmdToClient("YOURTURN");
	}
	
	public void logToServer(String msg){
		Intent intent  = new Intent("CTA_LOG");
		intent.putExtra("msg", msg);
		sendBroadcast(intent);
	}
}
