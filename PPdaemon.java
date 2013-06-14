import org.jibble.pircbot.*;

public class PPdaemon {
    
    public static void main(String[] args) throws Exception
    {
		if(args.length != 4)
		{
	    	System.err.println("arguments required: java <name> <channel name> <channel password> <bot name> <ident password>");
			System.exit(-1);
		}

        PPbot bot = new PPbot(args[0], args[1], args[2], args[3]);
        //bot.setVerbose(true);
    }
    
}
