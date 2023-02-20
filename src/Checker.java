import java.util.ArrayList;
import java.util.List;


/*
 * The only purpose of this class is to check if an account has already logged in
 */

public class Checker {
	
	public static List<Account> currently_active = new ArrayList<Account>();
	
	public Checker() {;} //Costruttore non fa niente. Non servono variabili di istanza.
	
	public boolean isAlreadyLoggedIn(Account a) {
		for(Account x : Checker.currently_active) 
			if(a.username.equals(x.username))
				return true;
		return false;
	}
	
	public void place(Account a) {
		currently_active.add(a);
	}
	
	public void delete(Account a) {
		Checker.currently_active.remove(a);
	}
}
