import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * ---------------------------------------		USAGE
 * 			Compile with < javac -cp ".;gson-2.8.6.jar" *.java >
 * 			Run with < java -cp ".;gson-2.8.6.jar" ServerMain > 
 * 
 * 			IN ORDER TO WORK, THE FILE "accounts.json" HAS TO HAVE "[]" INSIDE OF IT (at least for the first execution),
 * 			SO THAT THE METHOD ".beginArray()" doesn't raise EOFException.
 */

public class ServerMain {
	public static final String configFile = "clientAndServer.properties";
	
	public static int port;
	public static InetAddress ms_addr;
	
	
	public static void main(String[] args) {
		try {
			readConfig();	//Leggiamo dal file di config.
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		try(ServerSocket server = new ServerSocket(port);){
			
			System.out.println("---- Server started.\n");
			ExecutorService service = Executors.newCachedThreadPool();
			
			WordSelector ws = new WordSelector();
			Thread word_selector = new Thread(ws);
			word_selector.start(); //Startiamo il selettore di parole giornaliere
			
			while(true) {
				Socket socket = server.accept(); //Waiting for clients
				System.out.println("~~~ Found a pending client.\n");
				Server_Task task = new Server_Task(socket, ms_addr);
				service.execute(task);
			}
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void readConfig() throws FileNotFoundException, IOException {
		InputStream input = new FileInputStream(configFile);
		Properties prop = new Properties();
		prop.load(input);
		port = Integer.parseInt(prop.getProperty("port"));
		ms_addr = InetAddress.getByName(prop.getProperty("multicastAddr"));
		input.close();
	}
}