package com.matt.atconf;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {
    public static final String ACTIONTEC_DEFAULT_ADDR = "http://192.168.144.30/";

    public static final Path ROOT_PATH = Paths.get(".");
    public static final int RETRY_COUNT = 5;

    public static void main(String[] args) {
        if(args.length > 0 && args[0] != null) {
            String host = args[1];

            if(!host.toLowerCase().startsWith("http://"))
                host = "http://" + host;
            if(!host.endsWith("/"))
                host += "/";

            Utils.setActiontecHost(host);
        } else
            Utils.setActiontecHost(ACTIONTEC_DEFAULT_ADDR);

        System.out.println("Actiontec MoCA Adapter CLI over HTTP");
        System.out.println("Use :help to see internal commands");
        System.out.println("Use help to see Actiontec commands");
        System.out.println("Actiontec address: " + Utils.getActiontecHost());

        Scanner scanner = new Scanner(System.in);

        while(true) {
            System.out.print("> ");
            String entry = scanner.nextLine();
            for(String next : entry.split(";")) {
                next = next.trim();
                if(!next.isEmpty()) {
                    if(next.startsWith(":"))
                        processInternalCmd(next.substring(1));
                    else
                        processCmd(next);
                }
            }
        }
    }

    private static void processInternalCmd(String command) {
        if(command.toLowerCase().startsWith("exit")) {
            System.exit(0);
        } else if(command.toLowerCase().startsWith("dump")) {
            String[] ss = command.trim().split(" ");
            try {
                String file = ss[1];
                String gs = ss[2].toLowerCase();

                if (!gs.matches("get|ge|g") && !gs.matches("set|se|s") && !gs.matches("do|d")) {
                    System.err.println("second argument must be get or set");
                    return;
                }

                Path src = ROOT_PATH.resolve(file);
                Path dump = ROOT_PATH.resolve("dump-" + src.getFileName().toString());

                System.out.println("dump beginning, may take a while");

                Utils.buildOptionHelpFile(src, dump, gs);

                System.out.println("successfully dumped help text to '" + dump.getFileName().toString() + "'");
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("format: dump <file> <get | set>");
            } catch (Throwable t) {
                System.err.println(t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        } else if(command.toLowerCase().startsWith("help")) {
            System.out.println(String.format("%-16s --%s", ":dump <file> <get|set|do>", "Dump help option text to a file"));
            System.out.println(String.format("%-16s --%s", ":exit", "Exit this process"));
            System.out.println(String.format("%-16s --%s", ":help", "View internal command descriptions"));
        }
    }

    private static void processCmd(String command) {
        for(int r = RETRY_COUNT; r > 0; --r) {
            try {
                if(command.contains("|")) {
                    int at = command.indexOf("| grep ");
                    if(at != -1) {
                        try {
                            String cmd = command.substring(0, at).trim();
                            Pattern filter = Pattern.compile(command.substring(at + "| grep ".length()));
                            String result = Utils.httpGetRequest(Utils.formatCommand(cmd));

                            Scanner scanner = new Scanner(result);
                            while (scanner.hasNextLine()) {
                                String line = scanner.nextLine();
                                if (filter.matcher(line).find())
                                    System.out.println(line);
                            }
                            return;
                        } catch (Throwable t) {
                            System.err.println("grep err: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                            return;
                        }
                    } else {
                        System.err.println("unknown pipe: " + command.substring(command.indexOf('|') + 1).trim());
                        return;
                    }
                } else {
                    System.out.println(String.valueOf(Utils.httpGetRequest(Utils.formatCommand(command))));
                    return;
                }
            } catch (IOException e) {
                if(r == 1)
                    System.err.println("failed to connect after " + RETRY_COUNT + " retries");
                else
                    System.err.println("connection timed out, retrying...");
            }
        }
    }
}
