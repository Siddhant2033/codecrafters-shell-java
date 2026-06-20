import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {

    static class Job {

        int jobNumber;
        Process process;
        String command;

        Job(int jobNumber, Process process, String command) {
            this.jobNumber = jobNumber;
            this.process = process;
            this.command = command;
        }
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        File currentDirectory = new File(System.getProperty("user.dir"));

        int jobCounter = 1;

        Map<Integer, Job> backgroundJobs = new HashMap<>();

        Set<String> builtins = Set.of(
                "echo",
                "exit",
                "type",
                "pwd",
                "cd",
                "jobs"
        );

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

            // ================= REDIRECTION =================

            String outputFile = null;
            String errorFile = null;

            boolean appendOutput = false;
            boolean appendError = false;

            ArrayList<String> cleanedParts = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {

                // stdout overwrite
                if (parts[i].equals(">") || parts[i].equals("1>")) {

                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = false;
                    }

                    i++;
                }

                // stdout append
                else if (parts[i].equals(">>") || parts[i].equals("1>>")) {

                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = true;
                    }

                    i++;
                }

                // stderr overwrite
                else if (parts[i].equals("2>")) {

                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = false;
                    }

                    i++;
                }

                // stderr append
                else if (parts[i].equals("2>>")) {

                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = true;
                    }

                    i++;
                }

                else {
                    cleanedParts.add(parts[i]);
                }
            }

            parts = cleanedParts.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            String command = parts[0];

            // ================= BACKGROUND =================

            boolean runInBackground = false;

            if (parts[parts.length - 1].equals("&")) {

                runInBackground = true;

                String[] newParts = new String[parts.length - 1];

                System.arraycopy(parts, 0, newParts, 0, parts.length - 1);

                parts = newParts;

                command = parts[0];
            }

            // ================= EXIT =================

            if (command.equals("exit")) {
                break;
            }

            // ================= PWD =================

            else if (command.equals("pwd")) {

                try {

                    if (outputFile != null) {

                        PrintStream out = new PrintStream(
                                new FileOutputStream(outputFile, appendOutput)
                        );

                        out.println(currentDirectory.getAbsolutePath());

                        out.close();

                    } else {
                        System.out.println(currentDirectory.getAbsolutePath());
                    }

                    if (errorFile != null) {
                        new PrintStream(
                                new FileOutputStream(errorFile, appendError)
                        ).close();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // ================= CD =================

            else if (command.equals("cd")) {

                if (parts.length < 2) {
                    continue;
                }

                String path = parts[1];

                File newDir;

                if (path.equals("~")) {
                    newDir = new File(System.getenv("HOME"));
                }

                else if (path.startsWith("/")) {
                    newDir = new File(path);
                }

                else {
                    newDir = new File(currentDirectory, path);
                }

                try {
                    newDir = newDir.getCanonicalFile();
                }

                catch (Exception e) {

                    try {

                        if (errorFile != null) {

                            PrintStream err = new PrintStream(
                                    new FileOutputStream(errorFile, appendError)
                            );

                            err.println("cd: " + path + ": No such file or directory");

                            err.close();

                        } else {
                            System.err.println("cd: " + path + ": No such file or directory");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    continue;
                }

                if (newDir.exists() && newDir.isDirectory()) {

                    currentDirectory = newDir;

                    System.setProperty(
                            "user.dir",
                            currentDirectory.getAbsolutePath());

                } else {

                    try {

                        if (errorFile != null) {

                            PrintStream err = new PrintStream(
                                    new FileOutputStream(errorFile, appendError)
                            );

                            err.println("cd: " + path + ": No such file or directory");

                            err.close();

                        } else {
                            System.err.println("cd: " + path + ": No such file or directory");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            // ================= JOBS =================

            else if (command.equals("jobs")) {

                for (Job job : backgroundJobs.values()) {

                    if (job.process.isAlive()) {

                        System.out.printf(
                                "[%d]+  %-24s %s%n",
                                job.jobNumber,
                                "Running",
                                job.command + " &"
                        );
                    }
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

                    if (errorFile != null) {

                        PrintStream err = new PrintStream(
                                new FileOutputStream(errorFile, appendError)
                        );

                        err.close();
                    }

                    if (outputFile != null) {

                        PrintStream fileOut = new PrintStream(
                                new FileOutputStream(outputFile, appendOutput)
                        );

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

                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

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

                            ProcessBuilder pb = new ProcessBuilder(execArgs);

                            pb.directory(currentDirectory);

                            Process process = pb.start();

                            // ================= BACKGROUND =================

                            if (runInBackground) {

                                new Thread(() -> {
                                    try {
                                        process.getInputStream()
                                                .transferTo(System.out);
                                    } catch (Exception ignored) {
                                    }
                                }).start();

                                new Thread(() -> {
                                    try {
                                        process.getErrorStream()
                                                .transferTo(System.err);
                                    } catch (Exception ignored) {
                                    }
                                }).start();

                                int currentJob = jobCounter++;

                                backgroundJobs.put(
                                        currentJob,
                                        new Job(
                                                currentJob,
                                                process,
                                                input
                                        )
                                );

                                System.out.println(
                                        "[" + currentJob + "] " + process.pid()
                                );
                            }

                            // ================= FOREGROUND =================

                            else {

                                // STDOUT
                                if (outputFile != null) {

                                    FileOutputStream fos =
                                            new FileOutputStream(
                                                    outputFile,
                                                    appendOutput);

                                    process.getInputStream().transferTo(fos);

                                    fos.close();

                                } else {

                                    process.getInputStream()
                                            .transferTo(System.out);
                                }

                                // STDERR
                                if (errorFile != null) {

                                    FileOutputStream errFos =
                                            new FileOutputStream(
                                                    errorFile,
                                                    appendError);

                                    process.getErrorStream().transferTo(errFos);

                                    errFos.close();

                                } else {

                                    process.getErrorStream()
                                            .transferTo(System.err);
                                }

                                process.waitFor();
                            }

                            found = true;

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        break;
                    }
                }

                if (!found) {

                    String result = command + ": not found";

                    try {

                        if (errorFile != null) {

                            PrintStream err = new PrintStream(
                                    new FileOutputStream(errorFile, appendError)
                            );

                            err.println(result);

                            err.close();

                        } else {
                            System.err.println(result);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        scanner.close();
    }
}