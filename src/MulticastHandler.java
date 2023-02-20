import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

public class MulticastHandler implements Runnable{
	
	public MulticastSocket ms_client;
	private List<String> notificationList;
	
	public MulticastHandler(MulticastSocket client) {
		this.ms_client = client;
		this.notificationList = new ArrayList<String>();
	}
	
	public void run() {
		try {
			while(true) {
				DatagramPacket notificationAsBytes = new DatagramPacket(new byte[1024], 1024); // create a new request datagram packet
				ms_client.receive(notificationAsBytes);
				String notifAsString = new String(notificationAsBytes.getData());
				synchronized(this.notificationList) { //Accediamo ad una sezione critica in scrittura: synchronized
					notificationList.add(notifAsString);
				}
			}
		} catch(Exception e) {
			; //Il thread termina l'esecuzione.
		}
	}
	
	public void showMeSharing() {
		if(notificationList.isEmpty()) {
			System.out.println("Nessuno ha ancora condiviso niente!\n");
			System.out.println(notificationList.toString());
			return;
		}
		for(String notification : notificationList) {
			System.out.println(notification);
		}
	}
}

