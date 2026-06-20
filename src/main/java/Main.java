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

    // ================= REAP JOBS =================

    static void reapJobs(Map<Integer, Job> backgroundJobs) {

        ArrayList<Integer> removeJobs = new ArrayList<>();

        ArrayList<Job> activeJobs = new ArrayList<>();

        for (Integer key : backgroundJobs.keySet()) {

            Job job = backgroundJobs.get(key);

            if (job != null) {
                activeJobs.add(job);
            }
        }

        activeJobs.sort((a, b) -> a.jobNumber - b.jobNumber);

        int total = activeJobs.size();

        for (int i = 0; i < total; i++) {

            Job job = activeJobs.get(i);

            if (!job.process.isAlive()) {

                String marker = " ";

                if (i == total - 1) {
                    marker = "+";
                }

                else if (i == total - 2) {
                    marker = "-";
                }

                String cmd = job.command
                        .replaceAll("\\s*&\\s*$", "");

                System.out.printf(
                        "[%d]%s  %-24s%s%n",
                        job.jobNumber,
                        marker,
                        "Done",
                        cmd
                );

                removeJobs.add(job.jobNumber);
            }
        }

        for (Integer id : removeJobs) {
            backgroundJobs.remove(id);
        }
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        File currentDirectory =
                new File(System.getProperty("user.dir"));

        Set<String> builtins = Set.of(
                "echo",
                "exit",
                "type",
                "pwd",
                "cd",
                "jobs"
        );

        Map<Integer, Job> backgroundJobs =
                new HashMap<>();

        while (true) {

            // automatic reap before prompt
            reapJobs(backgroundJobs);

            System.out.print("$ ");

            String input = scanner.nextLine();

            // ================= TOKENIZER =================

            ArrayList<String> partsList = new ArrayList<>();

            StringBuilder current = new StringBuilder();

            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;

            for (int i = 0; i < input.length(); i++) {

                char ch = input.charAt(i);

                // backslash
                if (ch == '\\') {

                    // outside quotes
                    if (!inSingleQuote && !inDoubleQuote) {

                        if (i + 1 < input.length()) {
                            current.append(input.charAt(i + 1));
                            i++;
                        }
                    }

                    // inside double quotes
                    else if (inDoubleQuote) {

                        if (i + 1 < input.length()) {

                            char next = input.charAt(i + 1);

                            if (next == '"' || next == '\\') {
                                current.append(next);
                                i++;
                            }

                            else {
                                current.append('\\');
                            }

                        } else {
                            current.append('\\');
                        }
                    }

                    // inside single quotes
                    else {
                        current.append('\\');
                    }
                }

                // single quote
                else if (ch == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                }

                // double quote
                else if (ch == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                // whitespace
                else if (Character.isWhitespace(ch)
                        && !inSingleQuote
                        && !inDoubleQuote) {

                    if (current.length() > 0) {

                        partsList.add(current.toString());

                        current.setLength(0);
                    }
                }

                else {
                    current.append(ch);
                }
            }

            if (current.length() > 0) {
                partsList.add(current.toString());
            }

            String[] parts =
                    partsList.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            // ================= REDIRECTION =================

            String outputFile = null;
            String errorFile = null;

            boolean appendOutput = false;
            boolean appendError = false;

            ArrayList<String> cleaned = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {

                if (parts[i].equals(">")
                        || parts[i].equals("1>")) {

                    outputFile = parts[i + 1];
                    appendOutput = false;
                    i++;
                }

                else if (parts[i].equals(">>")
                        || parts[i].equals("1>>")) {

                    outputFile = parts[i + 1];
                    appendOutput = true;
                    i++;
                }

                else if (parts[i].equals("2>")) {

                    errorFile = parts[i + 1];
                    appendError = false;
                    i++;
                }

                else if (parts[i].equals("2>>")) {

                    errorFile = parts[i + 1];
                    appendError = true;
                    i++;
                }

                else {
                    cleaned.add(parts[i]);
                }
            }

            parts = cleaned.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            // ================= BACKGROUND =================

            boolean background = false;

            if (parts[parts.length - 1].equals("&")) {

                background = true;

                String[] newParts =
                        new String[parts.length - 1];

                System.arraycopy(
                        parts,
                        0,
                        newParts,
                        0,
                        parts.length - 1
                );

                parts = newParts;
            }

            String command = parts[0];

            // ================= EXIT =================

            if (command.equals("exit")) {
                break;
            }

            // ================= PWD =================

            else if (command.equals("pwd")) {

                try {

                    if (outputFile != null) {

                        PrintStream ps =
                                new PrintStream(
                                        new FileOutputStream(
                                                outputFile,
                                                appendOutput
                                        )
                                );

                        ps.println(
                                currentDirectory.getAbsolutePath()
                        );

                        ps.close();

                    } else {

                        System.out.println(
                                currentDirectory.getAbsolutePath()
                        );
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
                    newDir = new File(
                            System.getenv("HOME")
                    );
                }

                else if (path.startsWith("/")) {
                    newDir = new File(path);
                }

                else {
                    newDir = new File(
                            currentDirectory,
                            path
                    );
                }

                try {

                    newDir =
                            newDir.getCanonicalFile();

                    if (newDir.exists()
                            && newDir.isDirectory()) {

                        currentDirectory = newDir;

                        System.setProperty(
                                "user.dir",
                                currentDirectory.getAbsolutePath()
                        );

                    } else {

                        System.err.println(
                                "cd: "
                                        + path
                                        + ": No such file or directory"
                        );
                    }

                } catch (Exception e) {

                    System.err.println(
                            "cd: "
                                    + path
                                    + ": No such file or directory"
                    );
                }
            }

            // ================= JOBS =================

            else if (command.equals("jobs")) {

                ArrayList<Job> activeJobs =
                        new ArrayList<>();

                for (Integer key : backgroundJobs.keySet()) {

                    Job job = backgroundJobs.get(key);

                    if (job != null
                            && job.process.isAlive()) {

                        activeJobs.add(job);
                    }
                }

                activeJobs.sort(
                        (a, b) ->
                                a.jobNumber - b.jobNumber
                );

                int total = activeJobs.size();

                for (int i = 0; i < total; i++) {

                    Job job = activeJobs.get(i);

                    String marker = " ";

                    if (i == total - 1) {
                        marker = "+";
                    }

                    else if (i == total - 2) {
                        marker = "-";
                    }

                    String cmd =
                            job.command
                                    .replaceAll(
                                            "\\s*&\\s*$",
                                            ""
                                    )
                                    + " &";

                    System.out.printf(
                            "[%d]%s  %-24s%s%n",
                            job.jobNumber,
                            marker,
                            "Running",
                            cmd
                    );
                }
            }

            // ================= ECHO =================

            else if (command.equals("echo")) {

                StringBuilder out =
                        new StringBuilder();

                for (int i = 1; i < parts.length; i++) {

                    out.append(parts[i]);

                    if (i != parts.length - 1) {
                        out.append(" ");
                    }
                }

                try {

                    if (outputFile != null) {

                        PrintStream ps =
                                new PrintStream(
                                        new FileOutputStream(
                                                outputFile,
                                                appendOutput
                                        )
                                );

                        ps.println(out);

                        ps.close();

                    } else {

                        System.out.println(out);
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

                    System.out.println(
                            cmd + " is a shell builtin"
                    );

                    continue;
                }

                String[] paths =
                        System.getenv("PATH")
                                .split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {

                    File file =
                            new File(dir, cmd);

                    if (file.exists()
                            && file.canExecute()) {

                        System.out.println(
                                cmd
                                        + " is "
                                        + file.getAbsolutePath()
                        );

                        found = true;

                        break;
                    }
                }

                if (!found) {
                    System.out.println(
                            cmd + ": not found"
                    );
                }
            }

            // ================= EXTERNAL =================

            else {

                String[] paths =
                        System.getenv("PATH")
                                .split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {

                    File file =
                            new File(dir, command);

                    if (file.exists()
                            && file.canExecute()) {

                        try {

                            String[] execArgs =
                                    new String[parts.length];

                            execArgs[0] = command;

                            for (int i = 1;
                                 i < parts.length;
                                 i++) {

                                execArgs[i] = parts[i];
                            }

                            ProcessBuilder pb =
                                    new ProcessBuilder(execArgs);

                            pb.directory(currentDirectory);

                            Process process =
                                    pb.start();

                            // ================= BACKGROUND EXEC =================

                            if (background) {

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

                                // RECYCLE JOB NUMBERS

                                int currentJob = 1;

                                while (backgroundJobs.containsKey(currentJob)) {
                                    currentJob++;
                                }

                                backgroundJobs.put(
                                        currentJob,
                                        new Job(
                                                currentJob,
                                                process,
                                                input
                                        )
                                );

                                System.out.println(
                                        "[" + currentJob + "] "
                                                + process.pid()
                                );
                            }

                            // ================= FOREGROUND EXEC =================

                            else {

                                // stdout
                                if (outputFile != null) {

                                    FileOutputStream fos =
                                            new FileOutputStream(
                                                    outputFile,
                                                    appendOutput
                                            );

                                    process.getInputStream()
                                            .transferTo(fos);

                                    fos.close();

                                } else {

                                    process.getInputStream()
                                            .transferTo(System.out);
                                }

                                // stderr
                                if (errorFile != null) {

                                    FileOutputStream efos =
                                            new FileOutputStream(
                                                    errorFile,
                                                    appendError
                                            );

                                    process.getErrorStream()
                                            .transferTo(efos);

                                    efos.close();

                                } else {

                                    process.getErrorStream()
                                            .transferTo(System.err);
                                }

                                process.waitFor();
                            }

                            found = true;

                            break;

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (!found) {
                    System.err.println(
                            command + ": not found"
                    );
                }
            }
        }

        scanner.close();
    }
}