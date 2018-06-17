import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
	private static SSLEngine engine = null;
	private static SocketChannel channel = null;
	private MessengerClientReceivedMsg recvMsg; 
	private Messenger frame;
	boolean isClosed = false;
	
	public MessengerClientReceiver(String protocol, String remoteAddr, int portNum, SocketChannel channel, SSLEngine engine, MessengerClientReceivedMsg recvMsg, Messenger frame) throws Exception{
		
		this.remoteAddr = remoteAddr;
		this.portNum = portNum;
		this.channel = channel;
		this.engine = engine;
		this.recvMsg = recvMsg;
		this.frame = frame;
		
		SSLSession session = engine.getSession();
		
		AppData = ByteBuffer.allocate(session.getApplicationBufferSize());
		NetData = ByteBuffer.allocate(session.getPacketBufferSize()); 
		NodeAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
		NodeNetData = ByteBuffer.allocate(session.getPacketBufferSize());
	}
	
	public void run() {
		try {
			while(!isClosed) {
				recv();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void recv() throws Exception{
		recv(channel,engine);
	}
	//**수신 메소드**
	synchronized protected void recv(SocketChannel channel, SSLEngine engine) throws Exception {
		
		NodeNetData.clear();
		int waitToRecvMillis = 50;
		
		int byterecv = channel.read(NodeNetData);
		if(byterecv > 0) {
			NodeNetData.flip();
			while(NodeNetData.hasRemaining() && !isClosed) {
				SSLEngineResult result = engine.unwrap(NodeNetData, NodeAppData);
				
				switch(result.getStatus()) {
				case OK:
					NodeAppData.flip();
					Charset charset = Charset.defaultCharset();
					recvMsg.msg = charset.decode(NodeAppData).toString();
					frame.receivingTextArea.append(recvMsg.msg+"\n");
					frame.receivingTextArea.setCaretPosition(frame.receivingTextArea.getDocument().getLength());
					break;
				case BUFFER_OVERFLOW:
					NodeAppData =enlargeAppBuffer(engine,NodeAppData);
					break;
				case BUFFER_UNDERFLOW:
					NodeNetData = manageBufferUnderflow(engine,NodeNetData);
					break;
				case CLOSED:
					isClosed = true;
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