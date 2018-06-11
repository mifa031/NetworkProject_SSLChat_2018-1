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
//리시버 함수 x 센더 함수 o
public class MessengerClientSender extends MessengerBasic implements Runnable{
	private String remoteAddr;
	private int portNum;
	private SSLEngine engine;
	private SocketChannel channel;
    private static String nickName="";
	private static boolean loginOk=false;
	private static String checkCreateRoom="";

	//버퍼의 사이즈 설정 및 연결을 위한 준비.
	public MessengerClientSender(String protocol, String remoteAddr, int portNum, SocketChannel channel, SSLEngine engine) throws Exception{
		
		this.remoteAddr = remoteAddr;
		this.portNum = portNum;
		this.channel = channel;
		this.engine = engine;
		
		//SSLContext context = SSLContext.getInstance(protocol);
		//context.init(createKeyManagers("D:\\workspace\\180604_1\\bin\\.keystore\\SSLSocketServerKey\\", "123456", "123456"), createTrustManagers("C:\\Users\\Mr.JANG\\workspace\\InstantSSLMessenger\\bin\\.keystore\\SSLSocketServerKey\\", "123456"), new SecureRandom());
		
		//engine = context.createSSLEngine(remoteAddr,portNum);
		//engine.setUseClientMode(true);
		
		SSLSession session = engine.getSession();
		
		AppData = ByteBuffer.allocate(1024);
		NetData = ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData = ByteBuffer.allocate(1024);
		NodeNetData = ByteBuffer.allocate(session.getPacketBufferSize());
		
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
 
		//종료
		public void shutdown() throws IOException{
			closeConnection(channel,engine);
			executor.shutdown();
			System.out.println("GoodBye");
		}

	
		public void run(){
			
			try {
				System.out.print("입력  : ");
				Scanner scan = new Scanner(System.in);
				String msg = scan.nextLine();
				send(msg);
				while(channel.isConnected()) {
					//지금 안찍힘.
					msg = scan.nextLine();
					send(msg);
				
				}
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}