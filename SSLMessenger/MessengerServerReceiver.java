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
	private UserInfo userInfo=null;
	SSLEngineResult result;
	boolean isClosed=false;
	
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
		explainUser();
	}

	//클라이언트로 메시지 받음
	synchronized protected void recv(SocketChannel channel,SSLEngine engine) throws Exception {
		
		NodeNetData.clear();
		int waitToRecvMillis=50;
		try {
	    int byterecv = channel.read(NodeNetData);
		
		if(byterecv > 0) {
			NodeNetData.flip();
			while(NodeNetData.hasRemaining() && !isClosed) {
				NodeAppData.clear();
			    result = engine.unwrap(NodeNetData, NodeAppData);
				switch(result.getStatus()) {
				case OK:
					NodeAppData.flip();
					
					Charset charset = Charset.defaultCharset();
					String message = charset.decode(NodeAppData).toString();
					String [] m_array=null;
					String control_type="";
				
					if(message.charAt(0)=='@') {
						m_array=message.split("@");
						control_type=m_array[1];
						
					switch(control_type) {
						
						case "userinfo" :
								addUser(m_array[2],engine,k);
								break;
						case "explain" :
								explainUser();
								break;
						case "chatroomlist" :
							    getRoomList();
							    break;
						case "createchatroom" :
							    addRoom(m_array[2]);
							    for(UserInfo u : roomUserInfo.info) {
									if(u.key==k) {
										String sm="-------------------------\r\n"+
												  "-    "+u.id+" 님이 채팅방을 개설하셨습니다.     -\r\n"+
												  "------------------------------------------------------\r\n";
											for(UserInfo user:roomUserInfo.info) {
													if(user.key==k) {
															for(String r : roomUserInfo.room) {
																if(user.roomname.equals(r)) {
																	broadcastRoomSystem(r,sm);
																}
															}
													}
											}
									}
							    }
							    break;
						case "enterchatroom" :
								for(UserInfo u : roomUserInfo.info) {
									if(u.key==k) {
											addRoomUser(u,m_array[2]);
											String sm2="-------------------------\r\n"+
													  "-    "+u.id+" 님이 채팅방에 입장하셨습니다.    -\r\n"+
													  "------------------------------------------------------\r\n";
												for(UserInfo user:roomUserInfo.info) {
													if(user.key==k) {
														for(String r : roomUserInfo.room) {
															if(user.roomname.equals(r)) {
																broadcastRoomSystem(r,sm2);
															}
														}
													}
												}
									}
								}
								break;								
						case "exitchatroom" :
							for(UserInfo u : roomUserInfo.info) {
								if(u.key==k) {
											String sm3="-------------------------\r\n"+
													  "-    "+u.id+" 님이 채팅방에서 퇴장하셨습니다.     -\r\n"+
													  "------------------------------------------------------\r\n";
											for(UserInfo user:roomUserInfo.info) {
												if(user.key==k) {
													for(String r : roomUserInfo.room) {
														if(user.roomname.equals(r)) {
															broadcastRoomSystem(r,sm3);
														}
													}
												}
											}
											removeRoomUser(u);
									}
								}
								break;
						}
					}else {
							for(UserInfo u:roomUserInfo.info) {
								if(u.key==k) {
									for(String roomname : roomUserInfo.room) {
										if(u.roomname.equals(roomname)) {
											broadcastRoomUser(roomname,message);
										}else {
											String alert_message="채팅방에 입장한 뒤에 메시지 입력이 가능합니다.";
											send(channel,engine,alert_message);
										}
									}
								}
							}
					}
					
				case BUFFER_OVERFLOW:
					NodeAppData = enlargeAppBuffer(engine,NodeAppData);
					break;
				case BUFFER_UNDERFLOW:
					NodeNetData = manageBufferUnderflow(engine,NodeNetData);
					break;
				case CLOSED:
					isClosed = true;
					
					for(UserInfo u : roomUserInfo.info) {
						if(u.key==k) {
								removeRoomUser(u);
						}
					}
					
					System.out.println("클라이언트 쪽의 종료 요청으로, 연결을 종료합니다.");
					closeConnection(channel,engine);
					break;
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
	  }catch(IOException e) {
		  channel.finishConnect();
	  }
	
	}
	public void addUser(String id,SSLEngine engine, SelectionKey key) {
		
		userInfo = new UserInfo();
		userInfo.id=id;
		userInfo.engine=engine;
		userInfo.key=key;
		roomUserInfo.info.add(userInfo);
		
	}
	//채팅방 추가
	public void addRoom(String roomname) {
			Vector<UserInfo> roomvector;
	    if(roomUserInfo.map.get(roomname) == null)
	    	roomvector = new Vector<UserInfo>();
	    else
	    	roomvector = roomUserInfo.map.get(roomname);
		roomvector.add(userInfo);
		roomUserInfo.map.put(roomname,roomvector);
		roomUserInfo.room.add(roomname);
		userInfo.roomname=roomname;
	}
	//채팅방 삭제
	public void removeRoom(String roomname) {
		roomUserInfo.map.remove(roomname);
	}
	//채팅방에 유저 추가
	public void addRoomUser(UserInfo u,String roomname) {
		u.roomname=roomname;
		Vector<UserInfo> roomvector;
		if(roomUserInfo.map.get(u.roomname)==null) {
			
		}else {
			roomvector = roomUserInfo.map.get(u.roomname);
			roomvector.add(u);
			roomUserInfo.map.put(roomname,roomvector);
		}
		
	}
	//채팅방에서 유저 삭제
	public void removeRoomUser(UserInfo u) {
		if(u.roomname != null){
			Vector<UserInfo> roomvector = roomUserInfo.map.get(u.roomname);
			roomvector.remove(u);
			if(roomvector.size()==0) {
				removeRoom(u.roomname);
			}
			u.roomname=null;
		}
	}
	//방 사용자에게 모두 메시지 전송
	
	public void broadcastRoomUser(String roomname,String message) {
		String buffer = "<"+userInfo.id+"> : "+message;
		if(roomUserInfo.map.get(roomname)!=null) {
			Vector<UserInfo> roomvector = roomUserInfo.map.get(roomname);
			for(UserInfo u : roomvector) {
				MessengerServerSender sender = new MessengerServerSender(u.engine,(SocketChannel) u.key.channel(),buffer);
				Thread st2 = new Thread(sender);
				st2.start();
			}
		}
	}
	public void broadcastRoomSystem(String roomname,String message) {
		String buffer = "<System Message> : "+message;
		if(roomUserInfo.map.get(roomname)!=null) {
			Vector<UserInfo> roomvector = roomUserInfo.map.get(roomname);
			for(UserInfo u : roomvector) {
				MessengerServerSender sender = new MessengerServerSender(u.engine,(SocketChannel) u.key.channel(),buffer);
				Thread st2 = new Thread(sender);
				st2.start();
			}
		}
	}
	//채팅방 리스트 얻어오기
	public void getRoomList() {
		Set<String> set = roomUserInfo.map.keySet();
		String roomlist="------------------------------------------------------\r\n"+
				  "-             개설된 채팅방 목록                      -\r\n"+
				  "------------------------------------------------------\r\n";
		for(String roomname : set) {
			roomlist+=" "+roomname+"\r\n";
		}
		MessengerServerSender sender = new MessengerServerSender(engine,channel,roomlist);
		Thread st2 = new Thread(sender);
		st2.start();
	}	
	public void explainUser() {
			String explaintext="------------------------------------------------------------------\r\n"+
					  "-    *SSL Multi Room Messenger 사용법*     -\r\n"+
					  "------------------------------------------------------------------\r\n"+
					  "    @chatroomlist        	  <---     현재 개설된 채팅방 현황 보기\r\n"+
					  "    @createchatroom@방 이름           <---     '방 이름'으로 채팅방 개설\r\n"+
					  "    @enterchatroom@방 이름             <---     '방 이름'으로 채팅방 입장\r\n"+
					  "    @exitchatroom              <---     핸재 자신이 속한 채팅방에서 퇴장\r\n"+
					  "    @explain                   <---     사용법 다시보기          \r\n"+
					  "                                                       \r\n"+
					  "    주의 사항 : 채팅방으로 입장 or 개설하셔야 채팅이 가능합니다.\r\n"+
					  "------------------------------------------------------------------\r\n";
			MessengerServerSender sender = new MessengerServerSender(engine,channel,explaintext);
			Thread st2 = new Thread(sender);
			st2.start();
		}

	public void run() {
		try {
			while(channel.isConnected() && !isClosed) {
				recv(channel,engine);
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
}
