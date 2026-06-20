import java.io.File;
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

          

            ArrayList<String> partsList = new ArrayList<>();

            StringBuilder current = new StringBuilder();

            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;

            for (int i = 0; i < input.length(); i++) {

                char ch = input.charAt(i);

                if (ch == '\\') {

                    if (!inSingleQuote && !inDoubleQuote) {

                        if (i + 1 < input.length()) {
                            current.append(input.charAt(i + 1));
                            i++;
                        }
                    }

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

               
                    else {
                        current.append('\\');
                    }
                }

                else if (ch == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                }

         
                else if (ch == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

             
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

            String[] parts = partsList.toArray(new String[0]);

            if (parts.length == 0) {
                continue;
            }

            String command = parts[0];


            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
            }

    

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

      

            else if (command.equals("echo")) {

                StringBuilder output = new StringBuilder();

                for (int i = 1; i < parts.length; i++) {

                    output.append(parts[i]);

                    if (i != parts.length - 1) {
                        output.append(" ");
                    }
                }

                System.out.println(output);
            }

            

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


            else {

                String pathEnv = System.getenv("PATH");

                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {

                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {

                        try {

                            parts[0] = file.getAbsolutePath();

                            ProcessBuilder pb =
                                    new ProcessBuilder(parts);

                            pb.directory(currentDirectory);

                            pb.inheritIO();

                            Process process = pb.start();

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