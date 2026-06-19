import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");
        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine().trim();

            if (input.equals("exit")) {
                break;
            }

            else if (input.startsWith("cd ")) {

                String path = input.substring(3);

                File newDir;

                if (path.startsWith("/")) {
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
                    System.setProperty("user.dir", currentDirectory.getAbsolutePath());
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            else if (input.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
            }

            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            else if (input.startsWith("type ")) {
                String command = input.substring(5);

                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {
                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {
                        System.out.println(command + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println(command + ": not found");
                }
            } else {

                String[] parts = input.split(" ");
                String command = parts[0];

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {
                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {
                        try {

                            ProcessBuilder pb = new ProcessBuilder(parts);

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