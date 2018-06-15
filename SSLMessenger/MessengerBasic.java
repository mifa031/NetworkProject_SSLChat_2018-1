import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class MessengerBasic {

	protected ByteBuffer AppData; 
	protected ByteBuffer NetData;
	protected ByteBuffer NodeAppData;
	protected ByteBuffer NodeNetData;
	protected ExecutorService executor =Executors.newFixedThreadPool(10);
	
	protected  void recv(SocketChannel channel, SSLEngine engine) throws IOException{};
	protected  void send(SocketChannel channel, SSLEngine engine, String message) throws IOException{};

	protected boolean handshake(SocketChannel channel, SSLEngine engine) throws IOException{
		SSLEngineResult result;
		HandshakeStatus hstatus; // 핸드 쉐이킹 한 상태를 반환
		
		int appBufferSize = engine.getSession().getApplicationBufferSize();
		ByteBuffer appData = ByteBuffer.allocate(appBufferSize);
		ByteBuffer nodeAppData=ByteBuffer.allocate(appBufferSize);
		//네트워크로의 버퍼(읽기,쓰기를 위한) 초기화
		NetData.clear();
		NodeNetData.clear();
		
		//engine을 통한 핸드쉐이크 부분
		hstatus = engine.getHandshakeStatus();
		while(hstatus !=SSLEngineResult.HandshakeStatus.FINISHED && hstatus !=SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
			switch(hstatus) {
				case NEED_WRAP:
						NetData.clear();
						try {
							result=engine.wrap(AppData, NetData);
							hstatus=result.getHandshakeStatus();
						}catch(SSLException s){
							System.out.println("데이터 처리가 매끄럽지않아 정상적인 종료가 되지않았습니다. 서버를 정상적으로 종료합니다.");
							engine.closeOutbound(); //아웃바운드 닫음
							hstatus =engine.getHandshakeStatus();//정상적 종료를 확인
							break;
						}
						switch(result.getStatus()) {
						case OK:
							NetData.flip();
							while(NetData.hasRemaining()) {
								channel.write(NetData);
							}
							break;
						case BUFFER_OVERFLOW:
							
							NetData=enlargePacketBuffer(engine,NetData);
							break;
						case BUFFER_UNDERFLOW:
							throw new SSLException("버퍼의 랩핑후에 버퍼의 언더플로우가 발생했습니다.");
						case CLOSED:
							try {
								NetData.flip();
								while(NetData.hasRemaining()) {
									channel.write(NetData);
								}
								NodeNetData.clear();
							}catch(Exception e) {
								System.out.println("소켓 채널의 문제로, 서버의 종료 메시지를 보내지 못하였습니다.");
								hstatus=engine.getHandshakeStatus();
							}
							break;
						
					default:
						throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+result.getStatus());
					}
                break;
				case NEED_TASK:       //Task가 존재 할때
		                Runnable task;
		                while ((task = engine.getDelegatedTask()) != null) {
		                    executor.execute(task);
		                }
		                hstatus = engine.getHandshakeStatus();
		                break;
		         case FINISHED:        //엔진이 종료 되었을때
		               break;
		         case NOT_HANDSHAKING: //핸드 쉐이킹이 안됬을때 
		               break;
		         default:
		               throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+hstatus);
				case NEED_UNWRAP:
					if(channel.read(NodeNetData) < 0 ) {
						if(engine.isInboundDone() && engine.isOutboundDone()) {//엔진의 인바운드,아웃바운드 검사
							return false; 
						}
						try {
							engine.closeInbound();//인바운드 닫음
						}catch(SSLException s) {
							System.out.println("스트림의 종료로,인바운드를 닫습니다.");
						}
							engine.closeOutbound();//아웃바운드 닫음.
							
							hstatus = engine.getHandshakeStatus();//정상적 종료 확인
							break;
					}
					NodeNetData.flip(); //바이트 버퍼를쓰고 읽을때 사용
					try {
						result =engine.unwrap(NodeNetData, NodeAppData); //SSLEngine상의 랩핑 버퍼를 unwrap
						NodeNetData.compact(); 
						hstatus =result.getHandshakeStatus();
					}catch(SSLException s) {
						System.out.println("데이터 처리가 매끄럽지않아 정상적인 종료가 되지않았습니다. 서버를 정상적으로 종료합니다.");
						engine.closeOutbound();
						hstatus=engine.getHandshakeStatus();
						break;
					}
					
					switch(result.getStatus()) {
					case OK:
						break;
					case BUFFER_OVERFLOW: //오버 플로우 발생->앱 버퍼 사이즈 늘림.
						NodeAppData= enlargeAppBuffer(engine,NodeAppData);
						break;
					case BUFFER_UNDERFLOW://언더 플로우 발생->넷 버퍼 사이즈 조정
						NodeNetData= manageBufferUnderflow(engine,NodeNetData);
						break;
					case CLOSED:
						if(engine.isInboundDone()) {
							return false;
						}else{
							engine.closeOutbound();
							hstatus=engine.getHandshakeStatus();
							break;
						}
					default:
						throw new IllegalStateException("정의 되지않은 SSL엔진의 상태 : "+result.getStatus());
					}
				 break;
			}	
		}
		return true;
		
	}
	//패킷 버퍼 확장
	protected ByteBuffer enlargePacketBuffer(SSLEngine engine,ByteBuffer buffer) {
		return enlargeBuffer(buffer,engine.getSession().getPacketBufferSize());
	}
	//앱 버퍼 확장
	protected ByteBuffer enlargeAppBuffer(SSLEngine engine,ByteBuffer buffer) {
		return enlargeBuffer(buffer,engine.getSession().getApplicationBufferSize());
	}
	//세션의 크기와 버퍼의 크기 비교후 큰것을  버퍼의 사이즈 할당
	protected ByteBuffer enlargeBuffer(ByteBuffer buffer,int sessionProposedCapapcity) {
		buffer = ByteBuffer.allocate(sessionProposedCapapcity);
		
		if(sessionProposedCapapcity > buffer.capacity()) {
			buffer = ByteBuffer.allocate(sessionProposedCapapcity);
		}else {
			if(buffer.capacity()*2 > sessionProposedCapapcity){
				buffer = ByteBuffer.allocate(sessionProposedCapapcity);
			}else {
				buffer = ByteBuffer.allocate(buffer.capacity()*2);
			}
		}
		return buffer;
	}
	//언더 플로우 처리함수
	protected ByteBuffer manageBufferUnderflow(SSLEngine engine,ByteBuffer buffer) {
		if(engine.getSession().getPacketBufferSize() < buffer.limit()) {
			return buffer;
		}else {
			ByteBuffer replaceBuffer = enlargePacketBuffer(engine, buffer);
			buffer.flip();
			replaceBuffer.put(buffer);
			return replaceBuffer;
		}
	}
	//연결 종료
	protected void closeConnection(SocketChannel channel,SSLEngine engine) throws IOException{
		engine.closeOutbound();
		handshake(channel,engine);
		channel.close();
	}
	
	protected void manageEndOfStream(SocketChannel channel,SSLEngine engine) throws IOException {
		try {
			engine.closeInbound();
		}catch(Exception e) {
			System.out.println("스트림의 종료로,인바운드를 닫습니다.");
		}
		closeConnection(channel,engine);
	}
	//키 매니저 생성 함수
	protected KeyManager[] createKeyManagers(String fpath, String ksPassword,String kPassword) throws Exception{
		KeyStore ks = KeyStore.getInstance("JKS");
		InputStream ksIS = new FileInputStream(fpath);
		
		try {
			ks.load(ksIS, ksPassword.toCharArray());//키 스토어 파일 패스,키 스토어 패스워드를 통해 키 스토어 로드.
		}finally {
			if(ksIS!=null) {
				ksIS.close();
			}
		}
		//키 매니저 팩토리 생성-> 키 매니저 생성후 리턴
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, kPassword.toCharArray());
		return kmf.getKeyManagers();
	}
	//트러스트 매니저 생성함수
	protected TrustManager[] createTrustManagers(String fpath,String ksPassword) throws Exception{
		KeyStore ts=KeyStore.getInstance("JKS");
		InputStream tsIS = new FileInputStream(fpath);
		try {
			ts.load(tsIS, ksPassword.toCharArray());
		}finally {
			if(tsIS!=null) {
				tsIS.close();
			}
		}
		//트러스트 매니저 팩토리-> 트러스트 매니저 생성후 리턴
		TrustManagerFactory tsf= TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tsf.init(ts);
		return tsf.getTrustManagers();
	}
}
