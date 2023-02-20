import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public class WordSelector implements Runnable{

	public static final String configFile = "wordSelector.properties";
	
	private static String chosen_word; //Accedibile solo mediante getter.
	private static String fileName;
	private static int delaySceltaParola; //Tempo tra la scelta di due parole diverse
	
	
	public void run() {
		
		try {
			readConfig();	//Leggiamo dal file di config (configFile)
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Timer t = new Timer();  
		TimerTask tt = new TimerTask() {  
		    @Override  
		    public void run() {  
		    	/* 
		    	 * Sappiamo che le parole sono lunghe 10 caratteri. Questo vuol dire che nel file di parole, ogni riga sarà lunga 10 caratteri 
		    	 * (da 1 byte) più il '\n' che conta come singolo carattere (da 2 byte), cioè 11 (12 byte in tutto). Da qui capiamo che l'accesso 
		    	 * casuale al file deve essere  fatto per multipli di 12.
		    	 */
		    	
		    	try (RandomAccessFile file = new RandomAccessFile(fileName, "r");
		    		JsonReader json_in = new JsonReader(new FileReader("accounts.json"));) {
		    				    		
		    		int length = ((int) file.length()) / 11;
					Random rand = new Random();
					int idx = rand.nextInt(length);
					file.seek(idx * 11);
					String parola = file.readLine();
					chosen_word = parola;
					
		    		System.out.println("A new daily word has been chosen: " + chosen_word);
		    		
					//Ora settiamo lo stato "hasPlayed" di tutti i player a false.
					
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					
					//Carichiamo la lista di tutti gli account
					List<Account> list = new ArrayList<Account>();
					json_in.beginArray();
					while (json_in.hasNext()) {
						Account account = gson.fromJson(json_in, Account.class);
						list.add(account);
					}
					json_in.endArray();
					
					//Riscriviamo nel file Json
					String listAsString = gson.toJson(list);
					Writer writer = new FileWriter("accounts.json");
					writer.write(listAsString);
					writer.close();
					
					
				} catch (Exception e) {
					e.printStackTrace();
				}
		    }  
		};  
		t.schedule(tt, 0, delaySceltaParola); //Modificando i parametri decidiamo ogni quanto viene scelta la nuova parola.		
	}
	
	public static String getWord() {
		return chosen_word;
	}
	
	public static String getFileName() {
		return fileName;
	}
	
	public static void readConfig() throws FileNotFoundException, IOException {
		InputStream input = new FileInputStream(configFile);
		Properties prop = new Properties();
		prop.load(input);
		delaySceltaParola = Integer.parseInt(prop.getProperty("delay"));
		fileName = prop.getProperty("file");
		input.close();
	}
}
