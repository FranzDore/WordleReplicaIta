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
 * 		 
 */


public class Server_Task implements Runnable{
	public Socket socket;
	public InetAddress multicastAddr;
	public int ms_port;
	
	public Server_Task(Socket socket, InetAddress multicastAddr, int ms_port) {
		this.socket = socket;
		this.multicastAddr = multicastAddr;
		this.ms_port = ms_port;
	}
	
	public void run() {
		try(DataInputStream reader = new DataInputStream(this.socket.getInputStream());
			DataOutputStream writer = new DataOutputStream(this.socket.getOutputStream());){
			
			boolean closed = false; //if true, client was closed.
			while(! closed) {
				int choice = reader.readInt();
				switch(choice) {
				case 1:
					handleRegistration(this.socket, reader, writer, this.multicastAddr, this.ms_port);
					break;
				case 2:
					handleLogin(this.socket, reader, writer, this.multicastAddr, this.ms_port);
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
	private static void handleRegistration(Socket socket, DataInputStream in, DataOutputStream out, InetAddress ia, int ms_port) {
		boolean work = true;
		try(JsonReader json_in = new JsonReader(new FileReader("../bin/accounts.json"));){
			
			while(work) {
				String username_password = in.readUTF(); //Receive username and passward together, separated by a comma.
				String [] arr = username_password.split(",");
				if(arr.length != 2) { //Something went wrong when reading either username or password.
					out.writeBoolean(false);
					continue;
				}
				
				String username = arr[0];
				boolean isUsed = isUsed_Username(username); //Check if username is already used. 
				
				String password = arr[1].trim(); //Delete all leading and trailing whitespaces.
				boolean pw_check;
				//Check that password isn't empty string and that it doesn't contain whitespaces in the middle.
				if(password.equals("") || password.contains(" ")) {
					pw_check = false;
					continue;
				}	else {
					pw_check = true;
				}
				
				boolean overall = !isUsed && pw_check; //If conditions are met, it will be true and we can move on.
				out.writeBoolean(overall);
				if(! overall)	continue;
				
				//THIS PART HAS TO BE SYNCHRONIZED: CALL TO A SYNCHRONIZED METHOD.
				saveAccount(username, password, json_in);
				
				work = false;
			}
			System.out.println("Registration was successfull > Client " + socket.getInetAddress().toString() + "\n");
			
			handleLogin(socket, in, out, ia, ms_port); //Right after registering, user is asked to login.
			
		} catch(IOException e) {
			;
		}
	}
	
	//Passo anche la socket per prendere l'address (.getInetAddress().toString())
	private static void handleLogin(Socket socket, DataInputStream in, DataOutputStream out, InetAddress ia, int ms_port) {
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			
			Account temp = null;
			boolean work = true;
			
			//Ask for username and password to client.
			while(work) {
				
				JsonReader json_in = new JsonReader(new FileReader("../bin/accounts.json"));				String username = in.readUTF().trim();
				String password = in.readUTF().trim();
								//Load list of all accounts
				List<Account> list = new ArrayList<Account>();
				json_in.beginArray();
				while (json_in.hasNext()) {
					Account account = gson.fromJson(json_in, Account.class);
					list.add(account);
				}
				json_in.endArray();	
				boolean doesExist = false;
				temp = lookForName(list, username);		//Find account using given username (Static method).
				if(temp != null)
					doesExist = true;
				
				if(doesExist == false) {				//Check if account exists.
					out.writeBoolean(false);
					json_in.close();
					continue;
				} else {
					out.writeBoolean(true);  			//if-then-else is needed to use "continue" in case something goes wrong.
				}
				
				if(! password.equals(temp.password)) {	//Check if passwords are the same.
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
			//We prevent login if account is already logged in (Look at "Checker.java" class)
			if(checker.isAlreadyLoggedIn(temp)) {
				out.writeBoolean(true);
				return;
			}
			else out.writeBoolean(false);
			
			System.out.println("\n > Client " + socket.getInetAddress().toString() + " logged in.\n");
			
			//Game Phase is now accessible.
			checker.place(temp); //add the account to list of active users
			gameHandler(socket, in, out, temp, ia, ms_port);
			checker.delete(temp); //if gameHandler method has returned, it means account has logged out: we remove him from acctive players.
			
			
		} catch(IOException e) {
			;
		}
	}
	
	private static void gameHandler(Socket socket,DataInputStream in,DataOutputStream out,Account account,InetAddress ia, int ms_port) {
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
					handleShareRequest(in, out, account, ia, ms_port);
					break;
				case 4:
					/* Il server non riceverà mai un codice di tipo 4.
					 * Il codice 4 viene solo usato per la richiesta dal Client verso il suo MulticastHandler, non
					 * coinvolge in alcun modo il server.*/
					break;
				case 5:
					//We save data relative to last session before logging out.
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
				if(account.lastWord.equals(parolaCorretta)) { //Check if last guessed word is  the same daily word.
					System.out.println("Account " + account.username + " has played already.\n");
					out.writeBoolean(true); //true = has already played.
					return;
				}
			}
			
			//If they haven't played they don't go into the "if" statement, they don't return and can write false into the socket.
			out.writeBoolean(false);
			
			
			//Change account information right before starting the game
			account.lastWord = parolaCorretta; //By changing this parameter, we deny them from playing again until daily word changes.
			account.wasSolved = false;
			account.playedGames++;
			
			int turno = 1;			
			while(turno <= 12) {
				String userGuess = in.readUTF().trim();
				
				boolean exists = binarySearch(file, userGuess); //Check if word exists in the words file.
				out.writeBoolean(exists);
				
				if(! exists)	continue;
				
				HashMap<Character, Integer> map = new HashMap<Character, Integer>(); //Needed to count occurrences of the word.
				for(int i=0; i<parolaCorretta.length(); i++) {
					char lettera = parolaCorretta.charAt(i);
					if(map.containsKey(lettera))
						map.put(lettera, map.get(lettera) + 1);
					else map.put(lettera, 1);
				}
				
				//Check given word and insert tips into "res" variable.
				String res = null;
				res = checkGreenLetters(userGuess, parolaCorretta, map);
				res = checkYellowLetters(userGuess, parolaCorretta, map, res);
				
				//Send string after the adequate checks. If res is only made of '+' then we end game (Victory).
				out.writeUTF(res);
				out.flush();
				
				if(res.equals("++++++++++")) {
					account.wonGames++;
					account.guessDistribution[turno - 1]++;
					account.lastWordGuesses = turno;
					account.wasSolved = true;
					return; //Exit "PlayWordle" since they won.
				}
				turno++;	
			}
			account.lastWordGuesses = turno;
			account.wasSolved = false;
		} catch (IOException e) {
			System.out.println("");
		}
	}

	//Simply builds the string to be shown when Client asks for stats, and sends it through the socket.
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
	
	//Just like above, builds the string and sends it through the socket.
	private static void handleShareRequest(DataInputStream in, DataOutputStream out, Account account, InetAddress ia, int ms_port) {
		try {
			if(!account.lastWord.equals(WordSelector.getWord())) { //If they haven't played, nothing can be shared. We return false.
				out.writeBoolean(false);
				return;
			}
			out.writeBoolean(true); //We share the stats of last game.
			
			String toBeShared;
			if(account.wasSolved)
				toBeShared = "'"+account.username+"' ha indovinato la parola in "+account.lastWordGuesses+" tentativi!";
			else toBeShared = "'" + account.username + "' non ha indovinato la parola";
			
			DatagramPacket msgAsPacket = new DatagramPacket(toBeShared.getBytes(), toBeShared.length(), ia, ms_port);
			MulticastSocket ms_socket = new MulticastSocket();
			ms_socket.send(msgAsPacket);
			ms_socket.close();
						
		} catch (IOException e) {
			;
		}
		
		
		
	}
	
	//Binary search to look through the words.
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
	
	//returns true if username is used, false otherwise
	private static boolean isUsed_Username(String username) {

		boolean res = false;
		
		try(JsonReader reader = new JsonReader(new FileReader("../bin/accounts.json"));){
			Gson gson = new Gson();
			List<Account> users = new ArrayList<Account>();
			
			//Load all users
			reader.beginArray();
			while (reader.hasNext()) {
				Account account = gson.fromJson(reader, Account.class);
				users.add(account);
			}
			reader.endArray();
			
			//Look through the list, if we find a match we return true.
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
	
	//returns the actual account, not a boolean value. See isUsed_Username function for that matter
	private static Account lookForName(List<Account> lista, String username) {
		for(Account x : lista) {
			if(username.equals(x.username)) {
				return x;
			}	
		}
		return null;
	}

	//Saves session. All data will be saved afer calling this function. SYNCHRONIZED since we access a shared structure (.json file)
	private static synchronized void saveSession(Account account) {
		try(JsonReader json_in = new JsonReader(new FileReader("../bin/accounts.json"));){
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			//Load list of all users.
			List<Account> users = new ArrayList<Account>();
			json_in.beginArray();
			while (json_in.hasNext()) {
				Account temp_account = gson.fromJson(json_in, Account.class);
				users.add(temp_account);
			}
			json_in.endArray();
			
			//We remove the old instance of the account...
			for(Account x : users) {
				if(x.username.equals(account.username)) {
					users.remove(x);
					break;
				}	
			}
			
			//...so that we can add the modified and new version of it
			users.add(account);
			
			//Only need to rewrite the list into the file. The list now containes the new account, and so will the file after writing.
			String listAsString = gson.toJson(users);
			Writer writer = new FileWriter("../bin/accounts.json");
			writer.write(listAsString);
			writer.close();
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	//Same as before, but we dont need to loop through the list, we just add the account to it and re-write.
	private static synchronized void saveAccount(String username, String password, JsonReader json_in) {
		try {
			Account new_acc = new Account(username, password); //New instance of class Account.
			List<Account> users = new ArrayList<Account>();
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			json_in.beginArray();
			while (json_in.hasNext()) {
				Account account = gson.fromJson(json_in, Account.class);
				users.add(account);
			}
			json_in.endArray();
			
			//We add new account to the list of already existing accounts.
			users.add(new_acc);
			
			//Only need to rewrite the list into the file. The list now containes the new account, and so will the file after writing.
			String listAsString = gson.toJson(users);
			Writer writer = new FileWriter("../bin/accounts.json");
			writer.write(listAsString);
			writer.close();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	//Function checks for green letters and updates string and HashMap
	private static String checkGreenLetters(String userGuess, String parolaCorretta, HashMap<Character, Integer> map) {
		String result = "XXXXXXXXXX";
		
		for(int i=0; i<userGuess.length(); i++) { 	//we iterate over the user's guess
			char usr = userGuess.charAt(i);			
			char wrd = parolaCorretta.charAt(i);
			if(usr == wrd) {						//check if the letters of the user's guess and hidden word at "i" position are the same.
				result = result.substring(0, i) + '+' + result.substring(i + 1);	//We add a '+' in the result at corresponding position.
				map.put(wrd, map.get(wrd) - 1); //decrementiamo il valore associato alla lettera.
			}
		}
			
		return result;
	}
	
	//Function checks for yellow letters and updates string and HashMap
	private static String checkYellowLetters(String userGuess, String parolaCorretta, HashMap<Character, Integer> map, String result) {
		for(int i=0; i<userGuess.length(); i++) {
			char usr = userGuess.charAt(i);
			
			if(result.charAt(i) != '+')		//If it is '+' it means we already counted before in the "checkGreenLetters" method.
				if(map.containsKey(usr))	//Is the letter in the HashMap? (Is the character in the hidden word?)
					if(map.get(usr) > 0) {	
						map.put(usr, map.get(usr) - 1); //decrement value
						result = result.substring(0, i) + '?' + result.substring(i + 1); //Add a '?': letter exists in a different spot.
					}
		}
		return result;
	}
}
