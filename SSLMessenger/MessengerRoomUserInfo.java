import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Vector;

import javax.net.ssl.SSLEngine;

class UserInfo{
	
	public String id;
	public SSLEngine engine;
	public SelectionKey key;
	public String roomname;
}

public class MessengerRoomUserInfo {
	
	public Vector<String> room;
	public Vector<UserInfo> info; 
	public HashMap<String,Vector<UserInfo>> map = new HashMap<String,Vector<UserInfo>>(); 
	
	public MessengerRoomUserInfo(){
		room = new Vector<String>();
		info = new Vector<UserInfo>();
		map  = new HashMap<String,Vector<UserInfo>>();		
	}
	
}
