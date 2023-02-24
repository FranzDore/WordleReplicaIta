import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

/*
 * TODO: Finire Multicast:
 * 		 L'Handler dei messaggi multicast non riceve niente, rimane in receive.
 * 		 Prova dal GitHub di Gioele www.github.com/pavomod
 * 		 
 */


public class Server_Task implements Runnable{
	public Socket socket;
	public InetAddress multicastAddr;
	
	
	public Server_Task(Socket socket, InetAddress multicastAddr) {
		this.socket = socket;
		this.multicastAddr = multicastAddr;
	}
	
	public void run() {
		try(DataInputStream reader = new DataInputStream(this.socket.getInputStream());
			DataOutputStream writer = new DataOutputStream(this.socket.getOutputStream());){
			
			boolean closed = false;
			while(! closed) {
				int choice = reader.readInt();
				switch(choice) {
				case 1:
					handleRegistration(this.socket, reader, writer, this.multicastAddr);
					break;
				case 2:
					handleLogin(this.socket, reader, writer, this.multicastAddr);
					break;
				case 3:
					System.out.println("Connection was closed by Client > " + this.socket.getInetAddress() + "\n");
					this.socket.close();
					closed = true;
					break;
				default:
					break;
				}
			}
			
		} catch(IOException e) {
			System.err.println("Client " + this.socket.getInetAddress() + " has lost connection.");
		}
	}
	
