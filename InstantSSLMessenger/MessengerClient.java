import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

public class MessengerClient extends MessengerBasic{
	public static String protocol = "TLS";
	public static SSLEngine engine;
	public static SocketChannel channel;
	public static String srvIP;
	public static int srvPort;
	
	
	public static void main(String args[]) throws Exception {
		// 서버와 연결
		srvIP = "127.0.0.1";
		srvPort = 8500;

		MessengerClient client = new MessengerClient();
		SSLContext context = SSLContext.getInstance(protocol);
		context.init(client.createKeyManagers("C:\\Users\\Mr.JANG\\workspace\\InstantSSLMessenger\\bin\\.keystore\\SSLSocketServerKey\\", "123456", "123456"), client.createTrustManagers("C:\\Users\\Mr.JANG\\workspace\\InstantSSLMessenger\\bin\\.keystore\\SSLSocketServerKey\\", "123456"), new SecureRandom());
		engine = context.createSSLEngine(srvIP,srvPort);
		engine.setUseClientMode(true);
		SSLSession session = engine.getSession();
		
		client.AppData = ByteBuffer.allocate(1024);
		client.NetData = ByteBuffer.allocate(1024);
		client.NodeAppData = ByteBuffer.allocate(1024);
		client.NodeNetData = ByteBuffer.allocate(1024);
		
		client.connect(srvIP, srvPort);
		
		MessengerClientReceiver receiver = new MessengerClientReceiver(protocol,srvIP,srvPort, channel, engine);
		Thread receiverThread = new Thread(receiver);
		receiverThread.start();	
		
		MessengerClientSender sender = new MessengerClientSender(protocol,srvIP,srvPort, channel, engine);
		Thread senderThread = new Thread(sender);
		senderThread.start();
		

		
	}
	
	//** 연결 부분 **
	public boolean connect(String srvIP, int srvPort) throws Exception{
		
		
		channel = SocketChannel.open();//소켓 채널 생성
		channel.configureBlocking(false); // 넌 블럭 IO 설정
		channel.connect(new InetSocketAddress(srvIP,srvPort));//소켓채널 연결
		
		//System.out.println(channel.finishConnect());
		while(!channel.finishConnect()) { // 중요!!
			
		}
		
		engine.beginHandshake();// 핸드 쉐이킷!
		return handshake(channel,engine); //쉐이킷이 성공여부를 리턴
	}
}