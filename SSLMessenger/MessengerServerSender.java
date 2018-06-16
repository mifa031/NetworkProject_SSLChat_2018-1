import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

public class MessengerServerSender extends MessengerBasic implements Runnable {
	//리시버 함수 x 센더 함수 o
	
	private SSLEngine engine = null;
	private SocketChannel channel = null;
	private String message;
	public MessengerServerSender(SSLEngine engine,SocketChannel channel,String message) {
		this.engine = engine;
		this.channel = channel;
		this.message = message;
		
		SSLSession session= engine.getSession();
		
		AppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NetData=ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NodeNetData=ByteBuffer.allocate(session.getPacketBufferSize());
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
	public void run() {
		try {
			send(channel,engine,message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
