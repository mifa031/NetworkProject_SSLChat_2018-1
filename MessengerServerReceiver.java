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
	
	private boolean isActiveSvr;
	private SSLContext context;
	private Selector selector;
	private Queue queue = new LinkedList();
	//리시버 함수 o 센더함수 x 
	//서버 초기화 작업
	public MessengerServerReceiver(String protocol, String hostAddress, int portNumber) throws Exception {
		
		context=SSLContext.getInstance(protocol);
		context.init(createKeyManagers("C:\\Users\\Mr.JANG\\workspace\\InstantSSLMessenger\\bin\\.keystore\\SSLSocketServerKey\\","123456","123456"),createTrustManagers("C:\\Users\\Mr.JANG\\workspace\\InstantSSLMessenger\\bin\\.keystore\\SSLSocketServerKey\\","123456"), new SecureRandom());
		
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
		}else {
			channel.close();
			System.out.println("handshake 실패로 채팅 서버로의 Connection을 종료합니다.");
		}
	}
	//클라이언트로 메시지 받음
	synchronized protected void recv(SocketChannel channel,SSLEngine engine) throws IOException {
		NodeNetData.clear();
		int byterecv = channel.read(NodeNetData);
		
		if(byterecv > 0) {
			NodeNetData.flip();
			while(NodeNetData.hasRemaining()) {
				NodeAppData.clear();
				SSLEngineResult result = engine.unwrap(NodeNetData, NodeAppData);
				//NodeNetData.compact();
				
				
				switch(result.getStatus()) {
				case OK:
					NodeAppData.flip();//제거 대상?
					//NodeAppData.compact();
					//Charset charset = Charset.forName("UTF-8");
					Charset charset = Charset.defaultCharset();
					String aaa = charset.decode(NodeAppData).toString();
					System.out.println("유저 이름 : "+aaa);
					//send(channel,engine,"InstantMessenger에 오신것을 환영합니다.");
					//send(channel,engine,new String(NodeAppData.array()));
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
	}
	//클라이언트로 메시지 전송
	synchronized public void send(SocketChannel channel,SSLEngine engine,String m)throws IOException{
		
		AppData.clear();
		AppData.put(m.getBytes());
		AppData.flip();
	
		while(AppData.hasRemaining()) {
			NetData.clear();
			SSLEngineResult result =engine.wrap(AppData,NetData);
			switch(result.getStatus()) {
			
			case OK:
				NetData.flip();
				while(NetData.hasRemaining()) {
					channel.write(NetData);
				}
				break;
			case BUFFER_OVERFLOW:
				NetData=enlargePacketBuffer(engine,NetData);
			case BUFFER_UNDERFLOW:
				throw new SSLException("버퍼를 랩핑하고 난후에, 버퍼의 언더플로우가 발생했습니다.");
			case CLOSED:
				closeConnection(channel,engine);
				return;
			default:
			    throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+result.getStatus());
			}
		}
	}
	//서버 종료
	public void stop() {
		System.out.println("서버를 종료합니다.");
		isActiveSvr=false;
		executor.shutdown();
		selector.wakeup();
	}
	//서버 구동
	synchronized public void startServer() throws Exception{
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

	public void run() {
		try {
			startServer();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
