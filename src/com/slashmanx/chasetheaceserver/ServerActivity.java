package com.slashmanx.chasetheaceserver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;

public class ServerActivity extends Activity {

	public static TextView serverStatus;
	private StatusUpdateReceiver statusUpdateReceiver;
	public static TextView serverIpText;

	// default ip
	public static String SERVERIP = "127.0.0.1";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.server);
		serverStatus = (TextView) findViewById(R.id.server_status);
		serverIpText = (TextView) findViewById(R.id.server_ip);

		SERVERIP = getLocalIpAddress();

		serverIpText.setText("Server IP:" + SERVERIP);
		
		startService(new Intent(this, ServerService.class));
	}
	
	public void onResume() {
		super.onResume();
		if (statusUpdateReceiver == null) statusUpdateReceiver = new StatusUpdateReceiver();
		IntentFilter intentFilter = new IntentFilter("CTA_LOG");
		registerReceiver(statusUpdateReceiver, intentFilter);
	}


	// gets the ip address of your phone's network
	public String getLocalIpAddress() {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ipAddress = wifiInfo.getIpAddress();
		return String.format("%d.%d.%d.%d", (ipAddress & 0xff),
				(ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
				(ipAddress >> 24 & 0xff));
	}
	
	protected void onDestroy() {
		super.onDestroy();
		stopService(new Intent(this, ServerService.class));
		unregisterReceiver(statusUpdateReceiver);
	}
	
	private class StatusUpdateReceiver extends BroadcastReceiver {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        if (intent.getAction().equals("CTA_LOG")) {
	        	serverStatus.setText(serverStatus.getText() + "\n" + intent.getStringExtra("msg"));
	        }
	    }
	}
	
	public static void setServerIpText(String text)
	{
		serverIpText.setText(text);
	}

}