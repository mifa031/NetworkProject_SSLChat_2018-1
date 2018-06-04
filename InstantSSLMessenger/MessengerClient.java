import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

public class MessengerClient extends MessengerBasic implements Runnable{
	private String remoteAddr;
	private int portNum;
	private static SSLEngine engine;
	private static SocketChannel channel;
    private static String nickName="";
	private static boolean loginOk=false;
	private static String checkCreateRoom="";

	//버퍼의 사이즈 설정 및 연결을 위한 준비.
	public MessengerClient(String protocol, String remoteAddr, int portNum) throws Exception{
		
		this.remoteAddr = remoteAddr;
		this.portNum = portNum;
		
		SSLContext context = SSLContext.getInstance(protocol);
		context.init(createKeyManagers("C:/Users/Mr.JANG/workspace/InstantSSLMessenger/bin/.keystore/SSLSocketServerKey", "123456", "123456"), createTrustManagers("C:/Users/Mr.JANG/workspace/InstantSSLMessenger/bin/.keystore/SSLSocketServerKey", "123456"), new SecureRandom());
		
		engine = context.createSSLEngine(remoteAddr,portNum);
		engine.setUseClientMode(true);
		
		SSLSession session = engine.getSession();
		AppData = ByteBuffer.allocate(100);
		NetData = ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData = ByteBuffer.allocate(100);
		NodeNetData = ByteBuffer.allocate(session.getPacketBufferSize());
	}
	//** 연결 부분 **
	public boolean connect() throws Exception{
		channel = SocketChannel.open();//소켓 채널 생성
		channel.configureBlocking(false); // 넌 블럭 IO 설정
		channel.connect(new InetSocketAddress(remoteAddr,portNum));//소켓채널 연결
		while(!channel.finishConnect()) {
			
		}
		
		engine.beginHandshake();// 핸드 쉐이킷!
		return handshake(channel,engine); //쉐이킷이 성공여부를 리턴
	}
	//서버로 메시지 전송(껍데기)
	public void send(String message) throws IOException{
		send(channel,engine,message);	
	}
	//**send 부분**
	// 버퍼를 생성->랩핑->랩핑한 버퍼를 체널에 써서, send!
 synchronized protected void send(SocketChannel channel,SSLEngine engine,String message) throws IOException {
		AppData.clear();
		AppData.put(message.getBytes());
		AppData.flip();
		
		while(AppData.hasRemaining()) {
				NetData.clear();
				NetData.put(new byte[30]);
				NetData.clear();
				SSLEngineResult result = engine.wrap(AppData,NetData);
				switch(result.getStatus()) {
				case OK:
					NetData.flip();
					while(NetData.hasRemaining()) {
						channel.write(NetData);
						
					}
					break;
				case BUFFER_OVERFLOW:
					NetData = enlargePacketBuffer(engine,NetData);
					break;
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
		//수신 메소드 (껍데기)
		public void recv() throws IOException{
			recv(channel,engine);
		}
		//**수신 메소드**
	synchronized protected void recv(SocketChannel channel, SSLEngine engine) throws IOException {
			NodeNetData.clear();
			int waitToRecvMillis = 50;
			
			int byterecv = channel.read(NodeNetData);
			if(byterecv > 0) {
				NodeNetData.flip();
				
				while(NodeNetData.hasRemaining()) {
					NodeNetData.clear();
					SSLEngineResult result = engine.unwrap(NodeNetData, NodeAppData);
					
					switch(result.getStatus()) {
					case OK:
						NodeAppData.flip();
						System.out.println(new String(NodeAppData.array()));
						break;
					case BUFFER_OVERFLOW:
						NodeAppData =enlargeAppBuffer(engine,NodeAppData);
						break;
					case BUFFER_UNDERFLOW:
						NodeNetData = manageBufferUnderflow(engine,NodeNetData);
						break;
					case CLOSED:
						closeConnection(channel,engine);
						return;
					default:
						throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+result.getStatus());				
					}
				}
			}else if(byterecv < 0) {
				manageEndOfStream(channel,engine);
				return;
			}
		
			try {
				Thread.sleep(waitToRecvMillis);
			} catch (InterruptedException e) {
				
				e.printStackTrace();
			}
		}
		//종료
		public void shutdown() throws IOException{
			closeConnection(channel,engine);
			executor.shutdown();
			System.out.println("GoodBye");
		}

	
		public void run(){
			
			try {
				connect();
				//MessengerClientReceiver receiver = new MessengerClientReceiver(this);
				while(true) {
					System.out.print("입력  : ");
					Scanner scan = new Scanner(System.in);
					String msg = scan.nextLine();
					//System.out.println("User : "+msg);
					send(msg);
					//receiver.run();
				}
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}
