import org.jibble.pircbot.*;

public class PPdaemon {
    
    public static void main(String[] args) throws Exception
    {
		if(args.length == 0)
		{
	    	System.err.println("arguments required: java <name> <channel name> [optional bot nickname]");
			System.exit(-1);
		}

	PPbot bot;
	if(args.length > 1)
		bot = new PPbot(args[0], args[1]);
	else
		bot = new PPbot(args[0], "plusplusbot");
        
        bot.setVerbose(true);
        bot.connect("irc.freenode.net");
        bot.joinChannel("#" + args[0]);
    }
    
}