	//Passo anche la socket per prendere l'address (.getInetAddress().toString())
	private static void handleRegistration(Socket socket, DataInputStream in, DataOutputStream out, InetAddress ia) {
		boolean work = true;
		try(JsonReader json_in = new JsonReader(new FileReader("accounts.json"));){
			
			while(work) {
				String username_password = in.readUTF(); //Riceviamo username e password in una botta, separati da virgola.
				String [] arr = username_password.split(",");
				if(arr.length != 2) { //Qualcosa è andato storto nell'invio di username o password.
					out.writeBoolean(false);
					continue;
				}
				
				String username = arr[0];
				boolean isUsed = isUsed_Username(username);
				
				String password = arr[1].trim(); //cancelliamo i white-space davanti e dopo la password.
				boolean pw_check;
				//Controlliamo che la password non sia la stringa vuota e non abbia spazi bianchi all'interno
				if(password.equals("") || password.contains(" ")) {
					pw_check = false;
					continue;
				}	else {
					pw_check = true;
				}
				
				boolean overall = !isUsed && pw_check;
				out.writeBoolean(overall);
				if(! overall)	continue;
				
				//THIS PART HAS TO BE SYNCHRONIZED: CALL TO A SYNCHRONIZED METHOD.
				saveAccount(username, password, json_in);
				
				work = false;
			}
			System.out.println("Registration was successfull > Client " + socket.getInetAddress().toString() + "\n");
			
			handleLogin(socket, in, out, ia); //Right after registering, user is asked to login.
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	//Passo anche la socket per prendere l'address (.getInetAddress().toString())
	private static void handleLogin(Socket socket, DataInputStream in, DataOutputStream out, InetAddress ia) {
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			
			Account temp = null;
			boolean work = true;
			
			//Chiediamo username e password al Client
			while(work) {
				
				JsonReader json_in = new JsonReader(new FileReader("accounts.json"));				String username = in.readUTF().trim();
				String password = in.readUTF().trim();
				
				//System.out.println("US: " + username + " PW: " + password + "\n");
								//Carichiamo la lista di tutti gli account
				List<Account> list = new ArrayList<Account>();
				json_in.beginArray();
				while (json_in.hasNext()) {
					Account account = gson.fromJson(json_in, Account.class);
					list.add(account);
				}
				json_in.endArray();	
				boolean doesExist = false;
				temp = lookForName(list, username);		//Troviamo Account usando lo username fornito. (Metodo statico)
				if(temp != null)
					doesExist = true;
				
				if(doesExist == false) {				//Controlliamo che l'account richiesto esista.
					out.writeBoolean(false);
					json_in.close();
					continue;
				} else {
					out.writeBoolean(true);  			//Bisogna mettere un if-then-else, altrimenti non possiamo mettere il continue per tornare su.
				}
				
				if(! password.equals(temp.password)) {	//Controlliamo se le password coincidono.
					out.writeBoolean(false);
					json_in.close();
					continue;
				} else {
					out.writeBoolean(true);
				}
				
				work = false;
				json_in.close();
			}
			
			Checker checker = new Checker();
			//Impediamo il login se l'account risulta essere già loggato. (Si veda la classe "Checker.java")
			if(checker.isAlreadyLoggedIn(temp)) {
				out.writeBoolean(true);
				return;
			}
			else out.writeBoolean(false);
			
			System.out.println("\n > Client " + socket.getInetAddress().toString() + " logged in.\n");
			
	
			
						
			//Ora può iniziare la fase di gioco
			checker.place(temp); //aggiungiamo l'account alla lista di quelli attivi
			gameHandler(socket, in, out, temp, ia);
			checker.delete(temp); //if gameHandler method has returned, it means account has logged out: we remove him from acctive players.
			
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void gameHandler(Socket socket,DataInputStream in,DataOutputStream out,Account account,InetAddress ia) {
		try {
			boolean work = true;
			while(work) {
				int choice = in.readInt();
				switch(choice) {
				case 1:
					playWordle(in, out, account);
					break;
				case 2:
					sendStats(in, out, account);
					break;
				case 3:
					handleShareRequest(in, out, account, ia);
					break;
				case 4:
					/* Il server non riceverà mai un codice di tipo 4.
					 * Il codice 4 viene solo usato per la richiesta dal Client verso il suo MulticastHandler, non
					 * coinvolge in alcun modo il server.*/
					break;
				case 5:
					//Salviamo i dati relativi alla sessione prima di fare il logout.
					saveSession(account);
					return; //We return to handleLogin method and socket gets closed there. No resource leak.
				default:
					break;
				}
			}
			
			
		} catch (IOException e) {
			System.out.println("");
		}
	}

	private static void playWordle(DataInputStream in, DataOutputStream out, Account account) {
		try (RandomAccessFile file = new RandomAccessFile(WordSelector.getFileName(), "r")){
			
			String parolaCorretta = WordSelector.getWord();
			
			if(! account.lastWord.equals("null")) {
				if(account.lastWord.equals(parolaCorretta)) { //Controllo che l'ultima parola giocata sia diversa dalla daily word.
					System.out.println("Account " + account.username + " has played already.\n");
					out.writeBoolean(true); //true = ha già giocato.
					return;
				}
			}
			
			//Se non ha già giocato, non entro nell'if, quindi non eseguo la return e posso scrivere false nella socket.
			out.writeBoolean(false);
			
			
			//Inizializziamo l'account a seguito della richiesta di inizio nuova partita.
			account.lastWord = parolaCorretta; //impediamo che possa rigiocare finché non viene scelta una nuova parola.
			account.wasSolved = false;
			account.playedGames++;
			
			int turno = 1;			
			while(turno <= 12) {
				String userGuess = in.readUTF().trim();
				
				boolean exists = binarySearch(file, userGuess); //Controlliamo se esista la parola nel file di parole.
				out.writeBoolean(exists);
				
				if(! exists)	continue;
				
				HashMap<Character, Integer> map = new HashMap<Character, Integer>(); //Servirà per contare le occorrenze nella parola segreta
				for(int i=0; i<parolaCorretta.length(); i++) {
					char lettera = parolaCorretta.charAt(i);
					if(map.containsKey(lettera))
						map.put(lettera, map.get(lettera) + 1);
					else map.put(lettera, 1);
				}
				
				//Controlliamo la parola fornita, e inseriamo i suggerimenti in "res".
				String res = null;
				res = checkGreenLetters(userGuess, parolaCorretta, map);
				res = checkYellowLetters(userGuess, parolaCorretta, map, res);
				
				//Mandiamo la stringa ottenuta dopo i controlli. "Se composta solo da '+' allora chiudiamo la partita (vittoria).
				out.writeUTF(res);
				out.flush();
				
				if(res.equals("++++++++++")) {
					account.wonGames++;
					account.guessDistribution[turno - 1]++;
					account.lastWordGuesses = turno;
					account.wasSolved = true;
					return; //Usciamo da "PlayWordle".
				}
				turno++;	
			}
			account.lastWordGuesses = turno;
			account.wasSolved = false;
		} catch (IOException e) {
			System.out.println("");
		}
	}

	private static void sendStats(DataInputStream in, DataOutputStream out, Account account) {
		try {
			out.writeUTF(account.username);
			out.flush();
			String stats_toString = (String.valueOf(account.playedGames)) + "," +(String.valueOf(account.wonGames));
			String guessDistribution_toString = "";
			for(int i=0; i<12; i++) {
				guessDistribution_toString += String.valueOf(account.guessDistribution[i]);
				guessDistribution_toString += ",";
			}
			
			out.writeUTF(stats_toString);
			out.flush();
			out.writeUTF(guessDistribution_toString);
			out.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void handleShareRequest(DataInputStream in, DataOutputStream out, Account account, InetAddress ia) {
		try {
			if(!account.lastWord.equals(WordSelector.getWord())) {
				out.writeBoolean(false);
				return;
			}
			out.writeBoolean(true);
			
			String toBeShared;
			if(account.wasSolved)
				toBeShared = "'"+account.username+"' ha indovinato la parola in "+account.lastWordGuesses+" tentativi!";
			else toBeShared = "'" + account.username + "' non ha indovinato la parola";
			
			DatagramPacket msgAsPacket = new DatagramPacket(toBeShared.getBytes(), toBeShared.length(), ia, 3456);
			MulticastSocket ms_socket = new MulticastSocket();
			ms_socket.send(msgAsPacket);
			ms_socket.close();
						
		} catch (IOException e) {
			;
		}
		
		
		
	}
	
	public static boolean binarySearch(RandomAccessFile f, String key) throws IOException {
        final int numElements = ((int) f.length() / 11);

        int lower = 0, upper = numElements - 1, mid;
        while (lower <= upper) {
            mid = (lower + upper) / 2;
            f.seek(mid * 11);
            String value = f.readLine();
            if (key.compareTo(value) == 0) return true;
            if (key.compareTo(value) < 0) upper = mid - 1;
            else lower = mid + 1;
        }
        return false;
    }
	
	private static boolean isUsed_Username(String username) {

		boolean res = false;
		
		try(JsonReader reader = new JsonReader(new FileReader("accounts.json"));){
			Gson gson = new Gson();
			List<Account> users = new ArrayList<Account>();
			
			reader.beginArray();
			while (reader.hasNext()) {
				Account account = gson.fromJson(reader, Account.class);
				users.add(account);
			}
			reader.endArray();
			
			for(Account x : users) {
				if(username.equals(x.username)) {
					res = true;
					break;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	private static Account lookForName(List<Account> lista, String username) {
		for(Account x : lista) {
			if(username.equals(x.username)) {
				return x;
			}	
		}
		return null;
	}

	private static synchronized void saveSession(Account account) {
		try(JsonReader json_in = new JsonReader(new FileReader("accounts.json"));){
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			//Carichiamo la lista degli utenti dal file Json.
			List<Account> users = new ArrayList<Account>();
			json_in.beginArray();
			while (json_in.hasNext()) {
				Account temp_account = gson.fromJson(json_in, Account.class);
				users.add(temp_account);
			}
			json_in.endArray();
			
			for(Account x : users) {
				if(x.username.equals(account.username)) {
					users.remove(x);
					break;
				}	
			}
			
			//aggiungiamo l'account "modificato" alla lista
			users.add(account);
			
			//Ora abbiamo solo bisogno di riscrivere nel file tutti gli account nella lista, che ora comprende anche l'utente modificato
			String listAsString = gson.toJson(users);
			Writer writer = new FileWriter("accounts.json");
			writer.write(listAsString);
			writer.close();
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private static synchronized void saveAccount(String username, String password, JsonReader json_in) {
		try {
			Account new_acc = new Account(username, password); //Nuova istanza della classe Account.
			List<Account> users = new ArrayList<Account>();
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			json_in.beginArray();
			while (json_in.hasNext()) {
				Account account = gson.fromJson(json_in, Account.class);
				users.add(account);
			}
			json_in.endArray();
			
			//Dopo aver caricato la lista degli utenti dal file, aggiungiamo la nuova istanza alla lista.
			users.add(new_acc);
			
			//Ora abbiamo solo bisogno di riscrivere nel file tutti gli account nella lista, che ora comprende anche la nuova istanza
			String listAsString = gson.toJson(users);
			Writer writer = new FileWriter("accounts.json");
			writer.write(listAsString);
			writer.close();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}

	private static String checkGreenLetters(String userGuess, String parolaCorretta, HashMap<Character, Integer> map) {
		String result = "XXXXXXXXXX";
		
		for(int i=0; i<userGuess.length(); i++) {
			char usr = userGuess.charAt(i);
			char wrd = parolaCorretta.charAt(i);
			if(usr == wrd) {
				result = result.substring(0, i) + '+' + result.substring(i + 1);
				map.put(wrd, map.get(wrd) - 1); //decrementiamo il valore associato alla lettera.
			}
		}
			
		return result;
	}

	private static String checkYellowLetters(String userGuess, String parolaCorretta, HashMap<Character, Integer> map, String result) {
		for(int i=0; i<userGuess.length(); i++) {
			char usr = userGuess.charAt(i);
			
			if(result.charAt(i) != '+')
				if(map.containsKey(usr))
					if(map.get(usr) > 0) {
						map.put(usr, map.get(usr) - 1);
						result = result.substring(0, i) + '?' + result.substring(i + 1);
					}
		}
		return result;
	}
}
