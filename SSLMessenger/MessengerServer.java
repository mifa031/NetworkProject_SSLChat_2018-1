import java.io.IOException;
import java.net.InetAddress;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

public class MessengerServer extends MessengerBasic  {
	//서버 스타트 함수
	public boolean isActiveSvr;
	public SSLContext context;
	public Selector selector;
	MessengerRoomUserInfo roomUserInfo;
	
	public MessengerServer(String protocol, String hostAddress, int portNumber) throws Exception {
	    roomUserInfo = new MessengerRoomUserInfo();
		context=SSLContext.getInstance(protocol);
		context.init(createKeyManagers(".\\.keystore\\SSLSocketServerKey\\","123456","123456"),createTrustManagers(".\\.keystore\\SSLSocketServerKey\\","123456"), new SecureRandom());
		
		//엔진상의 세션을 통하여, 넷상으로 보낼/받을 버퍼,앱상으로 보낼/받을 버퍼 사이즈 초기화
		SSLSession session = context.createSSLEngine().getSession();
			
		AppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NetData=ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NodeNetData=ByteBuffer.allocate(session.getPacketBufferSize());
		session.invalidate();
		
		//실렉터 프로바이더로 실렉터 생성과 초기화
		selector=SelectorProvider.provider().openSelector();
		ServerSocketChannel channel = ServerSocketChannel.open(); 
		channel.configureBlocking(false);
		channel.socket().bind(new InetSocketAddress(hostAddress,portNumber));
		channel.register(selector, SelectionKey.OP_ACCEPT);
		
		isActiveSvr = true;
	}
	//Server의 구동 상태 리턴
	public boolean isActivated() {
		return isActiveSvr;
	}
	//서버가 읽을 준비가 됬을때, accept 작업
	private void accept(SelectionKey k) throws Exception {
		
		SocketChannel channel =((ServerSocketChannel) k.channel()).accept();
		channel.configureBlocking(false); //넌 블록킹 IO 설정
		
		SSLEngine engine = context.createSSLEngine();
		engine.setUseClientMode(false); //서버 모드 설정
		engine.beginHandshake();
		System.out.println(channel.socket().getInetAddress()+" 에서 채팅서버에 입장했습니다.");
		if(handshake(channel,engine)) {
			channel.register(selector, SelectionKey.OP_READ, engine);
			MessengerServerReceiver receiver = new MessengerServerReceiver(engine,channel,roomUserInfo, selector);
			Thread st1 = new Thread(receiver);
			st1.start();
			
			
		}else {
			channel.close();
			System.out.println("handshake 실패로 채팅 서버로의 Connection을 종료합니다.");
		}
	}
	//서버 구동
	synchronized public void startServer() throws Exception{
		System.out.println("InstantSSLMessenger Server를 구동합니다.");
		
		while(isActivated()) {
			selector.select(); // 준비된 오퍼레이션 셋의 키 넘버를 리턴.
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();//대기열
			
			while(selectedKeys.hasNext()) {
				SelectionKey k = selectedKeys.next();
				selectedKeys.remove();
		
				if(!k.isValid()) {
					continue;
				}
				
				if(k.isAcceptable()) {
					accept(k);
				}else if(k.isReadable()) {	
					recv((SocketChannel) k.channel(),(SSLEngine) k.attachment());
				}
				
			}
		}
		System.out.println("InstantSSLMessenger Server가 종료됩니다.");
	}
	//서버 종료
	public void stop() {
		System.out.println("서버를 종료합니다.");
		isActiveSvr=false;
		executor.shutdown();
		selector.wakeup();
	}
	
	public static void main(String args[]) {
		try {		
			MessengerServer server = new MessengerServer("TLS","localhost",8700);
			server.startServer();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
