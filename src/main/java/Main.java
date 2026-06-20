import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        File currentDirectory = new File(System.getProperty("user.dir"));

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");

        while (true) {

            System.out.print("$ ");

            String input = scanner.nextLine();

            // ================= PARSER =================

            ArrayList<String> partsList = new ArrayList<>();

            StringBuilder current = new StringBuilder();

            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;

            for (int i = 0; i < input.length(); i++) {

                char ch = input.charAt(i);

                // BACKSLASH HANDLING
                if (ch == '\\') {

                    // Outside quotes
                    if (!inSingleQuote && !inDoubleQuote) {

                        if (i + 1 < input.length()) {
                            current.append(input.charAt(i + 1));
                            i++;
                        }
                    }

                    // Inside double quotes
                    else if (inDoubleQuote) {

                        if (i + 1 < input.length()) {

                            char next = input.charAt(i + 1);

                            if (next == '"' || next == '\\') {
                                current.append(next);
                                i++;
                            } else {
                                current.append('\\');
                            }

                        } else {
                            current.append('\\');
                        }
                    }

                    // Inside single quotes
                    else {
                        current.append('\\');
                    }
                }

                // SINGLE QUOTES
                else if (ch == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                }

                // DOUBLE QUOTES
                else if (ch == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                // SPACES OUTSIDE QUOTES
                else if (Character.isWhitespace(ch)
                        && !inSingleQuote
                        && !inDoubleQuote) {

                    if (current.length() > 0) {
                        partsList.add(current.toString());
                        current.setLength(0);
                    }
                }

                // NORMAL CHARACTERS
                else {
                    current.append(ch);
                }
            }

            if (current.length() > 0) {
                partsList.add(current.toString());
            }

            String[] parts = partsList.toArray(new String[0]);

            // ================= OUTPUT REDIRECTION =================

            String outputFile = null;

            ArrayList<String> cleanedParts = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {

                if (parts[i].equals(">") || parts[i].equals("1>")) {

                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                    }

                    break;
                }

                cleanedParts.add(parts[i]);
            }

            parts = cleanedParts.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            String command = parts[0];

            // ================= EXIT =================

            if (command.equals("exit")) {
                break;
            }

            // ================= PWD =================

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
            }

            // ================= CD =================

            else if (command.equals("cd")) {

                if (parts.length < 2) {
                    continue;
                }

                String path = parts[1];

                File newDir;

                // HOME DIRECTORY
                if (path.equals("~")) {
                    newDir = new File(System.getenv("HOME"));
                }

                // ABSOLUTE PATH
                else if (path.startsWith("/")) {
                    newDir = new File(path);
                }

                // RELATIVE PATH
                else {
                    newDir = new File(currentDirectory, path);
                }

                try {
                    newDir = newDir.getCanonicalFile();
                } catch (Exception e) {
                    System.out.println("cd: " + path + ": No such file or directory");
                    continue;
                }

                if (newDir.exists() && newDir.isDirectory()) {

                    currentDirectory = newDir;

                    System.setProperty(
                            "user.dir",
                            currentDirectory.getAbsolutePath());

                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            // ================= ECHO =================

            else if (command.equals("echo")) {

                StringBuilder output = new StringBuilder();

                for (int i = 1; i < parts.length; i++) {

                    output.append(parts[i]);

                    if (i != parts.length - 1) {
                        output.append(" ");
                    }
                }

                try {

                    if (outputFile != null) {

                        PrintStream fileOut =
                                new PrintStream(new FileOutputStream(outputFile));

                        fileOut.println(output);

                        fileOut.close();

                    } else {
                        System.out.println(output);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // ================= TYPE =================

            else if (command.equals("type")) {

                if (parts.length < 2) {
                    continue;
                }

                String cmd = parts[1];

                // BUILTIN CHECK
                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                // PATH SEARCH
                String pathEnv = System.getenv("PATH");

                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {

                    File file = new File(dir, cmd);

                    if (file.exists() && file.canExecute()) {

                        System.out.println(
                                cmd + " is " + file.getAbsolutePath());

                        found = true;

                        break;
                    }
                }

                if (!found) {
                    System.out.println(cmd + ": not found");
                }
            }

            // ================= EXTERNAL COMMAND =================

            else {

                String pathEnv = System.getenv("PATH");

                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {

                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {

                        try {

                            String[] execArgs = new String[parts.length];

                            execArgs[0] = command;

                            for (int i = 1; i < parts.length; i++) {
                                execArgs[i] = parts[i];
                            }

                            Process process = Runtime.getRuntime().exec(
                                    execArgs,
                                    null,
                                    file.getParentFile()
                            );

                            if (outputFile != null) {

                                FileOutputStream fos =
                                        new FileOutputStream(outputFile);

                                process.getInputStream().transferTo(fos);

                                fos.close();

                            } else {

                                process.getInputStream().transferTo(System.out);
                            }

                            process.getErrorStream().transferTo(System.err);

                            process.waitFor();

                            found = true;

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        break;
                    }
                }

                if (!found) {
                    System.out.println(command + ": not found");
                }
            }
        }

        scanner.close();
    }
}