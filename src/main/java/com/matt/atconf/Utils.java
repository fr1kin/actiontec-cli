package com.matt.atconf;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class Utils {
    public static final Pattern EXTRA_SHELL_HELP_GROUPS = Pattern.compile("(.*?)[\\s{2,}|\\t]+--\\s?(.*)");

    private static String actiontecHost = null;

    public static void setActiontecHost(String host) {
        actiontecHost = host;
    }

    public static String getActiontecHost() {
        return actiontecHost;
    }

    public static String formatCommand(String command) {
        return command.replaceAll("\\s", "&"); // replace all spaces with &
    }

    public static String httpGetRequest(String command) throws IOException {
        URL url = new URL(getActiontecHost() + "cmd.sh?" + command);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setDoOutput(true);
        connection.connect();

        try {
            Scanner scanner = new Scanner(connection.getInputStream()).useDelimiter("\\A");
            StringBuilder builder = new StringBuilder();

            while (scanner.hasNext()) builder.append(scanner.next());

            return builder.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public static void buildOptionHelpFile(Path path, Path out, String command) {
        if(!Files.exists(path)) {
            System.err.println("no such file '" + path.getFileName().toString() + "'");
            return;
        }

        String data;
        try {
            data = new String(Files.readAllBytes(path));
        } catch (IOException e) {
            System.err.println("failed to read file '" + path.getFileName().toString() + "'");
            return;
        }

        String[] commands = data.replaceAll("\r", "").split("\n");
        Map<String, String> cmdHelpPair = new LinkedHashMap<>();

        AtomicInteger longest = new AtomicInteger(0);
        requestAll(command, commands, 0, cmdHelpPair, longest, 0);

        final int len = longest.get() + 5;
        StringBuilder builder = new StringBuilder();

        cmdHelpPair.forEach((cmd, help) -> {
            builder.append(cmd);
            builder.append("\n{\n\t");
            String str = help.replaceAll("\n", "\n\t");
            if(str.endsWith("\t"))
                str = str.substring(0, str.length() - 1);
            builder.append(str);
            builder.append("}\n\n");
        });

        try {
            Files.write(out, builder.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            System.err.println("failed to write output file '" + out.getFileName().toString() + "'");
        }
    }

    private static void requestAll(String command, String[] commands, int index, Map<String, String> data, AtomicInteger longest, int timeout) {
        if(index >= 0 && index < commands.length) {
            String cmd = commands[index];
            String send = "mocap " + command + " --" + cmd.trim() + " help";
            try {
                data.put(cmd.trim(), httpGetRequest(formatCommand(send)));
                System.out.print('!');
                longest.set(Math.max(cmd.trim().length(), longest.get()));
                Thread.sleep(50); // don't overload the device
            } catch (IOException | InterruptedException e) {
                if(timeout < 5) {
                    System.err.print("T");
                    try {
                        Thread.sleep(1000); // wait a second for the device to come back
                    } catch (InterruptedException e1) {
                    }
                    requestAll(command, commands, index, data, longest, timeout + 1);
                    return;
                } else
                {
                    System.err.print("F");
                }
            }
            requestAll(command, commands, ++index, data, longest, 0);
        } else {
            System.out.print('\n');
        }
    }
}
