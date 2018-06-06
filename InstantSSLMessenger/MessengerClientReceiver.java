import java.io.IOException;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

public class MessengerClientReceiver implements Runnable {
	MessengerClient client;
	public MessengerClientReceiver(MessengerClient client) {
		this.client=client;
	}
			
	public void run() {
		try {
			client.recv();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
