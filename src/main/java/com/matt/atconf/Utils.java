package com.matt.atconf;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final Pattern EXTRA_SHELL_HELP_GROUPS = Pattern.compile("(.*?)[\\s{2,}|\\t]+--\\s?(.*)");
    public static final Pattern EXTRACT_BINARY = Pattern.compile("([0-9a-f]{8})\\s|\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})\\s([0-9A-F|\\s\\s]{2})");

    private static String actiontecHost = null;

    public static void setActiontecHost(String host) {
        actiontecHost = host;
    }

    public static String getActiontecHost() {
        return actiontecHost;
    }

    public static long parseAddress(String addr) {
        return Long.valueOf(addr.startsWith("0x") ? addr.substring(2) : addr, addr.startsWith("0x") ? 16 : 10);
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

    public static void dumpMemoryRangeToFile(long startAddr, long endAddr, String type, Path dump) {
        final long MEM_STEP = 0x80;

        startAddr &= 4294967280L;
        endAddr &= 4294967280L;

        startAddr = clampAddress32(startAddr);
        endAddr = clampAddress32(endAddr);

        if(startAddr >= endAddr)
            return;

        int reqs = (int)(((endAddr - startAddr) / MEM_STEP) + 1L);

        System.out.printf("running %s from 0x%X - 0x%X (%d requests)\n", type, startAddr, endAddr, reqs);

        for(int i = 0, r = 0; i < reqs; ++i) {
            long addr = startAddr + (i * MEM_STEP);
            try {
                String response = httpGetRequest(formatCommand(type + " " + addr));
                r = 0; // reset retry counter on success
                System.out.print("!");
                try {
                    Files.write(dump, response.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Throwable t) {} // ignore
            } catch (Throwable t) {
                if(r < 5) {
                    System.out.print("T");
                    ++r;
                    --i;
                } else {
                    System.err.println("\nfailed after 5 retries");
                    return;
                }
            }
        }
        System.out.println();
        System.out.println("success");
    }

    public static void createBinaryFileFromMemoryDump(long startAddr, long endAddr, Path in, Path out) {
        try {
            Files.write(out, getDumpBytes(startAddr, endAddr, in), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            System.out.println("conversion successful");
        } catch (Throwable t) {
            System.err.println("error converting dump: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    private static byte[] getDumpBytes(long startAddr, long endAddr, Path dump) throws IOException {
        Scanner scanner = new Scanner(new String(Files.readAllBytes(dump)));

        int count = 0;
        ByteBuffer buffer = ByteBuffer.allocate((int)Files.size(dump)); // should be more than enough because we wont use all the data this file uses
        l0: while(scanner.hasNextLine()) {
            String next = scanner.nextLine();
            if(next.trim().isEmpty())
                continue;

            Matcher matcher = EXTRACT_BINARY.matcher(next);
            if(matcher.find()) {
                long address = Long.parseUnsignedLong(matcher.group(1), 16);
                if(matcher.find()) {
                    for (int i = 0; i < 16; ++i) {
                        if (endAddr < address + i)
                            break l0; // stop processing here
                        else if (address + i >= startAddr) {
                            String grp = matcher.group(i + 2);
                            byte value;
                            if (grp.matches("\\s{2}")) {
                                if (count == 0) continue;
                                value = Byte.MAX_VALUE;
                            } else value = (byte) Integer.parseUnsignedInt(grp, 16);

                            buffer.put(value);
                            ++count;
                        }
                    }
                }
            }
        }
        return Arrays.copyOfRange(buffer.array(), 0, count);
    }

    private static long clampAddress32(long address) {
        return Math.max(0L, Math.min(address, 4294967295L));
    }
}
