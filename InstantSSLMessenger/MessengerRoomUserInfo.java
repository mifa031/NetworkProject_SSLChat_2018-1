import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Vector;

public class MessengerRoomUserInfo {
	
	public Vector<String> room;
	public Vector<SelectionKey> user;
	public HashMap<String,Vector<SelectionKey>> map = new HashMap<String,Vector<SelectionKey>>(); 
	
	public MessengerRoomUserInfo(){
		room = new Vector<String>();
		user = new Vector<SelectionKey>();
		map  = new HashMap<String,Vector<SelectionKey>>();
	}
	
}
