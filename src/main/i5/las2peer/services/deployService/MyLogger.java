package i5.las2peer.services.deployService;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.*;
import java.util.stream.Stream;

/**
 * Created by adabru on 27.12.16.
 */
public class MyLogger {
    private static Logger l;
    public static Logger getLogger() {
        if (l == null) {
            l = Logger.getLogger("");
            ConsoleHandler ch = (ConsoleHandler) l.getHandlers()[0];
            ch.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    String msg = formatMessage(record);
                    if (msg.length() == 0)
                        return msg;

                    String[] color = new String[]{"", ""};
                    if (record.getLevel().equals(Level.WARNING)) {
                        // bright yellow
                        color[0] = "\u001b[33;1m";
                        color[1] = "\u001b[39;22m";
                    } else if (record.getLevel().equals(Level.SEVERE)) {
                        // red
                        color[0] = "\u001b[31m";
                        color[1] = "\u001b[39m";
                    } else if (record.getLevel().equals(Level.INFO)) {
                        // yellow
                        color[0] = "\u001b[33m";
                        color[1] = "\u001b[39m";
                    }

                    if (msg.length() > 1)
                        // inverse color
                        msg = "\u001b[7m" + msg.substring(0,1) + "\u001b[27m" + msg.substring(1);
                    return color[0] + msg.replaceAll("\n",color[1]+"\n"+color[0])+color[1] + "\n";
                }
            });
            try{ch.setEncoding("utf8");}catch(UnsupportedEncodingException e){e.printStackTrace();}
        }
        return l;
    }

}
