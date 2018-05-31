import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.swing.text.html.HTMLDocument.Iterator;

public class MessengerServer extends MessengerBasic{
	
	private boolean isActiveSvr;
	private SSLContext context;
	private Selector selector;
	
	//서버 초기화 작업
	public MessengerServer(String protocol, String hostAddress, int portNumber) throws Exception {
		
		context=SSLContext.getInstance(protocol);
		context.init(createKeyManagers("C:/Users/Mr.JANG/workspace/InstantSSLMessenger/bin/.keystore/SSLSocketServerKey","123456","123456"),createTrustManagers("C:/Users/Mr.JANG/workspace/InstantSSLMessenger/bin/.keystore/SSLSocketServerKey","123456"), new SecureRandom());
		
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
		System.out.println("채팅 서버에 새로운 Connection 요청이 들어왔습니다.");
		SocketChannel channel =((ServerSocketChannel) k.channel()).accept();
		channel.configureBlocking(false); //넌 블록킹 IO 설정
		
		SSLEngine engine = context.createSSLEngine();
		engine.setUseClientMode(false); //서버 모드 설정
		engine.beginHandshake();
		
		if(handshake(channel,engine)) {
			channel.register(selector, SelectionKey.OP_READ, engine);	
		}else {
			channel.close();
			System.out.println("handshake 실패로 채팅 서버로의 Connection을 종료합니다.");
		}
	}
	protected void recv(SocketChannel channel,SSLEngine engine) throws IOException {
		NodeNetData.clear();
		
	}
	//서버 구동
	public void run() throws Exception{
		System.out.println("InstantSSLMessenger Server를 구동합니다.");
		
		while(isActivated()) {
			selector.select(); // 준비된 오퍼레이션 셋의 키 넘버를 리턴.
			Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
			
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
	
	

}
