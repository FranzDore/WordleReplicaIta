
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;




public class ClientMain {
	
	public static Scanner keyboard_in = new Scanner(System.in); //Così che lo stream non venga chiuso ma riutilizzato.
	public static final String configFile = "clientAndServer.properties";
	public static int port;
	public static String hostName;
	
	//Alcuni di questi parametri vengono inizializzati nel metodo "readConfig()"
	public static InetAddress ms_addr; //Indirizzo
	public static MulticastSocket ms_client; //Socket effettiva
	
	public static void main (String[] args) {
		
		try {
			readConfig(); //Leggiamo le opzioni di config dal file di config
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try(Socket socket = new Socket(hostName, port);
			DataInputStream reader = new DataInputStream(socket.getInputStream());
			DataOutputStream writer = new DataOutputStream(socket.getOutputStream());) {
		    
			System.out.println("Connected to Server. Starting Game.\n\n");
			System.out.println("\n----------- Benvenuto! -----------\n");
			boolean work = true;
			while(work) {
				
				System.out.println("Cosa vuoi fare?\n"
						+ "1. Registrati\n"
						+ "2. Login\n"
						+ "3. Exit\n");
				
				String scelta = keyboard_in.nextLine();
				switch(scelta) {
				case "1":
					writer.writeInt(1);
					register(reader, writer);
					break;
				case "2":
					writer.writeInt(2);
					login(reader, writer);
					break;
				case "3":
					writer.writeInt(3);
					System.out.println("Alla prossima!\n");
					work = false;
					break; //Possiamo semplicemente fare break. La socket si chiude all'uscita del try with resources.
				default:
					System.err.println("Scusa, non ho capito.\n");
					break;
				}
			}
			
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void register(DataInputStream in, DataOutputStream out) {
		
		boolean work = true;
		try{			
			while(work) {
				//Richiesta Username
				System.out.println("Username --> ");
				String username = keyboard_in.nextLine().trim();
				if(username.contains(" ")) {
					System.out.println("Lo username non può contenere spazi.\n");
					continue;
				}
				//Richiesta Password
				System.out.println("Password --> ");
				String password = keyboard_in.nextLine().trim();
				if(password.contains(" ")) {
					System.out.println("Lo username non può contenere spazi.\n");
					continue;
				}
				
				//Invio e controllo di correttezza
				out.writeUTF(username + "," + password); 
				out.flush();	//Forza la scrittura nel buffer della socket
				boolean ok = in.readBoolean();
				if(! ok) {
					System.out.println(" > Username gia scelto o password non valida. Ritenta.\n");
					continue;
				}
				work = false; //Registrazione avvenuta, usciamo dal ciclo
			} 
			
		}catch (IOException e) {
				e.printStackTrace();
		}
		
		//Subito dopo la registrazione, richiediamo l'accesso (login) immediato. Chiamata a metodo.
		login(in, out);
	}
	
	@SuppressWarnings("deprecation")
	private static void login(DataInputStream in, DataOutputStream out) {
		try {
			boolean work = true;
			System.out.println("\n~~~ Login ~~~\n");
			
			while(work) {
				System.out.println("Username ---> ");
				String username = keyboard_in.nextLine().trim();
				if(username.contains(" ")) {
					System.out.println("Lo username non puo' contenere spazi. Ritenta.\n");
					continue;
				}
				
				System.out.println("Password --->");
				String password = keyboard_in.nextLine().trim();
				if(password.contains(" ")) {
					System.out.println("La password non puo' contenere spazi. Ritenta.\n");
					continue;
				}
				out.writeUTF(username);
				out.flush();
				
				out.writeUTF(password);
				out.flush();
				
				boolean esiste = in.readBoolean();
				if(! esiste) {
					System.out.println("Account non esistente. Ritenta.\n");
					continue;
				}
				
				boolean passwordCorretta = in.readBoolean();
				if(! passwordCorretta) {
					System.out.println("Password errata. Ritenta.\n");
					continue;
				}
				
				
				work = false;
			}
			
			boolean isLogged = in.readBoolean();
			if(isLogged) {
				System.out.println("--- Impossibile connettersi. L'accesso e' gia' stato effettuato da un altro dispositivo.\n");
				System.exit(1);
			}
			
			ms_client = new MulticastSocket(3456); // create a multicast client socket
			MulticastHandler multicastHandler = new MulticastHandler(ms_client); //Sarà startato dopo il login
			Thread multicastHandlerThread = new Thread(multicastHandler); // create a multicast notification handler thread
			
			multicastHandlerThread.start(); // start multicast notification thread
            ms_client.joinGroup(ms_addr); // join multicast group
			
			//if(isLogged && (!work)), inizia la fase di gioco
			gameScreen(in, out, multicastHandler);
			
			//Se facciamo il logout usciamo dal gruppo di multicast
			ms_client.leaveGroup(ms_addr);
			ms_client.close();
			
			//Fermiamo il thread
			multicastHandlerThread.interrupt();

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void gameScreen(DataInputStream in, DataOutputStream out, MulticastHandler multicastHandler) {
		System.out.println("\n==== Benvenuto/a su Wordle 3.0! ====");
		
		try {
			boolean work = true;
			while(work) {
				System.out.println("Cosa vuoi fare?\n"
						+ "1. Play Wordle!\n"
						+ "2. Statistiche\n"
						+ "3. Share\n"
						+ "4. Show me Sharing\n"
						+ "5. Logout\n");

				String choice = keyboard_in.nextLine();
				switch(choice) {
				case "1":
					out.writeInt(1);
					clientPlayWordle(in, out);
					break;
				case "2":
					out.writeInt(2);
					clientSendMeStats(in, out);
					break;
				case "3": //share
					out.writeInt(3);
					clientShare(in, out);
					break;
				case "4":
					multicastHandler.showMeSharing();
					System.out.println("------ PREMERE INVIO PER CONTINUARE -------\n");
					keyboard_in.nextLine();
					break;
				case "5":
					out.writeInt(5);
					return;		//Possiamo fare return. Nessun resource leak, in quanto verrà chiuso tutto dal chiamante (metodo login, poi main)
				default:
					System.out.println("Non ho capito...\n");
				}
			}
			
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	

	private static void clientPlayWordle(DataInputStream in, DataOutputStream out) {
		try {
			boolean already_played = in.readBoolean();
			if(already_played) {
				System.out.println("Hai gia' giocato per oggi! Riprova domani.\n");
				return;
			}

			int turno = 1;
			while(turno <= 12) {
				System.out.println("Turno: " + turno + "\n");
				String guess = keyboard_in.nextLine().trim();
				if(guess.contains(" ") || guess.length() != 10) { //Primo piccolo controllo per evitare comunicazioni inutili.
					System.out.println("Parola in un formato non consentito.\n");
					continue;
				}
				//Se il formato è corretto la mandiamo al server
				out.writeUTF(guess);
				out.flush();
				
				boolean parolaEsistente = in.readBoolean(); //Riceve false se "guess" non esiste nel file delle parole, vero altrimenti.
				if (! parolaEsistente) {
					System.out.println("La parola non esiste nel dizionario.\n");
					continue;
				}

				String res = in.readUTF();
				System.out.println("Guess: " + guess + "\n"
						+ "Tips : " + res + "\n");
				if(res.equals("++++++++++")) {
					System.out.println("\n========== Hai vinto! ==========\n");
					return;
				}
				turno++;
			}
			if(turno == 13)
				System.out.println("---------- Hai perso :( ----------\n");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void clientSendMeStats(DataInputStream in, DataOutputStream out) {
		try {
			String username = in.readUTF();
			System.out.println("\n------ STATISTICHE DELL'UTENTE '" + username + "' ------\n");
			
			String [] stats = in.readUTF().split(",");
			String [] guessDistr = in.readUTF().split(",");
			
			float playedGames = Float.parseFloat(stats[0]);
			float wonGames = Float.parseFloat(stats[1]);
			
			float res;
			if(playedGames == 0)
				res = 0;
			else
				res = wonGames/playedGames;
			
			System.out.println(" > Partite giocate: " + playedGames);
			System.out.println(" > Partite vinte: " + wonGames);
			System.out.println(" > Percentuale di vittorie: " + (res * 100) + "%");
			System.out.println(" > Distribuzione vittorie (numero di tentativi):");
			for(int i=0; i<12; i++) {
				System.out.println(" > " + (i+1) + " tentativi: " + guessDistr[i]);
			}
			System.out.println("----- PREMI INVIO PER ANDARE AVANTI -----\n");
			keyboard_in.nextLine();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void clientShare(DataInputStream in, DataOutputStream out) {
		try {
			boolean hasPlayed = in.readBoolean();
			if(!hasPlayed) {
				System.out.println("Non hai ancora giocato per oggi. Gioca una partita per poter condividerne il risultato!\n");
				return;
			}
			System.out.println("Risultato condiviso!\n");			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void readConfig() throws FileNotFoundException, IOException {
		InputStream input = new FileInputStream(configFile);
		Properties prop = new Properties();
		prop.load(input);
		port = Integer.parseInt(prop.getProperty("port"));
		hostName = prop.getProperty("hostname");
		//ms_addr = InetAddress.getByName(prop.getProperty("multicastAddr")); 
		ms_addr = InetAddress.getByName("225.4.5.6"); 
		input.close();
	}	
}
