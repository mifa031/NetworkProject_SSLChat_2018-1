import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

public class MessengerClientReceiver extends MessengerBasic implements Runnable {
	private String remoteAddr;
	private int portNum;
	private static SSLEngine engine;
	private static SocketChannel channel;
	
	public MessengerClientReceiver(String protocol, String remoteAddr, int portNum, SocketChannel channel, SSLEngine engine, SSLSession session) throws Exception{
		
		this.remoteAddr = remoteAddr;
		this.portNum = portNum;
		this.channel = channel;
		this.engine = engine;
		
		//SSLContext context = SSLContext.getInstance(protocol);
		//context.init(createKeyManagers("D:\\workspace\\180604_1\\bin\\.keystore\\SSLSocketServerKey\\", "123456", "123456"), createTrustManagers("D:\\workspace\\180604_1\\bin\\.keystore\\SSLSocketServerKey\\", "123456"), new SecureRandom());
		
		//engine = context.createSSLEngine(remoteAddr,portNum);
		//engine.setUseClientMode(true);
		
		//SSLSession session = engine.getSession();
		
		AppData = ByteBuffer.allocate(1024);
		NetData = ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData = ByteBuffer.allocate(1024);
		NodeNetData = ByteBuffer.allocate(session.getPacketBufferSize());
		
	}
	
	public void run() {
		try {
			while(channel.isConnected()) {
				recv();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
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
					Charset charset = Charset.defaultCharset();
					String message = charset.decode(NodeAppData).toString();
					System.out.println(message);
					break;
				case BUFFER_OVERFLOW:
					NodeAppData =enlargeAppBuffer(engine,NodeAppData);
			   
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
}