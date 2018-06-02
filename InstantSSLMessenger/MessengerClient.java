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

public class MessengerClient extends MessengerBasic{
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
		AppData = ByteBuffer.allocate(1024);
		NetData = ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData = ByteBuffer.allocate(1024);
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
	protected void send(SocketChannel channel,SSLEngine engine,String message) throws IOException {
		AppData.clear();
		AppData.put(message.getBytes());
		AppData.flip();
		
		while(AppData.hasRemaining()) {
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
		protected void recv(SocketChannel channel, SSLEngine engine) throws IOException {
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
					case BUFFER_UNDERFLOW:
						NodeNetData = manageBufferUnderflow(engine,NodeNetData);
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
		public void login() throws IOException{
				
			System.out.println("--------------------------------------------------");
			System.out.println("-           InstantSSLMessenger v1.0             -");
			System.out.println("--------------------------------------------------");
			System.out.print("- 사용할 닉네임을 입력하세요. : ");
			Scanner scan = new Scanner(System.in);
			nickName=scan.nextLine();
			if(nickName.equals(""))
				loginOk=false;
			else 
				loginOk=true;
			send("nickName : ");
			System.out.println("");
		}
		
		public void checkCreateChatRoom() {
			System.out.print("- 채팅방을 개설 하시겠습니까?(Y/N) : ");
			Scanner scan = new Scanner(System.in);
			checkCreateRoom=scan.nextLine();
			if(checkCreateRoom.equals("Y")) {
				//
			     
			}
			else if(checkCreateRoom.equals("N")) {
				System.out.println("--------------------------------------------------");
				System.out.println("-                    채팅방 List                  -");
				System.out.println("--------------------------------------------------");
				
				//
				
			}else {
				checkCreateChatRoom();
			}
		}
	
		public static void main(String args[]) {
			
			try {
				MessengerClient client = new MessengerClient("TLS","59.187.211.231",8500);
				client.connect();
				while(!loginOk) {
					client.login();
				}
			//
			//
			
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}
