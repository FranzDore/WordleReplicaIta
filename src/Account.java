
//Needed for JsonReader to correctly read from "accounts.json", so that server can operate with the objects.


public class Account {
	String username;
	String password;
	String lastWord;
	boolean wasSolved;
	int lastWordGuesses;
	int playedGames;
	int wonGames;
	int[] guessDistribution;
	
	
	public Account(String username, String password) {
		this.username = username;
		this.password = password;
		this.lastWord = "null"; //Non usiamo un booleano per permettere di giocare due parole nella stessa sessione, senza fare logout.
		this.wasSolved = false;
		this.lastWordGuesses = 0;
		this.playedGames = 0;
		this.wonGames = 0;
		this.guessDistribution = new int[12];
	}
	
	@Override
	public String toString() {
		return "{username: " + this.username + ", password: " + this.password + ", hasPlayed: " + this.lastWord + "}";
	}
}
