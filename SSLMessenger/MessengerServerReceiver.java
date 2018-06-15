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
import java.util.Set;
import java.util.Vector;

public class MessengerServerReceiver extends MessengerBasic implements Runnable {

	private SSLEngine engine=null;
	private SocketChannel channel=null;
	MessengerRoomUserInfo roomUserInfo=null;
	private SelectionKey k=null;
	
	public MessengerServerReceiver(SSLEngine engine,SocketChannel channel,MessengerRoomUserInfo roomUserInfo, Selector selector) {
		this.engine = engine;
		this.channel =channel;
		this.roomUserInfo=roomUserInfo;
		SSLSession session = engine.getSession();
		
		AppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NetData=ByteBuffer.allocate(session.getPacketBufferSize());
		NodeAppData=ByteBuffer.allocate(session.getApplicationBufferSize());
		NodeNetData=ByteBuffer.allocate(session.getPacketBufferSize());
		
		k = channel.keyFor(selector);
		
		
	}

	//클라이언트로 메시지 받음
	synchronized protected void recv(SocketChannel channel,SSLEngine engine) throws IOException {
		
		NodeNetData.clear();
		int waitToRecvMillis=50;
		int byterecv = channel.read(NodeNetData);
		
		if(byterecv > 0) {
			NodeNetData.flip();
			while(NodeNetData.hasRemaining()) {
				NodeAppData.clear();
				SSLEngineResult result = engine.unwrap(NodeNetData, NodeAppData);				
				switch(result.getStatus()) {
				case OK:
					NodeAppData.flip();
					
					Charset charset = Charset.defaultCharset();
					String message = charset.decode(NodeAppData).toString();
					String [] m_array=null;
					String control_type=null;
					
					m_array=message.split("@");
					control_type=m_array[1];
					
					switch(control_type) {
					
						case "userinfo" :
								addUser(m_array[2],engine,k);
								break;
						case "chatroomlist" :
							    getRoomList();
							    break;
						case "createroom" :
							    addRoom(m_array[2]);
							    break;
						case "enterchatroom" :
								for(UserInfo u : roomUserInfo.info) {
									if(u.key==k) {
											addRoomUser(u);
									}
								}
								break;								
						case "exitchatroom" :
							for(UserInfo u : roomUserInfo.info) {
								if(u.key==k) {
											removeRoomUser(u);
								}
							}
								break;				
						default :
							for(UserInfo u:roomUserInfo.info) {
								if(u.key==k) {
									for(String roomname : roomUserInfo.room) {
										if(u.roomname==roomname) {
											broadcastRoomUser(roomname,m_array[1]);
										}else {
											String alert_message="채팅방에 입장한 뒤에 메시지 입력이 가능합니다.";
											send(channel,engine,alert_message);
										}
									}
								}
							}
							    break;
					}
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
		try {
			Thread.sleep(waitToRecvMillis);
		} catch (InterruptedException e) {
			
			e.printStackTrace();
		}
	}
	public void addUser(String id,SSLEngine engine, SelectionKey key) {
		UserInfo userInfo = new UserInfo();
		userInfo.id=id;
		userInfo.engine=engine;
		userInfo.key=key;
		
		roomUserInfo.info.add(userInfo);
		
	}
	//채팅방 추가
	public void addRoom(String roomname) {
		Vector<UserInfo> roomvector = new Vector<UserInfo>();
		roomUserInfo.map.put(roomname,roomvector);
	}
	//채팅방 삭제
	public void removeRoom(String roomname) {
		roomUserInfo.map.remove(roomname);
	}
	//채팅방에 유저 추가
	public void addRoomUser(UserInfo u) {
		Vector<UserInfo> roomvector = roomUserInfo.map.get(u.roomname);
		roomvector.add(u);
	}
	//채팅방에서 유저 삭제
	public void removeRoomUser(UserInfo u) {
		Vector<UserInfo> roomvector = roomUserInfo.map.get(u.roomname);
		roomvector.remove(u);
		if(roomvector.size()==0) {
			removeRoom(u.roomname);
		}
	}
	//방 사용자에게 모두 메시지 전송
	
	public void broadcastRoomUser(String roomname,String message) {
		if(roomUserInfo.map.get(roomname)!=null) {
			Vector<UserInfo> roomvector = roomUserInfo.map.get(roomname);
			for(UserInfo u : roomvector) {
				MessengerServerSender sender = new MessengerServerSender(u.engine,(SocketChannel) u.key.channel(),message);
				Thread st2 = new Thread(sender);
				st2.start();
			}
		}
	}
	//채팅방 리스트 얻어오기
	public void getRoomList() {
		Set<String> set = roomUserInfo.map.keySet();
		String roomlist="--개설된 채팅방 목록--\r\n";
		for(String roomname : set) {
			roomlist+=" "+roomname+"\r\n";
		}
		MessengerServerSender sender = new MessengerServerSender(engine,channel,roomlist);
		Thread st2 = new Thread(sender);
		st2.start();
	}

	public void run() {
		while(channel.isConnected()) {
			try {
				recv(channel,engine);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
