import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Main {
    static class Job{
        int jobNumber;
        Process process;
        String command;
        boolean donePrinted;

        Job(int jobNumber, Process process, String command){
            this.jobNumber = jobNumber;
            this.process = process;
            this.command = command;
            this.donePrinted = false;
        }
    }

    public static void main(String[] args) throws Exception {
        Path currentDirectory = Paths.get(System.getProperty("user.dir"));
        List<Job> jobs = new ArrayList<>();

        try (Scanner sc = new Scanner(System.in)) {

            while (true) {

                System.out.print("$ ");
                if (!sc.hasNextLine()) {
                    break;
                }
                String command = sc.nextLine();
                if (command.isEmpty()) {
                    continue;
                }
                if (command.equals("exit")) {
                    break;
                }

                if (command.equals("pwd")) {
                    System.out.println(currentDirectory.toAbsolutePath());
                    reapJobs(jobs);
                    continue;
                }

                if (command.equals("jobs")) {
                    int size = jobs.size();

                    List<Job> jobsToRemove = new ArrayList<>();

                    for(int i = 0; i < jobs.size(); i++){
                        Job job = jobs.get(i);

                        char marker = ' ';

                        if(i == size - 1){
                            marker = '+';
                        }else if(i == size - 2){
                            marker = '-';
                        }

                        if(job.process.isAlive()){
                            System.out.printf(
                                "[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Running",
                                job.command
                            );
                        }else{
                            System.out.printf(
                                "[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Done",
                                job.command.replaceAll("\\s*&\\s*$", "")
                            );

                            jobsToRemove.add(job);
                        }
                    }

                    jobs.removeAll(jobsToRemove);

                    continue;
                }

                List<String> parts = parseCommand(command);                
                if(parts.isEmpty()){
                    continue;
                }

                boolean background = false;

                if(!parts.isEmpty() &&
                   parts.get(parts.size() - 1).equals("&")){

                    background = true;
                    parts.remove(parts.size() - 1);
                }

                List<List<String>> pipeline = splitPipeline(parts);

                if(pipeline.size() > 1){
                    executePipeline(
                        pipeline,
                        currentDirectory
                    );

                    reapJobs(jobs);
                    continue;
                }

                String stdoutFile = null;
                String stderrFile = null;
                boolean appendStdout = false;
                boolean appendStderr = false;


                for(int i = 0; i < parts.size(); i++){
                    if(parts.get(i).equals(">") ||
                    parts.get(i).equals("1>")){

                        stdoutFile = parts.get(i + 1);

                        parts = new ArrayList<>(
                            parts.subList(0, i)
                        );

                        break;
                    }

                    if(parts.get(i).equals(">>") ||
                    parts.get(i).equals("1>>")){

                        stdoutFile = parts.get(i + 1);
                        appendStdout = true;

                        parts = new ArrayList<>(
                            parts.subList(0, i)
                        );

                        break;
                    }

                    if(parts.get(i).equals("2>>")){
                        stderrFile = parts.get(i + 1);
                        appendStderr = true;

                        parts = new ArrayList<>(
                            parts.subList(0, i)
                        );

                        break;
                    }

                    if(parts.get(i).equals("2>")){
                        stderrFile = parts.get(i + 1);
                        parts = new ArrayList<>(
                            parts.subList(0, i)
                        );
                        break;
                    }
                }

                if(parts.get(0).equals("echo")){
                    String output = String.join(
                        " ",
                        parts.subList(1, parts.size())
                    );

                    if(stdoutFile != null){
                        if(appendStdout){
                            Files.writeString(
                                Paths.get(stdoutFile),
                                output + System.lineSeparator(),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                            );
                        }else{
                            Files.writeString(
                                Paths.get(stdoutFile),
                                output + System.lineSeparator()
                            );
                        }
                    }else{
                        System.out.println(output);
                    }
                    if(stderrFile != null){
                        if(appendStderr){
                            Files.writeString(
                                Paths.get(stderrFile),
                                "",
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                            );
                        }else{
                            Files.writeString(
                                Paths.get(stderrFile),
                                ""
                            );
                        }
                    }
                    
                    reapJobs(jobs);
                    continue;
                }

                if (command.startsWith("type ")) {
                    String arg = command.substring(5);

                if (arg.equals("echo")
                        || arg.equals("exit")
                        || arg.equals("type")
                        || arg.equals("pwd")
                        || arg.equals("cd")
                        || arg.equals("jobs")) {

                        System.out.println(arg + " is a shell builtin");
                        reapJobs(jobs);
                        continue;
                    }
                    Path executable = findExecutable(arg);

                    if (executable != null) {
                        System.out.println(arg + " is " + executable);
                    } else {
                        System.out.println(arg + ": not found");
                    }

                    reapJobs(jobs);
                    continue;
                }

                if(command.startsWith("cd ")){
                    String target = command.substring(3);
                    Path newDir;

                    if(target.equals("~")){
                        newDir = Paths.get(System.getenv("HOME"));
                    } else if(Paths.get(target).isAbsolute()){
                        newDir = Paths.get(target);
                    } else {
                        newDir = currentDirectory.resolve(target);
                    }

                    newDir = newDir.normalize();

                    if(Files.exists(newDir) && Files.isDirectory(newDir)){
                        currentDirectory = newDir;
                    } else {
                        System.out.println("cd: " + target + ": No such file or directory");
                    }

                    reapJobs(jobs);
                    continue;
                }

            Path executable = findExecutable(parts.get(0));

            if(executable != null){
                ProcessBuilder pb = new ProcessBuilder(parts)
                        .directory(currentDirectory.toFile());

                pb.redirectInput(
                    ProcessBuilder.Redirect.INHERIT
                );

            if(stdoutFile != null){
                if(appendStdout){
                    pb.redirectOutput(
                        ProcessBuilder.Redirect.appendTo(
                            new File(stdoutFile)
                        )
                    );
                }else{
                    pb.redirectOutput(
                        new File(stdoutFile)
                    );
                }
            }else{
                pb.redirectOutput(
                    ProcessBuilder.Redirect.INHERIT
                );
            }

                if(stderrFile != null){
                    if(appendStderr){
                        pb.redirectError(
                            ProcessBuilder.Redirect.appendTo(
                                new File(stderrFile)
                            )
                        );
                    }else{
                        pb.redirectError(
                            new File(stderrFile)
                        );
                    }
                }else{
                    pb.redirectError(
                        ProcessBuilder.Redirect.INHERIT
                    );
                }

                Process process = pb.start();

                if(background){
                    int jobNumber = getNextJobNumber(jobs);

                    jobs.add(
                        new Job(
                            jobNumber,
                            process,
                            command
                        )
                    );

                    System.out.println(
                        "[" + jobNumber + "] " +
                        process.pid()
                    );
                }else{
                    process.waitFor();
                }
                reapJobs(jobs);
                continue;
            }
                System.out.println(command + ": command not found");
                reapJobs(jobs);
            }
        }
    }

    private static Path findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path path = Paths.get(dir, command);

            if (Files.exists(path) && Files.isExecutable(path)) {
                return path;
            }
        }

        return null;
    }

    private static int getNextJobNumber(List<Job> jobs){
        int num = 1;

        while(true){
            boolean used = false;

            for(Job job : jobs){
                if(job.jobNumber == num){
                    used = true;
                    break;
                }
            }

            if(!used){
                return num;
            }

            num++;
        }
    }

    private static void executePipeline(
        List<List<String>> pipeline,
        Path currentDirectory
    ) throws Exception {
        boolean hasBuiltin = false;

        for(List<String> command : pipeline){
            if(command.isEmpty()){
                System.out.println(": command not found");
                return;
            }

            boolean builtin = isBuiltin(command.get(0));

            if(builtin){
                hasBuiltin = true;
            }else if(findExecutable(command.get(0)) == null){
                System.out.println(command.get(0) + ": command not found");
                return;
            }
        }

        if(!hasBuiltin){
            executeExternalPipeline(pipeline, currentDirectory);
            return;
        }

        byte[] input = null;

        for(int i = 0; i < pipeline.size(); i++){
            List<String> command = pipeline.get(i);
            boolean first = i == 0;
            boolean last = i == pipeline.size() - 1;

            if(isBuiltin(command.get(0))){
                input = runBuiltinForPipeline(command, currentDirectory)
                        .getBytes(StandardCharsets.UTF_8);

                if(last){
                    System.out.print(new String(input, StandardCharsets.UTF_8));
                }
            }else{
                input = runExternalForPipeline(
                    command,
                    input,
                    first,
                    last,
                    currentDirectory
                );
            }
        }
    }

    private static void executeExternalPipeline(
        List<List<String>> pipeline,
        Path currentDirectory
    ) throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();

        for(int i = 0; i < pipeline.size(); i++){
            ProcessBuilder builder = new ProcessBuilder(pipeline.get(i))
                    .directory(currentDirectory.toFile());

            if(i == 0){
                builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }

            if(i == pipeline.size() - 1){
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.add(builder);
        }

        List<Process> processes = ProcessBuilder.startPipeline(builders);
        Process lastProcess = processes.get(processes.size() - 1);

        lastProcess.waitFor();

        for(Process process : processes){
            if(process.isAlive()){
                process.destroy();

                if(!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)){
                    process.destroyForcibly();
                    process.waitFor();
                }
            }else{
                process.waitFor();
            }
        }
    }

    private static byte[] runExternalForPipeline(
        List<String> command,
        byte[] input,
        boolean first,
        boolean last,
        Path currentDirectory
    ) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(currentDirectory.toFile());

        if(input == null && first){
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }

        if(last){
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }

        builder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = builder.start();

        if(input != null){
            try(OutputStream stdin = process.getOutputStream()){
                stdin.write(input);
            }
        }else if(!first){
            process.getOutputStream().close();
        }

        byte[] output = new byte[0];

        if(!last){
            output = process.getInputStream().readAllBytes();
        }

        process.waitFor();
        return output;
    }

    private static List<List<String>> splitPipeline(List<String> parts){
        List<List<String>> pipeline = new ArrayList<>();
        List<String> command = new ArrayList<>();

        for(String part : parts){
            if(part.equals("|")){
                pipeline.add(command);
                command = new ArrayList<>();
            }else{
                command.add(part);
            }
        }

        pipeline.add(command);
        return pipeline;
    }

    private static boolean isBuiltin(String command){
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type")
                || command.equals("pwd")
                || command.equals("cd")
                || command.equals("jobs");
    }

    private static String runBuiltinForPipeline(
        List<String> command,
        Path currentDirectory
    ){
        String name = command.get(0);

        if(name.equals("echo")){
            return String.join(
                " ",
                command.subList(1, command.size())
            ) + System.lineSeparator();
        }

        if(name.equals("pwd")){
            return currentDirectory.toAbsolutePath() + System.lineSeparator();
        }

        if(name.equals("type")){
            if(command.size() < 2){
                return "";
            }

            String arg = command.get(1);

            if(isBuiltin(arg)){
                return arg + " is a shell builtin" + System.lineSeparator();
            }

            Path executable = findExecutable(arg);

            if(executable != null){
                return arg + " is " + executable + System.lineSeparator();
            }

            return arg + ": not found" + System.lineSeparator();
        }

        return "";
    }

    private static void reapJobs(List<Job> jobs){
        int size = jobs.size();

        for(int i = 0; i < jobs.size(); i++){
            Job job = jobs.get(i);

            if(!job.process.isAlive()){
                char marker = ' ';

                if(i == size - 1){
                    marker = '+';
                }else if(i == size - 2){
                    marker = '-';
                }

                System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.jobNumber,
                    marker,
                    "Done",
                    job.command.replaceAll("\\s*&\\s*$", "")
                );
            }
        }

        jobs.removeIf(job -> !job.process.isAlive());
    }

    private static List<String> parseCommand(String command){
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for(int i = 0; i < command.length(); i++){
            char c = command.charAt(i);
            
            if(inDoubleQuote && c == '\\'){
                if(i + 1 < command.length()){
                    char next = command.charAt(i + 1);

                    if(next == '"' || next == '\\'){
                        current.append(next);
                        i++;
                        continue;
                    }
                }

                current.append(c);
                continue;
            }

            if(c == '\'' && !inDoubleQuote){
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if(c == '"' && !inSingleQuote){
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if(c == '\\' &&
            !inSingleQuote &&
            !inDoubleQuote){

                if(i + 1 < command.length()){
                    current.append(command.charAt(i + 1));
                    i++;
                }

                continue;
            }            

            if(Character.isWhitespace(c) &&
            !inSingleQuote &&
            !inDoubleQuote){

                if(current.length() > 0){
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            }else if(c == '|' &&
            !inSingleQuote &&
            !inDoubleQuote){

                if(current.length() > 0){
                    tokens.add(current.toString());
                    current.setLength(0);
                }

                tokens.add(String.valueOf(c));
            }else{
                current.append(c);
            }
        }

        if(current.length() > 0){
            tokens.add(current.toString());
        }

        return tokens;
    }
}