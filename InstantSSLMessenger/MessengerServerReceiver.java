import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MessengerServerReceiver extends MessengerBasic implements Runnable {

	private SSLEngine engine=null;
	private SocketChannel channel=null;
	MessengerRoomUserInfo roomUserInfo=null;
	
	public MessengerServerReceiver(SSLEngine engine,SocketChannel channel,MessengerRoomUserInfo roomUserInfo, Selector selector) {
		this.engine = engine;
		this.channel =channel;
		this.roomUserInfo=roomUserInfo;
		SSLSession session = engine.getSession();
		
		AppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NetData=ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NodeNetData=ByteBuffer.allocate(session.getPacketBufferSize());
		
		SelectionKey k = channel.keyFor(selector);
		roomUserInfo.user.add(k);
		System.out.print(k);
	}

	//클라이언트로 메시지 받음
	synchronized protected void recv(SocketChannel channel,SSLEngine engine) throws IOException {
		
		NodeNetData.clear();
		int waitToRecvMillis=50;
		int byterecv = channel.read(NodeNetData);
		
		if(byterecv > 0) {
			NodeNetData.flip();
			while(NodeNetData.hasRemaining()) {
				NodeAppData.clear();
				SSLEngineResult result = engine.unwrap(NodeNetData, NodeAppData);				
				switch(result.getStatus()) {
				case OK:
					NodeAppData.flip();
					
					Charset charset = Charset.defaultCharset();
					String message = charset.decode(NodeAppData).toString();
					//System.out.println(message);
					
					for(SelectionKey u : roomUserInfo.user) {
						//System.out.println(u);
						MessengerServerSender sender = new MessengerServerSender(engine,(SocketChannel) u.channel(),message);
						Thread st2 = new Thread(sender);
						st2.start();
					}
					break;
				case BUFFER_OVERFLOW:
					NodeAppData = enlargeAppBuffer(engine,NodeAppData);
					break;
				case BUFFER_UNDERFLOW:
					NodeNetData = manageBufferUnderflow(engine,NodeNetData);
					break;
				case CLOSED:
					System.out.println("클라이언트 쪽의 종료 요청으로, 채팅 서버를 종료합니다.");
					closeConnection(channel,engine);
					return;
				default:
					throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+result.getStatus());
				}
			}
		}else if(byterecv < 0) {
			System.out.println("스트림의 종료로 클아이언트와 종료합니다.");
			manageEndOfStream(channel,engine);
		}
		try {
			Thread.sleep(waitToRecvMillis);
		} catch (InterruptedException e) {
			
			e.printStackTrace();
		}
	}

	
	public void run() {
		while(channel.isConnected()) {
			try {
				recv(channel,engine);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
