
public class Messenger {
	public static void main(String args[]) throws Exception {
		MessengerClient client = new MessengerClient("TLS","127.0.0.1",8500);
		Thread ct = new Thread(client);
		ct.start();
		
	}
}
